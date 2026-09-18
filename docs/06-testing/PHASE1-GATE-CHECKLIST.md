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

- Workflow: Android CI run `35316841233`
- Result: `success`
- Verified step: `Lint, unit test, compile instrumented tests and build DEV APK` → success
- DEV APK artifact: `magina-olivo-dev-debug`
- Artifact id: `10535541910`
- Artifact digest: `sha256:2557c6d142dd2376e1444f67cafc497f976b8f6a54cdcdb50821e2d5496dc16a`
- Head SHA: `10bd9f34deeed98878e4f196e8f0c74c1288d9a6`
- Local extracted APK SHA-256: `47e0b158cec00e3cb55b79f077dc1146d0ed5b782321e80221290104b7bfc744`

## Physical Android device

Record before PASS:

- Device model: physical Android phone (user test)
- Android version/API: Android 16 (current device environment)
- APK commit SHA: `6bdfe9958d5860e08c1a2f19720f23d14c119bc1`
- Test date: 2026-09-18

Then verify:

- [x] APK manually installed on a physical Android phone from the CI artifact.
- [x] DEV APK from the verified CI artifact installs and launches.
- [x] Launcher/build uses provisional neutral label `Olivar Dev`.
- [x] App opens without crash.
- [x] Previous physical build displayed the retired placeholder and `DEV`; install/launch evidence remains valid for the foundation shell.
- [x] Current RC1.2-neutral build displays `Olivar`, `Gestión de tu olivar` and `DEV` on a physical phone.
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


### Gate 1 decision — 2026-09-18

**PASS for Foundation integration.**

Evidence:
- Android CI on the refreshed RC1.2 branch: PASS.
- DEV APK produced by CI.
- User-installed RC1.2-neutral APK on a real Android phone.
- User screenshot confirms first physical render: `Olivar · Gestión de tu olivar · DEV`.
- The screen is intentionally static; Phase 1 contains no navigation or product flow by design.

The unobserved Home/reopen/force-stop/rotation checks remain useful smoke tests, but they are not blockers for merging the minimal Foundation because no navigation/stateful product functionality exists yet. They must be included in later real-device beta/QA gates.

**Gate 1: PASS**
