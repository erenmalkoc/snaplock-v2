# Snaplock

Snaplock is a KeePass-compatible Android password manager built with Kotlin,
Jetpack Compose, Hilt, Navigation Compose, and Kotpass. It opens a selected
`.kdbx` database, keeps the decoded database in memory for the active session,
and clears the session when the app is locked or moved to the background.

## Features

- Select and open a KeePass `.kdbx` database
- List entries from the unlocked database
- View entry username and password details
- Toggle password visibility
- Copy passwords to the clipboard with timed clearing
- Optional biometric unlock flow for the saved database password

## Project Structure

- `domain`: repository contracts, models, and use cases
- `data`: Kotpass data source, session cache, encrypted preferences, clipboard helper
- `presentation`: ViewModels and UI state
- `ui`: Compose screens, reusable components, navigation, and theme
- `di`: Hilt bindings

## Build And Test

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Release builds enable code and resource shrinking. Project-specific rules live
in `app/proguard-rules.pro`.

## Notes

Snaplock does not persist the decoded KeePass database. The selected database is
read through Android's document picker and the decoded database lives only in the
process session cache.

## Publishing

Snaplock is distributed on F-Droid and Google Play. Packaging metadata lives in
`fastlane/metadata/` and the F-Droid build recipe in `fdroid/`. See
[fdroid/PUBLISHING.md](fdroid/PUBLISHING.md) for the release steps.

## License

Snaplock is free software licensed under the GNU General Public License v3.0 or
later. See [LICENSE](LICENSE).
