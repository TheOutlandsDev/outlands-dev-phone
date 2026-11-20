# Outlands Home Android

Native Java Home/launcher source. Package `dev.outlands.launcher`, display name
**Outlands Home**, minimum API 26, compile/target API 36. No external runtime
dependencies. The SDK-script build passed, APK v2/v3 signing was verified, and a
same-key v0.1-to-v0.2 update was installed and exercised on one Android 16 phone.
See the [validation record](../docs/VALIDATION.md) for accepted UI, catalog,
pinning, browser, and terminal flows and their qualification limits.

The evaluated workflow uses [Build-Launcher.ps1](../scripts/Build-Launcher.ps1)
with the Android SDK tools. The optional Gradle project uses Android Gradle
Plugin 8.13.2, Gradle 8.13, and JDK 17, but that workflow remains untested. With
those prerequisites and Android SDK platform 36 available, its build command is
`gradle :app:assembleDebug`. This source package does not bundle a Gradle wrapper,
SDK, signing keys, or prebuilt APK.

## Behavior and boundaries

- Standard `MAIN`/`HOME`/`DEFAULT` registration and a separate app-drawer entry.
  The app does not force itself to become the default Home. Choose Home app and
  Android app settings remain available for rollback.
- Native, scrollable cards; real local time, date, and battery; searchable
  launchable apps loaded off the main thread. No invented connection/health
  indicators and no launcher widget hosting in this prototype.
- Persistent Home / Services / Apps navigation keeps the app drawer reachable
  with a full catalog, and reserves scroll padding above the navigation bar.
- Version 0.2 adds a prominent Pinned section after the hero. Long press an app
  in Apps to pin/unpin it, or long press a service for Pin to Home / Edit address.
  Long press a pinned Home card to remove it. Pins are app-private preferences;
  service pins survive catalog replacements when the same IDs remain present.
  Reset service catalog clears service pins and keeps pinned apps. Up to 64 apps
  and 128 services can be pinned. Missing apps remain removable unavailable cards.
- Original code-drawn cyan/yellow geometric badges identify service/tool types.
  App icons are displayed inside the same charcoal Outlands frame with their
  original app names. This does not modify or re-sign installed applications,
  replace their internal UI, or change Android's system-wide icon resources.
- Terminal/Tailscale quick actions open their installed apps. Codex copies the
  fixed text `~/bin/codex-pc`, then opens Termux; the user must paste and submit.
  Local-tool actions similarly copy fixed help/REPL commands. No Termux external
  command service, scanning, permission grant, root, overlay, accessibility,
  device administrator, or certificate override.
- Service links open using external `ACTION_VIEW`/`BROWSABLE` intents, preferring
  installed Chrome so ChatGPT Web stays in a browser. Without Chrome, Android's
  app chooser is shown. There is no WebView or network permission.
- Configuration is app-private SharedPreferences with cloud/device-transfer
  preference backup excluded. This is not a credential vault: do not use URLs
  containing passwords, tokens, or other secrets. Common token query names are
  rejected, but arbitrary secret values cannot be identified automatically.
- No private hostnames, tailnet names, account identifiers, telemetry, analytics,
  or advertising components are included in the public defaults.
- Apply Outlands wallpaper is an explicit Settings action for the bundled local
  asset (Home and lock screen). It uses only the normal `SET_WALLPAPER`
  permission, works off the UI thread, and never runs automatically on launch.
- Version 0.3 adds Home actions for **Change Library** and **Readiness Queue**.
  Saved briefs support comparison, local checklist review, local notes, and
  a reviewed Markdown export. See [offline workflows](../docs/OFFLINE-WORKFLOWS.md).

## Service catalog import

Settings → Import service catalog uses Android's document picker. The app reads
one selected document, validates the whole file, and asks before replacing the
current catalog. It does not retain access to the original file or export it.

UTF-8 JSON, at most 256 KiB and 128 services:

```json
{
  "version": 1,
  "services": [
    {
      "id": "example-dashboard",
      "label": "Example dashboard",
      "category": "Ops",
      "url": "https://dashboard.example/#overview",
      "description": "A privately configured service."
    }
  ]
}
```

IDs are unique, 1–64 ASCII letters/numbers/dot/underscore/hyphen, beginning with
a letter/number. Labels are 1–80 characters, categories 1–40, descriptions
optional and at most 180, URLs at most 2048. URLs may be blank for placeholders;
otherwise an HTTPS scheme, hostname, and no user information are required.
Anchor fragments are supported. Common credential/signed-link query parameters
are rejected. Invalid imports keep the existing catalog. Reset service catalog
restores six generic services; only ChatGPT Web has a populated public URL.

## Accepted scope and unqualified behavior

The evaluated device passed catalog imports, app/service pin retention, app-drawer
search/pin, a pinned Termux launch, force-stop/restart, and default-Home rollback.
The Codex shortcut reached the remote ready prompt. Chrome also passed bounded
authenticated AI-chat and Grafana/Loki checks described in the validation record.

Full phone reboot, arbitrary large-font/TalkBack combinations, tablet and multiuser
layouts, every navigation configuration, and exhaustive package-removal or invalid
import cases remain unqualified. They are outside the current one-device acceptance
claim. Imports never execute their contents. Browser and server certificate
decisions remain with those applications; the launcher does not certify the OS
or bypass service authentication.

`res/drawable/ic_outlands.xml` is the provisional launcher icon. The local
`res/drawable-nodpi/outlands_wallpaper.png` is the approved wallpaper/hero asset;
`res/drawable/outlands_hero.xml` is a geometric fallback study available to future
layouts. Use only approved local assets; no remote images are loaded.

Technical references:
[AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[package visibility declarations](https://developer.android.com/training/package-visibility/declaring),
[Android document picker](https://developer.android.com/training/data-storage/shared/documents-files).
