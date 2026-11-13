# Set up the development phone

Start with a working Android installation. This guide installs the development
workspace and launcher; it does not flash firmware. Read [device preparation](DEVICE-AND-OS.md)
before making an independent OS decision. Use this source package's root directory
for the relative paths below.

## 1. Install the native apps

- Install Termux from a source listed in the [Termux project's installation guide](https://github.com/termux/termux-app#installation),
  such as its linked F-Droid package or official GitHub releases. Keep Termux and
  any Termux plugins on the same distribution source/signature family.
- Install Tailscale from its [official Android download page](https://tailscale.com/download/android)
  or its linked Play Store listing.
- Install Chrome from its official Play Store listing, or use your existing
  trusted browser. Outlands Home prefers installed Chrome and otherwise shows
  Android's chooser. ChatGPT Web is a browser shortcut; the native ChatGPT app is
  optional and may reject a modified/uncertified OS.

Open Termux once and let its initial environment finish installing. These steps
do not require root, a Termux external-command permission, or shared-storage access.

## 2. Install local languages and tools

Run in Termux on a fresh setup:

```sh
pkg update
pkg upgrade
pkg install python nodejs-lts golang rust clang make cmake git openssh curl jq ripgrep tmux nano
mkdir -p "$HOME/dev" "$HOME/bin"
```

`clang` supplies C and C++ compilers. Keep project files under `~/dev`. On an
existing installation, keep its selected Node.js package; `nodejs` and
`nodejs-lts` are alternatives, not packages to install together.

The optional Nmap help shortcut needs the Termux package:

```sh
pkg install nmap
```

The launcher's language/tool actions only copy fixed help or REPL commands and
open Termux. Paste and submit the command yourself. Installing a tool does not
establish that an arbitrary project or network workflow is compatible.

## 3. Enroll in your own tailnet

Open Tailscale, select Get Started/Log in, accept Android's VPN configuration
prompt, and authenticate to the intended personal or organization tailnet. If
device approval is required, complete it through that tailnet's administrator.
Follow the [Android enrollment instructions](https://tailscale.com/docs/install/android).

Connect the development PC and service hosts to the same authorized network.
Their Tailscale access rules, host firewalls, listening addresses, DNS, and
service accounts must permit the intended connections. Enrollment alone does
not grant access to every machine or dashboard.

Open an existing HTTPS service in the phone browser and complete its normal
sign-in. Retain certificate verification. Grafana, Loki, and an AI workspace
remain services on their own hosts; installing this launcher does not install
or configure those servers.

## 4. Build, sign, and install Outlands Home

Use a Windows PC with JDK 17 and Google's Android SDK. In Android Studio's
[SDK Manager](https://developer.android.com/studio/intro/update#sdk-manager),
install API 36, Build Tools 36.1.0, and Platform Tools. The repository's
[toolchain guide](../scripts/TOOLCHAIN.md) describes the expected directory layout.
The optional Gradle project is documented [separately](../android/README.md).

From the source package root, in PowerShell, set paths to your installations:

```powershell
$sdkRoot = 'C:\Android\Sdk'
$jdkRoot = 'C:\Java\jdk-17'
$build = .\scripts\Build-Launcher.ps1 -SdkRoot $sdkRoot -JavaHome $jdkRoot | ConvertFrom-Json
$unsignedApk = $build.unsignedApk
$buildDir = [IO.Path]::GetDirectoryName($unsignedApk)
$signedApk = Join-Path $buildDir 'outlands-home-signed.apk'
```

The build creates an aligned **unsigned** APK. Create your signing identity once,
outside the source checkout. `keytool` prompts for the keystore password:

```powershell
$signingDir = Join-Path $env:LOCALAPPDATA 'OutlandsHomeSigning'
New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$keyStore = Join-Path $signingDir 'outlands-home.p12'
& "$jdkRoot\bin\keytool.exe" -genkeypair -storetype PKCS12 -keystore $keyStore `
    -alias outlands-home -keyalg RSA -keysize 3072 -validity 3650 `
    -dname 'CN=Outlands Home local build'
```

For later builds, reuse the existing keystore rather than generating another.
Sign interactively, then verify:

```powershell
$signer = Join-Path $sdkRoot 'build-tools\36.1.0\lib\apksigner.jar'
& "$jdkRoot\bin\java.exe" -jar $signer sign --ks $keyStore --ks-key-alias outlands-home `
    --out $signedApk $unsignedApk
& "$jdkRoot\bin\java.exe" -jar $signer verify --verbose --print-certs $signedApk
Get-FileHash -LiteralPath $signedApk -Algorithm SHA256
```

Continue only after successful commands and signature verification. The supplied
`Sign-Launcher.ps1` is an alternative for an existing keystore and private
password file. Keep signing material and generated artifacts out of publication.
APK signing enables your app's identity and updates; it does not certify Android.
[Android APK signing](https://developer.android.com/tools/apksigner)

Enable USB debugging on the intended phone and accept that PC's authorization
prompt. Use the serial reported by `devices` in place of the placeholder:

```powershell
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
& $adb devices
& $adb -s '<DEVICE_SERIAL>' install -r $signedApk
```

Updates require the same package and signing identity. A differently signed
installed copy cannot be updated with your new key; uninstalling it removes its
private catalog and pins. [ADB installation](https://developer.android.com/tools/adb#move)

Open **Outlands Home** from the existing launcher first. Its Settings → Choose
Home app opens Android's selector. Select Outlands Home when ready; select the
previous launcher there to roll back. The app never forces the default itself.

## 5. Configure the catalog and Home shortcuts

Copy [catalog.example.json](catalog.example.json) to a private local file and edit
it for your own services. Keep IDs stable between revisions so existing service
pins can be retained. Do not put passwords, tokens, or other credentials in URLs.
See the [catalog schema and limits](../android/README.md#service-catalog-import).

Transfer that private JSON file to a location the phone's document picker can
open. In Outlands Home, use Settings → Import service catalog, select it, and
confirm replacement. The source file is not deleted by import; keep it private.
Individual addresses can also be edited in Settings or through a service's
long-press menu.

Use Services for catalog search and categories. Long press a service → Pin to
Home. Use Apps to search installed apps; long press an app → Pin to Home. Long
press a pinned Home card to remove it. Pins and imported configuration stay in
the app's private preferences. Reset service catalog restores generic entries
and clears service pins while keeping app pins.

## 6. Apply and restore the appearance

Outlands Home → Settings → Apply Outlands wallpaper applies the bundled local
artwork to Home and lock screen. Change it again through normal Android wallpaper
settings. Android dark mode and available palette/style choices are separate
system settings and vary by OS.

To apply Termux colors, place this public source package at
`~/dev/outlands-dev-phone` inside Termux, then run:

```sh
cd "$HOME/dev/outlands-dev-phone"
sh scripts/Apply-TermuxTheme.sh "$PWD/themes/termux/colors.properties"
```

The script prints a unique backup directory. To restore that specific backup:

```sh
sh scripts/Restore-TermuxTheme.sh '<BACKUP_DIRECTORY_PRINTED_BY_APPLY>'
```

Launcher frames and glyphs affect Outlands Home only. They do not modify vendor
app icons, re-sign other apps, or replace system UI.

## 7. Optional display preference

Use Android's display timeout and Developer options → Stay awake controls if
they provide the behavior you want. The evaluated development preference was
awake on AC/USB/wireless charging and a 30-minute timeout on battery.

For that exact optional preference, first record both original values using
authorized ADB. Replace the serial placeholder and keep the original values:

```sh
adb -s '<DEVICE_SERIAL>' shell settings get global stay_on_while_plugged_in
adb -s '<DEVICE_SERIAL>' shell settings get system screen_off_timeout
adb -s '<DEVICE_SERIAL>' shell settings put global stay_on_while_plugged_in 7
adb -s '<DEVICE_SERIAL>' shell settings put system screen_off_timeout 1800000
```

To undo, use the same `put` commands with the recorded original numeric values.
If an original value was `null`, remove that override with `settings delete
global stay_on_while_plugged_in` or `settings delete system screen_off_timeout`
instead. Device policy can limit these settings. This does not change the
screen-lock credential. [Android charging wakefulness setting](https://developer.android.com/reference/android/provider/Settings.Global#STAY_ON_WHILE_PLUGGED_IN)

## 8. Configure PC-backed Codex separately

Follow [PC-backed Codex setup](CODEX-PC.md). Installing the launcher does not
create the phone's SSH key, enroll the PC, configure its SSH server/firewall,
authenticate Codex, generate a transport token, start an engine, or install
`~/bin/codex-pc`.

The evaluated arrangement keeps the authenticated engine and its transport
secret on the PC. The phone uses its own SSH key and launches a PC terminal
client; native phone Codex execution is not required. Windows host-session and
workspace policies need local configuration. Host reboot/logon automation and
recovery also remain deliberate host setup, not an included installer.

Only after the host and phone helper are configured, tap Codex in Outlands Home,
paste the copied `~/bin/codex-pc` at a **Termux shell prompt**, and submit it. Do
not paste it into an already-running Codex prompt. Consult the
[architecture](ARCHITECTURE.md), [limitations](LIMITATIONS.md), and current
[OpenAI app-server documentation](https://learn.chatgpt.com/docs/app-server)
for the execution and authentication boundaries.
