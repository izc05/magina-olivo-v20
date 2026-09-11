# V20 — Cierre de Beta

Estado: en ejecución sobre `feat/v20-visual-prototype`.

Objetivo: dejar de ampliar módulos grandes y cerrar una beta coherente, API-first, móvil y verificable.

## Criterio de salida Beta

La Beta no se considerará cerrada hasta cumplir simultáneamente:

1. `V20 full candidate check` en verde sobre el HEAD candidato.
2. Ninguna pantalla privada principal depende de datos demo/local cuando `NEXT_PUBLIC_API_URL` está configurado.
3. Los recorridos críticos funcionan de extremo a extremo con API real.
4. No se presentan como funcionales flujos GIS/localización que todavía sean solo UI.
5. Revisión móvil de Inicio, Mi Campo, Finca, Registrar, Hoy, Campaña, Profesional, Cliente, Presupuesto/Factura y documento público.
6. Auditoría mínima de seguridad/performance y despliegue staging antes de merge.

## Cambios ya aplicados durante el cierre

- ✅ El wizard de nueva finca ya no afirma ni sugiere que Catastro/SIGPAC/mapa han quedado vinculados si no existe persistencia real de esa geometría.
- ✅ En API/Beta guarda la finca y deja la geometría como pendiente para vincular después desde Mi Campo → Mapa.
- ✅ Se ha creado `NEXT_PUBLIC_PREVIEW_MODE` como flag explícito.
- ✅ GitHub Pages activa `NEXT_PUBLIC_PREVIEW_MODE=true` de forma intencionada.
- ✅ Inicio, Mi Campo y alta de finca ya no usan datos demo/local por el simple hecho de que falte la API.
- ✅ Una instalación sin API y sin preview muestra estado de configuración/error y no datos ficticios.

## Clasificación

- `REAL`: consume API/datos persistidos y su flujo principal existe.
- `HYBRID`: API real en producción + preview explícita para demo.
- `PREVIEW`: contenido estático/demo o interacción no conectada.
- `P0`: bloquea Beta.
- `P1`: debe cerrarse antes de candidate final.
- `P2`: puede completarse después de Beta sin falsear funcionalidad.

## Inventario vivo

| Área | Estado | Prioridad | Hallazgo / acción |
|---|---|---:|---|
| Inicio privado | HYBRID CONTROLADO | P1 | API real en Beta; preview solo con flag explícito. Revisar componentes secundarios para eliminar cualquier fallback implícito restante. |
| Inicio público / actualidad | PREVIEW | P1 | Noticias, eventos y patrocinado siguen siendo tarjetas estáticas/demo. Sustituir por contenido real o etiquetar/ocultar para Beta. |
| Mi Campo | HYBRID CONTROLADO | P1 | Fincas API reales; preview solo explícita. |
| Ficha de finca | HYBRID | P1 | API real para `source=api`; revisar el fallback local/demo para que solo funcione en preview explícita. |
| Nueva finca — datos básicos | REAL | — | Alta `/api/v1/fields` y catálogo territorial reales. |
| Nueva finca — localización | HONESTA / PENDIENTE DE CONEXIÓN | P1 | Ya no falsea vínculo. Falta integrar selector real Catastro/SIGPAC/Mapa en el wizard o dejarlo definitivamente como paso posterior. |
| Rutas `fincas/local` | PREVIEW | P1 | Mantener solo para preview explícita; no deben ser alcanzables desde candidate productivo. |
| Hoy / agenda | REAL | P1 | Falta recorrido visual móvil completo y regresión E2E. |
| Campaña | REAL | P1 | Revisar separación agrícola/profesional en todos los totales y UX móvil. |
| Profesional | REAL | P1 | Clientes, trabajos, costes, cobros, presupuestos, facturas, PDF, envío y aceptación pública. Falta consolidación UX/eventos de decisión. |
| Documentos/OCR | REAL | P1 | Falta hardening de merge review legacy, parser fixtures y catálogo/paginación. |
| GIS Catastro/SIGPAC backend | REAL | P1 | Servicios/modelo disponibles; falta cerrar selector de alta/edición de finca. |
| Mapa Mi Campo | REAL/HYBRID | P1 | Auditar geometrías y experiencia móvil en candidate. |
| Tiempo AEMET | REAL | P1 | Revisar error/stale/empty en móvil. |
| Radar | REAL | P1 | Falta overlay final y auditoría móvil; nunca afirmar ETA no validada. |
| Explorar | PREVIEW | P2 beta privada / P1 beta pública | Menú y contenido territorial aún no forman un CMS/directorio completo. |
| Admin/CMS | INCOMPLETO | P2 beta privada / P0 lanzamiento público | Falta contenido, negocios, publicidad, imágenes y moderación. |
| Mi Olivo | BLUEPRINT | P2 | No bloquea Beta núcleo. |

## P0

### P0-1 — Wizard de finca simulaba vínculo GIS

**RESUELTO para Beta.**

Se ha aplicado la opción segura: crear la finca real y marcar explícitamente la geometría como pendiente. El selector GIS completo se mantiene como P1 de producto, pero ya no existe una falsa confirmación de localización.

## P1 — Preview/local

Regla de candidate:

- Con API configurada, nunca caer automáticamente a `demo-data` o `local-prototype-store` tras un error.
- Sin API, solo hay demo cuando `NEXT_PUBLIC_PREVIEW_MODE=true`.
- Un error real produce estado vacío/error y permite recuperación; nunca datos inventados.
- Las rutas `fincas/local` son herramientas de preview, no flujo de Beta.

Pendiente inmediato:

1. Aplicar esta regla a `FarmDetailShell` y componentes privados restantes.
2. Auditar `Registrar`, `Mapa`, `Planificar`, `Hoy`, `Campaña`, documentos y Perfil en busca de fallback local/demo.
3. Aislar rutas local-only del candidate productivo.

## P1 — Recorridos E2E

### Agricultor

1. Login → workspace.
2. Crear finca.
3. Posponer localización sin engaño.
4. Abrir finca.
5. Registrar trabajo.
6. Ver actividad y costes.
7. Registrar entrega.
8. Añadir rendimiento posterior.
9. Ver campaña.
10. Documento → OCR → revisión → guardado explícito.
11. Tiempo/radar/alertas.

### Profesional

1. Crear cliente/sitio.
2. Presupuesto.
3. PDF.
4. Compartir enlace.
5. Confirmar envío.
6. Cliente acepta/rechaza.
7. Convertir a trabajo.
8. Costes.
9. Factura.
10. Cobros parciales/totales.
11. Pendiente y margen.

## P1 — Auditoría móvil

Objetivos mínimos: 360 px, 390–430 px, tablet y escritorio.

Revisar scroll horizontal, botones, densidad, sticky bars, targets táctiles, teclado, tablas, mapas, loading/error/empty, contraste/foco/labels.

## P1 — Hardening conocido

- CI candidate completo.
- Semántica campaña agrícola/profesional consistente.
- OCR review legacy: `extraction + confirmed + corrections`.
- Races/idempotencia pendientes de liquidaciones/cobros.
- Validación de workspace/filtros pendiente en rutas concretas.
- Financial attention: prioridad/orden y counts totales.
- Catálogo documental: paginación/búsqueda/límite de subida.
- Observabilidad workers/notificaciones.
- Seguridad/performance.

## P2 — Después de Beta núcleo

- Admin/CMS completo.
- Noticias/eventos/pueblos/almazaras dinámicos.
- Cerca de ti/publicidad/promociones.
- Mi Olivo.
- Monetización/planes.
- Nowcast solo con metodología validada.

## Orden de ejecución actual

1. ~~Resolver P0 del wizard de finca.~~ ✅
2. Convertir preview implícita en modo explícito. **En progreso; núcleo Inicio/Mi Campo/Nueva finca ya corregido.**
3. Cerrar `PREVIEW/HYBRID` privados restantes.
4. Ejecutar E2E y reparar.
5. Auditoría móvil.
6. Seguridad/performance.
7. Staging real.
8. Actualizar PR y decidir candidate final.

Este documento es el checklist vivo de Cierre Beta. No se añade una función grande nueva salvo que cierre un P0/P1.
