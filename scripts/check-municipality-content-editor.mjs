import fs from 'node:fs';

const pagePath = 'apps/web/src/app/admin/ayuntamientos/contenido/page.tsx';
const page = fs.readFileSync(pagePath, 'utf8');
const territory = fs.readFileSync('apps/api/src/routes/territory.ts', 'utf8');

const required = [
  "adminApi.createContent(payload)",
  "adminApi.updateContent(form.id, payload)",
  "adminApi.media()",
  "municipality_id: municipality.id",
  "municipality_name: municipality.name",
  "municipality_slug: municipality.slug",
  "municipality_role: form.role",
  "source_url: form.sourceUrl.trim()",
  "source_label: form.sourceLabel.trim()",
  "verified_at: form.verifiedAt",
  "status: form.status",
  "sort_order: form.sortOrder",
  "Mostrar en “Lo imprescindible”",
  "featuredCount >= 3",
  "ensureSingleProfile",
  "/admin/media",
  "mediaPublicUrl(asset)",
  "slugConflict",
];

for (const token of required) {
  if (!page.includes(token)) throw new Error(`Municipality content editor contract: missing ${token}`);
}

if (!territory.includes("ORDER BY c.featured DESC, c.sort_order DESC")) {
  throw new Error('Municipality content editor contract: public API must preserve featured/sort_order ordering');
}

if (page.includes("fetch('/api") || page.includes('fetch("/api')) {
  throw new Error('Municipality content editor contract: use canonical admin data source, not direct API fetch');
}

console.log('Municipality content editor contract OK: create/edit, canonical municipality identity, source provenance, media library, unique profile, max three essentials, status and ordering are protected.');
