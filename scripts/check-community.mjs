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
requireText(migration, 'community_reports_moderation_queue_idx', 'community moderation queue index');

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
requireText(routes, "CASE WHEN up.visibility = 'public' THEN u.id::text ELSE NULL END AS author_id", 'community author id privacy');
requireText(routes, "ELSE 'Miembro de Mágina'", 'community private author alias');
requireText(routes, "CASE WHEN up.visibility = 'public' THEN u.avatar_url ELSE NULL END AS author_avatar_url", 'community profile privacy');
if (routes.includes('workspace_id') || routes.includes('field_id') || routes.includes('geometry')) {
  fail('community API must not copy private workspace, field or geometry identifiers into the public feed');
}

const bookmarksRoute = requireFile('apps/api/src/routes/community-bookmarks.ts');
requireText(bookmarksRoute, '/api/v1/community/bookmarks', 'community saved-post API');
requireText(bookmarksRoute, 'requireAuthenticatedUser', 'community saved-post authentication');
requireText(bookmarksRoute, 'JOIN community_bookmarks', 'community saved-post source');
requireText(bookmarksRoute, "CASE WHEN up.visibility = 'public' THEN u.id::text ELSE NULL END AS author_id", 'saved-post author id privacy');
requireText(bookmarksRoute, "ELSE 'Miembro de Mágina'", 'saved-post private author alias');
if (bookmarksRoute.includes('workspace_id') || bookmarksRoute.includes('field_id') || bookmarksRoute.includes('geometry')) {
  fail('community saved-post API must remain account-scoped and independent from private farm geometry');
}

const adminRoutes = requireFile('apps/api/src/routes/admin-community.ts');
requireText(adminRoutes, '/api/v1/admin/community/reports', 'community moderation API');
requireText(adminRoutes, '/api/v1/admin/community/moderation', 'community moderation API');
requireText(adminRoutes, 'requirePlatformAccess', 'community moderation authorization');
requireText(adminRoutes, 'auditAdminAction', 'community moderation audit');

const app = requireFile('apps/api/src/app.ts');
requireText(app, 'registerCommunityRoutes(app, db)', 'community API registration');
requireText(app, 'registerCommunityBookmarkRoutes(app, db)', 'community saved-post registration');
requireText(app, 'registerAdminCommunityRoutes(app, db)', 'community admin API registration');

requireFile('apps/web/src/app/comunidad/page.tsx');
const client = requireFile('apps/web/src/app/comunidad/community-client.tsx');
for (const behavior of [
  'createCommunityPost',
  'setCommunityLike',
  'setCommunityBookmark',
  'loadCommunityBookmarks',
  'createCommunityComment',
  'reportCommunityTarget',
  'loadPublicMunicipalities',
  'sharePost',
]) requireText(client, behavior, 'community UI');
requireText(client, "reportTarget('comment', comment.id)", 'community comment reporting');
requireText(client, 'composerMunicipality', 'community municipality context');
requireText(client, "mode === 'saved'", 'community saved-post view');
const source = requireFile('apps/web/src/lib/community-source.ts');
requireText(source, '/api/v1/community/bookmarks', 'community saved-post client');
requireText(source, 'author_id: string | null', 'community private author client type');
const explore = requireFile('apps/web/src/app/explorar/explore-public-client.tsx');
requireText(explore, "href: '/comunidad'", 'Explore community entry');
requireFile('apps/web/src/app/comunidad/community.module.css');

requireFile('apps/web/src/app/admin/comunidad/page.tsx');
const adminConsole = requireFile('apps/web/src/components/admin-community-console.tsx');
requireText(adminConsole, '/api/v1/admin/community/reports', 'community Admin queue');
requireText(adminConsole, '/api/v1/admin/community/moderation', 'community Admin moderation');
requireFile('apps/web/src/components/admin-community-console.module.css');

const registry = JSON.parse(requireFile('apps/web/src/app/admin/modulos/admin-modules.json') || '[]');
const communityModule = Array.isArray(registry) ? registry.find((module) => module?.id === 'community') : null;
if (!communityModule || communityModule.status !== 'available' || communityModule.href !== '/admin/comunidad') {
  fail('community must be registered as available at /admin/comunidad in the unified Admin registry');
}

if (!process.exitCode) console.log('community-check: ok');
