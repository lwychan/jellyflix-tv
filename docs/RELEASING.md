# Releasing

## Cutting a release

```bash
# Bump versionCode / versionName in app/build.gradle.kts
git commit -am "chore(release): v0.2.0"
git tag v0.2.0
git push origin main --tags
```

The `Release` workflow picks up the tag, builds `:app:assembleRelease`, and publishes the APK to a GitHub Release
with auto-generated notes.

If the tag contains a hyphen (e.g. `v0.2.0-rc1`) the release is marked as a
pre-release automatically.

## One-time: configure release signing

Without signing secrets the workflow still produces installable APKs — it
falls back to `:assembleDebug`, which is signed with the debug keystore. Good
enough to sideload, but users can't upgrade between builds without uninstalling
first.

To produce properly signed releases, generate an upload keystore once and
store it in GitHub Actions secrets:

```bash
keytool -genkey -v \
  -keystore notflix-release.jks \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -alias jellyflix

# base64-encode so it fits in a GitHub secret
base64 -w0 notflix-release.jks > release-keystore.b64
```

Then in **Settings → Secrets and variables → Actions** add:

| Secret                       | Value                                              |
| ---------------------------- | -------------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64`    | Contents of `release-keystore.b64`                 |
| `RELEASE_KEYSTORE_PASSWORD`  | Store password from `keytool`                      |
| `RELEASE_KEY_ALIAS`          | `notflix` (or whatever alias you chose)          |
| `RELEASE_KEY_PASSWORD`       | Key password from `keytool`                        |

Keep `notflix-release.jks` offline — it's your app identity. Lose it and
you can't ship updates under the same package name.
