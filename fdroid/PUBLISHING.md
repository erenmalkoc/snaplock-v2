# Publishing Snaplock

This project targets both F-Droid and Google Play. F-Droid is the priority.

## Prerequisites (done)

- GPLv3 `LICENSE` file at repo root.
- No proprietary dependencies (no Firebase, Google Play Services, analytics).
- `local.properties` and signing keystores are git-ignored.
- Fastlane metadata under `fastlane/metadata/android/<locale>/`.
- F-Droid build recipe in `fdroid/com.erenium.snaplock.yml`.
- AGP's dependency metadata block is disabled (`dependenciesInfo` in
  `app/build.gradle.kts`); F-Droid rejects it.

## Signing model: reproducible builds

F-Droid rebuilds every release from source and publishes **our** signed APK only if
its own unsigned build is byte-identical (`Binaries` + `AllowedAPKSigningKeys` in the
recipe). This was chosen before the first F-Droid release because it cannot be
switched later, and because the same key can then be registered once for Android
developer verification, so F-Droid and GitHub installs are the same APK.

- Keystore: `~/.android-keys/snaplock-release.p12` (PKCS12, alias `snaplock`),
  password in `~/.android-keys/snaplock-release.pass`. Both stay out of git. Keep an
  offline backup: losing the key means existing installs can never be updated.
- Certificate SHA-256 (the `AllowedAPKSigningKeys` value):
  `e66b0d23e679d1d606573be6f705630f9f90e74be084ec4b59d1181ae608b8b7`

## Releasing a version

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts` and add
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
2. Push a `vX.Y.Z` tag. The Release workflow (`.github/workflows/release.yml`) builds
   the unsigned APK on Debian trixie, like the F-Droid buildserver, and a second job
   rebuilds it with another JDK, path and core count. Both builds must match.
3. Download the artifact and sign it with apksigner from **build-tools 34**: output
   from 35 and newer fails apksigcopier, which F-Droid uses for the comparison.

   ```bash
   gh run download <run-id> -n app-release-unsigned -D dist
   apksigner sign --ks ~/.android-keys/snaplock-release.p12 --ks-key-alias snaplock \
     --ks-pass file:$HOME/.android-keys/snaplock-release.pass \
     --out dist/snaplock-X.Y.Z.apk dist/app-release-unsigned.apk
   apksigcopier compare dist/snaplock-X.Y.Z.apk --unsigned dist/app-release-unsigned.apk
   ```

   `apksigcopier` is `pip install apksigcopier`. The compare must print nothing and
   exit 0. On Windows, call `build-tools\34.0.0\apksigner.bat` with Windows paths and
   give apksigcopier the same script: `--verify-cmd "<path>\apksigner.bat verify"`.
4. Create the GitHub release `vX.Y.Z` with `snaplock-X.Y.Z.apk` attached. The
   `Binaries` URL in the recipe points at it, so the file name must match.
5. Once the app is in fdroiddata, `AutoUpdateMode` and `UpdateCheckMode` pick up new
   tags; F-Droid downloads the APK from the release and verifies it.

Never build the APK you sign on Windows: the checkout uses CRLF line endings there,
so the output differs from F-Droid's Linux build.

## F-Droid submission

Merge request: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/40965. It was
closed for inactivity on 2026-08-14 while the signing model was undecided.

1. In the fork (`gitlab.com/erenmalkoc/fdroiddata`, branch `add-snaplock`), replace
   `metadata/com.erenium.snaplock.yml` with `fdroid/com.erenium.snaplock.yml`.
   `commit:` must be the full 40-character hash of the tagged commit.
2. Reopen the merge request and reply that it now uses reproducible builds. The
   pipeline rebuilds the app and compares it with the release APK.

## Anti-features

Since 1.1.0, Snaplock has the INTERNET permission for two opt-in features: the breach
check talks to Have I Been Pwned, a service whose server is not free software, and site
icon downloads talk to the websites in the vault. F-Droid marks the first as
`NonFreeNet`; add it to every build entry from 1.1.0 on:

```yaml
    antifeatures:
      NonFreeNet:
        en-US: Optional breach check against Have I Been Pwned (off by default).
```

## Passkey browser list

`app/src/main/assets/privileged_browsers.json` is Google's list of browsers allowed to
ask for passkeys on behalf of websites. Refresh it before a release and review the diff:

```bash
curl -sSL -o app/src/main/assets/privileged_browsers.json \
  https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json
git diff app/src/main/assets/privileged_browsers.json
```

A browser missing from it can still use passwords, but its passkey requests are signed
for the browser app itself, which websites reject.

## Android developer verification

From 2027, certified Android devices only install apps from registered developers
without extra steps, whatever the store. Register the package `com.erenium.snaplock`
with the certificate SHA-256 above in the Android Developer Console (or a Play
Console account). Because F-Droid ships our signed APK, no F-Droid key needs to be
registered.

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

Note: unless the release key above is uploaded as the Play app signing key, the Play
build is signed with a different key, so a user cannot switch between the Play and
F-Droid builds without uninstalling.
