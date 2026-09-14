# Experimental releases

The `release/experimental` branch contains this fork's cumulative playback changes. Experimental APKs use package `com.github.k1rakishou.chan.experimental`, app name KurobaEx Experimental, Dev runtime behavior, and a dedicated private signing key. The original Personal build remains available through `-PbuildType=2` without the experimental flag.

The built-in updater is disabled. Testers install each APK update manually. The experimental package has separate app data from Personal and upstream installations, so installing it does not migrate the data or settings from either app.

## Prepare the next release

1. Update both values in `Kuroba/experimental-version.properties`. Increase `versionCode` for every public release and increment the experimental suffix in `versionName`.
2. Add release notes at `docs/releases/<versionName>.md`.
3. Run `python3 -m unittest discover -s scripts -p 'test_*.py'`.
4. Run `bash ./gradlew detekt testDebugUnitTest` from `Kuroba` and review the changes.
5. Commit the release changes and push `release/experimental` to the fork.
6. Confirm that the push workflow's code checks pass and signed APK production is explicitly skipped while signing secrets are absent.

APK source provenance embeds the Git commit. Build distributable APKs after the final commit. Do not move an existing release tag to different code.

## GitHub Actions

Pushes to `release/experimental` run the Experimental APKs workflow. It validates code and tests. With signing secrets absent, the signed APK job reports an explicit skip. A manual run with `publish` enabled fails clearly until all signing secrets are configured.

The workflow is restricted to `darvilp/Kuroba-Experimental`. It does not call the inherited upstream publisher. Once signing is configured in a future change, it can verify every APK's package, version, debuggable flag, signing certificate, source commit, and copied checksum before producing release assets.

The initial private key is retained locally. This release-preparation pass stops before exporting it to GitHub Actions, creating a tag, uploading an asset, or publishing a release. GitHub-hosted signing and publication require a separate follow-up.

The required repository secrets are:

- `EXPERIMENTAL_KEYSTORE`: the private PKCS12 keystore, Base64 encoded.
- `EXPERIMENTAL_STORE_PASSWORD`: its store password.
- `EXPERIMENTAL_KEY_ALIAS`: the signing alias.
- `EXPERIMENTAL_KEY_PASSWORD`: the key password.

Keep the private key and its passwords out of the repository and release assets. `docs/experimental-signing-certificate.sha256` contains only the public certificate fingerprint and is checked by the APK verifier. Back up the private signing material securely; a replacement key does not normally update existing installations.

## Build locally

Use JDK 21 and an Android SDK with platform 36 and build-tools 36.0.0. Set `ANDROID_HOME` to that SDK. Load the private signing values into these environment variables:

- `KUROBA_EXPERIMENTAL_KEYSTORE`: absolute path to the local PKCS12 keystore.
- `KUROBA_EXPERIMENTAL_STORE_PASSWORD`.
- `KUROBA_EXPERIMENTAL_KEY_ALIAS`.
- `KUROBA_EXPERIMENTAL_KEY_PASSWORD`.

From `Kuroba`, build the release:

```bash
bash ./gradlew :app:assembleRelease -PbuildType=2 -PexperimentalRelease=true \
  --no-daemon --no-configuration-cache --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx3g -Xms256m -XX:MaxMetaspaceSize=768m"
```

From the repository root, prepare the assets:

```bash
python3 scripts/prepare_experimental_release.py \
  --apk-dir Kuroba/app/build/outputs/apk/release \
  --output-dir release-assets \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0"
```

Copy the verified APKs to the private distribution location and retain `SHA256SUMS.txt` and `release-manifest.json` with them. Confirm the copied files against the checksums before sharing them with testers.

## Future GitHub publication

The repository includes a publisher for the separate GitHub signing and publication follow-up. After the signing secrets are configured and publication is explicitly approved, run it against an unchanged verified bundle with an authenticated GitHub CLI:

```bash
python3 scripts/publish_experimental_release.py --assets release-assets
```

The publisher validates local checksums, creates a draft at the exact commit, uploads only the verified assets, compares GitHub's asset digests, and publishes it as a prerelease. A failed draft upload can be retried. A published release is accepted only if its commit and assets already match; it is never overwritten. Do not run this step during local APK preparation or CI verification.
