# Mágina Olivo — Visual Design Lock

**Estado:** LOCKED / referencia visual oficial  
**Baseline:** RC1.2  
**Fecha de fijación:** 2026-09-18

Este documento fija la dirección visual aprobada para **Mágina Olivo**. La aplicación Android debe construirse siguiendo estas referencias. Los datos, textos dinámicos y estados funcionales pueden variar, pero la identidad, composición, jerarquía, lenguaje visual y navegación no deben reinterpretarse sin una decisión explícita que actualice este lock.

## Fuente de verdad visual

Las referencias aprobadas están guardadas directamente en `docs/design/reference/` como tableros SVG autocontenidos (las imágenes están embebidas dentro de cada archivo). Son la **fuente visual de verdad** del proyecto:

- `01-brand.svg` — identidad de marca y logo.
- `10-core-a.svg` — Inicio, Mis fincas, Mapa y Catastro.
- `11-core-b.svg` — Detalle de finca, Campaña, Registrar actuación.
- `12-core-c.svg` — Cosecha, Gastos y documentos, Tiempo y mercado.
- `20-onboarding-a.svg` — Onboarding 1/6 a 3/6.
- `21-onboarding-b.svg` — Onboarding 4/6 a 6/6.

El índice navegable está en `docs/design/reference/README.md`. Estas referencias no son inspiración: son el objetivo visual contra el que debe validarse la implementación.

Orden funcional de referencias:

1. Identidad de marca y logo.
2. Inicio.
3. Mis fincas.
4. Mapa y Catastro.
5. Detalle de finca.
6. Campaña.
7. Registrar actuación.
8. Cosecha.
9. Gastos y documentos.
10. Tiempo y mercado.
11. Onboarding 1/6 — Bienvenida.
12. Onboarding 2/6 — Fincas y parcelas.
13. Onboarding 3/6 — Mapa y Catastro.
14. Onboarding 4/6 — Actividad y campaña.
15. Onboarding 5/6 — Cosecha, gastos y documentos.
16. Onboarding 6/6 — Tiempo, mercado y alertas.

## Principios visuales no negociables

- **Territorio + tecnología:** apariencia premium pero cercana al agricultor; Sierra Mágina y el olivar son parte de la identidad, no decoración genérica.
- **Fondo claro cálido:** crema/marfil como superficie base, evitando blanco clínico dominante.
- **Verde olivo como color principal:** acciones primarias, selección, estados positivos y marca.
- **Tarjetas suaves:** radios amplios, bordes muy discretos, sombra corta y baja; nunca aspecto de dashboard empresarial frío.
- **Fotografía realista:** olivares, Sierra Mágina, parcelas y cooperativas con tratamiento natural, luminoso y no saturado.
- **Iconografía lineal y agrícola:** simple, coherente y de trazo fino/medio.
- **Tipografía:** marca y titulares editoriales con serif elegante; información operativa con sans serif muy legible. El logotipo se trata como activo de marca y no se sustituye por texto genérico.
- **Mucho aire:** márgenes generosos, jerarquía clara y densidad controlada.
- **Decoración botánica:** ramas/hojas de olivo muy suaves en fondos y esquinas, nunca compitiendo con los datos.

## Paleta base

| Token | Valor | Uso |
|---|---|---|
| `olive-primary` | `#3E5A32` | marca, CTA, selección |
| `sage` | `#A7B08F` | secundarios, fondos, estados |
| `cream` | `#F8F6EE` | fondo principal |
| `earth` | `#B88B6B` | territorio / acentos cálidos |
| `soft-gold` | `#D4B76A` | valor, mercado, destacados |
| `ink` | `#173122` | texto principal oscuro |

Los valores se consideran punto de partida de implementación. Antes de cerrar UI se deben muestrear/ajustar contra las referencias para igualar el resultado visual.

## Estructura de navegación fijada

Barra inferior de **cinco destinos** con la misma filosofía visual:

`Inicio · Fincas · Mapa · Actividad/Tiempo y mercado según contexto final de IA · Perfil`

La arquitectura funcional RC1.2 decide el destino exacto cuando exista conflicto de nomenclatura; el patrón visual de la barra inferior no cambia.

## Pantalla Inicio

Debe conservar la composición de la referencia: marca + saludo, bloque territorial/fotográfico, tiempo, resumen de campaña, accesos rápidos, mercado AOVE/Virgen/Lampante, actualidad/cooperativa y navegación inferior. El objetivo es que un agricultor entienda su situación en segundos.

## Fincas y detalle

- Una finca tiene nombre propio e imagen de portada.
- Una finca contiene múltiples parcelas.
- Las tarjetas muestran superficie, nº de parcelas, variedad/campaña y estado sin sobrecarga.
- El detalle mantiene hero fotográfico, KPIs, acceso al mapa, nueva actuación, documentos y listado de parcelas.

## Mapa y Catastro

Referencia visual obligatoria: ortofoto/satélite a pantalla amplia, límites de parcelas en blanco, parcela seleccionada en verde translúcido y ficha inferior. Deben mantenerse buscador, capas, localización, zoom e importación/dibujo de parcela. La geometría real procederá del modelo GIS/Catastro; la imagen de referencia no sustituye datos cartográficos reales.

## Campaña, cosecha y histórico

Las campañas deben mostrar datos actuales e histórico con tarjetas KPI, comparativas visuales, fechas clave, kilos, rendimiento, entregas y evolución. Las campañas anteriores deben poder consultarse rápidamente manteniendo la misma gramática visual.

## Actividad, gastos y documentos

Los formularios usarán bloques grandes y táctiles, selectores claros, fotos y CTA principal verde. Gastos/documentos mantienen resumen, categorías y lista de archivos. Debe evitarse convertir estas vistas en formularios densos tipo ERP.

## Tiempo, mercado y alertas

Debe combinar previsión, radar/precipitación, alertas agrícolas, precios separados de **AOVE, Virgen y Lampante** y noticias/avisos. Las gráficas y tarjetas deben seguir el mismo lenguaje de Inicio.

## Onboarding fijado — 6 pantallas

1. **Bienvenida:** qué es Mágina Olivo y conexión territorial.
2. **Tus fincas y parcelas:** organización finca → parcelas → campañas.
3. **Mapa y Catastro:** localizar/importar/dibujar parcelas.
4. **Actividad y campaña:** registrar actuaciones, fotos, costes y fechas.
5. **Cosecha, gastos y documentos:** kilos, entregas, rendimiento y documentación.
6. **Tiempo, mercado y alertas:** previsión, radar, precios y avisos; CTA final `Comenzar`.

Debe conservarse el botón `Saltar`, progreso inferior, CTA verde y composición editorial/fotográfica.

## Regla de implementación

Toda pantalla nueva debe demostrar compatibilidad con este lock. Si un desarrollo introduce un nuevo componente, primero debe reutilizar tokens, espaciado, radios, tipografías, iconografía y patrones existentes. **No se permite crear un segundo lenguaje visual paralelo.**

## Gate visual antes de aceptar una pantalla

Una pantalla no se considera terminada hasta comprobarla junto a su referencia y validar: composición, jerarquía, espaciado, color, tipografía, iconos, radios, elevación, fotografía, estados y comportamiento móvil. El objetivo es una reproducción visual deliberada, no una reinterpretación genérica.
