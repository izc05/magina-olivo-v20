// Internal design fake for #536. Not an IUWS implementation; no network or credentials.
import { createHash } from 'node:crypto';

export class FakeCueGateway {
  #authorizations = new Set();
  #receipts = new Map();
  #correlations = new Map();
  constructor(adapterId = 'AndaluciaIuws3114Adapter') { this.adapterId = adapterId; }
  scope(context) { return JSON.stringify([context.workspaceId, context.holdingId]); }
  grant(context) { this.#authorizations.add(this.scope(context)); }
  revoke(context) { this.#authorizations.delete(this.scope(context)); }
  beginAuthorization(context, correlationId) {
    if (this.#correlations.has(correlationId)) throw new Error('duplicate_correlation');
    this.#correlations.set(correlationId, { scope: this.scope(context), consumed: false });
    return { correlationId }; // Fake: no remote token/URL, no TTL or cryptographic validation.
  }
  completeAuthorization(context, correlationId) {
    const pending = this.#correlations.get(correlationId);
    if (!pending || pending.consumed || pending.scope !== this.scope(context)) throw new Error('invalid_correlation');
    pending.consumed = true;
    this.grant(context);
  }
  submitRevision(context, revision, idempotencyKey) {
    if (!this.#authorizations.has(this.scope(context))) throw new Error('authorization_required');
    // Input is already serialized immutable canonical bytes, not arbitrary object JSON.
    const payloadHash = createHash('sha256').update(revision.payloadBytes, 'utf8').digest('hex');
    const key = JSON.stringify([context.workspaceId, context.holdingId, revision.id, revision.operation, idempotencyKey]);
    const previous = this.#receipts.get(key);
    if (previous) {
      if (previous.payloadHash !== payloadHash) throw new Error('idempotency_conflict');
      return { ...previous };
    }
    const receipt = { id: `FAKE-${this.#receipts.size + 1}`, state: 'SUBMITTING', payloadHash, adapterId: this.adapterId };
    this.#receipts.set(key, receipt);
    return { ...receipt };
  }
  checkSubmission(context, receipt, activityResults) {
    if (!this.#authorizations.has(this.scope(context))) throw new Error('authorization_required');
    if (![...this.#receipts.entries()].some(([key, value]) => {
      const scope = JSON.parse(key);
      return scope[0] === context.workspaceId && scope[1] === context.holdingId && value.id === receipt.id;
    })) throw new Error('unknown_receipt');
    return activityResults.map(result => ({
      activityId: result.activityId,
      state: result.valid === true ? 'ACCEPTED' : result.valid === false ? 'REJECTED' : 'SUBMITTING',
    }));
  }
}
