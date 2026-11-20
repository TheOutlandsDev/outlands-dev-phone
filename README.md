# OUTLANDS Dev Phone

An Android development console with local Termux tools, private network access, and a terminal connection to an existing development PC.

**v0.3 offline workflow source** adds Change Library, Readiness Queue, local
review notes, and redacted Markdown handoffs. The source also includes a
local Git brief builder, a ZIP handoff verifier, and a local event analyzer.
[Offline workflows](docs/OFFLINE-WORKFLOWS.md)

**v0.2 source release, evaluated on one Android 16 phone.** The reviewed source is [published on GitHub](https://github.com/TheOutlandsDev/outlands-dev-phone). All 43 published source files matched the approved path, blob, and size manifest. Accepted workflows and their limits are recorded below and in the [release record](docs/RELEASE-CHECKLIST.md). This is source and documentation, with no firmware, installed runtimes, or prebuilt APK included in the publication.

The evaluated setup can run small Python, JavaScript, Go, Rust, C, and C++ programs locally. Tailscale connects it to existing infrastructure. Codex runs on an authenticated PC engine, with the phone providing the terminal interface over SSH. Remote services keep running on their own hosts.

## What has been established

| Area | Result |
| --- | --- |
| Android boot, encrypted storage, F2FS, enforcing SELinux | Passed on one evaluated device/build combination |
| Termux language and compiler smoke tests | Passed for Python, Node.js, Go, Rust, C, and C++ |
| Tailscale connectivity and Grafana health endpoint | Passed; authenticated query acceptance is recorded separately below |
| Phone terminal to PC-backed Codex | Passed an actual Python execution test with exit code zero |
| Native Android Codex | Version/help worked; native sandbox execution and login were not accepted |
| Official ChatGPT Android app | Installed from Play; sign-in remains blocked by a reported Play Integrity error |
| Native launcher v0.2 | Signed update installed; app search, pins, process restart, and default-Home rollback passed |
| Authenticated browser AI chat | Open WebUI returned the requested exact marker using an existing Ollama/Qwen model |
| Authenticated Grafana/Loki | Explore returned the expected constant from `vector(1)` using an existing Loki datasource; raw logs and ingestion remain unqualified |
| Wallpaper and theme | Home/lock wallpaper, Android appearance settings, and Termux colors applied and checked |
| New Codex launcher action | PASS: helper copied, correct command pasted at a Termux shell, and actual remote Codex ready prompt observed |

The evaluation used an unofficial Android build. The user confirmed that Play Store reported the device as uncertified. Installing an app successfully does not establish that its sign-in or device-integrity checks will work.

The native launcher accepted a same-key update from v0.1 to v0.2. Catalog imports with 39 and then 40 entries retained the selected service and app pins. Seven Home shortcuts survived force-stop/restart. The private catalog is excluded from this package; these counts describe import acceptance, not successful authentication to every service.

Branding adds frames and glyphs inside Outlands Home. Standard Android dark mode and its Lemongrass/Expressive appearance were configured separately. Vendor app icons and APKs were not replaced or re-signed.

## Read the method

- [Setup and evaluation sequence](docs/SETUP.md)
- [Architecture and execution boundaries](docs/ARCHITECTURE.md)
- [PC-backed Codex setup](docs/CODEX-PC.md)
- [Device and OS preparation](docs/DEVICE-AND-OS.md)
- [Validation record and repeatable checks](docs/VALIDATION.md)
- [Known limitations](docs/LIMITATIONS.md)
- [Sources and attribution](docs/SOURCES.md)
- [Completion and release checklist](docs/RELEASE-CHECKLIST.md)

## Using the examples

Values such as `<HOST_USER>`, `<HOST_TAILNET_NAME>`, `<SSH_PORT>`, `<HOST_WORKSPACE>`, and `<GRAFANA_BASE_URL>` are placeholders. Replace them locally. A catalog entry or launcher shortcut describes how to reach a tool; it does not establish that a server, account, or integration is configured.

[catalog.example.json](docs/catalog.example.json) contains one fictional HTTPS dashboard. Keep real service addresses and inventories in private local configuration.

The current screen preference is to stay awake while charging through AC, USB, or wireless power, with a 30-minute timeout on battery. This is an explicit development preference and should remain configurable.

## Publication boundary

This package is freshly authored generic material. Private setup records, screenshots, device identifiers, network inventory, credentials, firmware, installed runtimes, and original workstation files belong outside it. See the release checklist before preparing a public source archive or repository.

## License

Newly authored original source and documentation use the [MIT License](LICENSE). Upstream products, dependencies, model weights, and trademarks retain their own terms; see [sources and attribution](docs/SOURCES.md).
