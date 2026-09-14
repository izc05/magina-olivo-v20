import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const must = (condition, message) => { if (!condition) throw new Error(message); };

const handoff = read('docs/V20_MUNICIPAL_INTEGRATION_HANDOFF.md');
const roadmap = read('docs/V20_MUNICIPAL_COMPLETION_ROADMAP.md');
const workflow = read('.github/workflows/v20-municipalities-directory.yml');

const orderedPrs = [83, 88, 89, 90, 91, 92, 94, 95, 96, 97, 98, 100, 101, 102, 103, 104, 106];
let cursor = -1;
for (const pr of orderedPrs) {
  const index = handoff.indexOf(`#${pr} `);
  must(index > cursor, `El handoff no conserva el orden municipal en #${pr}`);
  cursor = index;
}

for (const text of [
  'integrate/v20-beta-closure',
  'behind_by=0',
  'a9e250a9a6c54462dcffce24d4a42993618e90fb',
  'V20 municipalities directory #46',
  'V20 environment contract #795',
  'V20 foundation check #744',
  'V20 platform admin check #642',
  'V20 full candidate check #2976',
  'V20 staging readiness #889',
  'V20 beta browser E2E #1288',
  'No se han ejecutado como parte de este handoff contra una base staging/live',
  '#105 `feat/v20-admin-unified-control-center`',
  '#66 Admin transversal/Analítica/Fuentes',
  '#80 `feat/v20-business-directory`',
  '#81 `feat/v20-routes-explore`',
  '#87 `feat/v20-routes-adventure`',
  'No fusionar a `main`',
]) must(handoff.includes(text), `Falta en handoff: ${text}`);

must(handoff.includes('No existe un PR municipal #99'), 'El handoff debe dejar claro que #99 no pertenece a la cadena municipal');
must(handoff.includes('32 piezas'), 'El handoff debe explicar el estado de los catálogos importables');
must(!handoff.includes('32 piezas están cargadas'), 'El handoff no debe afirmar que los catálogos ya fueron importados a una base real');

must(roadmap.includes('Fase 5'), 'El roadmap perdió la Fase 5');
must(roadmap.includes('Fase 6'), 'El roadmap perdió la Fase 6');
must(workflow.includes('check-municipality-qa-16.mjs'), 'El workflow perdió el QA final 16/16');
must(workflow.includes('check-municipality-integration-handoff.mjs'), 'El workflow no ejecuta el contrato de handoff');

console.log('Municipality integration handoff contract: OK');
