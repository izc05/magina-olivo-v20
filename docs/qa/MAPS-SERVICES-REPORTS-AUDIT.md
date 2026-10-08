# Auditoría dirigida A4–A6 — mapas, servicios, estadísticas

#695/#693, main c4617f87, 2026-10-08. Solo lectura y pruebas existentes; sin editar Android/backend ni desplegar. Esta auditoría entrega localizadores y aceptación; no acredita recorridos físicos.

## A4 — Mapa/Catastro/SIGPAC

Observado: `feature/maps/ParcelLabelPoint.kt` elige el componente de mayor área exterior, usa centroide solo si queda dentro y fuera de huecos, y busca intervalo interior si no. Geometría no se modifica; no repara topología. `FarmMapScreen.kt` pasa `MapCameraInsets` al mapa (línea 190 en base auditada). No basta esto para aceptar área útil ni reconciliación SIGPAC/REAFA.

| Caso de aceptación | Expected | Evidencia aún necesaria |
| --- | --- | --- |
| Polígono cóncavo en U | Etiqueta dentro, no en vacío de la U | Test geométrico + screenshot de mapa (#574) |
| Polígono con hueco central | Etiqueta no cae en hueco | Test/screenshot, manteniendo geometría original |
| MultiPolygon, componente pequeño apartado | Etiqueta en componente dominante y autoencuadre incluye todos | Revisar elección área exterior vs área neta cuando hay grandes huecos; decisión documentada, no bug afirmado |
| Panel inferior/header y teclado visibles | Parcela encajada en viewport útil, no detrás del panel | Teléfono 360/390/430 equivalentes con padding real |
| JSON malformado/degenerado | Sin crash; no inventar geometría ni ubicación | Error/estado honesto y test de fallback |
| GPS denegado, municipio/búsqueda manual | Alta/consulta útil sin permiso obligatorio | Prueba guiada con permiso denegado y sin red |
| SIGPAC/REAFA vs parcela app | Identidad/procedencia y histórico preservados | Fuente oficial y reconciliación aún PREP; no equivalencia con Catastro |

Responsable de código/pruebas Android: Claude. Codex entrega casos; no hay nuevas geometrías operativas ni parche de algoritmo.

## A5 — Home y servicios externos

Observado: `HomeScreen.kt` llama onResumed vía LifecycleResumeEffect; `HomeViewModel.kt` tiene refreshIfStale. La existencia del hook no prueba el ciclo completo ni medianoche (#496/#619).

[#669](https://github.com/izc05/magina-olivo-v20/pull/669) MERGED. main contiene solar.ts, campos solares opcionales, y `HomeWeatherHero.kt:solarLine`: compara solarDate con el día en solarTimeZone (fallback Europe/Madrid), y devuelve null si difiere. El estado integrado incluye zona de fuente; no asumir que el body histórico de PR describe todas las correcciones posteriores. Falta prueba física de fresco/caducado/medianoche.

Ejecución fresca: `node --experimental-strip-types --test supabase/functions/weather-forecast/forecast.test.ts` → **18 passed, 0 failed**. Incluye Bedmar, frontera Canarias/Madrid, errores/rate limit/timeout/documento inválido AEMET con fallback MET y fallo de ambos proveedores. Son fixtures/dependencias inyectadas, no llamada upstream viva ni Android lifecycle/instancia desplegada.

Casos Claude: entrar/salir/reanudar con dato >1h; modo avión con cache existente/sin cache; cambio de día en zona Workspace; solar de ayer oculto; proveedor falla y etiqueta stale visible; radar/mercado falla sin bloquear Cuaderno; permiso notificaciones denegado/reenabling/reinicio. Mantener fecha/provider, no tratar retry como dato actualizado.

## A6 — Estadística/reportes/PDF

Consulta/gráficos existen; #689 bloquea pruebas de CampaignCharts y totales seguros. La búsqueda estática de `ReportSnapshot` y `PdfDocument` bajo app/src/main no encontró implementación con esos identificadores. Eso es evidencia negativa limitada, no demuestra que ningún PDF exista por otra biblioteca. No se acredita cierre #328 ni generación/exportación de reporte.

Casos para Claude/WEB-1: campaña/finca/parcela con todos los períodos; rendimiento con cobertura parcial visible; reparto desconocido separado de kg conocidos; gasto sin precio y monedas incompatibles sin suma falsa; overflow → desconocido; snapshot estable ante renombre/archivo; PDF resumen/detalle conciliado con UI/libro y anonimizado; varias páginas sin recortes; preview/compartir offline. Validar #328 frente implementación concreta antes de dar PASS.

## Handoff

A4/A6: acceptance preparado, ejecución geométrica/visual/PDF pendiente. A5: evidencia estática y suite backend 18/18; lifecycle Android/físico pendiente. No nuevos bugs declarados solo por falta de evidencia. NEXT=B1 inventario de ramas web; V3-A bloquea motor/consolidación, no QA independiente. Revisión CI de #694 debe continuar al SHA final.
