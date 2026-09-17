# Phase 1 Gate — Android Foundation

Gate 1 can be marked PASS only after all items are evidenced.

## Automated

- [ ] Gradle wrapper reports Gradle 9.4.1 on JDK 17.
- [ ] `lintDevDebug` passes.
- [ ] `testDevDebugUnitTest` passes.
- [ ] `assembleDevDebug` passes.
- [ ] `assembleDevDebugAndroidTest` passes.
- [ ] GitHub Android CI passes for the implementation PR.
- [ ] CI artifact `magina-olivo-dev-debug` exists.

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
