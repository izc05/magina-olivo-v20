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

- Workflow: Android CI run `35312901468`
- Result: `success`
- Verified step: `Lint, unit test, compile instrumented tests and build DEV APK` → success
- DEV APK artifact: `magina-olivo-dev-debug`
- Artifact id: `10534221918`
- Artifact digest: `sha256:f620e7b6be745ba9fa0d2286b4f3c84a06097c71cd3f7d52832b755c00130e4e`
- Head SHA: `16436e85016a28eea7dc3faad391d883b8aa7d61`

## Physical Android device

Record before PASS:

- Device model: physical Android phone (user test)
- Android version/API: Android 16 (current device environment)
- APK commit SHA: `6bdfe9958d5860e08c1a2f19720f23d14c119bc1`
- Test date: 2026-09-18

Then verify:

- [x] APK manually installed on a physical Android phone from the CI artifact.
- [x] DEV APK from the verified CI artifact installs and launches.
- [ ] Launcher shows provisional label `Olivar Dev`.
- [x] App opens without crash.
- [x] Previous physical build displayed the retired placeholder and `DEV`; install/launch evidence remains valid for the foundation shell.
- [ ] Current RC1.2-neutral build displays `Olivar`, `Gestión de tu olivar` and `DEV`.
- [ ] Android back/home/reopen does not crash.
- [ ] Force-stop and reopen succeeds.
- [ ] Rotation/configuration change does not crash where supported.
- [ ] `connectedDevDebugAndroidTest` passes on a physical device when ADB execution is available.

### Physical evidence received

- User-provided screenshot on 2026-09-18 shows the earlier DEV foundation build running on a real Android phone.
- This confirms install + launch + first render on physical hardware. Because the public branding decision changed afterward, the current neutral-label APK needs one final visual recheck.
- Remaining interaction smoke items must be confirmed before Gate 1 PASS.

## Gate result

- [ ] PASS — only after the physical-device section is complete.

Only after PASS may `docs/00-master/CURRENT-STATE.md` move to Phase 2.
