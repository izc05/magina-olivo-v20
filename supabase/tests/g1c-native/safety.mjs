import assert from 'node:assert/strict';
export function validateTarget(project, api, config) {
  const pattern = process.env.MAGINA_COMBINED_NATIVE === '1'
    ? /^magina-combined-native-[0-9]+-[0-9]+$/
    : /^magina-g1c-native-[0-9]+-[0-9]+$/;
  assert.match(project ?? '', pattern);
  const url = new URL(api);
  assert.equal(url.protocol, 'http:');
  assert.ok(['localhost', '127.0.0.1'].includes(url.hostname));
  assert.equal(url.username + url.password + url.search + url.hash, '');
  assert.ok(config.includes(`project_id = "${project}"`));
  return `supabase_db_${project}`;
}
