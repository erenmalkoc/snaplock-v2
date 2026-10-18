<p align="center">
  <img src="docs/assets/banner.png" alt="Snaplock — KeePass-compatible password manager" width="720">
</p>

<p align="center">
  <a href="https://github.com/erenmalkoc/snaplock-v2/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/erenmalkoc/snaplock-v2?color=1DBF73&label=release"></a>
  <a href="https://github.com/erenmalkoc/snaplock-v2/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/erenmalkoc/snaplock-v2/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License: GPL-3.0-or-later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-0A7148"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-1DBF73">
  <a href="fdroid/PUBLISHING.md"><img alt="Reproducible builds" src="https://img.shields.io/badge/builds-reproducible-0A7148"></a>
</p>

**Snaplock** is a fast, private password manager for Android that works with standard
KeePass (`.kdbx`) databases. Your vault is a single encrypted file that you own: keep it
on the device or sync it through any cloud storage or Syncthing folder. No account, no
tracking, no ads.

## Download

- **GitHub:** signed APKs on the [releases page](https://github.com/erenmalkoc/snaplock-v2/releases/latest).
- **F-Droid:** inclusion is in review. F-Droid will ship the same signed APK, so you can
  switch between the two without reinstalling.

Every release is built reproducibly from its tag: anyone can rebuild the APK and get the
same bytes. Signing certificate SHA-256:
`e66b0d23e679d1d606573be6f705630f9f90e74be084ec4b59d1181ae608b8b7`

## Features

- **KeePass compatible.** Opens and saves KDBX 4 databases from KeePassXC, KeePass,
  KeePassDX and Keepass2Android, on any storage Android's file picker can reach.
- **Autofill.** Fills logins in apps and browsers, with suggestions in the keyboard on
  Android 11 and later. Websites are only matched when a known browser reports them, so a
  web page inside another app cannot ask for your credentials.
- **Works with your existing links.** Reads the app and URL links KeePassDX,
  Keepass2Android and KeePassXC store in entries.
- **The whole KeePass vault.** Nested groups, custom fields (protected or plain), file
  attachments, entry history you can restore from, and a recycle bin.
- **Key files.** Unlock with a password, a key file or both, and change the master
  password at any time.
- **Biometric unlock** with a key kept in the Android Keystore.
- **Recent databases** to reopen a vault in one tap.
- **Password generator** with length and character-set options.
- **Clipboard clearing** after a configurable delay.
- **Auto-lock** when Snaplock leaves the screen, immediately or after a delay.
- **Material You** colours, light and dark themes, English and Turkish.

## Privacy and security

- Snaplock has no internet permission. The database never leaves the file you chose.
- The decrypted database lives only in memory and is dropped when the vault locks.
- Screenshots, screen recordings and the recents preview are blocked in release builds.
- For biometric unlock, the master password is encrypted with an Android Keystore key
  that requires your fingerprint or face, is invalidated when new biometrics are
  enrolled, and is excluded from backups and device transfers.

## Build and test

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Release builds enable code and resource shrinking; project-specific rules live in
`app/proguard-rules.pro`. How releases are built, signed and verified for F-Droid is
described in [fdroid/PUBLISHING.md](fdroid/PUBLISHING.md).

## Project structure

- `domain`: repository contracts, models and use cases
- `data`: Kotpass data source, session cache, preferences, clipboard helper
- `autofill`: autofill service, form detection and entry matching
- `presentation`: ViewModels and UI state
- `ui`: Compose screens, reusable components, navigation and theme
- `di`: Hilt bindings

## License

Snaplock is free software licensed under the GNU General Public License v3.0 or later.
See [LICENSE](LICENSE).
