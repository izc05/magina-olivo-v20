# Mágina Olivo V20 — Mi Olivo: niveles, progreso y recompensas

> Rama: `feat/v20-ui-ux-premium`
>
> Objetivo: fijar una base coherente para XP, evolución visual del olivo, aceitunas internas, desbloqueos y recompensas AOVE antes de producir el sistema visual definitivo en Figma y la lógica real en backend.

## 1. Principios

- **XP** y **aceitunas** son dos cosas distintas.
- El XP representa progreso y nivel.
- Las aceitunas son una unidad interna de fidelización/canje.
- Las aceitunas no son dinero, criptomoneda, token blockchain ni activo financiero.
- El olivo es la representación visual del progreso del usuario.
- El árbol debe evolucionar por etapas, no solo cambiar un número de nivel.
- La actividad real en Mágina Aventura, Pueblos, Comunidad y Mi Campo puede alimentar progreso, siempre con reglas antifraude y límites.
- Las recompensas deben depender de stock real y de una operación transaccional segura.

## 2. Niveles propuestos V20.0

| Nivel | Nombre | XP total para alcanzarlo | Etapa visual del olivo | Idea de desbloqueo |
|---:|---|---:|---|---|
| 1 | Brote | 0 | Brote recién nacido | Mi Olivo + primeras misiones |
| 2 | Primeras raíces | 250 | Plantón pequeño | Colecciones + primera insignia |
| 3 | Raíces viajeras | 1.000 | Olivo joven | Recompensas + progreso territorial |
| 4 | Olivo joven | 2.000 | Joven con copa visible | Nuevos retos + evolución avanzada |
| 5 | Copa de Mágina | 3.500 | Copa más densa y primeras aceitunas | Recompensas de mayor valor |
| 6 | Olivo de la Sierra | 5.500 | Olivo adulto | Insignias especiales + colección avanzada |
| 7 | Guardián del olivar | 8.000 | Adulto robusto | Retos territoriales especiales |
| 8 | Olivo maduro | 11.000 | Maduro y muy frondoso | Recompensas premium limitadas |
| 9 | Raíces centenarias | 15.000 | Apariencia centenaria | Distintivos de veteranía |
| 10 | Leyenda de Mágina | 20.000 | Olivo emblemático | Estatus máximo V20.0 y recompensas exclusivas |

### Regla importante

El nivel 3 se mantiene como **Raíces viajeras** para ser coherente con el concepto visual ya aprobado, donde se mostró `1.250 / 2.000 XP`.

## 3. Evolución visual

No necesitamos diez modelos de árbol totalmente independientes. Para V20.0 se proponen seis etapas visuales principales:

1. Brote.
2. Plantón.
3. Joven.
4. Adulto.
5. Maduro.
6. Centenario / emblemático.

Los niveles intermedios pueden modificar densidad de copa, cantidad de aceituna, iluminación, pequeños detalles, partículas, pedestal/terreno y efectos de progreso sin requerir otro árbol completamente nuevo.

### Movimiento recomendado

Para V20.0:

- 2.5D con capas.
- movimiento suave de ramas y hojas;
- pequeñas hojas/partículas en primer plano;
- luz dinámica muy discreta;
- parallax;
- transición de etapa al subir de nivel;
- reducción automática de movimiento con `prefers-reduced-motion`.

Three.js / árbol 3D completo queda como P2 si el 2.5D no alcanza el acabado deseado.

## 4. XP — reglas iniciales

Los valores siguientes son **punto de partida configurable desde Admin**, no constantes inmutables:

| Acción | XP inicial | Regla |
|---|---:|---|
| Completar perfil inicial | 50 | una sola vez |
| Completar una ruta única | 100–300 | según distancia/dificultad |
| Completar una aventura | 150–400 | según dificultad y checkpoints |
| Checkpoint válido | 10–25 | solo dentro de actividad válida |
| Descubrimiento especial | 25–75 | primera vez |
| Visitar un pueblo nuevo | 50 | primera visita validada |
| Completar colección parcial | 50–150 | por hito |
| Insignia | 50–300 | según rareza |
| Reseña aprobada | 10 | con límite antifarming |
| Foto aprobada | 10 | con límite antifarming |
| Hito de Mi Campo | variable | solo cuando existe dato real y la regla está habilitada |

### Antifraude XP

- La primera finalización de una ruta genera recompensa completa.
- Repeticiones pueden generar XP reducido o cero según configuración.
- Checkpoints requieren una actividad/ruta válida.
- Acciones sociales tienen límites diarios/semanales.
- No se premian reseñas/fotos rechazadas.
- Admin puede anular operaciones fraudulentas dejando trazabilidad.

## 5. Aceitunas — unidad de fidelización

### Separación conceptual

- XP **no se gasta**.
- Aceitunas **sí se gastan** al reservar/canjear premios.
- Subir de nivel depende del XP, no del saldo de aceitunas.
- Gastar aceitunas nunca reduce el nivel.

### Fuentes iniciales de aceitunas

| Acción | Aceitunas iniciales | Notas |
|---|---:|---|
| Completar aventura | 20–80 | según dificultad |
| Ruta única validada | 10–40 | primera finalización completa |
| Descubrimiento destacado | 5–20 | primera vez |
| Insignia territorial | 10–50 | según logro |
| Campaña/evento especial | variable | control Admin |

Las acciones fáciles de automatizar, como publicar comentarios repetidos, no deberían ser una fuente importante de aceitunas.

## 6. Recompensas AOVE — bandas de coste iniciales

No fijar precios definitivos hasta conocer coste, stock y aportación real de cada almazara. Como referencia de UX:

- recompensa pequeña / detalle: 150–300 aceitunas;
- botella AOVE estándar: 400–700 aceitunas;
- recompensa premium / edición especial: 800–1.500 aceitunas;
- experiencia / visita / cata: coste variable.

La imagen conceptual actual usa **500 aceitunas** para una botella AOVE; se mantiene como referencia visual, no como tarifa contractual.

## 7. Flujo transaccional de canje

1. Usuario abre catálogo.
2. Selecciona recompensa.
3. Backend comprueba:
   - usuario válido;
   - saldo suficiente;
   - nivel/requisitos, si existen;
   - stock disponible;
   - límites por usuario;
   - recompensa activa.
4. En una transacción atómica:
   - se reserva una unidad de stock;
   - se bloquean/descuentan aceitunas según política;
   - se crea reserva;
   - se genera token QR único.
5. Usuario recibe pantalla `QR activo`.
6. Almazara escanea.
7. Backend valida token, estado y caducidad.
8. Se confirma canje una sola vez.
9. Se registra historial completo.
10. Si reserva caduca o se cancela según reglas, se libera stock y, cuando corresponda, se restituyen aceitunas.

## 8. Estados de una recompensa/reserva

Estados mínimos recomendados:

- `AVAILABLE`
- `RESERVED`
- `READY_TO_REDEEM`
- `REDEEMED`
- `EXPIRED`
- `CANCELLED`
- `OUT_OF_STOCK`

El QR nunca debe ser la fuente de verdad; solo referencia una reserva/token que el servidor valida.

## 9. Seguridad del QR

- Token aleatorio de alta entropía.
- No incluir saldo, nombre completo ni datos sensibles directamente en el QR.
- Un único canje exitoso por token.
- Caducidad configurable.
- Auditoría de validación y canje.
- Validación siempre en backend cuando haya conexión.
- Código corto de respaldo opcional para incidencias operativas.

## 10. Subida de nivel — experiencia visual

Al alcanzar un umbral:

1. Mantener la pantalla estable y confirmar el evento en backend.
2. Mostrar transición del olivo.
3. Actualizar nombre de nivel y etapa.
4. Resumir desbloqueos reales.
5. Añadir el hito a la memoria del usuario.
6. No conceder una recompensa física automáticamente salvo que la regla lo indique explícitamente.

Ejemplo:

`Nivel 4 alcanzado · Olivo joven`

- nueva apariencia del árbol;
- nueva insignia o reto;
- acceso a nueva banda de recompensas, si procede.

## 11. Componentes Figma / React derivados

- `OliveHero`
- `LivingOliveScene`
- `LevelProgress`
- `OliveBalance`
- `LevelTimeline`
- `UnlockCard`
- `BadgeCard`
- `RewardCard`
- `RewardDetail`
- `ReservationCard`
- `RedemptionQR`
- `RedemptionHistory`
- `LevelUpOverlay`

## 12. Pantallas que Figma debe producir

### P0

- Mi Olivo Home mobile.
- Mi Olivo Home desktop.
- Evolución / niveles.
- Subida de nivel.
- Catálogo de recompensas.
- Detalle de recompensa.
- Confirmación de reserva.
- QR activo.
- QR canjeado.
- QR caducado / inválido.
- Historial de canjes.

### P1

- Detalle completo de insignia.
- Colección avanzada.
- Comparativa temporal del olivo.
- Recompensas de experiencias, no solo AOVE.

## 13. Datos que el backend debe exponer al frontend

Ejemplo conceptual:

```ts
{
  level: 3,
  levelName: "Raíces viajeras",
  xpTotal: 1250,
  xpCurrentLevelStart: 1000,
  xpNextLevel: 2000,
  olivesBalance: 500,
  visualStage: "young",
  nextUnlocks: [...],
  badges: [...],
  collections: [...],
  rewardsAvailable: [...]
}
```

No duplicar el cálculo de niveles en múltiples pantallas. Debe existir una única fuente de verdad de progresión.

## 14. Decisiones pendientes antes de producción

- Confirmar si V20.0 tendrá 10 niveles definitivos o si se desea ampliar a 12+.
- Afinar umbrales después de simular cuánto tardaría un usuario activo en progresar.
- Definir economía real de aceitunas cuando existan acuerdos/stock de almazaras.
- Definir caducidad por defecto de reservas.
- Definir límites por usuario y por campaña.
- Decidir si determinadas recompensas exigirán además un nivel mínimo.

## 15. Criterio de cierre

El sistema no se considera cerrado solo porque la interfaz muestre niveles. Debe quedar probado que:

- el XP no puede duplicarse accidentalmente;
- las aceitunas tienen ledger/historial auditable;
- stock y saldo se actualizan de forma transaccional;
- el QR no puede canjearse dos veces;
- una reserva caducada libera correctamente recursos;
- el frontend representa el mismo estado que el backend;
- Admin puede revisar y auditar operaciones.
