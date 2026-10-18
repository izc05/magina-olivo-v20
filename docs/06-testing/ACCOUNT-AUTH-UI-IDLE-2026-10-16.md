# Account Auth UI synchronization — 2026-10-16

Scope: test-only follow-up for the Gate 3 failure on PR #756, SHA
`4ed11dcdc6be8af3f7d432b002d8b09e451890f1`. No later-phase product scope is added.

## Failure and cause

The full instrumented CI suite ran 801 tests with one failure:
`registrationRecoveryAndErrorsAreHonestAndDoNotDuplicateSession`, at the original
`AccountAuthScreenTest.kt:114`. The downloaded log reports that `performScrollTo()`
could not find `account-recover`; it does not report a scroll-container failure.

The preceding wait observed only the persisted SIGNUP pending link.
`AccountRepository.register` intentionally stores this link before calling `signUp`
so a crash cannot lose the pending exchange. The ViewModel runs the operation on
`Dispatchers.IO`; the repository publishes the finished state and clears `busy` in
`operation`'s `finally`. The stored link therefore exists while the mode controls
can still be disabled (`enabled = !state.busy`). A recovery-mode click in that
interval leaves the registration form selected and `account-recover` absent.

## Correction

The UI test now waits for both the durable pending link and the published matching
`pendingPurpose` with `busy == false`, for signup and recovery. It asserts that mode
controls are enabled and waits for the selected form before interacting with its
submit button. All previous session, pending-link, call-count and evidence checks
remain; no assertion or timeout is relaxed.

The JVM characterization `pendingSignupIsStoredBeforeRegistrationFinishes` uses a
`CompletableDeferred` signup barrier and the coroutine test scheduler. It proves
that durable SIGNUP pending can coexist with `busy == true`, and that completing
the operation publishes the pending purpose and idle state without creating a
session. There are no arbitrary sleeps and no production changes.

## Validation

Gradle 9.4.1 with JBR and the installed Android SDK:
`:app:testDevDebugUnitTest :app:compileDevDebugAndroidTestKotlin` — BUILD SUCCESSFUL
in 2m 10s. The XML reports contain 675 tests, zero failures/errors/skips across
131 suites; `AccountRepositoryTest` passes all 13 cases. Android test sources
compile successfully. Existing Compose rule deprecation and redundant assertion
warnings remain. An earlier invocation with Gradle 9.3.1 stopped during plugin
configuration because AGP requires 9.4.1; it ran no tests.

No APK assembly/packaging task or local emulator execution was requested. A search
under `app/build` after validation found no APK files.
The original UI failure is CI evidence; final instrumented validation remains a
GitHub CI requirement for this follow-up.

## PR lineage

PR #756's original onboarding patch is already present in PR #755 as `12420c97`
(identical patch ID `3f13bedf`). This follow-up contains only the new Account Auth
test synchronization and characterization changes, based on `9575a1e5` without
repeating the onboarding patch. PR #756 is superseded and should be closed without
merging after the clean follow-up is available. PR #755 is not modified.
