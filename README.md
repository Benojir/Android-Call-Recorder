# Android Call Recorder 2.0

Local call recording for Android 10+ with privileged system-app support and an optional non-root microphone mode.

## What changed

- Cellular calls, held calls and conference parents/children use one recording session. Repeated ACTIVE callbacks do not start competing recorders. A short disconnect debounce covers conference replacement.
- Choose automatic, microphone, voice recognition, voice communication, camcorder, voice call, uplink or downlink sources. Protected sources require `CAPTURE_AUDIO_OUTPUT`.
- AAC/M4A with selectable sample rate and bitrate, or AMR-NB/AMR-WB with codec-compatible rates. Configuration applies to the next recording. Automatic mode can fall back to microphone; the notification shows the source actually used.
- Notification Stop and Pause/Resume buttons can be enabled independently. Stop saves the file and suppresses automatic restart for that detection session. Pause/resume continues the same file. The library also provides controls.
- Optional accessibility microphone sharing and phone-state detection for non-root users. The app no longer refuses to open outside `/system`.
- Optional notification-based call detection for WhatsApp/Business, Telegram/X, Messenger and Instagram, with per-app switches.
- Category filters, date groups, search, light/dark themes and a recording summary. Existing flat-folder M4A recordings remain visible under Phone calls. New recordings use category folders; AMR files are included.
- Built-in 6–12 digit app PIN using a salted PBKDF2 verifier and retry delay. Locks when all app activities enter the background; covers Settings shortcuts and hides recent-app previews. Backups are disabled. This is an interface lock, not audio-file encryption or protection against a rooted administrator. Forgotten PINs cannot be recovered inside the app.

## Audio support: read this first

| Mode | Requirements | Practical limit |
| --- | --- | --- |
| Privileged cellular recording | Privileged system installation, allowlisted `CAPTURE_AUDIO_OUTPUT`, runtime microphone permission | Both sides depend on ROM/audio HAL support. Root alone does not grant protected sources. |
| Non-root cellular recording | Enable automatic recording, non-root microphone mode, accessibility service, microphone and phone-state permissions | Microphone capture where Android allows it. Speakerphone can help; remote audio and Bluetooth audio are not guaranteed. Phone-state detection can start during outgoing dialing once the device enters call audio mode. |
| Internet calls | Enable automatic recording, VoIP detection, notification access and selected apps | Detects ongoing call notifications while communication audio is active. Some apps/versions do not publish a usable notification. Automatic uses microphone. Privileged sources can be tried manually but do not guarantee the remote stream. |
| Manual recording | Open the app, grant microphone permission, choose Record now and a category | Available when automatic detection is unsupported. Stop manually when finished. |

Accessibility does not read screen text, press call controls, or store message content. The notification listener inspects package identity, call category, ongoing flag and call style; it does not save notification text. Calls and PIN data stay on the device.

Android can refuse background microphone-service starts. When that happens, a **Call detected** notification lets the user explicitly start recording. Allow notifications for this flow. An expired call notification cannot start a recording after its session has ended. Only one microphone recording can run at a time; a second detected session is skipped with a message.

The OS can supply silence without reporting a recorder error. A successfully created file therefore does **not** prove both sides are audible. Make a short test call with each chosen source and route before relying on it. MediaProjection/playback capture is not a universal solution for private call audio.

Platform references: [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input), [background microphone service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Setup

1. Install the APK normally for microphone mode, or install the universal module through a root manager and reboot.
2. Open the app and grant microphone permission. Contacts are optional for contact names. Phone-state permission is required for accessibility cellular detection. Allow notifications for recording status and controls.
3. In Settings, enable automatic call recording and configure audio. For non-root use, enable the non-root option and **Call Recorder microphone support** in Android Accessibility settings.
4. For internet calls, enable VoIP detection, select apps and enable **Call Recorder VoIP detection** in Android notification access settings. Accessibility microphone sharing may also be needed on the device.
5. Optionally enable App lock and choose a PIN. Android may require enabling restricted settings for a sideloaded accessibility service.

## Universal module

One ZIP carries a privileged APK and the matching permission allowlist.

| Installation route | Behaviour |
| --- | --- |
| Magisk app | Uses Magisk's module installer and `customize.sh`. |
| KernelSU / KernelSU Next manager | Uses the manager's native ZIP installer and `customize.sh`; does not source Magisk utilities. |
| APatch manager | Uses the native APModule installer and `customize.sh`. |
| Recovery with root manager installed and decrypted `/data` | Stages the module in `/data/adb/modules/zoro.benojir.callrecorder`, then reboot with that manager enabled. |
| Recovery without root manager | Direct privileged installation only on an already mounted, writable system filesystem. Supports `/system`, `/system_root/system` and nested `/system/system` layouts. Copies the APK and permission XML together, sets ownership, permissions and SELinux labels, and preserves rollback copies during replacement. |

On KernelSU/Next/APatch versions that delegate system mounting, install and enable a compatible mounting backend/metamodule before installing this module. A successful ZIP extraction alone does not mean the APK is mounted as a system app. Verify **Permissions and installation status** in Settings after reboot.

Recovery cannot universally write EROFS or verified read-only dynamic partitions. This installer stops with an explanation rather than attempting to disable AVB, reformat partitions or change SELinux enforcement. Mount/decrypt `/data` first for systemless recovery installs. Root managers normally do not support recovery installation themselves; this ZIP supplies its own separate recovery path.

Reference formats: [Magisk](https://topjohnwu.github.io/Magisk/guides.html), [KernelSU](https://kernelsu.org/guide/module.html), [APatch](https://apatch.dev/apm-guide.html).

## Build and package

Use JDK 17 or 21, Android SDK 34 and the Gradle wrapper (8.7 / Android Gradle Plugin 8.5.1).

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\scripts\package-module.ps1 -ApkPath .\app\build\outputs\apk\debug\app-debug.apk -DebugBuild
node .\scripts\test-module.mjs
```

The packaging script verifies APK identity, version and signature, normalises installer line endings and uses the supplied APK. It deliberately excludes the historical checked-in v1.5 APK from `magisk-module/system/priv-app`.

For distribution, build/sign a release APK with your existing signing key and pass its path to the packaging script without `-DebugBuild`. This repository does not contain a release signing configuration. Debug builds use a different signature from a published release; do not replace an existing signed installation with one without planning for signature compatibility and preserving recordings. Update URLs remain pointed at the last published release until a real 2.0 release is published.

Outputs are written to `build/distributions/`. Android 10+ is required. No root commands or device flashing are run by the desktop build.

## Verification on hardware

Desktop validation on 2026-10-25: debug APK assembled; 12 JVM tests passed; 23 isolated installer checks passed; Android lint completed with 0 errors and 89 warnings (mostly unused resources and dependency updates). The debug APK signature and its identity inside the universal ZIP were verified. No physical Android device was connected, so call audio, UI rendering and real flashing remain unverified on hardware.

Host unit tests cover conference member transitions, duplicate active events, source permission selection, encoder rate compatibility, category mapping and PIN verification. Installer tests run shell syntax checks and isolated fixtures for all manager flags, recovery layouts, read-only rejection and rollback. These do not certify a device's audio stack.

Before release, test:

- Incoming/outgoing cellular call; hold/resume; add a second call; merge; disconnect one participant; end the conference. Check one readable recording and audible participants.
- Pause/resume and Stop from the notification; disable each button in Settings during recording; verify a stale action cannot affect a later call and Stop does not restart during the same conference.
- Non-root permission denial, accessibility disabled/re-enabled, outgoing dialing, locked screen and the Android 14+ notification-tap start flow.
- Voice/video calls in each selected VoIP app; rejected/missed calls and ordinary message notifications; speaker, earpiece, wired headset and Bluetooth. Check the actual audio, including remote audio and silence.
- App lock from launcher and Settings shortcut; background/return; rotation; process recreation; incorrect PIN delay; change/disable PIN. Playback/share/rename/delete in filtered libraries, including AMR and older files.
- Manager install/update/disable/remove and reboot for each root backend, with mounting enabled. Recovery installs on each supported layout, decrypted data, read-only rejection and interrupted replacement rollback.

Author: Benojir Sultana · [Project releases](https://github.com/Benojir/Android-Call-Recorder/releases) · [Issues](https://github.com/Benojir/Android-Call-Recorder/issues/)
