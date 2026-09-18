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

- Device model: physical Android phone (user test)
- Android version/API: Android 16 (current device environment)
- APK commit SHA: `6bdfe9958d5860e08c1a2f19720f23d14c119bc1`
- Test date: 2026-09-18

Then verify:

- [x] APK manually installed on a physical Android phone from the CI artifact.
- [x] DEV APK from the verified CI artifact installs and launches.
- [ ] Launcher shows `Mágina Olivo Dev`.
- [x] App opens without crash.
- [x] Screen displays `Mágina Olivo`, `Gestión privada de tu olivar` and `DEV`.
- [ ] Android back/home/reopen does not crash.
- [ ] Force-stop and reopen succeeds.
- [ ] Rotation/configuration change does not crash where supported.
- [ ] `connectedDevDebugAndroidTest` passes on a physical device when ADB execution is available.

### Physical evidence received

- User-provided screenshot on 2026-09-18 shows the DEV foundation screen running on a real Android phone.
- This confirms install + launch + first render on physical hardware.
- Remaining interaction smoke items must be confirmed before Gate 1 PASS.

## Gate result

- [ ] PASS — only after the physical-device section is complete.

Only after PASS may `docs/00-master/CURRENT-STATE.md` move to Phase 2.
