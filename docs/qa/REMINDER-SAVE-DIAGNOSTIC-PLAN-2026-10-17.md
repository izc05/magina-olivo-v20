# Reminder save — diagnostic experiment

## Warm 17a32aee result and failure-time evidence follow-up

Manual warm run `38991583774`, HEAD
`17a32aeef05fed10959cc8c28e3d1541603c97bf`, failed in the full suite before the
separate MainReminder wrapper/probe. Offline Room passed 220 tests. The full
suite ran 827 tests with three failures: AppNavigationTest central-add back action
(`RootViewWithoutFocusException`, line 75), MainActivitySmokeTest onboarding root
not displayed (line 30), and MapSearchScreenTest light coordinate-field collapse
wait after `farm-map-go` (10,000 ms, line 77). The light place-resolved screenshot
exists; the subsequent coordinates-resolved screenshot is absent.
MainReminderPersistenceTest passed within that full suite. Both real G1b process
recovery phases and MainMapNavigation also passed on this exact HEAD. The new
MapSearch observation does not establish the original map regression.
These are distinct observed failures; no common cause or IME cause is established.
Cold run `38991580782` on the same exact HEAD passed all 10 original and 5 IME
experiments. The original save-description timeout remains unreproduced.

The follow-up adds an opt-in `FailureEvidenceRule` only to those three test
classes. It is nested inside their Compose rule, so `TestWatcher.failed` runs
before Compose/Activity teardown. With warm opt-in, only the full instrumentation
command receives `qaFailureDiagnostics=true`; ordinary runs receive no extra
argument. DEV/QEMU checks precede capture. It writes the original exception,
screenshot, active window, window/IME/activity dumps, the last 400 logcat entries,
then unmerged Compose semantics. It reads no database, preferences or tokens.
Each shell command has a five-second Android timeout and the Kotlin reader caps
output at 262,144 characters. Commands contain no shell pipes: UiAutomation executes
the command directly. Each diagnostic
is attempted independently; errors are suppressed on the original test exception
and recorded when possible. Capturing does not press Back, restore focus, close
dialogs, retry, clear data, or change test assertions/timeouts.

Compose semantics is attempted last because its API synchronizes with Compose;
it uses the framework's existing synchronization timeout and reports an unavailable
tree on exception. A separate generic time limit would require changing global
idling policy or abandoning a thread still querying UI after teardown. Neither is
introduced. UIAutomation evidence is already saved before that query.

Warm full-suite evidence is pulled before the existing suite-result assertion.
The pull status and a manifest checking eight nonempty files in each of the two
sentinel folders are retained before that assertion. An original suite failure
remains the primary failure even if collection also fails. After a passing suite,
failed pull or missing sentinel captures makes the warm pipeline fail. An explicit
semantics-unavailable text file is permitted; a missing/empty file is not.
The fake-adb/static self-test verifies opt-in arguments, pull-before-failure for
adb exit 0/JUnit failure and nonzero adb failure, default behavior, and nesting
inside Compose. It also rejects warm success on pull failure or missing captures,
including simultaneous JUnit/pull failure preserving the original suite diagnosis.
This follow-up has not yet been compiled or executed on an emulator.

An additional passing instrumented self-test runs only with
`qaFailureDiagnostics=true`. With live synthetic Compose content, it explicitly
applies the watcher to a Statement throwing a sentinel, catches and checks the
same exception object, then verifies all eight evidence files. It also validates
recognizable window, IME and activity dump headers plus structured
threadtime logcat, so nonempty command usage output cannot pass as valid evidence.
A second sentinel
forces the semantics lambda to throw and checks that the original exception is
still retained and unavailability recorded. The two sentinel folders include
`synthetic-self-test.txt` and must not be counted as suite failures. This proves
the capture path when run remotely without waiting for a flaky failure; ordinary
suite runs skip this self-test. Remote compilation/execution is still pending.

## Optional warm pipeline experiment — NOT EXECUTED

Manual `workflow_dispatch` input `warm_pipeline=true` selects the complete existing
`gate3-emulator-evidence.sh`, in its original capture, offline/full-suite, map and
notification order. Its job budget is 60 minutes. PR runs and manual runs without
that explicit input retain the cold 10 original + 5 IME experiment and 30-minute
budget. No warm workflow has been dispatched or executed for this change.

Only the MainReminderPersistence wrapper receives `qaReminderDiagnostics=true`,
`qaReminderCloseIme=false`, and `qaReminderIteration=warm-later` through
`GATE3_REMINDER_DIAGNOSTICS=1`. It does not clear app data, clear logcat, retry the
test, close the IME, change assertions, or extend test timeouts. The existing
pipeline retains its own original resets and device-state transitions. The
disposable-emulator guard runs before wrapper permission mutations. Permission
restoration and its existing EXIT trap are preserved.

After instrumentation, and before interpreting any JUnit success/failure output,
the wrapper pulls all six save captures and logcat. A manifest flags every missing
or empty capture; missing evidence makes the wrapper fail even with passing JUnit.
An adb failure retains its original exit status and instrumentation output.
`evidence/` is uploaded in full for warm runs, including partial pipeline evidence
when an earlier stage fails. Such a failure may prevent reaching the target test.

Fake-adb coverage verifies collection after adb exit 0 with JUnit failure, missing
capture failure, original nonzero adb exit preservation, permission restoration,
unchanged default arguments, and rejection before mutations without safety opt-in.
These local checks do not establish Kotlin compilation, warm reproduction or an
IME cause. The cold observations (an early isolation guard failure followed by
passing save experiments) do not reproduce the original save assertion timeout.

Isolated QA branch `fix/reminder-save-diagnostics`, starting at G1b `12909d0a71054f5be338d1a8cd3e8043f20943fe`. Draft PR targets `feat/g1b-farm-consent` to show only QA changes. No production, database migration, existing gate assertion or ordinary test timeout changes.

Observed failure: Android push run38958634772 passed827 full/220 offline and real process recovery, then `MainReminderPersistenceTest#failedPlanRetryAndRecreationPreserveReminder` timed out at the original line108 waiting15seconds for the edited Room description. The method passed in that same full suite and other exact-SHA pipelines. The failing artifact lacks the UI/Room/IME state at that point. IME resizing swallowing the save tap is a hypothesis, supported by the existing `AppNavigationTest.saveEditor` comment; it is not a proven cause.

The dedicated workflow builds matching DEV target/test APKs remotely and runs a fresh API35 google_apis x86_64 emulator behind the existing image and disposable-device guards. Each iteration clears only DEV data after explicit opt-in, revokes POST_NOTIFICATIONS and clears user flags, matching `verify-context-notifications.sh`. The original method runs with `notificationQaMode=later`.

Experiments are bounded and separately labelled:10original iterations, then5explicit-IME-close iterations. The default test path retains the original save tap and15second Room assertion. Only explicit `qaReminderDiagnostics=true` permits the candidate `qaReminderCloseIme=true`, which closes the keyboard and waits for Compose idle before the same tap. There is no repeated save tap, increased timeout or test retry converting a failure into PASS.

Each failed save records the original exception, unmerged Compose semantics, the isolated synthetic activity/reminders, screenshot, active window, window dump and input-method dump before test teardown. Success also records state after the save assertion. The script saves every instrumentation result, permission state and logcat, pulls device diagnostics before the next reset, and writes per-iteration TSV results. Any failed iteration, including an original failure followed by successful candidates, makes the job fail. These are diagnostic observations, not release acceptance.

Static validation: Bash syntax check; existing disposable-device safety self-test6/6; new fake-adb test verifies10+5iterations, revoked permission, candidate arguments, retention of a baseline failure with adb exit0 and final job failure, plus an all-pass case. No local Gradle or emulator execution: local disk remains insufficient. Kotlin compilation and actual reproduction remain pending remote CI.

If the failure does not reproduce, report non-reproduction rather than proof of an IME fix. If it reproduces, inspect Room/semantics/IME/logcat together before selecting a correction. Do not mark G1b READY from this experiment alone.

Review follow-up: the runner now requires nonempty Room, semantics, active-window, IME, window and PNG captures per iteration. Missing captures or failed logcat/pull produce FAIL_DIAGNOSTICS rather than a passing observation. The fake-adb self-test also verifies this failure path. Checkout pins the PR head explicitly and records that SHA. No ordinary gate or production behavior was changed.
