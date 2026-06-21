# Publishing Snaplock

This project targets both F-Droid and Google Play. F-Droid is the priority.

## Prerequisites (done)

- GPLv3 `LICENSE` file at repo root.
- No proprietary dependencies (no Firebase, Google Play Services, analytics).
- `local.properties` and signing keystores are git-ignored.
- Fastlane metadata under `fastlane/metadata/android/<locale>/`.
- F-Droid build recipe in `fdroid/com.erenium.snaplock.yml`.

## F-Droid

F-Droid builds from source on its own servers and signs with its own key. You do
not upload an APK.

1. Make sure the GitHub repo is **public**.
2. Add at least one phone screenshot to
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` (PNG/JPG, e.g.
   `01.png`, `02.png`). Optionally add an `icon.png` (512x512) under
   `fastlane/metadata/android/en-US/images/`.
3. Tag the release commit so the build recipe can find it:

   ```bash
   git tag -s v1.0 -m "Snaplock 1.0"   # -s signs the tag (recommended); use -a if no GPG key
   git push origin v1.0
   ```

   The tag name (`v1.0`) must match `commit:` in `fdroid/com.erenium.snaplock.yml`.
4. Submit the app:
   - Easiest: open a **Request For Packaging (RFP)** issue at
     https://gitlab.com/fdroid/rfp/-/issues — title `com.erenium.snaplock`, link
     the repo. A volunteer can pick it up.
   - Faster (self-service): fork https://gitlab.com/fdroid/fdroiddata, add
     `metadata/com.erenium.snaplock.yml` (copy from `fdroid/com.erenium.snaplock.yml`),
     test locally with `fdroid build -v -l com.erenium.snaplock`, then open a
     merge request.

For every new release: bump `versionCode`/`versionName` in
`app/build.gradle.kts`, add a `fastlane/.../changelogs/<versionCode>.txt`, push a
new `vX.Y` tag, and add a matching `Builds:` entry to the metadata yaml (or rely
on `AutoUpdateMode` once it is in fdroiddata).

## Google Play

Play requires you to upload a signed App Bundle (.aab).

1. Create an upload keystore (keep it OUT of git):

   ```bash
   keytool -genkey -v -keystore ~/snaplock-upload.jks -keyalg RSA \
     -keysize 2048 -validity 9125 -alias snaplock
   ```
2. Add a `signingConfigs` block to `app/build.gradle.kts` that reads the keystore
   path/passwords from `local.properties` or environment variables (never commit
   them).
3. Build the bundle: `./gradlew bundleRelease`.
4. Upload to the Play Console, fill the Data Safety form (state that no data is
   collected and the database never leaves the device), and provide a privacy
   policy URL.

Note: the F-Droid build and the Play build are signed with different keys, so a
user cannot switch between them without uninstalling. This is normal.
