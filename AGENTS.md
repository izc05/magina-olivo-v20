# Mágina Olivo V20 — Parallel Agent Protocol

Este repositorio se desarrolla mediante varios chats/agentes en paralelo. El objetivo es acelerar V20 sin perder coherencia de producto, estabilidad del candidate ni trazabilidad de integración.

## Regla de producto

**Sencillo por fuera, estructurado por dentro.**

El agricultor debe ver conceptos familiares (`Finca`, `Hoy`, `Registrar`, `Campaña`, `Cliente`). Catastro, SIGPAC, OCR, estados contables, claves técnicas y detalles de infraestructura permanecen detrás de la interfaz salvo cuando sean necesarios para tomar una decisión.

## Fuentes de verdad

Antes de cambiar UI o comportamiento, revisar `docs/INDEX.md` y los documentos relevantes del dominio, especialmente:

- `docs/V20_MASTER_PRODUCT_ARCHITECTURE.md`
- `docs/MI_CAMPO_STRUCTURAL_BLUEPRINT.md`
- `docs/MI_CAMPO_NAVIGATION_BLUEPRINT.md`
- `docs/V20_MI_CAMPO_DATA_CONTRACT.md`
- `docs/V20_VISUAL_DIRECTION.md`
- `docs/V20_BETA_CLOSURE_AUDIT.md`
- documentación específica de GIS, weather/radar, OCR, profesional y Admin.

El mapa vigente de ramas y ownership está en `docs/WORKSTREAMS.md`.

## Antes de programar

1. Confirmar la rama asignada en `docs/WORKSTREAMS.md`.
2. Leer el HEAD actual de esa rama y de `feat/v20-visual-prototype`.
3. Si otro workstream ya posee la pantalla o módulo, no abrir una implementación duplicada.
4. Si la rama está desfasada y necesita una sincronización compartida, informar al coordinador; no reescribir historia ajena.
5. Identificar desde el inicio cualquier archivo compartido que la feature necesite tocar.

## Branching e integración

- Nunca trabajar directamente sobre `main`.
- Nunca fusionar automáticamente `feat/v20-visual-prototype` ni otro workstream.
- Cada chat trabaja exclusivamente en su rama asignada.
- No hacer force-push/rebase sobre ramas de otros chats.
- Mantener commits revisables y de intención clara.
- El candidate `feat/v20-visual-prototype` es la línea de integración V20.
- El workstream `chore/v20-cleanup-structure` mantiene tooling, CI, runtime, documentación transversal y controles de integración.

## Ownership de archivos

Un agente modifica solo su alcance salvo necesidad explícita.

### Integración-owned por defecto

- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/app/premium.css`
- `apps/web/src/app/mobile-hardening.css`
- `apps/web/src/components/bottom-nav.tsx`
- `apps/web/src/components/topbar.tsx`
- `packages/contracts/**`
- `database/migrations/**`
- cambios de arquitectura que afecten a varios dominios.

### Foundation-owned por defecto

- `package.json` raíz
- `pnpm-lock.yaml`
- `.nvmrc`
- `.env.example` raíz
- `.github/workflows/**` cuando sea infraestructura transversal
- `scripts/**` de validación/auditoría común
- `README.md`, `docs/INDEX.md`, `docs/WORKSTREAMS.md`, `AGENTS.md`.

Si una feature necesita uno de estos archivos, debe hacer el cambio mínimo imprescindible y declararlo en su handoff/PR.

## Definition of done para una pantalla

Una pantalla no está terminada solo porque se vea bien. Debe:

1. usar API real cuando `NEXT_PUBLIC_API_URL` está configurado; demo/local solo como preview explícita;
2. conservar contexto de autenticación y workspace;
3. funcionar a 360, 390 y 430 px sin overflow horizontal;
4. mantener objetivos táctiles utilizables en móvil;
5. manejar loading, empty, error y success;
6. evitar acciones falsas: un botón funcional debe ejecutar una acción soportada o indicar claramente que está pendiente;
7. usar español conciso y comprensible para un agricultor;
8. no mostrar claves técnicas sin traducción de producto;
9. pasar typecheck/build/tests relevantes;
10. añadir regresión enfocada cuando cambia comportamiento.

## Validación común

Antes del handoff, cuando aplique:

```bash
pnpm check:lockfile
pnpm check:env
pnpm check:fast
pnpm check
```

La base técnica se valida además con `V20 foundation check`. Los recorridos funcionales deben pasar sus smokes/E2E específicos.

## Roles

### Coordinator / Integrator

Mantiene `feat/v20-visual-prototype`, revisa colisiones, integra ramas y valida el candidate resultante.

### Engineering Foundation

Mantiene `chore/v20-cleanup-structure`: tooling, lockfile, contratos de entorno, CI transversal, documentación operativa y deuda compartida. No absorbe features que ya tengan rama propietaria.

### Screen / Domain Agent

Construye una pantalla o grupo coherente de rutas dentro de su workstream. Se centra en API real, UX móvil, accesibilidad, estados y pruebas de dominio.

### GIS Agent

Nueva finca, Catastro/SIGPAC, geometría y enlace verificado. Nunca presenta una selección GIS falsa como real.

### Professional Agent

Cliente → trabajo → presupuesto → factura → cobro → documento/share, preservando auditabilidad.

### Documents/OCR Agent

Upload, extracción, revisión y vínculo documental. Las propuestas OCR siguen siendo revisables; no se convierten silenciosamente en dato autoritativo.

### QA / Mobile Agent

Encuentra regresiones y fortalece Playwright/smokes. No rediseña producto para conseguir verde ni debilita assertions sin causa.

## Handoff obligatorio

Cada agente termina su lote con:

- **Branch**
- **Commit SHA(s)**
- **Implemented**
- **Files changed**
- **Tests/checks passed**
- **Screens checked at 360/390/430** (si aplica)
- **Shared change requested** (`none` si no existe)
- **Known limitations**
- **Ready for coordinator review:** yes/no

El trabajo no se considera integrado hasta que el candidate resultante pase sus gates globales.
