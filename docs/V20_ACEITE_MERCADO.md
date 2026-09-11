# V20 · Aceite y Mercado

## Objetivo

Construir un módulo de mercado sencillo para el agricultor de Sierra Mágina que responda a tres preguntas sin convertir Mágina Olivo en una terminal financiera:

1. ¿A cuánto está el aceite de referencia?
2. ¿Está subiendo o bajando?
3. ¿Qué significa aproximadamente para mi cosecha?

La entrega vive en `/mercado` y permanece deliberadamente independiente de `Mi Campo`, Admin, GIS, tiempo, profesional y demás frentes paralelos.

## Fuente oficial actual

La referencia es el **Observatorio de Precios y Mercados de la Junta de Andalucía** para aceites de oliva en **almazara o bodega**.

El corte inicial persistido corresponde a la **semana 36 de 2026 (31 de agosto a 6 de septiembre)**, publicado el **9 de septiembre de 2026**, y conserva ocho semanas para:

- aceite de oliva virgen extra (AOVE),
- aceite de oliva virgen,
- aceite de oliva lampante de 1 grado.

Para la actualización se han identificado dos superficies oficiales vivas:

- tabla de precios del subsector aceite: `FrontController?action=UltimosPrecios&posicion=2291332&producto=33000&subsector=33`;
- listado de últimas publicaciones: `FrontController?action=List&page=1&table=12030`.

La primera aporta periodos y precios. La segunda permite comprobar la fecha de publicación del informe semanal. Se usan juntas para evitar inferir una fecha de publicación a partir de la fecha final del periodo.

Los recursos CSV/JSON del catálogo de datos abiertos investigados previamente no se usan como fuente de producción porque los recursos localizados aparecían obsoletos o vacíos.

## Regla de producto

Mágina Olivo debe distinguir siempre entre:

- **precio del aceite en origen**, usado como referencia de mercado;
- **precio o liquidación de la aceituna del agricultor**, que depende de rendimiento, calidad, gastos, condiciones de entrega, anticipos, bonificaciones y reglas de cada cooperativa o almazara.

No se mostrará un precio en origen como si fuera el dinero que va a cobrar el agricultor. Tampoco se presentará una variación histórica como una previsión.

## Entrega visible

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

El bootstrap de la migración incorpora las 24 observaciones ya contrastadas: 8 semanas × 3 categorías. A partir de ahí, PostgreSQL es la fuente conectada de ejecución.

`apps/api/src/market/history.ts` encapsula la lectura y `upsertOliveOilMarketSnapshot()` el camino de escritura idempotente. No existe un endpoint público de escritura.

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

`weeks` admite enteros entre **1 y 52**. Si se solicitan más semanas de las disponibles, se devuelve el histórico existente y `windowWeeks` refleja la ventana real.

Los dos endpoints publican `ETag`, `Last-Modified` y caché pública corta (`max-age=900`) con `stale-while-revalidate`. `If-None-Match` produce 304 cuando la revisión y la ventana no han cambiado.

## Adaptador oficial de lectura

`apps/api/src/market/junta-observatorio-adapter.ts` transforma las dos páginas oficiales en un `OliveOilMarketSnapshot` normalizado.

El adaptador es deliberadamente **fail-closed**. Antes de aceptar una publicación exige:

- una tabla HTML reconocible;
- periodos semanales con número de semana y fechas válidas;
- periodos de exactamente siete días;
- entre 2 y 12 semanas en la tabla obtenida;
- exactamente una fila para AOVE, una para Virgen y una para Lampante;
- el mismo número de precios que de periodos;
- precios numéricos positivos dentro de un rango defensivo;
- una publicación de `Informe semanal de aceite. Semana N` para la última semana encontrada;
- una fecha de publicación no anterior al fin del periodo y no absurdamente alejada del mismo.

Si cambia la estructura de la web oficial y el contrato deja de poder demostrarse, el parser lanza `MarketSourceContractError` y no devuelve datos parciales.

La descarga de la fuente aplica además timeout, seguimiento controlado de redirecciones, `User-Agent` identificable y rechazo de respuestas HTTP fallidas o anormalmente pequeñas.

## Planificador de refresco seguro

`apps/api/src/market/refresh.ts` separa **leer la fuente** de **decidir si se puede escribir**.

El resultado se clasifica como:

```text
initial      → no había histórico persistido
unchanged    → el corte oficial coincide con lo ya almacenado
new-period   → existe una semana oficial posterior
correction   → la fuente oficial corrige valores de una semana ya publicada
```

Antes de aceptar el candidato se comprueban también los relojes de fuente. Se rechaza:

- una publicación con fechas inválidas;
- `validatedThrough` posterior a `publishedOn`;
- una publicación fechada injustificadamente en el futuro;
- un candidato cuyo periodo final retrocede respecto al ya persistido.

Una corrección no reutiliza silenciosamente la revisión anterior. Se genera una revisión determinista:

```text
junta-andalucia-olive-oil-YYYY-wN-v1-corr-<fingerprint>
```

Esto hace que una rectificación oficial invalide también las cachés/ETag asociadas al snapshot anterior.

## CLI: comprobar primero, escribir solo de forma explícita

`apps/api/src/market/refresh-cli.ts` expone dos comandos:

```bash
pnpm --filter @magina/api market:source-check
pnpm --filter @magina/api market:refresh
```

`market:source-check` es de solo lectura. Sin `DATABASE_URL` valida la fuente viva y muestra el snapshot normalizado.

`market:refresh` ejecuta el mismo recorrido con `--apply`; requiere `DATABASE_URL` y solo hace `UPSERT` cuando el plan es `initial`, `new-period` o `correction`. Un resultado `unchanged` no escribe.

No se ha conectado este comando a una credencial de producción ni a un endpoint remoto de escritura en esta rama.

## Monitor semanal de contrato

`.github/workflows/market-source-monitor.yml` deja preparado un monitor semanal de solo lectura:

- ejecución manual mediante `workflow_dispatch`;
- programación los jueves a las 06:17 UTC;
- permisos `contents: read`;
- sin `DATABASE_URL` ni secretos de escritura;
- ejecuta únicamente `market:source-check`.

Su objetivo no es actualizar producción, sino detectar de forma temprana si la Junta cambia la estructura HTML o deja de publicar el contrato esperado.

**Importante:** el `schedule` de GitHub Actions solo comenzará a ejecutarse cuando este workflow esté integrado en la rama por defecto del repositorio. Mientras permanezca únicamente en esta rama/PR, está preparado pero no debe considerarse un monitor programado activo.

## Flujo de actualización previsto

```text
Observatorio Junta · HTML vivo
        ↓
adaptador estricto
        ↓
OliveOilMarketSnapshot candidato
        ↓
planOliveOilMarketRefresh()
        ↓
initial / unchanged / new-period / correction
        ↓
[solo con --apply explícito]
upsertOliveOilMarketSnapshot()
        ↓
market_olive_oil_weekly
        ↓
API pública
        ↓
/mercado
```

El monitor semanal cubre únicamente la parte superior hasta validar el candidato. La escritura automática de producción queda separada a propósito.

## Fallback de lectura

El recorrido normal es:

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

La web conserva además un snapshot local como último fallback para preview estática o falta total de API. Si falla solo el endpoint histórico, el selector recorta localmente el snapshot ya visible en vez de vaciar la gráfica.

## Validación automatizada

`e2e/market.spec.ts` cubre:

- API pública y `origin: database`;
- ETag/cache/304;
- precios y revisión oficiales;
- las 24 observaciones persistidas;
- ventanas históricas 4/8 y validación 1–52;
- hidratación API → UI;
- cambio real 8 → 4 → 8 semanas;
- mobile 360 px sin overflow;
- calculadora, presets y avisos de contexto.

`e2e/market-source-adapter.spec.ts` añade fixtures deterministas que cubren:

- HTML con la estructura oficial esperada → snapshot exacto de semana 36;
- desaparición de una categoría obligatoria → fallo cerrado;
- publicación sin cambios → `unchanged`;
- corrección del AOVE de 3,42 a 3,43 €/kg en la misma semana → `correction` y revisión nueva.

En el HEAD que introduce el adaptador/monitor:

- `V20 full candidate check` pasa completo;
- `V20 beta browser E2E` pasa completo, incluidas las pruebas anteriores;
- `V20 staging readiness` falla únicamente en `Smoke staging containers and migration repeatability`.

Ese último bloqueo es **heredado de la rama base**: el HEAD actual de `feat/v20-visual-prototype`, sin la migración 0042 ni el código de Mercado, falla independientemente en el mismo paso porque el servicio `migrate` del compose de staging termina con código 1. Por aislamiento entre chats, esta rama no modifica el runner de staging/OCR para ocultar o reparar un fallo ajeno a Mercado.

## Siguientes fases del frente

1. Mantener el adaptador y monitor en modo de lectura hasta integrar el workflow en la rama por defecto.
2. Corregir primero el gate compartido de staging en su frente propietario y volver a validar este PR contra esa base.
3. Acumular histórico persistente real para 3, 6 y 12 meses a medida que entren nuevos cortes.
4. Definir un mecanismo de producción con credencial mínima para ejecutar `market:refresh` después de una validación previa, sin exponer escritura pública.
5. Ampliar progresivamente el selector cuando existan ventanas reales suficientes, sin rellenar meses con datos inventados.
6. Preferencias/alertas de precio solo cuando se coordine con el frente de notificaciones compartido; no se toca desde esta rama.
7. Relación opcional con una campaña real de `Mi Campo` para reutilizar kilos y rendimiento del usuario.
8. Información de cooperativas/almazaras como contexto separado del índice de mercado.

## Fuera de alcance de esta rama

Para evitar conflictos entre chats, esta rama no modifica:

- la navegación global de `Explorar`;
- `Mi Campo` y ficha de finca;
- Admin;
- mapas, Catastro o SIGPAC;
- tiempo y radar;
- módulo profesional;
- OCR y documentos;
- campañas o liquidaciones existentes;
- infraestructura compartida de staging;
- colas y preferencias compartidas de notificaciones.

La integración de acceso, staging y alertas se hará al reunir los frentes paralelos.
