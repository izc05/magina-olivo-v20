# Reminder save — diagnostic experiment

Isolated QA branch `fix/reminder-save-diagnostics`, starting at G1b `12909d0a71054f5be338d1a8cd3e8043f20943fe`. Draft PR targets `feat/g1b-farm-consent` to show only QA changes. No production, database migration, existing gate assertion or ordinary test timeout changes.

Observed failure: Android push run38958634772 passed827 full/220 offline and real process recovery, then `MainReminderPersistenceTest#failedPlanRetryAndRecreationPreserveReminder` timed out at the original line108 waiting15seconds for the edited Room description. The method passed in that same full suite and other exact-SHA pipelines. The failing artifact lacks the UI/Room/IME state at that point. IME resizing swallowing the save tap is a hypothesis, supported by the existing `AppNavigationTest.saveEditor` comment; it is not a proven cause.

The dedicated workflow builds matching DEV target/test APKs remotely and runs a fresh API35 google_apis x86_64 emulator behind the existing image and disposable-device guards. Each iteration clears only DEV data after explicit opt-in, revokes POST_NOTIFICATIONS and clears user flags, matching `verify-context-notifications.sh`. The original method runs with `notificationQaMode=later`.

Experiments are bounded and separately labelled:10original iterations, then5explicit-IME-close iterations. The default test path retains the original save tap and15second Room assertion. Only explicit `qaReminderDiagnostics=true` permits the candidate `qaReminderCloseIme=true`, which closes the keyboard and waits for Compose idle before the same tap. There is no repeated save tap, increased timeout or test retry converting a failure into PASS.

Each failed save records the original exception, unmerged Compose semantics, the isolated synthetic activity/reminders, screenshot, active window, window dump and input-method dump before test teardown. Success also records state after the save assertion. The script saves every instrumentation result, permission state and logcat, pulls device diagnostics before the next reset, and writes per-iteration TSV results. Any failed iteration, including an original failure followed by successful candidates, makes the job fail. These are diagnostic observations, not release acceptance.

Static validation: Bash syntax check; existing disposable-device safety self-test6/6; new fake-adb test verifies10+5iterations, revoked permission, candidate arguments, retention of a baseline failure with adb exit0 and final job failure, plus an all-pass case. No local Gradle or emulator execution: local disk remains insufficient. Kotlin compilation and actual reproduction remain pending remote CI.

If the failure does not reproduce, report non-reproduction rather than proof of an IME fix. If it reproduces, inspect Room/semantics/IME/logcat together before selecting a correction. Do not mark G1b READY from this experiment alone.

Review follow-up: the runner now requires nonempty Room, semantics, active-window, IME, window and PNG captures per iteration. Missing captures or failed logcat/pull produce FAIL_DIAGNOSTICS rather than a passing observation. The fake-adb self-test also verifies this failure path. Checkout pins the PR head explicitly and records that SHA. No ordinary gate or production behavior was changed.
