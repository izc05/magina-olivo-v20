# Phase 1 Gate — Android Foundation

Gate 1 can be marked PASS only after all items are evidenced.

## Automated

- [x] Gradle wrapper reports Gradle 9.4.1 on JDK 17.
- [x] `lintDevDebug` passes.
- [x] `testDevDebugUnitTest` passes.
- [x] `assembleDevDebug` passes.
- [x] `assembleDevDebugAndroidTest` passes.
- [x] GitHub Android CI passes for the implementation PR.
- [x] CI artifact `magina-olivo-dev-debug` exists.

### Automated evidence

- Workflow: Android CI run `35273042885`
- Result: `success`
- Verified step: `Lint, unit test, compile instrumented tests and build DEV APK` → success
- DEV APK artifact: `magina-olivo-dev-debug`
- Artifact id: `10518558625`
- Head SHA: `3f179c73058197bd27d2a91b864c5425e71652f0`

## Physical Android device

Record before PASS:

- Device model:
- Android version/API:
- APK commit SHA:
- Test date:

Then verify:

- [ ] Physical phone is authorized through ADB or the APK is manually installed from the CI artifact.
- [ ] DEV APK installs as `com.isivoltpro.maginaolivo.dev`.
- [ ] Launcher shows `Mágina Olivo Dev`.
- [ ] App opens without crash.
- [ ] Screen displays `Mágina Olivo` and `DEV`.
- [ ] Android back/home/reopen does not crash.
- [ ] Force-stop and reopen succeeds.
- [ ] Rotation/configuration change does not crash where supported.
- [ ] `connectedDevDebugAndroidTest` passes on a physical device when ADB execution is available.

## Gate result

- [ ] PASS — only after the physical-device section is complete.

Only after PASS may `docs/00-master/CURRENT-STATE.md` move to Phase 2.
