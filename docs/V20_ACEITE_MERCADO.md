# V20 · Aceite y Mercado

## Objetivo

Construir un módulo de mercado sencillo para el agricultor de Sierra Mágina que responda a tres preguntas sin convertir Mágina Olivo en una terminal financiera:

1. ¿A cuánto está el aceite de referencia?
2. ¿Está subiendo o bajando?
3. ¿Qué significa aproximadamente para mi cosecha?

La primera entrega vive en `/mercado` y es deliberadamente independiente de `Mi Campo`, Admin, GIS, tiempo, profesional y demás frentes paralelos.

## Fuente inicial

La referencia inicial es el **Observatorio de Precios y Mercados de la Junta de Andalucía** para aceites de oliva en **almazara o bodega**.

El snapshot incluido está fechado en la **semana 36 de 2026 (31 de agosto a 6 de septiembre)** y conserva ocho semanas de histórico para:

- aceite de oliva virgen extra (AOVE),
- aceite de oliva virgen,
- aceite de oliva lampante de 1 grado.

La URL de la fuente queda almacenada junto a los datos en `apps/web/src/lib/market-data.ts` para mantener trazabilidad.

## Regla de producto

Mágina Olivo debe distinguir siempre entre:

- **precio del aceite en origen**, usado como referencia de mercado;
- **precio o liquidación de la aceituna del agricultor**, que depende de rendimiento, calidad, gastos, condiciones de entrega, anticipos, bonificaciones y reglas de cada cooperativa o almazara.

No se mostrará un precio en origen como si fuera el dinero que va a cobrar el agricultor.

## Primera entrega

La ruta `/mercado` incorpora:

- tarjetas AOVE / Virgen / Lampante con último dato validado;
- variación respecto a la semana anterior;
- evolución visual de ocho semanas;
- fecha y nivel de mercado visibles;
- enlace a la fuente oficial;
- calculadora orientativa de kilos de aceituna × rendimiento industrial × precio de referencia;
- advertencia explícita de que la estimación no es una liquidación ni una oferta de compra;
- diseño mobile-first y sin dependencia de servicios externos en tiempo de render.

## Arquitectura de datos

En esta fase el dato se mantiene como un **snapshot explícito**. Esto evita presentar como tiempo real algo que todavía no dispone de un proceso de ingestión controlado.

La siguiente fase deberá separar:

```text
Fuente externa
    ↓
Adaptador / ingesta
    ↓
Market snapshot normalizado
    ↓
Persistencia + fecha de validación
    ↓
API de mercado
    ↓
Web / alertas / Mi Campo
```

Campos mínimos del futuro snapshot persistido:

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
```

## Siguientes fases

1. Ingesta automática y caché controlada del Observatorio.
2. Histórico persistente para 3, 6 y 12 meses.
3. Comparación con una segunda referencia independiente cuando su licencia y estabilidad lo permitan.
4. Preferencias de precio y alertas, sin notificaciones especulativas.
5. Relación opcional con una campaña real de `Mi Campo` para reutilizar kilos y rendimiento del usuario.
6. Información de cooperativas/almazaras como contexto separado del índice de mercado.

## Fuera de alcance de esta rama inicial

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
