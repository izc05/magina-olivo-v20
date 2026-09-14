import fs from 'node:fs';
import process from 'node:process';

const root = process.cwd();
const read = (path) => fs.readFileSync(`${root}/${path}`, 'utf8');
const fail = (message) => { console.error(`community-check: ${message}`); process.exitCode = 1; };
const requireFile = (path) => {
  if (!fs.existsSync(`${root}/${path}`)) { fail(`missing ${path}`); return ''; }
  return read(path);
};
const requireText = (source, text, label) => { if (!source.includes(text)) fail(`${label} missing ${text}`); };

const migration = requireFile('database/migrations/0063_community.sql');
for (const table of ['community_posts','community_comments','community_reactions','community_bookmarks','community_reports']) {
  requireText(migration, `CREATE TABLE ${table}`, 'community migration');
}
requireText(migration, "status IN ('published','hidden','deleted')", 'community moderation state');
requireText(migration, 'Exact farm geometry', 'community privacy contract');

const routes = requireFile('apps/api/src/routes/community.ts');
for (const endpoint of [
  '/api/v1/public/community',
  '/api/v1/community/posts',
  '/api/v1/community/posts/:id/comments',
  '/api/v1/community/posts/:id/reactions',
  '/api/v1/community/posts/:id/bookmark',
  '/api/v1/community/reports',
]) requireText(routes, endpoint, 'community API');
requireText(routes, 'requireAuthenticatedUser', 'community authenticated writes');
if (routes.includes('workspace_id') || routes.includes('field_id') || routes.includes('geometry')) {
  fail('community API must not copy private workspace, field or geometry identifiers into the public feed');
}

const adminRoutes = requireFile('apps/api/src/routes/admin-community.ts');
requireText(adminRoutes, '/api/v1/admin/community/reports', 'community moderation API');
requireText(adminRoutes, '/api/v1/admin/community/moderation', 'community moderation API');
requireText(adminRoutes, 'requirePlatformAccess', 'community moderation authorization');
requireText(adminRoutes, 'auditAdminAction', 'community moderation audit');

const app = requireFile('apps/api/src/app.ts');
requireText(app, 'registerCommunityRoutes(app, db)', 'community API registration');
requireText(app, 'registerAdminCommunityRoutes(app, db)', 'community admin API registration');

requireFile('apps/web/src/app/comunidad/page.tsx');
const client = requireFile('apps/web/src/app/comunidad/community-client.tsx');
for (const behavior of ['createCommunityPost','setCommunityLike','setCommunityBookmark','createCommunityComment','reportCommunityTarget']) {
  requireText(client, behavior, 'community UI');
}
const explore = requireFile('apps/web/src/app/explorar/explore-public-client.tsx');
requireText(explore, "href: '/comunidad'", 'Explore community entry');
requireFile('apps/web/src/lib/community-source.ts');
requireFile('apps/web/src/app/comunidad/community.module.css');

if (!process.exitCode) console.log('community-check: ok');
