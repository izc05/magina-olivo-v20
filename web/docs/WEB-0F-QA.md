# WEB-0F · Auditoría de calidad y accesibilidad

Fecha de revisión: 5 de octubre de 2026  
Alcance: rutas públicas y vistas de demostración `/mi` de Web V3, conforme a WEB-0F (#392) y al Visual Lock (#394).

## Resultado

- Las rutas públicas y privadas incluidas en la baseline responden sin 404 y sin overflow horizontal en las pruebas E2E.
- La Home pública mantiene crema y verde olivo, fotografía editorial y producto como protagonista. El dashboard conserva sidebar, topbar, KPIs y paneles preparados con datos marcados como Demo.
- El acceso por teclado empieza con un enlace visible para saltar al contenido en Home y Mi. La navegación móvil se puede abrir con teclado, los enlaces de navegación indican la página actual y los controles táctiles comprobados alcanzan 48 px de alto.
- La vista privada lleva `noindex,nofollow` y no recibe canonical. La configuración productiva revisada puede indexar páginas públicas y generar sitemap; el preview y la exportación de GitHub Pages siguen cerrados a indexación.
- No se usaron servicios externos ni datos de usuarios. Todo dato del área privada se identifica como demostración.

## Matriz de viewport

Home (`/`) y panel privado (`/mi`) comprobados en Chromium a:

| Viewport | Home | Mi | Overflow horizontal |
| --- | --- | --- | --- |
| 360 × 800 | OK | OK | 0 px |
| 390 × 844 | OK | OK | 0 px |
| 430 × 932 | OK | OK | 0 px |
| 768 × 1024 | OK | OK | 0 px |
| 1366 × 768 | OK | OK | 0 px |
| 1440 × 900 | OK | OK | 0 px |
| 1920 × 1080 | OK | OK | 0 px |

El test de matriz comprueba respuesta 200, título principal visible y `scrollWidth` dentro del ancho del viewport. La suite también visita cada ruta pública y cada vista privada preparada tanto en los proyectos Chromium de escritorio como móvil.

## Accesibilidad y movimiento

- El enlace “Saltar al contenido” se enfoca primero, se hace visible al recibir foco y enfoca el `main` correspondiente.
- Menú privado móvil: se abre con Enter desde el teclado y conserva navegación semántica.
- `aria-current="page"` marca la página activa en la barra lateral.
- Los destinos táctiles visibles comprobados de navegación, CTA y pie público, y el menú privado móvil tienen un mínimo de 48 px de alto.
- La prueba verifica contraste WCAG AA de texto atenuado del menú y contraste mínimo 3:1 para el indicador de foco contra el fondo crema. No es una auditoría completa de todos los pares de color.
- La prueba de Home verifica que la imagen hero carga y que el modo `prefers-reduced-motion: reduce` no oculta ni rompe el contenido.

No se ejecutó una auditoría con lector de pantalla ni una exploración exhaustiva automatizada de todos los componentes.

## SEO, metadatos y rutas

- Prueba de configuración con `ALLOW_INDEXING=true` y `SITE_URL` de comprobación: metadatos `index,follow`, canonical de Home, `robots.txt` con Allow, sitemap con rutas públicas y sin `/mi`; Mi conserva `noindex,nofollow` y carece de canonical.
- Exportación Pages: manifiesto con `start_url` y `scope` bajo `/magina-olivo-v20/`, iconos de marca cuadrados de 192 × 192 y 512 × 512, Open Graph con URL absoluta, sitemap sin rutas y `robots.txt` con bloqueo de indexación.
- Las comprobaciones E2E visitan las rutas públicas y privadas preparadas; además validan el endpoint `/api/health` en modo servidor.

## Rendimiento local

Mediciones de una compilación de producción servida en local, Chromium, viewport 1440 × 900, URL local, el 5 de octubre de 2026:

| Medida | Resultado | Contexto |
| --- | ---: | --- |
| LCP | 1.356 ms | Captura local de producción; no es dato de usuarios reales. |
| CLS | 0 | En la misma carga observada. |
| Transferencia de recursos tras `networkidle` | 1.102.856 bytes | Incluye imágenes editoriales cargadas en las secciones inferiores y datos de navegación precargados. No equivale a transferencia estricta del primer viewport. |
| CSS principal | 51.514 bytes | Tamaño sin comprimir; transferencia observada de 11.006 bytes. |
| JavaScript inicial observado | ~155 KB transferidos | Suma aproximada de recursos script del documento; no incluye código de navegación precargado. |
| Heap JS observado | ~10 MB | Muestreo puntual de Chromium, variable según ejecución. |
| Interacción sintética | 16 ms | Click de prueba local medido con Event Timing; no representa INP de campo. |

El hero se solicita como preload. La vista Home actual usa una imagen fija: no tiene canvas ni secuencia de frames (`canvasCount=0`, `frameCount=0`); por tanto, el uso de memoria de una secuencia no se pudo medir.

## Hallazgo que mantiene abierta la puerta de calidad

La transición cinematográfica aprobada de campo → móvil → producto y su movimiento scroll → frame/canvas todavía no están implementados en la Home. La página revisada es estática. No se inventan fotogramas ni se disfraza esta ausencia como resultado aprobado. Se debe resolver dentro de WEB-0C/#389 y volver a ejecutar las pruebas de scroll, precarga, memoria y `reduced-motion` antes de dar WEB-0 por terminado.

## Evidencia visual

- [Home escritorio · 1440 px](screenshots/web-0f-home-desktop.png)
- [Home móvil · 390 px](screenshots/web-0f-home-mobile.png)
- [Mi escritorio · 1440 px](screenshots/web-0f-mi-desktop.png)
- [Mi móvil · 390 px](screenshots/web-0f-mi-mobile.png)
- [Foco de teclado en Mi](screenshots/web-0f-mi-focus.png)

Las capturas se tomaron en servidor local de producción sin el overlay de desarrollo de Next.js.
## Ampliación de cobertura reduced-motion — 2026-10-08

`tests/reduced-motion.spec.ts` comprueba Home y `/mi` en los dos proyectos Chromium. Con la preferencia reduce, los elementos visibles deben tener duraciones calculadas de animación/transición <=0,02 ms y no usar scroll suave. Se comprueba además el destino del skip link. Es una verificación de la política CSS actual, sin afirmar que exista o esté validada una secuencia cinematográfica. Cuando #389 tenga motor/frames, añadir cobertura de pausa, selección de fallback y ausencia de precarga innecesaria.
