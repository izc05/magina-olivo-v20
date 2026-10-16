# G1c isolated durable reservation journal

Scope approved for #759: prepare an explicit file-backed private journal, without an app consumer, Context factory, agricultural/Room writes, G1b ledger changes, network activation, sync, migrations or APK builds.

The selected design is a versioned bounded binary snapshot with SHA-256 over its body. Fields retain issuer, actor UUID, operation UUID and the exact immutable request. Receipts remain opaque reservation evidence, never access grants. `prepare` commits before `attempt` may send; accepted receipts use the domain validator and commit before returning. Unknown transport outcomes preserve the pending operation. A change of visible actor retains the validated receipt under the original scope.

An adjacent lock file and a per-canonical-path process mutex serialize read/update across journal instances/processes. Transport executes outside the lock. Updating after a concurrent state change compares against the current committed recovery and refuses to overwrite a different result. Disk reads validate all records, unique scopes, version, lengths, checksum and domain invariants. Corrupt committed data blocks reads and writes; it is never reset automatically.

Commit writes a sibling temporary file, flushes and fsyncs it, then uses `ATOMIC_MOVE` with replacement. There is no non-atomic fallback. A temporary file left by a crash is uncommitted and ignored. With no committed file, no operation is available for automatic send. I/O exceptions return no successful recovery. The previous committed snapshot remains the recovery point when failure occurs before rename. File sync and atomic rename cover process interruption; power-loss directory metadata guarantees still require platform-specific Android validation before wiring.

JVM validation covers request-before-send, process reopening, lost acknowledgement, immutable replay, issuer/account isolation, late acknowledgement, duplicate receipt, corrupted/torn data, failures before request and receipt commits, and preservation of two actor records. This is preparation, not activation or G1/G2 acceptance.

## Draft RPC adapter and remaining integration gates

`ReservationRpcTransport` serializes the proposed RPC through an injected wire only. There is no installed HTTP implementation, endpoint, application consumer, service, worker or credential persistence. Fresh credentials must match issuer and actor and remain unexpired. Responses are bounded and validated against operation, farm, canonical hash and expected remote workspace. Temporary/unknown outcomes preserve the operation; malformed receipts never become a reservation. Credentials and response bodies are redacted from their diagnostic strings.

The remote workspace routing parameter is separate from canonical payload v1. Before any application integration, its authorized mapping must be durably recorded and recovered with the original operation; reconstructing a different target after restart is forbidden. The server candidate rejects changed replay targets, but that alone does not implement client recovery. Neither the journal nor a receipt grants access to agricultural data.

The additional wire/journal test simulates a remote commit with a lost response, reopens the journal, retries the identical serialized request and accepts the same receipt without another send after durable success. It also checks that the bearer is absent from the journal. Unit fault injection is reproducible evidence of commit handling; it does not substitute for Android process-kill, disk-full, physical power-loss or real gateway validation. Those remain gates before activation.

Validation before publication: `:app:testDevDebugUnitTest` completed successfully, 710 tests / 0 failures / 0 errors / 0 skipped, including seven journal and seven RPC/recovery tests. `npm test --prefix supabase` completed 59/59, including six isolated proposal tests. The security workflow now includes proposal path changes and its existing npm test step runs the isolated suite automatically. Native G0 jobs continue to validate the existing migrations only; they do not deploy or validate this proposal as a native G1c service.
