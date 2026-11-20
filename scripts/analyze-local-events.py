#!/usr/bin/env python3
"""Analyze local authentication, HTTP, and application JSONL events."""

import argparse
from collections import defaultdict, deque
from datetime import datetime, timezone
import json
from pathlib import Path
import sys


REQUIRED = {
    "auth": ("actor", "client", "result"),
    "http": ("service", "route", "request"),
    "app": ("service", "request", "level", "event"),
}


def load_events(paths):
    """Validate each local record and normalize timestamps before sorting."""
    events = []
    for path in paths:
        with Path(path).open(encoding="utf-8") as source:
            for line_number, line in enumerate(source, 1):
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                    if not isinstance(record, dict):
                        raise ValueError("record must be an object")
                    kind = record.get("kind")
                    if kind not in REQUIRED:
                        raise ValueError("unknown event kind")
                    for key in ("ts",) + REQUIRED[kind]:
                        if not isinstance(record.get(key), str) or not record[key]:
                            raise ValueError(f"{key} must be nonempty text")
                    instant = datetime.fromisoformat(record["ts"].replace("Z", "+00:00"))
                    if instant.utcoffset() is None:
                        raise ValueError("timestamp needs a UTC offset")
                    if kind == "auth" and record["result"] not in ("success", "failure"):
                        raise ValueError("unknown authentication result")
                    if kind == "http":
                        status = record.get("status")
                        if type(status) is not int or not 100 <= status <= 599:
                            raise ValueError("HTTP status must be an integer from 100 to 599")
                    events.append((instant.astimezone(timezone.utc), record))
                except (ValueError, TypeError) as error:
                    raise ValueError(f"{Path(path).name}:{line_number}: {error}") from error
    return sorted(events, key=lambda event: event[0])


def stamp(instant):
    return instant.isoformat().replace("+00:00", "Z")


def bursts(events, rule, keys, matches, threshold, window_seconds):
    """One finding per group episode; a quiet gap longer than the window resets it."""
    windows = defaultdict(deque)
    active = {}
    findings = []
    for instant, record in events:
        if not matches(record):
            continue
        group = tuple(record[key] for key in keys)
        window = windows[group]
        while window and (instant - window[0][0]).total_seconds() > window_seconds:
            window.popleft()
        if not window:
            active.pop(group, None)
        window.append((instant, record))
        if len(window) < threshold:
            continue
        finding = active.get(group)
        if finding is None:
            finding = {
                "rule": rule,
                "group": dict(zip(keys, group)),
                "first_seen": stamp(window[0][0]),
                "last_seen": stamp(instant),
                "peak_count": len(window),
                "window_seconds": window_seconds,
                "_requests": set(),
            }
            active[group] = finding
            findings.append(finding)
        finding["last_seen"] = stamp(instant)
        finding["peak_count"] = max(finding["peak_count"], len(window))
        finding["_requests"].update(item.get("request") for _, item in window if item.get("request"))
    return findings


def analyze(events):
    """Fixed lab rules, deliberately grouped to avoid merging unrelated traffic."""
    findings = bursts(
        events, "failed_login_burst", ("actor", "client"),
        lambda row: row["kind"] == "auth" and row["result"] == "failure", 5, 60,
    )
    findings += bursts(
        events, "http_5xx_burst", ("service", "route"),
        lambda row: row["kind"] == "http" and 500 <= row["status"] <= 599, 3, 60,
    )
    for finding in findings:
        requests = finding.pop("_requests")
        if finding["rule"] == "http_5xx_burst":
            first = datetime.fromisoformat(finding["first_seen"].replace("Z", "+00:00"))
            last = datetime.fromisoformat(finding["last_seen"].replace("Z", "+00:00"))
            linked = [row for instant, row in events if row["kind"] == "app"
                      and first <= instant <= last
                      and row["service"] == finding["group"]["service"]
                      and row["request"] in requests and row["level"].upper() == "ERROR"]
            finding["linked_app_errors"] = len(linked)
            finding["app_error_types"] = sorted({row["event"] for row in linked})
    findings.sort(key=lambda finding: (finding["first_seen"], finding["rule"]))
    return {"records_read": len(events), "findings": findings}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+", help="local JSONL event files")
    args = parser.parse_args(argv)
    try:
        report = analyze(load_events(args.files))
    except (OSError, ValueError) as error:
        print(f"Input error: {error}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
