#!/usr/bin/env python3
"""Exercise the production Room recovery UPDATE using SQLite, not copied SQL.

This validates SQL selection/atomicity only; Android Room tests remain necessary.
"""
import concurrent.futures
import pathlib
import re
import sqlite3
import tempfile
import threading
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
DAO = ROOT / 'app/src/main/java/com/isivoltpro/maginaolivo/data/local/dao/SyncOutboxDao.kt'
HELD_DAO = ROOT / 'app/src/main/java/com/isivoltpro/maginaolivo/data/local/dao/DeliveryHeldReceiptDao.kt'
MIGRATIONS = ROOT / 'app/src/main/java/com/isivoltpro/maginaolivo/data/local/DatabaseMigrations.kt'


def query_for(method, dao=DAO):
    source = dao.read_text()
    prefix = source[:source.index('fun ' + method + '(')]
    return re.findall(r'@Query\(\s*"""(.*?)"""', prefix, re.S)[-1]


def migration_27_28():
    source = MIGRATIONS.read_text()
    body = source[source.index('MIGRATION_27_28 ='):]
    body = body[:body.index('val all')]
    return re.findall(r'db\.execSQL\("(.*?)"\)', body)


CLAIM = query_for('claimFailedDeliveryForRecovery')
READY = query_for('listReadyForAccount')
MARK = query_for('markDeliveryLeaseClaim')
RECOVER = query_for('recoverDeliveryLeaseClaim')
OBSERVE = query_for('observeReady')
RELEASE = query_for('releaseHeldDeliveryForReceipt')
REOPEN = query_for('reopenBlockedDeliveryWithoutReceipt')
MARK_CONFLICT = query_for('markConflict', HELD_DAO)
PARAMS = dict(operationId='old', accountId='A', farmId='F', nowEpochMillis=100)


def fixture(db):
    db.execute('''CREATE TABLE sync_outbox (
        id TEXT PRIMARY KEY, entity_type TEXT, entity_id TEXT, account_id TEXT,
        farm_id TEXT, operation TEXT, payload_version INTEGER, payload_json TEXT,
        status TEXT, attempt_count INTEGER, base_remote_version INTEGER,
        next_attempt_at INTEGER, created_at INTEGER, updated_at INTEGER)''')
    db.executemany('INSERT INTO sync_outbox VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)', [
        ('old', 'DELIVERY', 'D', 'A', 'F', 'CREATE', 2, '{"netGrams":2850000}',
         'FAILED', 1, None, None, 10, 10),
        ('new', 'DELIVERY', 'D', 'A', 'F', 'CREATE', 2, '{"netGrams":3300000}',
         'PENDING', 0, None, None, 1, 1),
    ])
    db.execute('ALTER TABLE sync_outbox ADD COLUMN last_error_code TEXT')
    db.execute('ALTER TABLE sync_outbox ADD COLUMN last_error_message TEXT')
    for statement in migration_27_28():
        db.execute(statement)
    db.commit()


def hold(db, operation='old', delivery='D', state='HELD'):
    db.execute('''INSERT INTO delivery_held_receipts (operation_id, delivery_id, account_id, farm_id,
        remote_version, payload_hash, payload_version, revocation_operation_id, state, received_at)
        VALUES (?, ?, 'A', 'F', 1, ?, 2, 'R', ?, 50)''', (operation, delivery, 'a' * 64, state))


class RecoverySQLTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        fixture(self.db)

    def tearDown(self):
        self.db.close()

    def claim(self, **overrides):
        return self.db.execute(CLAIM, dict(PARAMS, **overrides)).rowcount

    def test_failed_with_edit_claims_same_uuid_and_payload_once(self):
        self.assertEqual([], list(self.db.execute(READY, PARAMS)))
        self.assertEqual(1, self.claim())
        self.assertEqual(0, self.claim())
        self.assertEqual(('old', '{"netGrams":2850000}', 'PROCESSING', 2),
                         self.db.execute('SELECT id,payload_json,status,attempt_count FROM sync_outbox WHERE id="old"').fetchone())
        self.assertEqual('{"netGrams":3300000}', self.db.execute('SELECT payload_json FROM sync_outbox WHERE id="new"').fetchone()[0])
        self.assertEqual([], list(self.db.execute(READY, PARAMS)))

    def test_cross_account_or_farm_cannot_claim(self):
        self.assertEqual(0, self.claim(accountId='B'))
        self.assertEqual(0, self.claim(farmId='other'))

    def test_legacy_unbound_sibling_stays_blocked(self):
        self.db.execute('UPDATE sync_outbox SET account_id=NULL WHERE id="new"')
        self.assertEqual(0, self.claim())

    def test_processing_blocked_or_failed_sibling_prevents_takeover(self):
        for status in ('PROCESSING', 'BLOCKED', 'FAILED'):
            self.db.execute('UPDATE sync_outbox SET status=? WHERE id="new"', (status,))
            self.assertEqual(0, self.claim(), status)

    def test_previously_attempted_pending_sibling_is_not_discardable(self):
        self.db.execute('UPDATE sync_outbox SET attempt_count=1 WHERE id="new"')
        self.assertEqual(0, self.claim())

    def test_backoff_and_unsupported_operation_are_respected(self):
        self.db.execute('UPDATE sync_outbox SET next_attempt_at=101 WHERE id="old"')
        self.assertEqual(0, self.claim())
        self.db.execute('UPDATE sync_outbox SET next_attempt_at=NULL, operation="DELETE" WHERE id="old"')
        self.assertEqual(0, self.claim())

    def test_reopen_retains_claim_and_never_steals_by_age(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / 'outbox.db'
            db = sqlite3.connect(path)
            fixture(db)
            self.assertEqual(1, db.execute(CLAIM, PARAMS).rowcount)
            db.commit()
            db.close()
            with sqlite3.connect(path) as reopened:
                self.assertEqual(0, reopened.execute(CLAIM, dict(PARAMS, nowEpochMillis=999999)).rowcount)
                self.assertEqual('PROCESSING', reopened.execute('SELECT status FROM sync_outbox WHERE id="old"').fetchone()[0])

    def test_two_connections_have_only_one_successful_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / 'race.db'
            with sqlite3.connect(path) as db:
                fixture(db)
            barrier = threading.Barrier(2)

            def worker():
                with sqlite3.connect(path, timeout=5) as db:
                    barrier.wait(timeout=5)
                    return db.execute(CLAIM, PARAMS).rowcount

            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                outcomes = list(pool.map(lambda _: worker(), range(2)))
            self.assertEqual([0, 1], sorted(outcomes))

    def test_recovery_requires_matching_durable_namespace_and_preserves_request(self):
        self.assertEqual(1, self.claim())
        scoped = dict(PARAMS, previousMarker='DELIVERY_LEASE_V2:scope-A:old', currentMarker='DELIVERY_LEASE_V2:scope-A:new', namespacePrefix='DELIVERY_LEASE_V2:scope-A:')
        self.assertEqual(1, self.db.execute(MARK, dict(scoped, claimMarker=scoped['previousMarker'])).rowcount)
        self.assertEqual(0, self.db.execute(RECOVER, dict(scoped, currentMarker=scoped['previousMarker'])).rowcount)
        self.assertEqual(0, self.db.execute(RECOVER, dict(scoped, namespacePrefix='DELIVERY_LEASE_V2:scope-B:')).rowcount)
        self.assertEqual(0, self.db.execute(RECOVER, dict(scoped, accountId='B')).rowcount)
        self.assertEqual(1, self.db.execute(RECOVER, scoped).rowcount)
        self.assertEqual(0, self.db.execute(RECOVER, scoped).rowcount)
        self.assertEqual(('old', '{"netGrams":2850000}', 'FAILED', 2, 'OUTCOME_UNKNOWN'),
                         self.db.execute('SELECT id,payload_json,status,attempt_count,last_error_code FROM sync_outbox WHERE id="old"').fetchone())
        self.assertEqual(1, self.claim())

    def test_unmarked_processing_is_not_adopted_by_new_protocol(self):
        self.assertEqual(1, self.claim())
        self.assertEqual(0, self.db.execute(RECOVER, dict(PARAMS, previousMarker='DELIVERY_LEASE_V1:scope-A', currentMarker='DELIVERY_LEASE_V2:scope-A:new', namespacePrefix='DELIVERY_LEASE_V2:scope-A:')).rowcount)
        self.assertEqual('PROCESSING', self.db.execute('SELECT status FROM sync_outbox WHERE id="old"').fetchone()[0])

    def test_same_owner_cannot_recover_or_reclaim_its_live_request(self):
        self.assertEqual(1, self.claim())
        prefix = 'DELIVERY_LEASE_V2:' + 'a' * 64 + ':'
        marker = prefix + '00000000-0000-0000-0000-000000000001'
        self.assertEqual(1, self.db.execute(MARK, dict(PARAMS, claimMarker=marker)).rowcount)
        self.assertEqual(0, self.db.execute(RECOVER, dict(PARAMS, previousMarker=marker, currentMarker=marker, namespacePrefix=prefix)).rowcount)
        self.assertEqual(0, self.claim())
        self.assertEqual(('PROCESSING', 2), self.db.execute('SELECT status,attempt_count FROM sync_outbox WHERE id="old"').fetchone())


class HeldReceiptSQLTest(unittest.TestCase):
    """G2.4 block 2: a late ACK held after revocation freezes its Delivery."""

    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        fixture(self.db)
        self.db.execute('DELETE FROM sync_outbox WHERE id="new"')

    def tearDown(self):
        self.db.close()

    def test_held_receipt_blocks_ready_claim_and_observe_for_whole_delivery(self):
        self.db.execute('INSERT INTO sync_outbox (id, entity_type, entity_id, account_id, farm_id, operation, payload_version, payload_json, status, attempt_count, created_at, updated_at) '
                        'VALUES ("edit", "DELIVERY", "D", "A", "F", "UPDATE", 2, "{}", "PENDING", 0, 20, 20)')
        self.db.execute('UPDATE sync_outbox SET status="CONFIRMED" WHERE id="old"')
        self.assertEqual(['edit'], [r[0] for r in self.db.execute(READY, PARAMS)])
        hold(self.db)
        self.assertEqual([], list(self.db.execute(READY, PARAMS)))
        self.assertEqual([], list(self.db.execute(OBSERVE, dict(nowEpochMillis=100))))
        self.db.execute('UPDATE delivery_held_receipts SET state="RECONCILED"')
        self.assertEqual(['edit'], [r[0] for r in self.db.execute(READY, PARAMS)])

    def test_held_receipt_prevents_failed_recovery_claim(self):
        hold(self.db, operation='other-op')
        self.assertEqual(0, self.claim())
        self.db.execute('UPDATE delivery_held_receipts SET state="CONFLICT"')
        self.assertEqual(0, self.claim())

    def test_held_receipt_of_another_delivery_does_not_block(self):
        hold(self.db, operation='x', delivery='OTHER')
        self.assertEqual(1, self.claim())

    def test_release_requires_blocked_operation_with_held_receipt(self):
        self.db.execute('UPDATE sync_outbox SET status="BLOCKED" WHERE id="old"')
        release = dict(operationId='old', accountId='A', farmId='F', nowEpochMillis=100)
        self.assertEqual(0, self.db.execute(RELEASE, release).rowcount)
        hold(self.db)
        self.assertEqual(0, self.db.execute(RELEASE, dict(release, accountId='B')).rowcount)
        self.assertEqual(1, self.db.execute(RELEASE, release).rowcount)
        self.assertEqual(0, self.db.execute(RELEASE, release).rowcount)

    def test_conflict_never_replaces_original_receipt(self):
        hold(self.db)
        args = dict(operationId='old', remoteVersion=9, payloadHash='b' * 64)
        self.assertEqual(1, self.db.execute(MARK_CONFLICT, args).rowcount)
        self.assertEqual(0, self.db.execute(MARK_CONFLICT, dict(args, remoteVersion=10)).rowcount)
        self.assertEqual((1, 'a' * 64, 'CONFLICT', 9, 'b' * 64), self.db.execute(
            'SELECT remote_version, payload_hash, state, conflict_remote_version, conflict_payload_hash FROM delivery_held_receipts').fetchone())

    def test_blocked_without_receipt_reopens_same_uuid_and_payload(self):
        args = dict(operationId='old', accountId='A', farmId='F', nowEpochMillis=100)
        self.db.execute('UPDATE sync_outbox SET status="BLOCKED" WHERE id="old"')
        self.assertEqual(0, self.db.execute(REOPEN, dict(args, accountId='B')).rowcount)
        self.assertEqual(1, self.db.execute(REOPEN, args).rowcount)
        self.assertEqual(('old', '{"netGrams":2850000}', 'FAILED', 1, 'OUTCOME_UNKNOWN'), self.db.execute(
            'SELECT id, payload_json, status, attempt_count, last_error_code FROM sync_outbox').fetchone())
        self.db.execute('UPDATE sync_outbox SET status="BLOCKED", attempt_count=0 WHERE id="old"')
        self.assertEqual(1, self.db.execute(REOPEN, args).rowcount)
        self.assertEqual(('PENDING', None), self.db.execute('SELECT status, last_error_code FROM sync_outbox').fetchone())

    def test_blocked_with_receipt_or_without_account_is_not_reopened(self):
        args = dict(operationId='old', accountId='A', farmId='F', nowEpochMillis=100)
        self.db.execute('UPDATE sync_outbox SET status="BLOCKED" WHERE id="old"')
        hold(self.db)
        self.assertEqual(0, self.db.execute(REOPEN, args).rowcount)
        self.db.execute('DELETE FROM delivery_held_receipts')
        self.db.execute('UPDATE sync_outbox SET last_error_code="RECEIPT_CONFLICT"')
        self.assertEqual(0, self.db.execute(REOPEN, args).rowcount)
        self.db.execute('UPDATE sync_outbox SET last_error_code=NULL, account_id=NULL')
        self.assertEqual(0, self.db.execute(REOPEN, args).rowcount)

    def claim(self, **overrides):
        return self.db.execute(CLAIM, dict(PARAMS, **overrides)).rowcount


if __name__ == '__main__':
    unittest.main(verbosity=2)
