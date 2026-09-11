# V20 · Aceite y Mercado

## Objetivo

Construir un módulo de mercado sencillo y trazable para el agricultor de Sierra Mágina:

1. conocer la referencia oficial del aceite;
2. entender si sube o baja;
3. estimar de forma orientativa qué significa para una cosecha.

La entrega vive en `/mercado` y permanece aislada de Mi Campo, Admin, GIS, clima, profesional, OCR/documentos y notificaciones compartidas.

## Fuente oficial

La referencia es el **Observatorio de Precios y Mercados de la Junta de Andalucía** en nivel **almazara o bodega · Andalucía**.

Snapshot inicial persistido: **semana 36 de 2026, 31/08–06/09**, publicado el **09/09/2026**:

- AOVE: **3,42 €/kg**;
- Virgen: **3,25 €/kg**;
- Lampante 1º: **3,17 €/kg**.

La actualización segura usa dos superficies oficiales vivas:

- últimos precios del subsector aceite: `FrontController?action=UltimosPrecios&posicion=2291332&producto=33000&subsector=33`;
- últimas publicaciones: `FrontController?action=List&page=1&table=12030`.

La primera aporta precios y periodos; la segunda confirma la fecha del informe semanal. Los CSV/JSON antiguos localizados en datos abiertos no se usan como fuente productiva porque aparecían obsoletos o vacíos.

## Regla de producto

Siempre se distingue entre:

- **precio del aceite en origen** como referencia de mercado;
- **liquidación de la aceituna del agricultor**, que depende de rendimiento, calidad, gastos, bonificaciones y condiciones de cooperativa/almazara.

El histórico es descriptivo, no una previsión.

## Pantalla `/mercado`

Incluye:

- tarjetas AOVE / Virgen / Lampante;
- variación semanal;
- histórico real con selector **4 / 8 semanas**;
- lectura rápida descriptiva;
- fecha, periodo, nivel de mercado y fuente oficial visibles;
- calculadora `kg aceituna × rendimiento × €/kg aceite`;
- presets AOVE / Virgen / Lampante y precio manual;
- avisos para no confundir estimación, predicción y liquidación;
- diseño mobile-first y fallback de lectura.

## Persistencia

`database/migrations/0042_market_olive_oil_history.sql` crea `market_olive_oil_weekly`.

Granularidad: **una observación por fuente + categoría + semana**.

Campos principales:

```text
source_key
category
period_week
period_start
period_end
price_eur_kg
revision
snapshot_published_on
validated_through
source_name
source_url
market_level
status
ingested_at
```

La clave `(source_key, category, period_start)` permite `UPSERT` idempotente. El bootstrap incorpora **24 observaciones**: 8 semanas × 3 categorías.

## API pública

### Último snapshot

```text
GET /api/v1/public/market/olive-oil
```

Reconstruye el snapshot desde PostgreSQL. Si la base o el histórico no están disponibles, usa el snapshot bootstrap conocido.

### Histórico

```text
GET /api/v1/public/market/olive-oil/history?weeks=N
```

`N` admite enteros **1–52**. Los endpoints usan `ETag`, `Last-Modified`, caché pública corta y 304.

## Adaptador oficial fail-closed

`apps/api/src/market/junta-observatorio-adapter.ts` transforma las dos páginas oficiales en un `OliveOilMarketSnapshot` normalizado.

Antes de aceptar datos exige:

- tabla HTML reconocible;
- periodos semanales válidos de siete días;
- entre 2 y 12 semanas;
- exactamente AOVE, Virgen y Lampante;
- mismo número de precios que de periodos;
- precios numéricos positivos dentro de un rango defensivo;
- publicación `Informe semanal de aceite. Semana N` compatible con la última semana;
- fecha de publicación coherente con el periodo.

Si cambia la estructura y el contrato no puede demostrarse, lanza `MarketSourceContractError`; no devuelve datos parciales.

La descarga aplica timeout, redirecciones controladas, `User-Agent` identificable y rechazo de respuestas HTTP fallidas o anormalmente pequeñas.

## Planificador de refresco

`apps/api/src/market/refresh.ts` separa la lectura de la fuente de la decisión de escritura.

Clasifica cada candidato como:

```text
initial      → no existe histórico
unchanged    → coincide con lo persistido
new-period   → existe una semana posterior
correction   → corrige una semana ya publicada
```

Rechaza:

- fechas inválidas;
- `validatedThrough` posterior a `publishedOn`;
- publicación injustificadamente futura;
- retroceso del periodo respecto al histórico persistido.

Una corrección genera una revisión determinista distinta:

```text
junta-andalucia-olive-oil-YYYY-wN-v1-corr-<fingerprint>
```

Así también cambia el ETag y no se conserva una caché anterior después de una rectificación oficial.

## Operación controlada

`apps/api/src/market/refresh-cli.ts` expone:

```bash
pnpm --filter @magina/api market:source-check
pnpm --filter @magina/api market:refresh
```

- `market:source-check`: solo lectura / dry-run;
- `market:refresh`: aplica el candidato de forma explícita y requiere `DATABASE_URL`;
- `unchanged`: no escribe;
- no existe endpoint público de escritura.

## Monitor semanal de contrato

`.github/workflows/market-source-monitor.yml` prepara un monitor de solo lectura:

- `workflow_dispatch`;
- `schedule` jueves 06:17 UTC;
- permiso `contents: read`;
- sin credenciales de DB;
- ejecuta `market:source-check`.

Su función es detectar cambios del contrato HTML, no escribir en producción.

**El schedule solo se activará cuando el workflow esté integrado en la rama por defecto.** En este PR está preparado, no activo como monitor programado de producción.

## Flujo de actualización

```text
Observatorio Junta
        ↓
adaptador estricto
        ↓
OliveOilMarketSnapshot candidato
        ↓
planOliveOilMarketRefresh()
        ↓
initial / unchanged / new-period / correction
        ↓
[solo en ejecución explícita de refresh]
upsertOliveOilMarketSnapshot()
        ↓
market_olive_oil_weekly
        ↓
API pública
        ↓
/mercado
```

## Fallback de lectura

```text
PostgreSQL
   ↓
API pública
   ↓
/mercado
```

Si la DB o el histórico fallan, backend usa bootstrap. Si falla totalmente la API, web conserva un snapshot local de último recurso. Si falla solo el histórico, el selector 4/8 recorta localmente el snapshot visible.

## Pruebas

`e2e/market.spec.ts` cubre:

- API y `origin: database`;
- ETag/cache/304;
- precios/revisión;
- 24 observaciones persistidas;
- histórico 4/8 y validación 1–52;
- hidratación API → UI;
- cambio 8 → 4 → 8;
- móvil 360 px sin overflow;
- calculadora, presets y avisos.

`e2e/market-source-adapter.spec.ts` cubre con fixtures deterministas:

- HTML oficial esperado → snapshot exacto semana 36;
- categoría obligatoria ausente → fallo cerrado;
- mismo corte → `unchanged`;
- corrección AOVE 3,42 → 3,43 → `correction` y revisión nueva.

## Staging e integración paralela

Durante el desarrollo, `V20 staging readiness` comenzó a fallar en `Smoke staging containers and migration repeatability`. Se comprobó que la propia rama base `feat/v20-visual-prototype`, sin Mercado ni la migración 0042, fallaba en el mismo punto.

El frente propietario de staging corrigió después `deploy/staging/migrate.sh` en **`b1b2a261` — `fix(staging): verify applied migration status cleanly`**. El HEAD de la base pasó entonces:

- `V20 full candidate check` ✅
- `V20 beta browser E2E` ✅
- `V20 staging readiness` ✅, incluido `Smoke staging containers and migration repeatability`.

Aceite/Mercado no copió ni modificó esa corrección; espera la integración mediante la base para mantener la separación entre chats.

Este commit documental fuerza una nueva validación del PR #23 contra la base saneada.

## Siguientes fases

1. Confirmar los tres gates del PR #23 contra la base con el fix de staging.
2. Mantener el adaptador/monitor en lectura hasta integrar el workflow en la rama por defecto.
3. Acumular histórico real para 3, 6 y 12 meses a medida que se publiquen nuevos cortes.
4. Diseñar ejecución productiva del `market:refresh` con credencial mínima y sin escritura pública.
5. Ampliar el selector solo cuando existan datos reales suficientes.
6. Coordinar alertas de precio con el frente propietario de notificaciones; no tocarlo desde esta rama.
7. Conectar opcionalmente una campaña de Mi Campo para reutilizar kilos y rendimiento cuando se integren los frentes.

## Fuera de alcance

Esta rama no modifica:

- navegación global de Explorar;
- Mi Campo/fichas/campañas;
- Admin;
- GIS/Catastro/SIGPAC;
- clima/radar;
- profesional;
- OCR/documentos;
- staging compartido;
- colas/preferencias compartidas de notificaciones.
