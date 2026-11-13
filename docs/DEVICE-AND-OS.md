# Device and OS preparation

This project supplies a development method, not a universal firmware installer. Termux, Tailscale, and remote terminals can be useful on supported stock Android. A custom OS is an independent, device-specific decision.

## Establish compatibility first

Record the exact hardware model and variant privately. Confirm bootloader eligibility with the manufacturer and support with the chosen ROM maintainer. A carrier/SIM unlock and a bootloader unlock serve different purposes. Motorola documents restrictions on some carrier-exclusive models. [Motorola bootloader eligibility](https://en-us.support.motorola.com/app/utils/guided_assistant/g_id/8133)

Select a maintained build whose instructions explicitly cover the exact variant. An unofficial build is not evidence of official LineageOS support. Consult the [LineageOS device index](https://wiki.lineageos.org/devices/) and the selected maintainer's current instructions independently.

Before any destructive step, establish that needed data is backed up or deliberately disposable. Confirm access to the Google account required after a reset. Factory reset protection and a forgotten screen lock are separate from bootloader eligibility. [Android reset protection](https://support.google.com/android/answer/9459346)

## Follow the selected installation sequence

The maintainer's instructions determine the required stock firmware baseline, recovery image, slot handling, data filesystem, optional Google apps, and first-boot sequence. These details can differ across devices and build families.

Bootloader fastboot and userspace fastboot are distinct environments. A recovery image and a complete bootchain archive also have different purposes. Inspect partition sizes and image metadata, but do not treat a matching image size or archive digest as proof of hardware compatibility.

The evaluated installation used its maintainer's device-specific recovery and formatting sequence. It did not establish that flashing every image in a bootchain archive is required. This scaffold intentionally contains no generic flash command sequence.

## Retain an independent recovery route

Keep the correct manufacturer restore package and official recovery procedure privately. Check all referenced components against the package manifest and verify expected checksums from a trusted source. A digest matching a manifest establishes consistency; it does not independently authenticate the publisher or prove that a downgrade is permitted.

Bootloader, partition-table, modem, and slot changes require their own compatibility checks. Preserve the ability to return to a supported state using manufacturer instructions. Bootloader relocking requires the correct trusted image and supported procedure; it should not be used as a quick fix for an app sign-in error.

## Accept the installed system

Check boot completion, completed setup, enforcing SELinux, file-based encryption, the filesystem required by the maintainer, and system/vendor patch dates separately. An updated framework does not establish that vendor firmware has received the same security updates.

Useful read-only checks include:

```sh
adb shell getprop sys.boot_completed
adb shell getenforce
adb shell getprop ro.crypto.state
adb shell getprop ro.crypto.type
adb shell getprop ro.build.version.security_patch
adb shell getprop ro.vendor.build.security_patch
```

Expected encryption values for the evaluated configuration were `encrypted` and `file`. These checks are preliminary acceptance, not complete security certification. [Android encryption validation](https://source.android.com/docs/security/features/encryption/file-based), [SELinux validation](https://source.android.com/docs/security/features/selinux/validate)

Keep device identifiers, recovery logs, firmware files, and screenshots in private evidence storage. Publish only a newly written summary of the checks and their limits.
