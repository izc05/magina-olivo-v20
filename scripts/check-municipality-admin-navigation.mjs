import fs from 'node:fs';

const files = {
  context: fs.readFileSync('apps/web/src/lib/municipality-admin-context.ts', 'utf8'),
  nav: fs.readFileSync('apps/web/src/components/municipality-admin-nav.tsx', 'utf8'),
  ficha: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/page.tsx', 'utf8'),
  contenido: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/contenido/page.tsx', 'utf8'),
  portada: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/editorial/page.tsx', 'utf8'),
  patrimonio: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/patrimonio/page.tsx', 'utf8'),
  actualidad: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/actualidad/page.tsx', 'utf8'),
  cobertura: fs.readFileSync('apps/web/src/app/admin/ayuntamientos/cobertura/page.tsx', 'utf8'),
};

function requireText(source, text, message) {
  if (!source.includes(text)) throw new Error(message);
}

requireText(files.context, "MUNICIPALITY_QUERY_KEY = 'municipio'", 'municipality context must use canonical ?municipio= query key');
requireText(files.context, 'window.location.hash.replace', 'legacy hash compatibility is required');
requireText(files.context, 'replaceMunicipalityContext', 'context writer is required');
for (const label of ['Ficha', 'Contenido', 'Portada', 'Patrimonio', 'Actualidad', 'Cobertura', 'Ver público']) {
  requireText(files.nav, label, `shared municipality nav is missing ${label}`);
}
for (const [name, source] of Object.entries({ ficha: files.ficha, contenido: files.contenido, portada: files.portada, patrimonio: files.patrimonio, actualidad: files.actualidad, cobertura: files.cobertura })) {
  requireText(source, 'MunicipalityAdminNav', `${name} must render shared municipality navigation`);
  requireText(source, 'replaceMunicipalityContext', `${name} must persist municipality changes in URL context`);
}
requireText(files.ficha, 'readMunicipalitySlug', 'control center must restore municipality from query/hash');
requireText(files.contenido, 'readMunicipalitySlug', 'content editor must restore municipality from query/hash');
requireText(files.portada, 'readMunicipalitySlug', 'editorial page must restore municipality from query/hash');
requireText(files.patrimonio, 'contextId && municipalityId !== contextId', 'heritage editor must focus rows on current municipality');
requireText(files.actualidad, "linkedId !== contextId && linkedId !== ''", 'current affairs must focus on municipality plus unassigned content');
requireText(files.cobertura, 'row.municipality.id !== contextId', 'coverage must focus on current municipality');

console.log('Municipality Admin navigation contract OK: canonical URL context, shared workspace navigation and per-municipality focus.');
