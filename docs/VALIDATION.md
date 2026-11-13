# Validation record

This is a sanitized summary of one evaluation as of 2026-11-13. Private evidence remains outside the package. Results apply to the tested device, OS build, host, and tool versions; they do not qualify every installation.

## Observed results

| Area | Observation | Limit |
| --- | --- | --- |
| Android | Boot and setup completed; F2FS, file-based encryption, and enforcing SELinux verified | One unofficial Android 16 build; long-duration stability not qualified |
| Local languages | Tiny Python, Node.js, Go, Rust, C, and C++ runtime/compiler checks passed | Small fixtures, not complete projects or performance benchmarks |
| Supporting tools | Git, OpenSSH, tmux, and Nmap version checks passed | Version checks do not validate privileged scanning or every feature |
| OpenAI JavaScript SDK | Import passed | No API request or API authentication test; Python SDK installation not claimed |
| Tailscale | Phone online, peer reachability passed | Broader network and lifecycle recovery not qualified |
| Grafana reachability | Health endpoint passed through short-name, fully qualified name, and address paths | Broader dashboard and organization permissions unqualified |
| Authenticated Grafana/Loki | Existing Loki datasource in Explore returned the expected constant from `vector(1)` over a one-hour range | Synthetic query only; raw log retrieval, ingestion, and broader tenant access unqualified |
| Native Android Codex | Version/help passed | Login incomplete; native sandbox execution failed before the test command |
| PC engine coding | Model created and ran a tiny Python file; expected output and exit zero observed | Dedicated workspace, network disabled for this bounded test |
| Actual phone terminal | Phone issued a Python execution request through SSH and the PC engine; output and exit zero verified | Final interactive policy allowed network and asked for approval |
| ChatGPT Android app | Official Play installer verified | Reported preauthentication Play Integrity error; sign-in has not passed |
| Certification | User confirmed Play Store showed the device as uncertified | Actual per-app integrity verdict unavailable |
| Browser ChatGPT/mobile pairing | Chrome displayed a signed-in ChatGPT workspace | Login flow, active-account matching, chat submission, and official mobile host pairing untested |
| Automatic time | Fresh Wi-Fi NTP sample obtained and accepted with automatic time enabled | Forced refresh on the VPN path failed; sustained VPN refresh unqualified |
| Screen behavior | AC/USB/wireless stay-awake mask and 30-minute battery timeout configured; charging wakefulness observed | Battery duration and every charging type not individually tested |
| Launcher update | Same-key signed update from v0.1 to v0.2 installed on Android 16 | Public source-package and broader lifecycle qualification remain separate |
| Catalog and pins | 39-entry then 40-entry imports accepted; selected app/service pins retained | Private inventory excluded; import does not establish service authentication |
| Native app actions | App-drawer search/pin and an actual pinned Termux launch passed | Other unobserved actions remain separate checks |
| Home persistence | Seven pins survived force-stop/restart; Outlands-to-stock-to-Outlands default-Home rollback passed | Full phone reboot, tablet, large-font, and multiuser behavior unqualified |
| Browser handoff | AI-workspace and Grafana incident deep links reached login pages in Chrome | Authenticated synthetic query passed separately; actual incident contents unvalidated |
| Authenticated Open WebUI | A bounded exact-response prompt returned its requested marker through an existing Ollama chat model | One text response; other models, tools, retrieval, and API integrations unqualified |
| Wallpaper and colors | Both wallpaper targets applied and checked; lock preview, Android dark/Lemongrass/Expressive settings, and Termux colors checked | In-app branding is not a system-UI or vendor-app replacement |
| New Codex shortcut | Fixed helper copied, correct Termux command pasted, and remote Codex TUI ready prompt observed | No new model call; earlier coding acceptance preserved |

System and vendor patch dates were different in the evaluation. Their separate values belong in each operator's private acceptance record; a newer OS label does not establish current vendor firmware.

## Native launcher v0.2 acceptance

The evaluated Home layout contained seven pinned shortcuts: four browser-service entries and three installed utility apps. Pin retention was observed across catalog replacement and force-stop/restart. App-drawer filtering and pinning worked, and a pinned Termux shortcut opened the actual installed app.

The system's default Home selection changed from Outlands Home to the stock launcher and back successfully. This established an available rollback path for the observed session. It was not a full phone reboot test.

Catalog URLs remained HTTPS-only. Chrome reached an AI-workspace login page and a Grafana incident deep-link login page. Open WebUI later passed an authenticated text-response check using the existing Qwen2.5 7B Instruct model through Ollama: a bounded prompt requested one exact marker and the response matched. This does not qualify model tools or other integrations. No claim is made that all imported services were opened or signed in.

Authenticated Grafana Explore subsequently ran `vector(1)` in Code mode through an existing Loki datasource, using a range query over the last hour. The displayed graph was a horizontal value of one with an empty-label series. This verifies the bounded authenticated query path. No raw log retrieval, ingestion test, saved dashboard change, or server configuration change was accepted or requested by that check.

Home and lock-screen wallpaper targets were applied. Android service state and the lock-screen preview were checked. Dark mode and the Android Lemongrass preset (`#DDEB78`) with Expressive styling were applied separately from the launcher's local branding. Termux colors were applied and verified. The display preference remains charging mask `7` and battery timeout `1800000` milliseconds.

The new Codex shortcut copied its helper and opened Termux. The pasted command matched the intended helper and began the actual SSH connection. The remote Codex ready prompt was subsequently observed from this launcher path without another model call. The earlier phone-issued Python execution with exit zero remains accepted independently.

## Toolchain snapshot

These are observed versions, not recommended pins or claims about the latest releases.

| Component | Observed version |
| --- | --- |
| Python | 3.14.10 |
| Node.js / npm | 24.22.3 / 12.2.2 |
| Go | 1.27.3 |
| Rust | 1.99.1 |
| Clang | 21.1.8 |
| Git | 2.56.1 |
| OpenSSH | 10.6p2 |
| tmux | 3.8 |
| Nmap | 7.993 |
| Tailscale | 1.104.7 |

## Repeat the useful checks

1. Record the selected versions and effective sandbox policy privately.
2. Run tiny local language fixtures in a disposable test directory. Capture stdout and exit status for each runtime or compiler.
3. Check Tailscale enrollment and a deliberately permitted peer. Test DNS and the specific service endpoint separately.
4. Treat a service health response, authenticated UI access, and a real authorized query as three separate results.
5. Check PC engine readiness. Run a workspace command with the intended policy, then a model-created file test, then one command initiated from the actual phone terminal.
6. Confirm where the files and commands execute. Preserve the selected model, credentials, and approval policy during a bounded test.
7. Inspect automatic-time state and the age of its accepted network sample. A manually corrected clock alone is insufficient.
8. Repeat relevant checks after an OS, toolchain, Codex, host-session, or network configuration change.

A fresh Python fixture can use `print("DEV_PHONE_SMOKE_OK")`. A passing result needs the expected stdout and exit zero from the real command result, not just a model's summary. Do not reuse private project files or publish raw transcripts.

Final browser check: the ChatGPT Web shortcut opened Chrome and displayed a signed-in workspace UI. Account matching and message submission were not tested. This does not qualify native ChatGPT app login. App unpin/re-pin was checked: seven to six to seven Home shortcuts.
