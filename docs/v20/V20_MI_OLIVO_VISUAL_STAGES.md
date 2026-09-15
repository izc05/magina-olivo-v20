# Mágina Olivo V20 — Etapas visuales del olivo vivo

> Rama: `feat/v20-ui-ux-premium`
>
> Objetivo: definir cómo cambia visualmente el olivo según nivel y estado para que Figma, arte y frontend compartan una misma referencia.

## 1. Enfoque

V20.0 no necesita diez árboles completamente distintos. Se proponen **seis etapas visuales principales** y pequeños cambios entre niveles.

La escena debe sentirse viva y premium, no infantil ni fantástica. El árbol representa progreso, memoria y territorio.

## 2. Etapa A — Brote

### Niveles
- Nivel 1 · Brote

### Apariencia
- tallo pequeño;
- pocas hojas;
- terreno limpio y luminoso;
- mucha luz y espacio alrededor;
- sensación de comienzo.

### Movimiento
- balanceo mínimo;
- hojas pequeñas reaccionan al viento;
- una ligera respiración de luz.

### UI
- foco en onboarding;
- siguiente hito muy visible;
- pocas métricas para no llenar la pantalla de ceros.

## 3. Etapa B — Plantón

### Niveles
- Nivel 2 · Primeras raíces

### Apariencia
- tronco ya definido;
- primera ramificación;
- más densidad de hoja;
- raíces sugeridas visualmente mediante terreno/pedestal.

### Movimiento
- ramas secundarias con movimiento muy suave;
- alguna hoja en primer plano.

### Desbloqueos visuales
- primera insignia;
- colecciones visibles;
- transición Brote -> Plantón especialmente clara.

## 4. Etapa C — Olivo joven

### Niveles
- Nivel 3 · Raíces viajeras
- Nivel 4 · Olivo joven

### Apariencia
- árbol protagonista;
- tronco joven pero con carácter;
- copa reconocible;
- primeras aceitunas puntuales;
- fondo de Sierra Mágina más presente.

### Diferencia Nivel 3 -> 4
- Nivel 3: copa todavía abierta, pocas aceitunas.
- Nivel 4: más volumen, ramas laterales y densidad.

### Movimiento
- varias capas de copa;
- ramas principales casi quietas;
- hojas finas reaccionan al viento;
- parallax entre árbol, terreno y fondo.

### UI
- XP y aceitunas ya forman parte central del hero;
- recompensas visibles;
- acceso a evolución, colección y cuidados.

## 5. Etapa D — Olivo adulto

### Niveles
- Nivel 5 · Copa de Mágina
- Nivel 6 · Olivo de la Sierra

### Apariencia
- tronco más grueso;
- copa amplia;
- aceitunas claramente visibles;
- raíces/terreno más ricos;
- luz más cinematográfica.

### Diferencia Nivel 5 -> 6
- Nivel 5: primera copa realmente completa.
- Nivel 6: más robustez, profundidad de ramas y frutos.

### Movimiento
- profundidad 2.5D más evidente;
- hojas delanteras con movimiento independiente;
- pequeños destellos de luz entre ramas.

### UI
- visualización más rica de memoria e hitos;
- recompensas especiales;
- insignias territoriales destacadas.

## 6. Etapa E — Olivo maduro

### Niveles
- Nivel 7 · Guardián del olivar
- Nivel 8 · Olivo maduro

### Apariencia
- tronco con formas más complejas;
- mayor anchura y textura;
- copa densa;
- aceituna abundante según temporada;
- pequeñas señales de edad y carácter.

### Diferencia Nivel 7 -> 8
- Nivel 7: madurez avanzada.
- Nivel 8: árbol plenamente maduro y visualmente imponente.

### Movimiento
- muy sutil para conservar sensación de peso y edad;
- hojas y ramitas, no el tronco completo.

### UI
- menos énfasis en explicar la gamificación;
- más énfasis en historia, colección y logros acumulados.

## 7. Etapa F — Centenario / emblemático

### Niveles
- Nivel 9 · Raíces centenarias
- Nivel 10 · Leyenda de Mágina

### Apariencia
- tronco viejo, retorcido y monumental;
- gran identidad visual;
- raíces y piedra/terreno integrados;
- fondo territorial premium;
- composición reconocible incluso sin texto.

### Diferencia Nivel 9 -> 10
- Nivel 9: centenario.
- Nivel 10: versión emblemática con iluminación, composición y detalles exclusivos.

### Movimiento
- árbol estable;
- movimiento fino de hojas;
- luz y profundidad aportan vida sin parecer artificial.

### UI
- insignia de nivel máximo;
- memoria e hitos como protagonistas;
- recompensas exclusivas cuando existan.

## 8. Temporadas

Las etapas del árbol son independientes de la estación. La misma etapa puede tener variantes ambientales.

### Primavera
- luz limpia;
- brotes nuevos;
- terreno más verde.

### Verano
- luz intensa;
- tonos más secos en suelo;
- copa estable.

### Otoño / campaña
- aceituna más visible;
- tonos cálidos;
- ambientación ligada a recolección.

### Invierno
- luz fría y baja;
- ambiente más sobrio;
- no convertir el olivo en árbol caducifolio: mantener coherencia botánica general.

Las variantes estacionales no deben crear estados falsos de salud o cultivo.

## 9. Momento del día

P0 opcional si el rendimiento lo permite:

- mañana;
- tarde;
- noche suave.

La hora cambia iluminación y fondo, no la progresión del árbol.

## 10. Sistema de capas 2.5D propuesto

`LivingOliveScene`

- fondo Sierra Mágina;
- atmósfera/luz;
- terreno trasero;
- tronco/base;
- ramas/copa trasera;
- copa media;
- aceitunas;
- copa delantera;
- hojas de primer plano;
- partículas opcionales;
- sombra de contacto.

Cada capa debe poder moverse a distinta intensidad.

## 11. Reglas de movimiento

- Nunca movimiento continuo fuerte.
- El árbol debe transmitir peso real.
- Tronco prácticamente estable.
- Hojas y ramas finas son las que reaccionan.
- Evitar loops evidentes de 2–3 segundos.
- Usar variación temporal para reducir sensación artificial.
- Soportar `prefers-reduced-motion`.
- Pausar/reducir animación cuando la pestaña no está visible.
- Mobile de gama baja debe poder usar una versión simplificada.

## 12. Interacciones

### Tap / click sobre el árbol
- pequeño cambio de cámara/profundidad;
- acceso a evolución o estado;
- nunca animación exagerada.

### Subida de nivel
- transición luminosa;
- cambio gradual de densidad/etapa;
- aparición de desbloqueos;
- duración breve y skippable.

### Scroll
- parallax limitado;
- el árbol no debe dificultar lectura ni navegación.

## 13. Figma

Figma debe producir al menos:

- una variante visual por cada una de las seis etapas;
- estados de Nivel 3 y Nivel 4 especialmente detallados;
- versión mobile 390;
- versión desktop 1440;
- LevelUp overlay;
- ejemplo de primavera y otoño/campaña;
- anotaciones de qué partes serán animadas en producción.

Figma simulará movimiento mediante prototipos/Smart Animate, pero no será la implementación final de la animación.

## 14. Frontend

El frontend no decide el aspecto del árbol por texto libre. Recibe un estado estructurado, por ejemplo:

```ts
{
  visualStage: "young",
  level: 3,
  season: "autumn",
  timeOfDay: "afternoon",
  reducedMotion: false
}
```

A partir de ahí selecciona assets y parámetros de movimiento.

## 15. Rendimiento

Objetivo V20.0:

- hero rápido en móvil;
- assets WebP/AVIF cuando proceda;
- carga diferida de capas no críticas;
- evitar vídeos pesados como única solución;
- fallback estático de alta calidad;
- medir LCP y consumo real antes de activar efectos más complejos.

## 16. Criterio de cierre visual

El olivo vivo se considera listo cuando:

- se reconoce claramente la diferencia entre etapas;
- Nivel 3 `Raíces viajeras` coincide con la dirección premium aprobada;
- el movimiento parece natural y no distrae;
- funciona con reduced motion;
- mantiene rendimiento aceptable en móvil;
- el árbol sigue siendo protagonista sin tapar acciones, XP, aceitunas y recompensas;
- desktop y mobile tienen composición propia.
