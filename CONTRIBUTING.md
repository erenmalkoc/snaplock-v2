# Contributing to Snaplock

Thanks for helping. Bug reports, translations, documentation and code are all welcome.

## Before you start

- Search the [issues](https://github.com/erenmalkoc/snaplock-v2/issues) first.
- Report security problems privately, see [SECURITY.md](SECURITY.md).
- For a larger change, open an issue to talk about it before you write code, so the work is not
  wasted.

## Reporting a bug

Use the bug report form. Say which version you run (Settings shows it) and where you installed
it from, F-Droid or GitHub, and add steps that reproduce the problem. Never paste a real
database, master password or entry into an issue.

## Build and test

You need JDK 17 or newer and the Android SDK. Android Studio sets up both.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

The unit tests run on the JVM. Run them and lint before you open a pull request, CI runs the same
steps.

## Code

- Kotlin with Jetpack Compose and Hilt, layered as described under project structure in the
  [README](README.md#build-from-source).
- The Kotlin sources have no code comments. Put the reasoning in the commit message and name
  things so the code explains itself.
- Every user-facing string goes into `app/src/main/res/values/strings.xml` and into every other
  `values-*` folder in the same pull request.
- Keep changes small and focused, one logical change per commit, and add or update tests with it.
- Snaplock has no analytics, ads or proprietary libraries, and will not take any. F-Droid does
  not accept them either.

## Texts and translations

- User-facing text uses short plain sentences and no semicolons. End a sentence with a full stop,
  or join two with "and" or "but".
- English says "database" and "password". Turkish says "veritabanı" and "parola". Keep the terms
  the app already uses.
- The store listing lives in `fastlane/metadata/android/<locale>`. A unit test checks its length
  limits, its markup and the semicolon rule.
- New languages are welcome. Open an issue first so the translation can be set up properly and
  reviewed by a native speaker.

## Releases

Releases are built reproducibly and signed by the maintainer, see
[fdroid/PUBLISHING.md](fdroid/PUBLISHING.md). A pull request does not need to bump the version.

## License

By contributing you agree that your work is licensed under the GPL-3.0-or-later, like the rest of
Snaplock.
