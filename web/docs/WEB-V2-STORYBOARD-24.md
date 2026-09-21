# Mágina Olivo Web V2 — Storyboard 24 Keyframes

Estado: **G2 DRAFT / READY FOR VISUAL PRODUCTION**  
Fecha: 2026-09-21  
Fuente de verdad:
- `WEB-V2-CINEMATIC-MASTER-SPEC.md`
- `WEB-V2-ART-DIRECTION.md`

## Objetivo

Convertir la Home en una historia continua controlada por scroll.

Narrativa:

`olivar → observación → móvil → Mágina Olivo → funciones → regreso al campo → cierre`

La historia debe entenderse aunque el usuario no lea ningún párrafo largo.

---

## ACTO 1 · EL CAMPO

### K01 — Hero
Plano general/medio del agricultor en el olivar.

Copy:
**Tu olivar en buenas manos.**

Duración aproximada:
0–9 % del scroll narrativo.

### K02 — Caminar
El agricultor avanza entre olivos.

Copy:
**Tu día empieza aquí.**

### K03 — Mirar
Se acerca a una rama.

Copy:
**Mira.**

### K04 — Mano
Primer plano de mano y aceitunas.

Copy:
**Cada detalle importa.**

### K05 — Decidir
El agricultor observa la parcela/árbol.

Copy:
**Decide.**

### K06 — Pausa
Plano corto sin texto durante un pequeño tramo.

Objetivo:
dar respiración antes del móvil.

---

## ACTO 2 · DEL CAMPO AL MÓVIL

### K07 — Mano al bolsillo
Comienza el gesto de sacar el teléfono.

Copy:
**Registra.**

### K08 — Teléfono aparece
Móvil parcialmente visible.

### K09 — Móvil en mano
El teléfono ya está en contexto real.

Copy:
**Todo en tu mano.**

### K10 — Acercamiento
La cámara/encuadre se acerca al dispositivo.

### K11 — Giro
El móvil pasa de ángulo natural a casi frontal.

### K12 — Pantalla
La pantalla ocupa gran parte de la escena.

Copy:
**Mágina Olivo.**

---

## ACTO 3 · PRODUCTO

### K13 — Inicio
UI real de Inicio.

Copy:
**Todo tu olivar.**

### K14 — Fincas
Transición a lista/resumen de fincas.

Copy:
**Tus fincas.**

### K15 — Finca
Entrada a una finca.

Copy:
**Todo empieza por saber qué tienes.**

### K16 — Parcelas
Vista de parcelas.

Copy:
**Tus parcelas.**

### K17 — Mapa
Mapa/Catastro.

Copy:
**Tu tierra, localizada.**

### K18 — Campaña
Resumen de campaña/actuaciones.

Copy:
**Tu campaña.**

### K19 — Cosecha
Producción, entregas, rendimiento.

Copy:
**Tu cosecha.**

### K20 — Gastos
Gastos/documentos.

Copy:
**Tus números.**

### K21 — Tiempo
Tiempo/avisos.

Copy:
**Decide con contexto.**

### K22 — Histórico
Comparativa campañas.

Copy:
**Aprende de cada campaña.**

---

## ACTO 4 · REGRESO

### K23 — Móvil vuelve al campo
El dispositivo pierde protagonismo y vuelve a la mano.

Copy:
**Menos papeles. Más control.**

### K24 — Cierre
Agricultor continúa su trabajo / plano amplio.

Copy principal:
**Todo tu olivar. En un solo lugar.**

CTA:
**Conocer Mágina Olivo**

---

# Timeline recomendado

## Secuencia A — Campo
K01–K06  
25 % del recorrido.

## Secuencia B — Campo → móvil
K07–K12  
25 % del recorrido.

## Secuencia C — Producto
K13–K22  
35 % del recorrido.

## Secuencia D — Regreso/cierre
K23–K24  
15 % del recorrido.

---

# Frames intermedios

Los 24 keyframes NO son los frames finales.

Entre ellos se generarán/interpolarán imágenes hasta alcanzar aproximadamente:

### Desktop
- secuencia campo/móvil: 120–180 frames;
- producto: preferentemente UI DOM/canvas, no 100 imágenes rasterizadas.

### Mobile
- secuencia campo/móvil: 70–110 frames;
- composición vertical específica.

Ejemplo:

`K01 → 12 frames → K02 → 10 frames → K03 ...`

La densidad de frames aumenta en:
- manos;
- salida del móvil;
- giro del teléfono.

Puede reducirse en:
- planos generales;
- pausas;
- producto UI.

---

# Continuidad visual obligatoria

Bloquear durante K01–K12 y K23–K24:

- mismo agricultor;
- misma ropa;
- misma gorra/ausencia de gorra;
- mismo teléfono;
- misma hora de luz;
- mismo tratamiento de color;
- manos anatómicamente coherentes;
- orientación espacial comprensible.

Si una generación rompe continuidad, se rechaza aunque individualmente sea bonita.

---

# Desktop / Mobile

## Desktop
- copy principalmente a izquierda;
- agricultor centro/derecha;
- móvil puede ocupar hasta 55–70 % de alto al acercarse;
- horizonte disponible para profundidad.

## Mobile
- copy corto en zona segura;
- personaje más centrado;
- teléfono más grande antes;
- eliminar elementos secundarios;
- no recortar manos;
- no reutilizar simplemente crop del desktop.

---

# Copy máximo

Durante la película:
- 2–5 palabras preferidas;
- máximo 8 palabras;
- una sola idea por escena.

No poner párrafos sobre la secuencia.

---

# Gate G2 — criterio de cierre

G2 puede cerrarse cuando:

- [x] narrativa de 24 keyframes definida;
- [x] copy por escena definido;
- [x] timeline porcentual definido;
- [x] desktop/mobile definidos conceptualmente;
- [ ] K01 desktop aprobado como asset real;
- [ ] K01 mobile aprobado;
- [ ] K09 móvil en mano aprobado;
- [ ] K12 móvil frontal aprobado;
- [ ] K13 UI Inicio aprobada;
- [ ] K24 cierre aprobado.

Después de aprobar esos keyframes visuales se puede cerrar G2 y entrar formalmente en G3.
