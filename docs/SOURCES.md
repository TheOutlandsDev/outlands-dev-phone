# Sources and attribution

Public references for this draft, assembled on 2026-11-13:

| Source | Purpose |
| --- | --- |
| [Termux](https://termux.dev/en/) | Android terminal and package environment |
| [Tailscale Android installation](https://tailscale.com/docs/install/android) | Native private-network client |
| [Motorola bootloader eligibility](https://en-us.support.motorola.com/app/utils/guided_assistant/g_id/8133) | Manufacturer eligibility and carrier-variant restrictions |
| [LineageOS device index](https://wiki.lineageos.org/devices/) | Independent lookup of officially supported devices |
| [Android reset protection](https://support.google.com/android/answer/9459346) | Ownership verification after a reset |
| [Android file-based encryption](https://source.android.com/docs/security/features/encryption/file-based) | Preliminary encryption acceptance |
| [Android SELinux validation](https://source.android.com/docs/security/features/selinux/validate) | Enforcing-mode acceptance |
| [Android network time](https://source.android.com/docs/core/connect/time/network-time-detection) | Network samples and time-service diagnostics |
| [Android stay-awake setting](https://developer.android.com/reference/android/provider/Settings.Global#STAY_ON_WHILE_PLUGGED_IN) | Configurable charging display preference |
| [Codex app-server](https://learn.chatgpt.com/docs/app-server) | Engine protocol and terminal client |
| [Codex permissions](https://learn.chatgpt.com/docs/permissions) | Filesystem, network, and approval boundaries |
| [Codex Windows sandbox](https://learn.chatgpt.com/docs/windows/windows-sandbox) | Current Windows setup and troubleshooting |
| [Codex sandbox](https://learn.chatgpt.com/docs/sandboxing) | Linux sandbox requirements |
| [OpenAI remote connections](https://learn.chatgpt.com/docs/remote-connections) | Separate official mobile/desktop remote workflow |
| [Microsoft OpenSSH server configuration](https://learn.microsoft.com/en-us/windows-server/administration/OpenSSH/openssh-server-configuration) | Windows SSH configuration |
| [Google Play Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts) | Interpretation of integrity labels |
| [Google Play Protect certification](https://support.google.com/googleplay/answer/7165974) | Certification status and its limits |
| [Grafana Loki authentication](https://grafana.com/docs/loki/latest/operations/authentication/) | Authentication and tenancy boundaries |
| [Open WebUI documentation](https://docs.openwebui.com/) | Upstream browser AI workspace |
| [Ollama documentation](https://docs.ollama.com/) | Upstream model-serving system used by the accepted browser chat |
| [Qwen2.5 model card](https://huggingface.co/Qwen/Qwen2.5-7B-Instruct) | Upstream model identity and its separate terms |

Upstream documentation describes the relevant products; it does not certify this project's integration. The validation summary describes observed checks independently and preserves untested areas.

Termux, Tailscale, OpenSSH, OpenAI, Android, LineageOS, Motorola, Google, Grafana, Open WebUI, Ollama, Qwen, and the language toolchains are separate upstream projects or products. Their authors retain their licenses and trademarks. Any selected unofficial ROM also needs its own maintainer attribution if a device-specific guide is added.

Newly authored original source and documentation use the [MIT License](../LICENSE), copyright 2026 TheOutlandsDev. That license does not change the terms of third-party dependencies, products, models, or trademarks.

The intended public source package excludes upstream binaries, firmware, copied source trees, private records, and screenshots. Local build artifacts remain outside the publication allowlist. Retain any notices required by included dependencies or assets before publication.
