# V20 — trabajo en paralelo y ramas

Objetivo: permitir que varios chats/agentes avancen a la vez sin que un cambio de mapa, UX, backend o estructura bloquee o contamine a los demás.

## Regla base

Cada rama debe tener **un propósito principal revisable**. Si un cambio no pertenece a ese propósito, va a otra rama. Un chat no debe abrir una segunda implementación de un frente que ya tenga rama activa salvo que se acuerde explícitamente una sustitución.

## Mapa actual de workstreams

Estas ramas existen en el repositorio y deben tratarse como zonas de trabajo separadas mientras sigan activas:

| Frente | Rama | Propiedad principal |
| --- | --- | --- |
| Candidate / integración visual | `feat/v20-visual-prototype` | base candidata, integración y validación final |
| Ingeniería transversal | `chore/v20-cleanup-structure` | estructura, tooling, CI, entorno y deuda compartida |
| Admin | `feat/v20-admin-progress` | administración y gestión interna |
| Weather / mapa | `feat/v20-weather-map` | experiencia meteorológica y mapa asociada |
| GIS / fincas | `agent/gis-fincas` | geometría, Catastro/SIGPAC y flujo GIS de finca |
| Mi Campo / fincas | `agent/mi-campo-fincas` | listado, ficha y experiencia principal de finca |
| Registro / campaña | `agent/registro-campana` | trabajos, cosecha, rendimiento y campaña |
| Profesional | `agent/profesional` | clientes, trabajos para terceros, presupuestos y facturación |
| Documentos / OCR | `agent/documentos-ocr` | documentos, extracción y OCR |
| Inicio / Hoy | `agent/home-hoy` | home operativo y prioridades del día |
| Público / explorar | `agent/public-explore` | experiencia pública y contenido explorable |
| QA móvil / E2E | `agent/qa-mobile-e2e` | regresión móvil, responsive y recorridos navegador |

Antes de crear una rama nueva debe comprobarse este mapa y las ramas remotas. Si un frente ya existe, se continúa en su workstream o se escoge otra responsabilidad.

## Tipos de rama

- `feat/*` — funcionalidad de producto.
- `fix/*` — corrección aislada.
- `chore/*` — limpieza, estructura, tooling o mantenimiento.
- `docs/*` — documentación sin cambio funcional.
- `agent/*` — workstream temporal y acotado para trabajo paralelo.

## Frentes que deben permanecer separados

En V20 conviene separar, como mínimo:

1. GIS / mapa / Catastro / SIGPAC.
2. Mi Campo y ficha de finca.
3. Registro / campaña / cosecha / rendimiento.
4. Profesional / clientes / presupuestos / facturas.
5. Documentos / OCR.
6. Tiempo / radar / alertas.
7. Inicio / Hoy.
8. Público / explorar.
9. Admin.
10. QA móvil / accesibilidad / E2E.
11. Ingeniería transversal / deuda / documentación / tooling / CI.

Una rama puede tocar contratos compartidos si son necesarios para su objetivo, pero debe evitar refactors laterales que generen conflictos artificiales.

## Flujo recomendado

1. Partir del HEAD candidato o de la base acordada para ese workstream.
2. Crear o reutilizar una rama con nombre explícito.
3. Mantener commits pequeños y con intención clara.
4. Ejecutar `pnpm check:env` si se añaden variables de entorno o runtime configuration.
5. Ejecutar `pnpm check` antes de considerar el workstream listo.
6. Ejecutar los checks específicos afectados por el cambio.
7. Abrir PR preferentemente como draft mientras haya trabajo pendiente.
8. No fusionar un workstream solo porque compile: revisar impacto funcional y conflictos con ramas paralelas.

## Propiedad de archivos compartidos

Los siguientes cambios deben coordinarse con `chore/v20-cleanup-structure` o mantenerse mínimos en ramas de producto:

- `package.json` raíz y configuración de pnpm/Node;
- `.env.example` raíz y contratos de configuración;
- `.github/workflows/**` cuando el cambio sea transversal;
- documentación de entrada (`README.md`, `docs/INDEX.md`, este archivo);
- scripts raíz de validación y tooling.

Las ramas de producto sí pueden modificar estos archivos cuando sea estrictamente necesario para su feature, pero deben evitar reordenados o refactors no relacionados.

## Criterio de cierre del workstream transversal

El frente de ingeniería transversal no se considera una tarea de una sola pasada. Va cerrando lotes independientes y permanece disponible mientras V20 tenga ramas paralelas. Cada lote debe:

- no cambiar funcionalidad de usuario sin documentarlo;
- corregir deuda estructural concreta;
- dejar una validación automática cuando sea razonable;
- mantener los gates troncales verdes;
- registrar la deuda que deba resolverse en un frente de producto.

## Conflictos

Si dos ramas necesitan modificar el mismo archivo estructural:

- gana la rama cuyo objetivo principal sea ese archivo/área;
- la otra rama debe reducir su cambio al mínimo;
- no se hace merge cruzado entre ramas de trabajo salvo necesidad clara;
- la reconciliación se hace al integrar sobre el candidate, no copiando cambios a ciegas.

## `main`

`main` representa la línea estable. Los workstreams V20 no deben usar `main` como zona de trabajo ni recibir commits directos durante el cierre del candidate.
