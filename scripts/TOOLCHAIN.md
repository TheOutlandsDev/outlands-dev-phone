# Local Android build

The launcher uses plain Java and Android framework APIs. `Build-Launcher.ps1`
builds an aligned **unsigned** APK with JDK 17 and official Android SDK tools;
it does not require Gradle, create signing keys, or install an app.

```powershell
.\scripts\Build-Launcher.ps1 -SdkRoot C:\Android\Sdk -JavaHome C:\Java\jdk-17
```

The SDK root must contain `platforms/android-36/android.jar` and
`build-tools/36.1.0/{aapt2.exe,zipalign.exe,lib/d8.jar}`. Output goes to a fresh
directory under `artifacts/`. Keep that directory out of source publication.
Sign the aligned APK separately with your private signing key before installing;
never commit a keystore or its passwords.

## Official packages used

Package metadata and publisher checksums come from Google's HTTPS
[repository manifest](https://dl.google.com/android/repository/repository2-1.xml).
These Windows packages were listed on the stable channel:

| Package | Archive | Bytes | Publisher SHA1 |
| --- | --- | ---: | --- |
| Android API 36, revision 2 | [platform-36_r02.zip](https://dl.google.com/android/repository/platform-36_r02.zip) | 65,878,410 | `2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576` |
| Build Tools 36.1.0 | [build-tools_r36.1_windows.zip](https://dl.google.com/android/repository/build-tools_r36.1_windows.zip) | 59,366,211 | `95f048ae758dad90617975fe3f787f038b03338c` |

The verified original archives also produced these locally computed SHA256
values; Google supplied the SHA1 checksums above:

```text
37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e  platform-36_r02.zip
23189d2d52b40a070a05e9cf7e497c9563f67fee76902e8fd3135ef29ef4dbeb  build-tools_r36.1_windows.zip
```

Use Google's SDK license supplied with the original package metadata. The SDK,
JDK, local download receipts, generated APKs, and private signing material are
not part of the reusable source.

For this application, the build script adds the application package to a staging
copy of the manifest for AAPT2. It leaves the source manifest and optional Gradle
namespace configuration unchanged. Java is compiled to Java 8 bytecode and D8
targets API 26 or later.
