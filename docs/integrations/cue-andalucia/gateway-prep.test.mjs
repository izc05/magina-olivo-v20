import test from 'node:test';
import assert from 'node:assert/strict';
import { FakeCueGateway } from './gateway-prep.fake.mjs';

const context = { workspaceId: 'SYN-WORKSPACE-A', holdingId: 'SYN-HOLDING-A' };
const revision = { id: 'SYN-REVISION-1', operation: 'UPSERT', payloadBytes: '{"synthetic":true,"activityId":"SYN-ACTIVITY-1"}' };

test('registered software/valid certificate are not substitutes for holding authorization', () => {
  const gateway = new FakeCueGateway();
  assert.throws(() => gateway.submitRevision(context, revision, 'KEY-1'), /authorization_required/);
  gateway.grant(context);
  assert.equal(gateway.submitRevision(context, revision, 'KEY-1').state, 'SUBMITTING');
  gateway.revoke(context);
  assert.throws(() => gateway.submitRevision(context, revision, 'KEY-1'), /authorization_required/);
});

test('same immutable revision/key returns original receipt, changed bytes conflict', () => {
  const gateway = new FakeCueGateway(); gateway.grant(context);
  const receipt = gateway.submitRevision(context, revision, 'KEY-1');
  assert.deepEqual(gateway.submitRevision(context, revision, 'KEY-1'), receipt);
  assert.throws(() => gateway.submitRevision(context, { ...revision, payloadBytes: '{}'}, 'KEY-1'), /idempotency_conflict/);
  receipt.state = 'ACCEPTED';
  assert.equal(gateway.submitRevision(context, revision, 'KEY-1').state, 'SUBMITTING');
});

test('receipt scopes and authorization isolate workspaces and holdings', () => {
  const gateway = new FakeCueGateway(); gateway.grant(context);
  const receipt = gateway.submitRevision(context, revision, 'KEY-1');
  const other = { ...context, workspaceId: 'SYN-WORKSPACE-B' };
  assert.throws(() => gateway.submitRevision(other, revision, 'KEY-1'), /authorization_required/);
  gateway.grant(other);
  assert.throws(() => gateway.checkSubmission(other, receipt, []), /unknown_receipt/);
  assert.notEqual(gateway.submitRevision(other, revision, 'KEY-1').id, receipt.id);
});

test('partial batch produces activity states, receipt is never blanket acceptance', () => {
  const gateway = new FakeCueGateway(); gateway.grant(context);
  const receipt = gateway.submitRevision(context, revision, 'KEY-1');
  assert.equal(receipt.state, 'SUBMITTING');
  const results = gateway.checkSubmission(context, receipt, [
    { activityId: 'SYN-1', valid: true }, { activityId: 'SYN-2', valid: false }, { activityId: 'SYN-3', valid: null },
  ]);
  assert.deepEqual(results.map(r => r.state), ['ACCEPTED', 'REJECTED', 'SUBMITTING']);
});

test('callback correlation is tied to initiator scope and consumed only once', () => {
  const gateway = new FakeCueGateway();
  gateway.beginAuthorization(context, 'SYN-CORRELATION');
  assert.throws(() => gateway.completeAuthorization({ ...context, holdingId: 'OTHER' }, 'SYN-CORRELATION'), /invalid_correlation/);
  gateway.completeAuthorization(context, 'SYN-CORRELATION');
  assert.throws(() => gateway.completeAuthorization(context, 'SYN-CORRELATION'), /invalid_correlation/);
  assert.throws(() => gateway.beginAuthorization(context, 'SYN-CORRELATION'), /duplicate_correlation/);
});

test('protocol adapter change preserves domain bytes and idempotency hash', () => {
  const before = JSON.stringify(revision);
  const adapters = ['AndaluciaIuws3114Adapter', 'SyntheticFutureHorizontalAdapter'];
  const receipts = adapters.map(id => {
    const gateway = new FakeCueGateway(id); gateway.grant(context);
    return gateway.submitRevision(context, revision, 'KEY-1');
  });
  assert.equal(receipts[0].payloadHash, receipts[1].payloadHash);
  assert.equal(JSON.stringify(revision), before);
  assert.notEqual(receipts[0].adapterId, receipts[1].adapterId);
});
