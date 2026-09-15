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
correction   → corrige el último corte ya publicado
```

Además cuenta `historicalCorrections`: puntos de periodos ya persistidos cuyo precio cambia dentro del nuevo snapshot.

Rechaza:

- fechas inválidas;
- `validatedThrough` posterior a `publishedOn`;
- publicación injustificadamente futura;
- retroceso del periodo respecto al histórico persistido.

Una corrección del mismo corte genera una revisión determinista distinta:

```text
junta-andalucia-olive-oil-YYYY-wN-v1-corr-<fingerprint>
```

Así cambia el ETag y no se conserva una caché anterior después de una rectificación oficial.

## Política de escritura

La automatización aplica una regla conservadora:

- `unchanged` → no escribe;
- `new-period` sin cambios retrospectivos → puede escribirse;
- `correction` → requiere aprobación explícita;
- `new-period` que también cambia semanas anteriores → requiere aprobación explícita;
- regresión temporal → se rechaza siempre.

`assertMarketRefreshApplyAllowed()` bloquea cualquier corrección salvo que la operación se ejecute con `allowCorrections=true`.

Esto evita que una actualización automática reescriba histórico sin intervención humana.

## CLI explícito

`apps/api/src/market/refresh-cli.ts` ya no deduce el modo a partir de la presencia de `DATABASE_URL`. Cada operación es explícita:

```bash
pnpm --filter @magina/api market:source-check
pnpm --filter @magina/api market:refresh:dry-run
pnpm --filter @magina/api market:refresh
pnpm --filter @magina/api market:refresh:corrections
```

Equivalencias:

```text
market:source-check         → --source-check
market:refresh:dry-run      → --dry-run
market:refresh              → --apply
market:refresh:corrections  → --apply --allow-corrections
```

`dry-run` y `apply` requieren `DATABASE_URL`. `source-check` no accede a PostgreSQL.

Flags desconocidos, combinaciones ambiguas o `--allow-corrections` fuera de `--apply` fallan cerrado.

## Monitor semanal de contrato

`.github/workflows/market-source-monitor.yml` mantiene el monitor de solo lectura:

- `workflow_dispatch`;
- `schedule` jueves 06:17 UTC;
- permiso `contents: read`;
- sin credenciales de DB;
- ejecuta `market:source-check`.

Su función es detectar cambios del contrato HTML, no escribir en producción.

## Workflow de refresco productivo

`.github/workflows/market-refresh.yml` prepara la operación de escritura sin activarla por defecto.

Modos manuales:

```text
source-check
dry-run
apply
```

Para `apply` manual se exige escribir exactamente `APPLY` en `confirm_apply`.

Las correcciones necesitan además `allow_corrections=true`. Esa opción nunca se habilita desde el schedule automático.

Configuración futura:

```text
Secret:   MARKET_DATABASE_URL
Variable: MARKET_AUTO_APPLY=true
```

Sin `MARKET_AUTO_APPLY=true`, el job programado no escribe. Sin `MARKET_DATABASE_URL`, `dry-run` y `apply` fallan antes de ejecutar el refresco.

Cuando ambas piezas estén configuradas y el workflow se encuentre en la rama por defecto, el schedule del jueves 07:47 UTC podrá aplicar únicamente semanas nuevas sin correcciones retrospectivas.

Cada ejecución publica:

- modo;
- si las correcciones estaban aprobadas;
- tipo de plan;
- revisión actual/candidata;
- periodo actual/candidato;
- número de correcciones históricas;
- precios del snapshot.

El resultado se conserva también como artifact de auditoría durante **90 días**.

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
control de correcciones ya persistidas
        ↓
[solo si la operación está autorizada]
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
- `initial`, `unchanged`, `new-period` y `correction`;
- regresión temporal → rechazo;
- corrección del mismo corte → revisión nueva;
- cambio retrospectivo dentro de una nueva semana → detectado;
- correcciones → bloqueadas salvo aprobación explícita;
- modos CLI explícitos y rechazo de flags ambiguos/desconocidos.

## Integración paralela

El frente propietario de staging corrigió `deploy/staging/migrate.sh` en **`b1b2a261` — `fix(staging): verify applied migration status cleanly`**. Aceite/Mercado no copió ni modificó esa corrección.

La rama continúa sin tocar navegación global, Mi Campo, Admin, GIS, clima, profesional, OCR/documentos, staging compartido ni notificaciones.

## Activación futura

Antes de activar escritura programada en la rama por defecto:

1. configurar `MARKET_DATABASE_URL` con credencial limitada a la base de staging/producción correspondiente;
2. ejecutar `source-check`;
3. ejecutar `dry-run` contra la DB real;
4. revisar el resultado y el histórico existente;
5. ejecutar un primer `apply` manual limpio;
6. comprobar API `/market/olive-oil` e histórico;
7. solo entonces establecer `MARKET_AUTO_APPLY=true`;
8. mantener correcciones siempre fuera del auto-apply.

## Siguientes fases

1. Acumular histórico real hasta disponer de ventanas suficientes para 3, 6 y 12 meses.
2. Ampliar el selector solo cuando existan datos reales suficientes.
3. Coordinar alertas de precio con el frente propietario de notificaciones; no tocarlo desde esta rama.
4. Conectar opcionalmente una campaña de Mi Campo para reutilizar kilos y rendimiento cuando se integren los frentes.

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
