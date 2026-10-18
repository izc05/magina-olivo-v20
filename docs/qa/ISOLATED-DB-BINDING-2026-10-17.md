# Isolated database binding — observed RED and candidate correction

QA branch `fix/isolated-db-binding` starts at main `21826292263f099c529da270093780a827f1b5e3`, independently of G1b and the reminder-save diagnostics branch. No production or migration changes.

`IsolatedAppStateRule` previously read and wrote the private volatile singleton by reflection without coordinating with the initializer. `MaginaOlivoDatabase.getInstance` uses double-checked initialization inside `synchronized(this)` in its companion object. That actual monitor is `MaginaOlivoDatabase.Companion`, not the database Class object. A reflective write outside this monitor can complete after the initializer's inner null check and before its final assignment, allowing the initializer to overwrite the fixture.

The existing unsafe install/restore operations are extracted into the test-only `IsolatedDatabaseBinding` helper without adding synchronization. The new test exercises the actual production `getInstance`; the initializer atomically clears the singleton and calls getInstance while holding the actual Companion monitor. It pauses in a ContextWrapper's applicationContext after the real inner null check. The binder invokes the shared helper, and the parent waits for the binder to terminate (old behavior) or block on the monitor (intended fixed behavior) before releasing initialization. There are no arbitrary sleeps or simulated singleton implementations. The focused workflow force-stops only the fresh DEV package before instrumentation. The controlled null-to-initializer sequence holds the real Companion monitor, so it does not require disabling any receiver.

Expected RED: the old binder completes first, then the real initializer overwrites the fixture and `assertSame(fixture, getInstance(...))` fails. Intended later GREEN, only after observing remote RED: synchronize helper install and restore on the actual Companion monitor; the binder blocks until initialization completes, installs the fixture, and preserves/restores the initializer-created database.

The test requires DEV and QEMU, opens no Activity, maps the initializer's database path to a fresh QA filename, and uses a separate fixture filename. It restores the original singleton in finally, closes only newly created objects, and deletes only those unique QA database names. It does not open or mutate an existing user's database. Local compilation/emulation is not requested because of disk constraints; root owns the focused remote RED workflow.

This demonstrates the unsafe test binding independently. It does not establish why reminder-save line108 timed out, nor prove an IME correction. The first-iteration isolation failure in PR760 is consistent with this mechanism but did not log the overwritten singleton identity; avoid claiming that specific event's thread interleaving was observed.

Harness correction: run 38990208337 at d8f2ee4d built and installed successfully but Android rejected shell component-state mutation before instrumentation. This is not a causal RED result. Remove that unnecessary shell operation and retain all test assertions and timeouts; the next exact HEAD must demonstrate the expected fixture-identity failure before fixing the helper.

## Observed causal RED and candidate GREEN

Run 38990866397, HEAD 7108c3097bf3e0f8e35af7677baa4695f68350eb, artifact 12085062096: matching DEV build/install succeeded; instrumentation ran 1 test and failed only at the expected fixture identity assertion (line88, 0.474s). Logcat records binder TERMINATED before releasing the initializer. The actual initializer replaced the installed fixture. Two reviewers independently inspected the artifact.

Candidate correction synchronizes only test helper install/restore on MaginaOlivoDatabase.Companion. The test also captures the original singleton in the same critical section as its reset, closing a cleanup snapshot window identified in review. No assertion or timeout is removed or increased. The focused test and complete Android/Evidence workflows must pass on the new exact HEAD before any completion claim. Production source, auth, Room schema and migrations are unchanged.
