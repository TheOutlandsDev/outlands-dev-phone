# Known limitations

## Native Android Codex

The evaluated native CLI started and printed version/help output. Its login was not completed, and its sandbox test failed while constructing a bubblewrap command, before the requested command ran. Kernel checks showed user and PID namespace support unavailable in that build. This does not establish that every Android kernel has the same limitation.

Codex documents its Linux sandbox requirements, but general Linux CLI documentation does not establish Android support. Native Android agent execution remains experimental in this method. The verified phone workflow uses the PC-backed engine. [Codex sandbox documentation](https://learn.chatgpt.com/docs/sandboxing)

## Windows sessions and executable access

Running the coding engine directly in a Windows SSH service session failed with a sandbox runner startup timeout. A signed-in desktop-session engine with an authenticated loopback client passed the bounded coding test and the actual phone-terminal test.

Separate probes also found that a per-user WindowsApps shell alias and a per-user Python executable could fail with process-creation access denied. Accessible shells could start while still lacking Python on their sandbox PATH. A successful shell startup therefore did not establish language execution.

The evaluated correction used an accessible explicit shell, a separate generic Python runtime, and an engine-specific environment. A reusable installer must obtain dependencies from their publishers and validate their licenses and integrity. It must not copy personal packages or private workstation state into a release. Follow current [Windows sandbox guidance](https://learn.chatgpt.com/docs/windows/windows-sandbox) and test the effective permissions rather than disabling isolation.

Full PC reboot/logon, sleep recovery, and network-switch recovery remain unqualified. Intermittent USB ADB visibility was also observed. Available Windows logs did not establish its cause; the package should not claim a verified cable or driver fix.

## ChatGPT app and device integrity

The official Play-installed ChatGPT app did not complete sign-in. A preauthentication Play Integrity failure was reported, and the user confirmed an uncertified-device label in Play Store. The exact per-app verdict was unavailable. These observations support an integrity-compatibility concern, but do not identify every failed check.

Google documents that the Android 13+ device-integrity label requires hardware-backed evidence of a locked bootloader and a certified manufacturer image. The Play Store certification label is a separate observation. [Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts), [Play Protect certification](https://support.google.com/googleplay/answer/7165974)

The method contains no attestation spoofing or account-security workaround. Returning to manufacturer software and locking a bootloader requires the proper device-specific procedure. ChatGPT Web displayed a signed-in workspace in Chrome; active account matching and message submission were not tested. Official mobile-app pairing remains unresolved and account verification still applies. A passing Codex SSH terminal test does not establish ChatGPT app sign-in.

## Observability and time

Grafana health reachability and a bounded authenticated Explore query passed. The existing Loki datasource returned the expected constant for `vector(1)` over a one-hour range. This does not establish raw log retrieval, ingestion, incident contents, or every dashboard/tenant permission. Loki requires deliberate authentication handling; tenant identifiers are not substitutes for authenticated identity. [Loki authentication](https://grafana.com/docs/loki/latest/operations/authentication/)

Automatic time obtained and accepted a fresh sample on Wi-Fi. A forced request failed when the VPN was the default network. The existing sample remained usable, but future refresh on that path was not established. Preserve automatic time and certificate validation while diagnosing the actual network path. [Android network time](https://source.android.com/docs/core/connect/time/network-time-detection)

## Interface and catalog

Native v0.2 acceptance covered same-key update installation, 39-entry and 40-entry imports, app/service pin retention, app-drawer search/pin, a real pinned Termux launch, force-stop/restart, and default-Home rollback. Seven pins remained present. Full phone reboot, arbitrary large-font layouts, tablet layouts, and multiuser behavior have not been qualified.

The new Codex action copied its fixed command, opened Termux, and reached the actual remote Codex ready prompt after manual paste. No new model call was made. Earlier phone-terminal model coding remains accepted separately.

An AI-workspace login page and a Grafana incident deep-link login page opened in Chrome. Authenticated Open WebUI subsequently returned the expected exact response through an existing Ollama/Qwen model. Authenticated Grafana Explore also passed the bounded Loki constant-query check. These results cover their specific paths. Successful import of the complete catalog does not establish that every service is available or authenticated.

Wallpaper, normal Android appearance settings, and Termux colors were applied and checked. Icon frames and glyphs render inside Outlands Home; vendor APKs and system UI were not replaced.

Public catalog entries must use generic placeholders. Importing a private inventory locally does not authorize publishing its endpoints, names, or contents. A shortcut is a navigation aid; it does not prove installation, authentication, or runtime compatibility.
