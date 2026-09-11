# V20 — trabajo en paralelo y ramas

Objetivo: permitir que varios chats/agentes avancen a la vez sin que un cambio de mapa, UX, backend o estructura bloquee o contamine a los demás.

## Regla base

Cada rama debe tener **un propósito principal revisable**. Si un cambio no pertenece a ese propósito, va a otra rama. Un chat no debe abrir una segunda implementación de un frente que ya tenga rama activa salvo que se acuerde explícitamente una sustitución.

Antes de empezar a programar, cada chat debe comprobar el HEAD de su propia rama y el HEAD actual de `feat/v20-visual-prototype`. No se hace rebase/force-push de otra rama para "ponerse al día"; la sincronización compartida se coordina y se revisa.

## Mapa actual de workstreams

| Frente | Rama | Referencia | Propiedad principal |
| --- | --- | --- | --- |
| Candidate / integración | `feat/v20-visual-prototype` | PR #1 | integración, salud global y validación final |
| Ingeniería transversal | `chore/v20-cleanup-structure` | PR #10 | estructura, tooling, CI, runtime y deuda compartida |
| Inicio / Hoy | `agent/home-hoy` | Issue #7 | entrada diaria, agenda y prioridades |
| Mi Campo / fincas | `agent/mi-campo-fincas` | Issue #3 | listado, ficha y experiencia principal de finca |
| Registro / campaña | `agent/registro-campana` | Issue #4 | trabajos, cosecha, rendimiento y campaña |
| Profesional | `agent/profesional` | Issue #5 | clientes, trabajos, presupuestos y facturación |
| Documentos / OCR | `agent/documentos-ocr` | Issue #6 | upload, revisión, extracción y enlaces documentales |
| GIS / nueva finca | `agent/gis-fincas` | Issue #2 | Catastro/SIGPAC, geometría y alta GIS |
| QA móvil / E2E | `agent/qa-mobile-e2e` | Issue #8 | regresión, responsive 360/390/430 y recorridos navegador |
| Público / explorar | `agent/public-explore` | Issue #9 | superficies públicas y documentos/links públicos |
| Perfil / ajustes | `agent/perfil-ajustes` | Issue #11 | identidad, preferencias, permisos y ajustes de cuenta/workspace |
| Planificar / tareas | `agent/planificar-tareas` | Issue #12 | tareas futuras, agenda y vínculo con ejecución real |
| Admin | `feat/v20-admin-control-center` | Issue #13 | usuarios/workspaces, CMS, territorio y control administrativo |
| Admin anterior | `feat/v20-admin-progress` | antecedente | base histórica del control center; no abrir aquí desarrollo paralelo nuevo |
| Weather / radar | `feat/v20-weather-map` | Issue #14 | previsión, radar, alertas y overlays meteorológicos |

`feat/v20-admin-control-center` está por delante de `feat/v20-admin-progress` y contiene su historia; se considera el frente Admin actual para evitar dos implementaciones paralelas del mismo bloque.

La rama `chore/v20-parallel-workstreams` conserva el protocolo inicial que creó varios `agent/*`, pero su baseline está desfasado. Las reglas vigentes se consolidan ahora en `AGENTS.md` y en este documento.

## Tipos de rama

- `feat/*` — funcionalidad de producto o workstream preexistente.
- `fix/*` — corrección aislada.
- `chore/*` — estructura, tooling, integración o mantenimiento.
- `docs/*` — documentación sin cambio funcional.
- `agent/*` — workstream acotado para trabajo paralelo de pantalla/dominio.

## Propiedad de archivos compartidos

### Integración / candidate

El coordinador de `feat/v20-visual-prototype` revisa por defecto los cambios con impacto global de producto:

- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/app/premium.css`
- `apps/web/src/app/mobile-hardening.css`
- `apps/web/src/components/bottom-nav.tsx`
- `apps/web/src/components/topbar.tsx`
- `packages/contracts/**`
- `database/migrations/**`
- decisiones de arquitectura que cruzan varios dominios

Una rama de producto puede necesitar contrato o migración, pero debe mantener el cambio mínimo y señalarlo expresamente en el PR/handoff.

### Ingeniería transversal

`chore/v20-cleanup-structure` es propietaria por defecto de:

- `package.json` raíz y runtime Node/pnpm;
- `pnpm-lock.yaml` y política de instalación;
- `.env.example` raíz y contrato runtime transversal;
- `.github/workflows/**` cuando el cambio sea de infraestructura común;
- scripts raíz de validación/auditoría;
- `README.md`, `docs/INDEX.md`, `docs/WORKSTREAMS.md` y `AGENTS.md`.

Las ramas de producto pueden tocar estos archivos si es imprescindible para su feature, pero deben evitar refactors laterales y declarar el solape.

## Reglas para cada chat/agente

1. Trabajar exclusivamente en la rama asignada.
2. No tocar `main`.
3. No fusionar automáticamente el candidate ni otro workstream.
4. No hacer force-push/rebase sobre otra rama.
5. Preferir componentes/estilos locales antes que CSS global.
6. Usar datos reales cuando existe API; preview/demo debe ser explícito.
7. No presentar acciones falsas ni estados técnicos como si fueran producto terminado.
8. En UI móvil comprobar 360, 390 y 430 px sin overflow horizontal.
9. Manejar loading, empty, error y success cuando la pantalla dependa de datos.
10. Mantener commits revisables y reportar cualquier necesidad compartida al integrador.

## Validación común

Cuando aplique:

```bash
pnpm check:lockfile
pnpm check:env
pnpm check:fast
pnpm check
```

Además se ejecutan los tests/smokes del dominio y E2E cuando cambia un recorrido de usuario. `V20 foundation check` valida la base transversal con instalación congelada, typecheck y build completos.

## Handoff obligatorio

Cada workstream debe entregar:

- rama;
- commit SHA final o rango de commits;
- qué implementó;
- archivos principales cambiados;
- checks/tests pasados;
- viewports comprobados si hay UI;
- cambio compartido solicitado (`none` si no existe);
- limitaciones/riesgos conocidos;
- estado `ready for coordinator review: yes/no`.

## Integración

1. Revisar el handoff y el scope contra el candidate actual.
2. Detectar colisiones con otras ramas antes de mezclar.
3. Incorporar mediante merge/cherry-pick revisado según convenga; nunca copiar a ciegas.
4. Ejecutar checks específicos + `V20 foundation check` cuando afecte a base técnica.
5. Ejecutar full candidate y browser E2E sobre el HEAD resultante.
6. Solo entonces marcar el workstream como integrado.

## `main`

`main` representa la línea estable. Ningún workstream V20 usa `main` como zona de trabajo ni recibe commits directos durante el cierre del candidate.
