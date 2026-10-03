# WEB V3 — Motion timeline

Estado: propuesta para revisión del Gate V3-A; no es aún la especificación del motor de producción.

## Principios

- El progreso de scroll conduce una timeline de escenas; no se captura ni bloquea el scroll nativo.
- Canvas/image-sequence o composición 2.5D se limita a los actos fotográficos iniciales y a la transición. La UI del producto permanece en DOM.
- `requestAnimationFrame` agrupa actualizaciones; el preload carga el poster y los frames próximos, no toda la secuencia.
- Desktop y móvil disponen de composición/manifiesto propios. El fallback estático conserva copy y CTA.
- `prefers-reduced-motion: reduce` elimina secuencias y transforms: poster y escenas en flujo normal con el mismo contenido.

## Ritmo por actos

| Tramo normalizado | Keyframes | Movimiento propuesto | Copy/UI |
|---|---|---|---|
| 0–18 % | K01–K02 | Hero sticky breve; avance de cámara muy lento, overlay estable | Titular y CTA disponibles desde el primer render |
| 18–32 % | K03–K04 | Cambio de foco rama→agricultor; profundidad suave | «Lo ves.» |
| 32–47 % | K05–K06 | La mano introduce el teléfono dentro de escena; escala contenida | «Lo decides.» |
| 47–58 % | K07–K08 | Giro frontal y takeover progresivo de pantalla | «Y lo registras.» |
| 58–76 % | K09–K10 | Secciones de producto en DOM sustituyen contenido de pantalla con transiciones discretas | Mi Campo y Campaña, contenido Demo |
| 76–88 % | K11 | Apertura de escala móvil→escritorio | «Próximamente / Demo» visible y persistente |
| 88–100 % | K12 | Reducción de movimiento, cierre editorial estable | CTA aprobados por #394 |

Los porcentajes expresan orden y énfasis, no duración rígida ni scroll-jacking. Se ajustarán con el storyboard aprobado y pruebas del POC V3-B (24–40 frames).

## Carga, fallback y QA

1. Renderizar poster y copy antes de cargar imágenes secundarias.
2. Precargar solo poster, frame visible y ventana corta alrededor del progreso.
3. Cachear bitmaps con límite de memoria y descartar frames lejanos.
4. Si canvas, assets o memoria fallan, mostrar poster y contenido DOM estático.
5. Verificar scroll, estabilidad de memoria, reduced-motion y composición propia a 360, 390, 430, 768, 1366, 1440 y 1920 px durante Gate V3-B.
