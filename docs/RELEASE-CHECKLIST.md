# v0.2 release record

This source release was evaluated on one Android 16 phone and published to the [public repository](https://github.com/TheOutlandsDev/outlands-dev-phone). The initial complete source revision is [e502f4d](https://github.com/TheOutlandsDev/outlands-dev-phone/commit/e502f4dbc951d608fef69df40211faa941fd03ef). A separate GitHub API tree read matched all 43 approved file paths, blob identities, and sizes. This record documents that completed check. No binary release was published, and archive contents have not been independently checked.

## Passed evaluation gates

- [x] Build with the evaluated Android SDK script, verify APK v2/v3 signing, and install the same-key v0.1-to-v0.2 update.
- [x] Import catalogs with 39 and 40 entries while retaining selected app/service pins.
- [x] Verify app-drawer search/pin, a pinned Termux launch, and seven-pin retention after force-stop/restart.
- [x] Verify default-Home rollback from Outlands to stock and back.
- [x] Verify browser routing and an authenticated Open WebUI exact-response check through the existing Ollama model.
- [x] Verify authenticated Grafana Explore through the existing Loki datasource with `vector(1)`, while keeping raw-log and ingestion claims outside acceptance.
- [x] Apply/check Home and lock wallpaper, Android dark/Lemongrass/Expressive appearance, and Termux colors.
- [x] Observe the Codex shortcut's helper copy/paste, SSH connection, and actual remote ready prompt.
- [x] Preserve the earlier actual phone-issued Python execution result and the bounded backend model-coding result as separate passed checks.
- [x] Verify the chosen charging stay-awake mask and 30-minute battery timeout.
- [x] Review the native app's bounded import, explicit actions, private pin/configuration storage, and backup exclusions.

## Source and scope gates

- [x] Use freshly authored generic source and documentation rather than private setup originals.
- [x] Keep firmware selection/flashing outside the generic package and document the device-specific requirements.
- [x] Supply a fictional HTTPS catalog example; exclude the private inventory and real service endpoints.
- [x] Document where local tools, remote services, and the PC-backed coding engine execute.
- [x] Preserve ordinary account authentication, host-key checking, PC-loopback engine binding, and the observed sandbox/approval boundaries.
- [x] Distinguish installation, service health, authenticated use, and the exact tested query/response paths.
- [x] Record unresolved native Android Codex, ChatGPT app integrity, and lifecycle limits in the release scope.
- [x] License original source/documentation under MIT and document separate upstream terms and artwork provenance.
- [x] Review source candidates for private identifiers, credentials, network inventory, local paths, and generated artifacts.

## Publication file boundary

Copy only explicitly reviewed source files: the root README, license and ignore rules; Markdown/fictional examples under `docs`; Android source/resources and build descriptors; the approved original artwork/palette; Termux color properties; and the generic build, signing and theme scripts with their documentation.

Exclude `artifacts`, build outputs, caches, SDKs, installed runtimes, signing keys, firmware, private configuration, logs, evidence, screenshots, and backups. Ignore rules support this boundary; the final file manifest is the acceptance check.

## Completed publication gates

- [x] Copy the explicit reviewed file list into the clean publication tree.
- [x] Upload that source tree to the existing public repository.
- [x] Compare the remote source tree with the approved file manifest and record the published source revision.

The source was copied with an explicit allowlist and checked against the staged Git blobs before upload. The remote source-tree comparison passed independently after upload. Later documentation-only commits record the result. If an archive is produced later, inspect its contents separately before claiming archive acceptance.

## Optional and unqualified follow-up

These are disclosed qualification limits, not claims of completed testing:

- Full phone reboot, full PC reboot/logon, sleep recovery, and network-switch recovery.
- Sustained automatic-time refresh while the VPN remains the default network.
- Arbitrary large-font/TalkBack combinations, tablet layouts, multiuser behavior, every navigation configuration, and exhaustive malformed-import/package-removal cases.
- The optional Gradle workflow and clean-host or broader device/Windows compatibility.
- Raw Loki log retrieval, ingestion, actual incident contents, and every catalog service's availability or authorization.
- Native Android Codex sandbox execution and successful ChatGPT Android app sign-in.

Repeat the checks relevant to a change and keep new claims within the evidence recorded in [VALIDATION.md](VALIDATION.md).
