# Offline workflows

## Compare release captures

Use Python 3.10 or newer and Git to build a brief from two local commits.
Supply check outcomes you have recorded. Use `not_run` for a check you know
was skipped. Omit an unknown outcome so Outlands can display **Not recorded**.

```powershell
python scripts/build-change-brief.py --repo ../example-project --base main --target release-candidate --title "Release candidate" --check "Build=passed" --check "Device test=not_run" --pr-url https://github.com/example-org/example-project/pull/17 --output ../change-brief.json
```

Omit `--output` for a preview. Use a new output path outside the repository
to save the same JSON. Review the changed paths before transferring the file.
To compare two captures, use the same PR link and distinct capture times.

On Android, choose **Home → Change Library → Import change brief**. The library
keeps the two latest captures per PR in app-private storage. **Compare briefs**
shows check transitions and paths that entered or left the changed-path lists.

## Review readiness and save a handoff

Open a PR brief, choose **Release readiness → Edit checklist**, and enter one
exact required check name per line. **Readiness Queue** in Home shows the latest
captured state and can filter to **Needs review only**. **Mark reviewed** records
an optional local note for that capture and checklist. A newer capture or a
different checklist marks the last review **Outdated**.

From a comparison, use **Review release handoff** to preview and save Markdown
to a local document. The default export uses capture times and counts.
Readiness counts, check names, changed paths, and commit IDs each have separate
options. The PR URL, titles, and local review note stay outside the Markdown.

## Verify an evidence handoff

```powershell
python scripts/verify-evidence-handoff.py handoff.zip --receipt receipt.json
```

The verifier reads the ZIP in place and checks the declared file sizes and
hashes. A receipt from a separate trusted channel identifies the expected ZIP.
Signed receipts require the optional Python `cryptography` package and
`--trusted-signer-sha256` with an independently verified signer fingerprint.

## Analyze local events

```powershell
python scripts/analyze-local-events.py auth.jsonl http.jsonl app.jsonl
```

Each JSONL record requires a timezone-aware ISO 8601 `ts` and a `kind`.

| Kind | Required fields |
| --- | --- |
| `auth` | `actor`, `client`, `result` with `success` or `failure` |
| `http` | `service`, `route`, `request`, integer `status` |
| `app` | `service`, `request`, `level`, `event` |

The JSON output groups failed authentications and HTTP 5xx bursts within
60-second windows. Matching error records can provide related context.
It reads only the files you provide. Review the local output before sharing
because it can contain input labels and timestamps.

