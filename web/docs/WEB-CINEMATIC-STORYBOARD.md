# Mágina Olivo Web — Cinematic Storyboard v0.1

> **SUPERSEDED FOR COMMERCIAL WEB V2 · 2026-09-21**  
> Este documento se conserva como historial de la V1. Para cualquier trabajo nuevo de la Home/promoción usar como fuente de verdad:
> - `WEB-V2-CINEMATIC-MASTER-SPEC.md`
> - `WEB-V2-ROADMAP.md`
>
> La V2 elimina el enfoque territorial de Sierra Mágina en la Home comercial y pasa a un enfoque product-first, visual-first y válido para el olivar de Jaén y otras zonas.


Estado: **CANONICAL DIRECTION**  
Ámbito: Home promocional  
Referencia visual: composiciones aprobadas en conversación del 20-09-2026.

## Objetivo

La Home debe sentirse como una pieza audiovisual controlada por scroll, no como una sucesión de bloques independientes.

El usuario recorre una historia continua:

`territorio → agricultor → tarea → móvil → Mágina Olivo → funciones → decisiones → territorio`

La navegación y el contenido deben permanecer accesibles aunque las animaciones estén desactivadas.

## Regla visual

Mantener:

- crema cálido;
- verde olivo y verde profundo;
- titulares serif editoriales;
- textos operativos sans;
- caligrafía solo como acento;
- Sierra Mágina / olivar como eje visual;
- persona real realizando tareas;
- móvil como puente entre mundo físico y digital;
- transiciones orgánicas y sin cortes duros.

No convertir la experiencia en una demo tecnológica agresiva. El movimiento debe acompañar la historia.

## Película de scroll — secuencia maestra

### ACTO 1 · LA TIERRA

**F01 — Apertura**  
Plano general de Sierra Mágina y olivar. Entrada lenta. Logo discreto.

**F02 — Acercamiento**  
La cámara virtual avanza hacia el olivar. Aparece el agricultor.

**F03 — Trabajo real**  
El agricultor camina entre olivos.

**F04 — Observación**  
Plano más cercano: revisa una rama / aceitunas.

**F05 — Necesidad**  
Pequeña pausa narrativa: “Tu olivar cambia cada día.”

**F06 — Decisión**  
La mano se dirige al bolsillo.

### ACTO 2 · DEL CAMPO AL MÓVIL

**F07 — El móvil aparece**  
El agricultor saca el teléfono.

**F08 — Giro a pantalla**  
La cámara cambia progresivamente hacia el móvil.

**F09 — Pantalla encendida**  
Aparece Mágina Olivo.

**F10 — Bienvenida**  
La pantalla de onboarding ocupa el protagonismo.

Transición: el teléfono pasa a posición sticky y el mundo real queda como atmósfera de fondo.

### ACTO 3 · EL PRODUCTO

**F11 — Fincas**  
Transición desde bienvenida a Fincas y parcelas.

**F12 — Profundización finca → parcelas**  
Las tarjetas parecen salir ligeramente del teléfono.

**F13 — Mapa**  
La interfaz se transforma en mapa/catastro.

**F14 — Parcela seleccionada**  
Zoom visual suave sobre geometría y superficie.

**F15 — Actividad**  
La parcela se convierte en campaña y actuaciones.

**F16 — Registro**  
Calendario, foto y checklist aparecen en capas.

**F17 — Cosecha**  
Los elementos anteriores se repliegan y aparecen producción/rendimiento.

**F18 — Documentos**  
Documentos y gastos entran como capas físicas.

**F19 — Tiempo**  
Cambio de luz y atmósfera; entra previsión.

**F20 — Mercado y alertas**  
Datos del día completan la escena.

### ACTO 4 · RESULTADO

**F21 — Móvil vuelve al contexto real**  
El teléfono deja de ocupar toda la escena.

**F22 — Agricultor continúa**  
Vuelve a mirar el olivar con la información ya consultada.

**F23 — Paisaje**  
Plano general de Sierra Mágina.

**F24 — Cierre**  
“Más que olivos, nuestra tierra.” + CTA de la app.

## Mecánica técnica prevista

- Hero a pantalla completa.
- Bloques sticky de 100svh.
- Scroll narrativo por pasos.
- IntersectionObserver para estado activo.
- Capas de teléfono construidas como UI real.
- Reemplazo progresivo de ilustraciones por fotogramas WebP/AVIF.
- Precarga únicamente de los fotogramas cercanos al viewport.
- Mobile con reducción de movimiento y número de capas.
- `prefers-reduced-motion` debe mostrar una versión estable sin pérdida de contenido.

## Fotogramas definitivos

No se generará un vídeo pesado como recurso principal.

Se producirán escenas clave y fotogramas intermedios optimizados. La implementación podrá interpolar:

- escala;
- posición;
- opacidad;
- profundidad;
- desenfoque;
- máscaras;
- movimiento de capas.

Esto da sensación de vídeo con mucho menos peso y permite detener la historia exactamente donde marque el scroll.

## Lotes de imágenes

### Lote A — Campo
A1 paisaje inicial  
A2 agricultor caminando  
A3 agricultor observando rama  
A4 detalle aceitunas/mano  
A5 saca móvil  
A6 móvil en mano

### Lote B — Transición
B1 móvil lejano  
B2 móvil medio  
B3 móvil frontal  
B4 pantalla legible

### Lote C — Producto
Las seis pantallas canónicas del onboarding:
1. Bienvenida
2. Fincas y parcelas
3. Mapa y Catastro
4. Actividad y campaña
5. Cosecha, gastos y documentos
6. Tiempo, mercado y alertas

### Lote D — Cierre
D1 agricultor + móvil  
D2 agricultor continúa trabajo  
D3 paisaje final  
D4 rama/aceitunas para CTA

## Estado de implementación

- [x] narrativa base codificada;
- [x] hero cinematográfico;
- [x] escena provisional;
- [x] móvil sticky;
- [x] seis estados funcionales del teléfono;
- [x] cambio de estado por scroll;
- [x] beneficios;
- [x] territorio;
- [x] CTA;
- [x] responsive inicial;
- [x] reducción de movimiento;
- [ ] generación de fotogramas definitivos;
- [ ] integración de imágenes reales/definitivas;
- [ ] interpolación fina entre fotogramas;
- [ ] validación visual desktop;
- [ ] validación visual Android;
- [ ] rendimiento y Core Web Vitals.

## Regla de trabajo

Primero se cierra la Home cinematográfica. Después se reutiliza su sistema visual para diseñar y construir el resto de páginas. No se rediseña la identidad entre páginas.
