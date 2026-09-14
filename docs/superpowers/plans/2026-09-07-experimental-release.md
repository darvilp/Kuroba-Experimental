# Experimental release implementation plan

> For agentic workers: execute with superpowers:executing-plans. Review the changes before publishing.

**Goal:** Fix the two reviewed defects, prepare locally signed experimental APKs from the cumulative release branch, push that branch to the fork, and verify its code-check workflow.

**Architecture:** Configure completion handling after successful video preparation, independently of autoplay. Add an explicit experimental build identity that retains Dev behavior, with a separate package and private signing key. Pushes validate the branch in GitHub Actions while hosted signing and publication remain disabled until credentials are configured in a separate follow-up.

**Tech stack:** Kotlin, Media3, Gradle, Robolectric/JUnit, Python, GitHub Actions.

**Spec:** User-approved recommendations in the preceding project review, including separate experimental identity and private signing.

## Constraints

- Preserve all features at a930dc4f3, including the Samsung VP8 operating-rate experiment.
- Preserve the original Personal branch and its package/signing compatibility.
- Keep private signing material outside Git and release assets.
- Push release/experimental to darvilp/Kuroba-Experimental with exact commit provenance.
- Copy verified APKs to E:\apks after validation.
- Stop before creating tags, uploading GitHub release assets, publishing a release, or exporting signing credentials.

## Tasks

- [ ] Add ExoPlayerWrapperTest reproducing pause, deactivate, prepare while paused, raw Play, completion. Assert callback delivery and retained pause; confirm failure before fixing.
- [ ] Configure end-behavior/listener setup after successful preparation while retaining the settings refresh in start. Remove the listener on release as well as deactivation so pooled players cannot notify an old wrapper. Test preparation without autoplay, duplicate prevention, and release cleanup.
- [ ] Rename AppMigration_V3_V4 to AppMigrationV3ToV4 and update its registration. Verify Detekt passes without broad baseline changes.
- [ ] Add an explicit experimental build option with package suffix .experimental, label KurobaEx Experimental, Dev runtime behavior, version v1.3.44-experimental.1, and monotonically increasing version code. Keep build types 0/1/2 unchanged.
- [ ] Configure experimental release signing from environment variables. Keep the initial private key outside the checkout. Export to fork Actions secrets in a separate follow-up.
- [ ] Add a release/experimental workflow that always runs Detekt and all unit tests. Explicitly skip signed APK production on pushes without signing secrets and fail a manual publication request when they are absent.
- [ ] Add a reusable APK packaging/verification script and tests for its failure cases. Document manual invocation, package/signing compatibility, and future version increments.
- [ ] Run regression tests, full unit tests, Detekt, release build, and independent code review. Commit explicit changed files.
- [ ] Build from the final commit, verify APK identity/signatures/commit and checksums, push the release branch, verify CI and the exact remote commit, and hand off local APKs.

GitHub-hosted signing, tagging, asset upload, and release publication are outside this plan's completion point.
