#!/usr/bin/env python3
"""Build an Outlands change brief from two local Git commits."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unicodedata


MAX_BYTES = 32 * 1024
MAX_PATHS = 200
MAX_CHECKS = 50
CHECK_STATES = {"passed", "failed", "not_run"}
NONDISPLAY_CATEGORIES = {"Cc", "Cf", "Cs", "Zl", "Zp"}
GITHUB_PR = re.compile(
    r"https://github\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}/pull/[1-9][0-9]{0,11}"
)


def text(value: str, limit: int, label: str) -> str:
    if not value.strip() or any(unicodedata.category(ch) in NONDISPLAY_CATEGORIES for ch in value):
        raise ValueError(f"{label} must be a nonempty, single line of displayable text.")
    if len(value.encode("utf-16-le")) // 2 > limit:
        raise ValueError(f"{label} exceeds the {limit}-character brief limit.")
    return value


def check_results(values: list[str]) -> list[dict[str, str]]:
    if len(values) > MAX_CHECKS:
        raise ValueError(f"A brief can record up to {MAX_CHECKS} checks.")
    checks = []
    seen = set()
    for entry in values:
        name, separator, status = entry.rpartition("=")
        if not separator or status not in CHECK_STATES:
            raise ValueError("Use --check 'Check name=passed', 'Check name=failed', or 'Check name=not_run'.")
        name = text(name.strip(), 100, "Check name")
        if name in seen:
            raise ValueError("Check names must be distinct.")
        seen.add(name)
        checks.append({"name": name, "status": status})
    return checks


class LocalGit:
    def __init__(self, repository: Path):
        self.arguments = ["git", "--no-pager", "--no-replace-objects", "-C", str(repository.resolve())]
        self.environment = os.environ.copy()
        for name in (
            "GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
            "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_NAMESPACE",
        ):
            self.environment.pop(name, None)
        self.environment.update(
            GIT_NO_LAZY_FETCH="1", GIT_ALLOW_PROTOCOL="", GIT_OPTIONAL_LOCKS="0", GIT_TERMINAL_PROMPT="0"
        )

    def read(self, *arguments: str) -> bytes:
        result = subprocess.run(
            [*self.arguments, *arguments], env=self.environment, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if result.returncode:
            diagnostic = result.stderr.decode("utf-8", errors="replace").strip()
            raise ValueError(diagnostic or f"Git {arguments[0]} failed.")
        return result.stdout

    def commit(self, reference: str) -> str:
        commit = self.read("rev-parse", "--verify", "--end-of-options", f"{reference}^{{commit}}")
        return commit.decode("ascii").strip()

    def changed_paths(self, base: str, target: str) -> list[str]:
        data = self.read(
            "diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--ignore-submodules=none",
            "--name-only", "-z", base, target, "--",
        )
        if not data:
            return []
        if not data.endswith(b"\0"):
            raise ValueError("Git returned an invalid changed-path list.")
        paths = data[:-1].split(b"\0")
        if len(paths) > MAX_PATHS:
            raise ValueError(f"Git returned {len(paths)} paths. Choose a range with at most {MAX_PATHS}.")
        decoded = []
        for name in paths:
            try:
                path = text(name.decode("utf-8"), 240, "Changed path")
            except UnicodeDecodeError as error:
                raise ValueError("A changed path is not a UTF-8 filename.") from error
            if "\\" in path or re.match(r"[A-Za-z]:", path) or any(
                part in {"", ".", ".."} for part in path.split("/")
            ):
                raise ValueError("A changed path cannot be represented as a repository-relative brief path.")
            decoded.append(path)
        return sorted(decoded)

    def protected_directories(self) -> list[Path]:
        metadata = self.read("rev-parse", "--path-format=absolute", "--git-dir", "--git-common-dir")
        protected = [Path(line).resolve() for line in metadata.decode("utf-8").splitlines()]
        if self.read("rev-parse", "--is-inside-work-tree").strip() == b"true":
            root = self.read("rev-parse", "--show-toplevel").decode("utf-8").rstrip("\r\n")
            protected.append(Path(root).resolve())
        return protected


def build_brief(options: argparse.Namespace, git: LocalGit) -> bytes:
    title = text(options.title.strip(), 120, "Title")
    checks = check_results(options.check)
    url = options.pr_url
    if url is not None and (not GITHUB_PR.fullmatch(url) or "/./" in url or "/../" in url):
        raise ValueError("Use a GitHub pull request permalink such as https://github.com/owner/repo/pull/123.")
    base = git.commit(options.base)
    target = git.commit(options.target)
    paths = git.changed_paths(base, target)
    brief = {
        "version": 1,
        "title": title,
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z"),
        "baseCommit": base,
        "targetCommit": target,
        "changedPaths": paths,
        "checks": checks,
    }
    if url is not None:
        brief["pullRequestUrl"] = url
    payload = (json.dumps(brief, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if len(payload) > MAX_BYTES:
        raise ValueError("The brief exceeds 32 KiB. Choose a smaller change range or shorten the check names.")
    return payload


def save_new(output: Path, payload: bytes) -> None:
    descriptor, name = tempfile.mkstemp(prefix=".outlands-brief-", dir=output.parent)
    staging = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as file:
            file.write(payload)
        if os.name == "nt":
            staging.rename(output)
        else:
            os.link(staging, output)
    finally:
        staging.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True, help="Local Git repository or worktree")
    parser.add_argument("--base", required=True, help="Reference for the base commit")
    parser.add_argument("--target", required=True, help="Reference for the target commit")
    parser.add_argument("--title", required=True, help="Title to show in Change Library")
    parser.add_argument("--check", action="append", default=[], metavar="NAME=STATE",
                        help="Recorded check result, repeatable (passed, failed, not_run)")
    parser.add_argument("--pr-url", help="Optional GitHub PR permalink for grouping captures")
    parser.add_argument("--output", type=Path, help="New JSON file outside the repository. Omit to preview only.")
    options = parser.parse_args()

    try:
        git = LocalGit(options.repo)
        payload = build_brief(options, git)
        output = options.output.resolve() if options.output is not None else None
        if output is not None and any(output.is_relative_to(directory) for directory in git.protected_directories()):
            raise ValueError("Choose an output file outside the Git repository and its worktree.")
        sys.stdout.buffer.write(payload)
        sys.stdout.buffer.flush()
        if output is not None:
            save_new(output, payload)
            print(f"Saved {output}", file=sys.stderr)
    except (OSError, ValueError) as error:
        print(f"Brief Builder: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
