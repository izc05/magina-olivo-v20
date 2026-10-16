import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateTarget } from './safety.mjs';
test('runner target rejects persistent/remote projects and credential-bearing URLs', () => {
  const p = 'magina-g1c-native-123-456', config = `project_id = "${p}"`;
  assert.equal(validateTarget(p, 'http://127.0.0.1:54321', config), `supabase_db_${p}`);
  for (const api of ['https://project.supabase.co', 'http://192.168.1.5', 'http://user:pass@localhost', 'http://localhost?secret=x']) {
    assert.throws(() => validateTarget(p, api, config));
  }
  assert.throws(() => validateTarget('production', 'http://localhost', config));
  assert.throws(() => validateTarget(p, 'http://localhost', 'project_id = "retained"'));
});
