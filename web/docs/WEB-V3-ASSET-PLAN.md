# WEB V3 — Asset plan

Estado: **sin assets visuales aprobados**. No se reutilizarán por defecto imágenes, vídeo, teléfono ni composición de V1/V2. Las referencias visuales de esta fase son briefs textuales y estados de review, no imágenes finales.

## Paquetes de entrega

| Paquete | Keyframes | Formatos previstos | Brief |
|---|---|---|---|
| Hero | K01 desktop, K02 mobile | AVIF/WebP + poster estático | Olivar premium a primera hora, jerarquía y espacio para titulares; móvil compuesto en vertical |
| Continuidad humana | K03–K06 | AVIF/WebP; stills de referencia | Misma persona, ropa, manos, luz y teléfono; sin stock genérico |
| Gesto/takeover | K07–K08 | Secuencia de frames o capas 2.5D | Mismo teléfono gira y toma control del encuadre; transición campo→producto |
| Producto | K09–K10 | DOM + componentes; solo assets aprobados de producto | Mi Campo y Campaña; coherente con Android; toda cifra/fixture etiquetado Demo |
| Escritorio/cierre | K11–K12 | DOM/UI y fotografía aprobada | Preview escritorio Demo/Próximamente y composición final de #394 |

## Especificación de captura/producción

- Desktop fullscreen: mínimo 1600×900; objetivo 2200–2800 px de ancho.
- Mobile: composición vertical nativa, objetivo ≥1200 px de ancho; no derivar mediante crop automático.
- Color: natural, cálido y sobrio; sin textos, logos ni interfaz quemados en fotografía.
- Protagonista: casting adulto y creíble; fijar rostro/manos, vestuario y continuidad antes de capturar la secuencia.
- Teléfono: fijar modelo visual, funda/reflejos, orientación y pantalla; UI se superpone como DOM cuando corresponda.
- Entrega: master, poster optimizado, crops desktop/mobile, crédito/licencia y hoja de continuidad.

## Nombres y admisión

Rutas destino previstas, no creadas hasta aprobar material:

- `public/images/v3/k01-hero-desktop.avif`
- `public/images/v3/k02-hero-mobile.avif`
- `public/images/v3/k03-branch-detail.avif` … `k08-product-takeover.avif`
- `public/images/v3/k11-desktop-transition.avif`, `k12-closing.avif`
- `public/images/v3/poster-desktop.avif`, `poster-mobile.avif`

Por keyframe se requiere: source/procedencia, licencia/consentimiento, composición desktop/móvil, continuidad, peso final, poster/fallback y aprobación. Un asset pasa de «pendiente» a «aprobado» solo tras revisión explícita; la página `/v3-review` no simula esa aprobación.
