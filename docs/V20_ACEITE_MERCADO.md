# V20 · Aceite y Mercado

## Objetivo

Construir un módulo de mercado sencillo para el agricultor de Sierra Mágina que responda a tres preguntas sin convertir Mágina Olivo en una terminal financiera:

1. ¿A cuánto está el aceite de referencia?
2. ¿Está subiendo o bajando?
3. ¿Qué significa aproximadamente para mi cosecha?

La entrega vive en `/mercado` y permanece deliberadamente independiente de `Mi Campo`, Admin, GIS, tiempo, profesional y demás frentes paralelos.

## Fuente actual

La referencia es el **Observatorio de Precios y Mercados de la Junta de Andalucía** para aceites de oliva en **almazara o bodega**.

El corte inicial persistido corresponde a la **semana 36 de 2026 (31 de agosto a 6 de septiembre)**, publicado el **9 de septiembre de 2026**, y conserva ocho semanas para:

- aceite de oliva virgen extra (AOVE),
- aceite de oliva virgen,
- aceite de oliva lampante de 1 grado.

La procedencia, URL oficial, periodo, fecha de publicación y revisión viajan dentro del contrato público para mantener la trazabilidad.

## Regla de producto

Mágina Olivo debe distinguir siempre entre:

- **precio del aceite en origen**, usado como referencia de mercado;
- **precio o liquidación de la aceituna del agricultor**, que depende de rendimiento, calidad, gastos, condiciones de entrega, anticipos, bonificaciones y reglas de cada cooperativa o almazara.

No se mostrará un precio en origen como si fuera el dinero que va a cobrar el agricultor.

## Entrega implementada

La ruta `/mercado` incorpora:

- tarjetas AOVE / Virgen / Lampante con último dato validado;
- variación respecto a la semana anterior;
- histórico visual con selector real de **4 / 8 semanas** alimentado por la API persistente;
- fallback del selector sobre el snapshot ya visible si falla la petición histórica;
- lectura rápida descriptiva del mercado, sin predicción;
- fecha de publicación, periodo y nivel de mercado visibles;
- enlace a la fuente oficial;
- calculadora orientativa de kilos de aceituna × rendimiento industrial × precio de referencia;
- presets AOVE / Virgen / Lampante y precio manual;
- advertencia explícita de que la estimación no es una liquidación ni una oferta de compra;
- diseño mobile-first;
- hidratación desde la API pública de mercado;
- fallback local del último snapshot conocido para preview u operación sin API.

## Persistencia

La migración `0042_market_olive_oil_history.sql` crea `market_olive_oil_weekly`.

La granularidad es **una observación por fuente + categoría + semana**. El modelo guarda:

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

La clave natural `(source_key, category, period_start)` permite reingestar una publicación y aplicar una corrección histórica mediante `UPSERT` sin duplicar semanas.

El bootstrap de la migración incorpora las 24 observaciones ya contrastadas: 8 semanas × 3 categorías. A partir de ahí, la base de datos es la fuente de ejecución conectada.

`apps/api/src/market/history.ts` encapsula tanto la lectura como el camino de escritura idempotente para una futura ingesta. No se ha añadido un endpoint público de escritura.

## Contratos públicos

### Último snapshot

```text
GET /api/v1/public/market/olive-oil
```

Cuando PostgreSQL está disponible, el endpoint reconstruye el snapshot visible desde el histórico persistido. Si la base no está disponible o el histórico está incompleto, cae de forma controlada al snapshot bootstrap conocido.

La respuesta incluye:

```text
origin = database | bootstrap
market
  schemaVersion
  revision
  source
  period
  series[]
  note
```

### Histórico

```text
GET /api/v1/public/market/olive-oil/history?weeks=8
```

`weeks` admite enteros entre **1 y 52**. El endpoint devuelve:

```text
history
  schemaVersion
  origin
  revision
  source
  windowWeeks
  availableFrom
  availableThrough
  series[]
    points[]
```

Si se solicitan más semanas de las disponibles, se devuelve el histórico existente y `windowWeeks` refleja la ventana real.

Los dos endpoints publican `ETag`, `Last-Modified` y caché pública corta (`max-age=900`) con `stale-while-revalidate`. `If-None-Match` produce 304 cuando la revisión y la ventana no han cambiado.

## Flujo conectado y fallback

El recorrido actual es:

```text
PostgreSQL · market_olive_oil_weekly
        ↓
repositorio de histórico
        ↓
GET /api/v1/public/market/olive-oil
        ↓
validación cliente
        ↓
/mercado
```

La gráfica histórica utiliza además:

```text
Selector 4 / 8 semanas
        ↓
GET /api/v1/public/market/olive-oil/history?weeks=N
        ↓
validación de contrato
        ↓
serie visible
```

Y el modo degradado:

```text
DB no disponible / histórico incompleto
        ↓
snapshot backend conocido
        ↓
API pública
        ↓
web
```

La web conserva además un snapshot local únicamente como último fallback para preview estática o falta total de API. La interfaz distingue el estado API del fallback mediante `data-market-source="api|fallback"`. Si falla únicamente la petición del histórico, el selector recorta de forma local el snapshot ya disponible en lugar de vaciar la gráfica.

## Preparación de ingesta

La escritura persistente ya está preparada mediante `upsertOliveOilMarketSnapshot()`:

```text
Fuente externa fiable
    ↓
Adaptador
    ↓
validación + normalización
    ↓
OliveOilMarketSnapshot
    ↓
upsertOliveOilMarketSnapshot()
    ↓
market_olive_oil_weekly
    ↓
API / web
```

No se automatiza todavía la captura desde una fuente estructurada no verificada. Durante la investigación, los recursos CSV/JSON del catálogo de Datos Abiertos localizados aparecían obsoletos o vacíos. Hasta disponer de un recurso máquina-a-máquina estable y comprobable, la autoridad sigue siendo el Observatorio semanal vivo.

## Validación automatizada

`e2e/market.spec.ts` cubre:

- respuesta 200 de la API pública;
- origen `database` cuando CI levanta PostgreSQL;
- cabeceras de caché y `ETag`;
- revisión, periodo y fechas de fuente;
- AOVE 3,42 €/kg, Virgen 3,25 €/kg y Lampante 3,17 €/kg para semana 36;
- respuesta 304 con `If-None-Match`;
- histórico de 8 semanas y 24 observaciones persistidas;
- ventana de 4 semanas;
- rechazo de `weeks` fuera de 1–52 o no enteros;
- hidratación real de `/mercado` desde API;
- cambio interactivo 8 → 4 → 8 semanas consumiendo el endpoint histórico;
- coincidencia de precios API/UI;
- lectura rápida de mercado;
- ausencia de overflow horizontal a 360 px;
- cálculo orientativo de cosecha y presets de precio;
- presencia de avisos para no confundir estimación, predicción y liquidación.

## Siguientes fases

1. Conectar un adaptador de ingesta automática únicamente cuando exista una fuente oficial estructurada y verificable.
2. Acumular histórico persistente real para 3, 6 y 12 meses a medida que entren nuevos cortes.
3. Ampliar progresivamente el selector cuando existan ventanas reales suficientes, sin rellenar meses con datos inventados.
4. Comparar con una segunda referencia independiente cuando su licencia y estabilidad lo permitan.
5. Preferencias de precio y alertas, sin notificaciones especulativas.
6. Relación opcional con una campaña real de `Mi Campo` para reutilizar kilos y rendimiento del usuario.
7. Información de cooperativas/almazaras como contexto separado del índice de mercado.

## Fuera de alcance de esta rama

Para evitar conflictos entre chats, esta rama no modifica:

- la navegación global de `Explorar`;
- `Mi Campo` y ficha de finca;
- Admin;
- mapas, Catastro o SIGPAC;
- tiempo y radar;
- módulo profesional;
- OCR y documentos;
- campañas o liquidaciones existentes.

La integración del acceso a `/mercado` en las superficies compartidas se hará cuando se reúna el trabajo paralelo.
