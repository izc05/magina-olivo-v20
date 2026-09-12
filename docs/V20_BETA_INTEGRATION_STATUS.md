# Estado de integración V20 Beta

- Rama coordinadora: `integrate/v20-beta-closure`
- Base inicial: `feat/v20-visual-prototype` @ `99fcd6ed3eb3a6491467210fe8c45737b0c94794`
- `main`: no tocar durante la integración.
- Gate de fase: **funcional pre-visual**. No iniciar rediseño visual grande hasta cerrar P0/P1 funcionales y staging real.

## Referencia actual

HEAD funcional de trabajo antes de esta actualización: `ac29549d82b8dee5721024d8c8b11d7cc1ac5af0`.

La rama ya reúne en una única superficie integrada el núcleo agricultor, campaña, profesional, documentos/OCR, superficies públicas, Mercado, Admin/CMS/Territorio, Mi Olivo, Planes y hardening Foundation/Runtime. Los workflows específicos de Admin, Mi Olivo, Planes, Runtime y los gates transversales se ejecutan sobre la rama coordinadora.

## Incidencias de integración corregidas

### Staging readiness
El contrato nuevo exige `PUBLIC_WEB_ORIGIN`, pero el fixture considerado válido en `staging-readiness.yml` no lo incluía. Se ha alineado el fixture con el contrato sin relajar el preflight.

### Next.js / Foundation
Next.js 16.3.4 normalizaba `apps/web/next-env.d.ts` y `apps/web/tsconfig.json` durante `next build`, haciendo fallar correctamente el guardarraíl de Foundation que prohíbe que el build modifique archivos versionados. Se han persistido las salidas/configuración esperadas para que el build vuelva a ser limpio y reproducible.

## Trabajo listo para absorber

### Centro de Avisos — PR #59
Rama `agent/centro-avisos` @ `3312d57e9762a8dffbc4a11210c68d186faa6522`.

Validación propia completa:
- `V20 notification center check #2` ✅
- `V20 full candidate check #2254` ✅
- `V20 beta browser E2E #566` ✅
- `V20 staging readiness #167` ✅

Debe integrarse después de estabilizar el HEAD coordinador actual y volver a ejecutar gates sobre el conjunto.

## Trabajo no bloqueante / decisión de alcance

### Herramientas rápidas — PR #57
Rama `agent/herramientas-rapidas` @ `ed44d668711f548b8c422d68230a5c32b0c44c0b`.

Está terminada y validada, pero no es necesaria para cerrar el Gate Funcional Pre-Visual. Puede integrarse al final por ser aislada o diferirse a post-Beta.

## P0/P1 funcionales antes de rediseño visual

1. **Selector GIS real de finca**: cerrar selección/vinculación real Catastro/SIGPAC dentro del alta/edición, sin falsear geometrías.
2. **Radar/mapa funcional final**: cerrar overlay, contexto de finca y estados de datos/error antes del pulido visual.
3. **Centro de Avisos**: absorber PR #59 y comprobar convivencia con navegación, auth y notificaciones existentes.
4. **Staging externo real**: validar Google Auth, PostgreSQL/PostGIS, S3/R2, OCR/worker, AEMET/radar, VAPID/notificaciones y CMS/multimedia con credenciales/host reales.
5. **Backup/restore en host real** y smoke postdeploy externo.
6. **QA funcional integrado**: Full Candidate + Browser E2E + Staging Readiness + gates específicos verdes sobre el mismo HEAD.
7. **Auditoría móvil final 360/390/430** y escritorio de todos los recorridos críticos antes del rediseño.

## Trabajo paralelo recomendado

Mientras este frente coordina integración y CI, otros chats pueden avanzar sin pisarlo:

- **GIS funcional** en `agent/gis-fincas`: únicamente selector real alta/edición, referencias Catastro/SIGPAC, persistencia y E2E. No rediseñar la UI global.
- **Clima/Radar funcional** en `feat/v20-weather-map`: únicamente overlay/datos reales, estados error/stale/contexto de finca y pruebas. No hacer todavía el rediseño visual general.

No abrir otro workstream transversal de integración, navegación global, Foundation o staging-readiness mientras esta rama coordinadora esté activa.

## Criterio para pasar a fase visual

Solo pasar a rediseño cuando el mismo HEAD coordinador tenga:
- Full Candidate ✅
- Browser E2E ✅
- Staging Readiness ✅
- gates específicos relevantes ✅
- staging externo real validado ✅
- backup/restore real validado ✅
- cero P0 funcionales
- cero P1 que obliguen a rehacer arquitectura, datos o recorridos.
