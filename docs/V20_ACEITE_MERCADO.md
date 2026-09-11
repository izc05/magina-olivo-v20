# V20 · Aceite y Mercado

## Objetivo

Construir un módulo de mercado sencillo para el agricultor de Sierra Mágina que responda a tres preguntas sin convertir Mágina Olivo en una terminal financiera:

1. ¿A cuánto está el aceite de referencia?
2. ¿Está subiendo o bajando?
3. ¿Qué significa aproximadamente para mi cosecha?

La primera entrega vive en `/mercado` y es deliberadamente independiente de `Mi Campo`, Admin, GIS, tiempo, profesional y demás frentes paralelos.

## Fuente actual

La referencia es el **Observatorio de Precios y Mercados de la Junta de Andalucía** para aceites de oliva en **almazara o bodega**.

El snapshot incluido está fechado en la **semana 36 de 2026 (31 de agosto a 6 de septiembre)**, publicado el **9 de septiembre de 2026**, y conserva ocho semanas de histórico para:

- aceite de oliva virgen extra (AOVE),
- aceite de oliva virgen,
- aceite de oliva lampante de 1 grado.

La procedencia, URL oficial, periodo, fecha de publicación y revisión del snapshot viajan dentro del contrato público de mercado para mantener la trazabilidad.

## Regla de producto

Mágina Olivo debe distinguir siempre entre:

- **precio del aceite en origen**, usado como referencia de mercado;
- **precio o liquidación de la aceituna del agricultor**, que depende de rendimiento, calidad, gastos, condiciones de entrega, anticipos, bonificaciones y reglas de cada cooperativa o almazara.

No se mostrará un precio en origen como si fuera el dinero que va a cobrar el agricultor.

## Entrega implementada

La ruta `/mercado` incorpora:

- tarjetas AOVE / Virgen / Lampante con último dato validado;
- variación respecto a la semana anterior;
- evolución visual de ocho semanas;
- fecha de publicación, periodo y nivel de mercado visibles;
- enlace a la fuente oficial;
- calculadora orientativa de kilos de aceituna × rendimiento industrial × precio de referencia;
- advertencia explícita de que la estimación no es una liquidación ni una oferta de compra;
- diseño mobile-first;
- hidratación desde la API pública de mercado;
- fallback local del último snapshot conocido para preview u operación sin API.

## Contrato público

El backend expone:

```text
GET /api/v1/public/market/olive-oil
```

El endpoint no requiere autenticación y devuelve un snapshot versionado con:

```text
schemaVersion
revision
source
  name
  url
  marketLevel
  publishedOn
  validatedThrough
period
  week
  start
  end
  label
series[]
  id
  name
  shortName
  unit
  points[]
  latest
note
```

La revisión actual es:

```text
junta-andalucia-olive-oil-2026-w36-v1
```

La respuesta publica `ETag`, `Last-Modified` y una política de caché pública corta (`max-age=900`) con `stale-while-revalidate`, de forma que puede servirse eficientemente sin presentar el dato semanal como tiempo real.

## Flujo web y fallback

La pantalla sigue este recorrido:

```text
Snapshot local conocido
        ↓ render inmediato
/mercado
        ↓ useEffect
GET /api/v1/public/market/olive-oil
        ↓
validación de schema + series + precios
        ↓
reemplazo del snapshot visible
        ↓
Dato API validado
```

Si la API o la red no responden, se conserva el último snapshot local conocido. Ese fallback es deliberado: permite que una preview estática o una situación sin conexión siga mostrando la última referencia conocida, pero la interfaz distingue ambos estados mediante `data-market-source="api|fallback"` y el texto correspondiente.

La prueba Playwright exige `data-market-source="api"` en el recorrido completo con backend levantado. Por tanto, CI no considera válida una pantalla que simplemente coincida con la API por tener constantes duplicadas.

## Fuente de verdad y actualización semanal

En esta fase, la **fuente canónica para ejecución conectada** es `apps/api/src/market/snapshot.ts`. El snapshot de `apps/web/src/lib/market-data.ts` existe únicamente como fallback offline/preview y debe actualizarse junto con la revisión oficial cuando se publique un nuevo corte.

El siguiente salto técnico será eliminar esa actualización manual doble mediante una ingesta persistente:

```text
Fuente externa
    ↓
Adaptador / ingesta
    ↓
Validación y normalización
    ↓
Persistencia del snapshot
    ↓
API de mercado
    ↓
Web / alertas / Mi Campo
```

Campos mínimos de un snapshot persistido:

```text
source
market_level
category
period_start
period_end
price_eur_kg
published_at
fetched_at
status (validated/provisional)
revision
```

## Validación automatizada

`e2e/market.spec.ts` comprueba:

- respuesta 200 de la API pública;
- cabeceras de caché y `ETag`;
- revisión, periodo y fechas de fuente;
- AOVE 3,42 €/kg, Virgen 3,25 €/kg y Lampante 3,17 €/kg para semana 36;
- respuesta 304 con `If-None-Match`;
- hidratación real de `/mercado` desde API;
- coincidencia de precios API/UI;
- ausencia de overflow horizontal a 360 px;
- cálculo orientativo de cosecha;
- presencia del aviso que evita confundir estimación con liquidación.

## Siguientes fases

1. Ingesta automática y persistencia controlada del Observatorio.
2. Histórico persistente para 3, 6 y 12 meses.
3. Comparación con una segunda referencia independiente cuando su licencia y estabilidad lo permitan.
4. Preferencias de precio y alertas, sin notificaciones especulativas.
5. Relación opcional con una campaña real de `Mi Campo` para reutilizar kilos y rendimiento del usuario.
6. Información de cooperativas/almazaras como contexto separado del índice de mercado.

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
