# Mágina Olivo V20 — Simulación de progresión y economía

> Rama: `feat/v20-ui-ux-premium`
>
> Este documento no fija todavía la economía definitiva. Sirve para comprobar si los umbrales de XP y el coste visual de referencia de 500 aceitunas producen una progresión razonable antes de implementar backend y Figma final.

## 1. Perfiles de simulación

### Casual
Supuesto mensual aproximado:
- 1 aventura;
- 1 ruta;
- 2 descubrimientos;
- 1 contribución aprobada ocasional.

Resultado usado para simulación:
- ~460 XP/mes
- ~85 aceitunas/mes

### Habitual
Supuesto mensual aproximado:
- 2 aventuras;
- 2 rutas;
- 4 descubrimientos;
- 1 insignia/hito;
- 2 contribuciones aprobadas.

Resultado usado para simulación:
- ~1.020 XP/mes
- ~195 aceitunas/mes

### Muy activo
Supuesto mensual aproximado:
- 4 aventuras;
- 4 rutas;
- 8 descubrimientos;
- 2 insignias/hitos;
- 4 contribuciones aprobadas.

Resultado usado para simulación:
- ~2.040 XP/mes
- ~390 aceitunas/mes

## 2. Tiempo estimado para alcanzar niveles

Con los umbrales actuales:

| Nivel | XP total | Casual | Habitual | Muy activo |
|---:|---:|---:|---:|---:|
| 2 | 250 | 0,5 meses | 0,25 meses | 0,1 meses |
| 3 | 1.000 | 2,2 meses | 1,0 mes | 0,5 meses |
| 4 | 2.000 | 4,3 meses | 2,0 meses | 1,0 mes |
| 5 | 3.500 | 7,6 meses | 3,4 meses | 1,7 meses |
| 6 | 5.500 | 12,0 meses | 5,4 meses | 2,7 meses |
| 7 | 8.000 | 17,4 meses | 7,8 meses | 3,9 meses |
| 8 | 11.000 | 23,9 meses | 10,8 meses | 5,4 meses |
| 9 | 15.000 | 32,6 meses | 14,7 meses | 7,4 meses |
| 10 | 20.000 | 43,5 meses | 19,6 meses | 9,8 meses |

## 3. Lectura de la simulación

La curva parece razonable como primera base porque:

- un usuario nuevo ve progreso rápido al principio;
- `Raíces viajeras` puede alcanzarse aproximadamente en 1 mes por un usuario habitual;
- a partir de nivel 5 la progresión deja de ser inmediata;
- el nivel máximo no se alcanza en pocas semanas;
- un usuario muy activo puede llegar al máximo dentro de aproximadamente un año, mientras que uno casual tardaría varios años.

Esto debe verificarse con telemetría real cuando existan usuarios activos.

## 4. Recompensa de referencia: botella AOVE a 500 aceitunas

Tiempo aproximado para reunir 500 aceitunas si el usuario no gasta antes:

- Casual: ~5,9 meses.
- Habitual: ~2,6 meses.
- Muy activo: ~1,3 meses.

### Interpretación

500 aceitunas funciona bien como referencia de una recompensa física real si queremos que:

- no pueda conseguirse cada pocos días;
- un usuario habitual pueda aspirar a varias recompensas al año;
- el usuario muy activo siga necesitando actividad sostenida;
- el coste de producto para las almazaras siga siendo controlable mediante stock.

No debe considerarse todavía precio definitivo.

## 5. Reglas de equilibrio propuestas

- Mantener XP generoso al principio para que el usuario vea crecer su olivo pronto.
- Mantener aceitunas más escasas que XP porque tienen impacto en premios físicos.
- Recompensar más la actividad territorial validada que acciones sociales fáciles de repetir.
- Reducir o eliminar premio económico en repeticiones de una misma ruta.
- Introducir topes semanales en acciones fáciles de farmear.
- No permitir saldo negativo.
- Registrar cada movimiento de aceitunas en un ledger auditable.
- No prometer una recompensa física si no existe stock reservado.

## 6. Ajustes que podremos hacer después de Beta

Con datos reales se podrán ajustar:

- XP por aventura;
- XP por dificultad/duración;
- aceitunas por primera finalización;
- coste de botellas;
- recompensas por temporada;
- límites semanales;
- umbrales de niveles altos.

La configuración debe vivir en backend/Admin y no estar dispersa en componentes frontend.

## 7. Conclusión inicial

Para V20 Beta se propone conservar provisionalmente:

- 10 niveles;
- nivel 3 `Raíces viajeras` en 1.000 XP;
- nivel 4 en 2.000 XP;
- nivel máximo en 20.000 XP;
- botella AOVE de referencia en 500 aceitunas;
- XP y aceitunas completamente separados.

Antes de producción pública, volver a simular con el catálogo real de rutas, aventuras, campañas y recompensas disponibles.
