import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateTarget } from './safety.mjs';

test('valid disposable target passes validation', () => {
  const container = validateTarget('magina-g2-native-123-456', 'http://127.0.0.1:54321', 'project_id = "magina-g2-native-123-456"');
  assert.equal(container, 'supabase_db_magina-g2-native-123-456');
});

test('production or remote target is rejected', () => {
  assert.throws(() => validateTarget('magina-g2-native-123-456', 'https://example.supabase.co', 'project_id = "magina-g2-native-123-456"'));
  assert.throws(() => validateTarget('production-project', 'http://127.0.0.1:54321', 'project_id = "production-project"'));
});
