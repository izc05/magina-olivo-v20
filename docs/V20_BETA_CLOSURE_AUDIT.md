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

## Clasificación

- `REAL`: consume API/datos persistidos y su flujo principal existe.
- `HYBRID`: API real en producción + fallback preview/local explícito.
- `PREVIEW`: contenido estático/demo o interacción no conectada.
- `P0`: bloquea Beta.
- `P1`: debe cerrarse antes de candidate final.
- `P2`: puede completarse después de Beta sin falsear funcionalidad.

## Inventario inicial

| Área | Estado | Prioridad | Hallazgo / acción |
|---|---|---:|---|
| Inicio privado | HYBRID | P1 | Agenda, fincas, prioridades y clima usan API cuando está configurada. Conserva preview demo cuando no hay API. Mantener preview solo como modo explícito, no como fallback silencioso productivo. |
| Inicio público / actualidad | PREVIEW | P1 | Noticias, eventos y patrocinado siguen siendo tarjetas estáticas/demo. Sustituir por contenido real o marcar claramente sección no disponible para Beta. |
| Mi Campo | HYBRID | P1 | Fincas usan API con auth; conserva `demo/local` para preview. Separar modo preview del runtime productivo. |
| Ficha de finca | HYBRID | P1 | API real para finca `source=api`; conserva rutas/derivaciones local/demo. Candidate productivo debe entrar siempre por API. |
| Nueva finca — datos básicos | REAL | — | Alta `/api/v1/fields` y catálogo territorial reales cuando API está configurada. |
| Nueva finca — localización | PREVIEW | **P0** | Mapa/Catastro/SIGPAC/dibujar muestran UI, pero el wizard no vincula todavía la referencia/geometría al guardar. No debe aparentar localización completada. Conectar o degradar a “vincular después”. |
| Rutas `fincas/local` | PREVIEW | P1 | Persistencia local explícita de prototipo. Excluir del recorrido productivo/staging o proteger por modo preview. |
| Hoy / agenda | REAL | P1 | API de agenda/planned tasks ya existe. Falta recorrido visual móvil completo y regresión E2E. |
| Campaña | REAL | P1 | Agregado real; revisar separación agrícola/profesional en todos los totales y UX móvil. |
| Profesional | REAL | P1 | Clientes, trabajos, costes, cobros, presupuestos, facturas, PDF, envío y aceptación pública implementados. Falta consolidación UX y eventos de decisión en seguimiento. |
| Documentos/OCR | REAL | P1 | Persistencia, versiones, OCR y revisión humana reales. Falta hardening de merge de revisión legacy, parser fixtures y catálogo/paginación. |
| GIS Catastro/SIGPAC backend | REAL | P1 | Servicios/modelo disponibles. Falta cerrar selector dentro del flujo de alta/edición de finca. |
| Mapa Mi Campo | REAL/HYBRID | P1 | Existe MapPlatform y datos GIS; requiere auditoría visual y confirmar que no haya geometrías demo en candidate productivo. |
| Tiempo AEMET | REAL | P1 | API real por finca/municipio con stale. Falta UX final y manejo visual de ausencia/error. |
| Radar | REAL | P1 | Reflectividad observada y reglas reales. Falta overlay final y auditoría móvil; no afirmar ETA. |
| Explorar | PREVIEW | P2 para Beta privada / P1 para Beta pública | Menú visual estático; Noticias, Eventos, Aceite, Gastronomía, Rutas y Cerca de ti aún no son directorio/CMS completo. |
| Admin/CMS | INCOMPLETO | P2 Beta privada / P0 lanzamiento público | Blueprint existe; falta implementación completa de contenido, negocios, publicidad, imágenes y moderación. |
| Mi Olivo | BLUEPRINT | P2 | No bloquea Beta del núcleo agrícola. |

## P0 actuales

### P0-1 — Wizard “Nueva finca” no debe simular vínculo GIS

Situación actual:

- Alta de finca básica: real.
- Catastro/SIGPAC/Mapa/Dibujar: interfaz de selección aún no conectada al guardado de la finca.
- El CTA puede dar a entender que la localización elegida ha quedado asociada.

Resolución Beta aceptable (orden preferido):

1. Conectar selector real `mapa/catastro/sigpac` → referencia/geometría → confirmación → asociación a finca.
2. Si no se termina en esta fase, degradar el Paso 2 a una elección informativa y guardar la finca con texto inequívoco: “Finca creada. Vincular ubicación después”. Nunca mostrar “localizada” si no existe vínculo persistido.

## P1 — Datos demo/local

Regla de candidate:

- Con API configurada, nunca caer automáticamente a `demo-data` o `local-prototype-store` tras un error de API.
- Un error real debe producir estado vacío/error y permitir reintento.
- Preview debe activarse de forma explícita y reconocible.
- Las rutas `fincas/local` se mantienen como herramienta de prototipo, no como flujo normal de Beta.

## P1 — Recorridos E2E a validar

### Agricultor

1. Login → workspace.
2. Crear finca.
3. Vincular/posponer localización sin engaño.
4. Abrir finca.
5. Registrar trabajo.
6. Ver actividad y costes.
7. Registrar entrega de cosecha.
8. Añadir rendimiento posterior.
9. Ver campaña.
10. Subir documento → OCR → revisar → guardar dominio explícitamente.
11. Ver tiempo/radar/alertas.

### Profesional

1. Crear cliente y sitio.
2. Crear presupuesto.
3. Generar/archivar PDF.
4. Compartir enlace.
5. Confirmar envío.
6. Cliente abre y acepta/rechaza.
7. Convertir aceptado a trabajo.
8. Registrar costes.
9. Facturar.
10. Registrar cobros parciales/totales.
11. Ver pendiente y margen en cliente/Profesional/Inicio.

## P1 — Auditoría móvil

Anchuras mínimas objetivo:

- 360 px
- 390–430 px
- tablet
- escritorio

Comprobar por pantalla:

- scroll horizontal accidental;
- botones fuera de viewport;
- tarjetas excesivamente densas;
- sticky bars que tapen contenido;
- tamaños táctiles;
- formularios y teclado móvil;
- tablas comerciales;
- mapas;
- estados loading/error/empty;
- contraste/foco/labels.

## P1 — Hardening conocido

- CI candidate completo.
- Semántica campaña agrícola/profesional consistente.
- OCR review: merge `extraction + confirmed + corrections` para legacy/partial.
- Evitar duplicidad/race en asociaciones de liquidación/cobro restantes.
- Validación explícita de filtros/IDs de workspace en rutas pendientes.
- Financial attention: ordenar por urgencia real y contar totales independientemente del límite.
- Catálogo documental: paginación/búsqueda y límite de subida más razonable.
- Observabilidad de workers/notificaciones.
- Auditoría de seguridad/performance.

## P2 — Después de la Beta núcleo

- Admin/CMS completo.
- Noticias/eventos/pueblos/almazaras dinámicos.
- Cerca de ti + publicidad/promociones.
- Mi Olivo/gamificación.
- Monetización/planes.
- Nowcast solo si existe metodología validada; nunca inferir ETA desde dBZ sin modelo adecuado.

## Orden de ejecución desde este documento

1. Resolver P0 del wizard de finca.
2. Convertir preview implícita en modo explícito.
3. Inventariar y cerrar todos los `PREVIEW/HYBRID` privados.
4. Ejecutar recorridos E2E y reparar.
5. Auditoría móvil.
6. Seguridad/performance.
7. Staging real.
8. Actualizar PR y decidir candidate final.

Este documento es el checklist vivo de Cierre Beta. Cualquier función nueva grande debe esperar salvo que cierre un P0/P1 de esta lista.
