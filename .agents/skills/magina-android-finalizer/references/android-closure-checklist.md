# Mágina Olivo — checklist verificable de cierre Android 1.0

> Este documento acompaña al skill `magina-android-finalizer`. **No es un certificado de PASS**. Cada agente debe rellenar el estado con pruebas actuales (issue/PR/CI/SHA/dispositivo). Si no lo comprobó, escribir `PENDIENTE`. No sustituye los checklists normativos de Gate en `docs/06-testing/`.

## Estado inicial obligatorio
- [ ] Revisado `AGENTS.md`, baseline/Product Lock, `CURRENT-STATE.md` y orden del propietario en issues.
- [ ] Revisados `main` HEAD, último CI, PRs abiertas, PRs fusionadas, bloqueos y responsable actual.
- [ ] Confirmado que no hay otro agente editando el mismo slice.
- [ ] Confirmado alcance Android; Web V3 sigue pausada si #696 permanece vigente.

## A. Integridad y recorrido agrícola
| Caso | Criterio mínimo de aceptación | Evidencia necesaria |
| --- | --- | --- |
| A1 Fincas | Crear/editar/archivar/restaurar con/sin foto; permanecer tras reinicio | Instrumentación + móvil |
| A2 Parcelas | Añadir parcela manual o Catastro, municipio/provincia, geometría y alias; distinguir importación verificada | Test integración + móvil + offline |
| A3 Campañas | Activar y reabrir campaña con varias parcelas; historial estable y aislado por finca/workspace | Room + E2E |
| A4 Contexto | Mi Campo ↔ Cuaderno ↔ Campaña conserva selección; cambiar finca no mezcla datos de otra | Navegación E2E |
| A5 Pesadas | Varias por día; vale, kilos exactos, cooperativa, suelo/árbol, parcelas, foto; crea día automático sin falsos kilos | Instrumentación + E2E |
| A6 Peso extremo | Overflow/parser inválido informa error; desconocido continúa desconocido; ninguna suma se hace negativa por overflow | Unit + regression |
| A7 Jornales | Nombre, fecha, completa/media/horas, coste y pagos parciales; saldo correcto, sin doble gasto | Domain + Room + UI |
| A8 Maquinaria/gastos | Coste jornada y campaña reconciliado con Expense POSTED; pago no duplica costes | Unit + E2E |
| A9 Rendimientos | Añadir después; promedio ponderado y cobertura veraces; histórico de parcelas mixtas no inventado | Domain + histórico |
| A10 Edición legacy | Modificar registros antiguos sin borrar campos ajenos o snapshots regulados | Migración + edición |
| A11 Adjuntos | Fotos opcionales y PDF conservados tras reiniciar, actualizar y ejecutar fixtures aisladas | API35 + verificación SHA |
| A12 Fuentes externas | Clima/radar/mercado/Catastro fallan sin bloquear trabajo; caché y frescura fieles | Offline + error states |
| A13 Accesibilidad | 360–412 dp y letra grande, TalkBack, teclado si aplica, controles alcanzables, colores aprobados | Capturas + device |

## B. Robustez y pruebas
- [ ] **CI Android:** lint, unit tests, variantes DEV/STAGING/PRODUCTION, compilación de AndroidTest, todos sobre el SHA de entrega.
- [ ] **Emulador API 35:** pruebas instrumentadas que apliquen, especialmente Room, fincas, fotos, campaña, cambios de contexto y regresiones del issue.
- [ ] **Room:** migraciones entre versiones soportadas; no pérdida de datos ni permisos/workspace atenuados.
- [ ] **Conectividad:** modo avión, vuelta de red, cierre forzoso/reinicio y repetición sin duplicados.
- [ ] **Almacenamiento:** los fixtures jamás limpian directorios globales de fotos de usuario; comprobar ficheros antes/después cuando cambien adjuntos.
- [ ] **Prueba móvil real:** instalar actualizando la app (`install -r` o vía instalación normal), sin desinstalar/borrar datos; verificar datos ya registrados. Anotar modelo y versión Android.
- [ ] **APK DEV:** compilada desde `main` integrado, artefacto GitHub Actions identificado y arranque verificado.
- [ ] **Gate:** el propietario da conformidad cuando el Gate la requiere; no convertir CI verde en aceptación física.

## C. Cierre por etapas (sin adelantar fases)
1. **Antes de Gate 21:** cerrar P0 Android pendientes, flujo real y APK de aceptación, contrastando el tracker vigente (#696 en 2026-10-08).
2. **Gate 21:** Perfil, municipio/cooperativa, avisos, ayuda/privacidad y aceptación en móvil según checklist normativo. No marcar PASS por suposiciones.
3. **Fase 22:** Supabase/Auth/Postgres/PostGIS/RLS, únicamente tras autorización y contrato aprobados.
4. **Fase 23:** outbox/WorkManager/sincronización, idempotencia y conflictos con ciclos offline/online repetibles.
5. **Fases 24–25:** admin separada e informes/PDF reconciliados; no mezclar con fixes urgentes.
6. **Fases 26–28:** QA de resistencia, beta real y publicación con privacidad/legal/versionado/rollback.

**No reactivar OCR** como requisito Android 1.0: CR-013 aplaza la lectura automática y conserva la Pesada manual con foto opcional.

## Plantilla de evidencia por bloque

```text
Bloque / issue:
Rama y PR:
main HEAD de partida:
Commit verificado:
Agente ejecutor:
Prueba / comando:
Resultado real (PASS / FAIL / PENDIENTE):
Emulador (API y cantidad de tests):
Móvil (modelo / versión Android / actualización conservando datos):
Artefactos, logs y capturas:
Impacto Room / adjuntos / workspace:
Revisión independiente:
Gate y aceptación propietario:
Siguiente prioridad:
```
