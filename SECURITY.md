# Security policy

Snaplock keeps passwords, so security reports are taken seriously.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Report it privately through GitHub:
[Report a vulnerability](https://github.com/erenmalkoc/snaplock-v2/security/advisories/new).

Include what you found, the Snaplock version, where you installed it from (F-Droid or GitHub),
your Android version and device, and the steps to reproduce it. Never send a real database or
master password. A small test database made for the report is enough.

Snaplock is maintained by one person, so replies are best effort. The report is acknowledged as
soon as possible and you are kept informed until it is fixed. Say how you would like to be
credited in the release notes, or ask to stay anonymous.

## Supported versions

Only the latest release gets security fixes. F-Droid usually publishes a release a few days
after GitHub, so check both before reporting something that may already be fixed.

## What counts

- A way to read a locked database, a master password, a key file or a decrypted entry
- A mistake in how the KDBX file is read, written or merged that could lose or expose data
- Autofill or passkey behaviour that fills or signs for the wrong app or website
- Data leaving the device without an opt-in, including through backups, the clipboard or screenshots

Problems that need a rooted device, or physical access to a phone that is already unlocked, are
limits of every password manager and are usually out of scope.

## Verifying a download

Every release is a reproducible build signed with one key. How to check the signing certificate
is described in the [README](README.md#verify-your-download).
