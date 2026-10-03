# WEB V3 — Matriz de recuperación selectiva

Estado: auditoría WEB-0A (#387), 2026-10-03

Base propuesta para WEB-0B: `main` en `a9b86844e779edbd8e3bec411868342813208b69`

Contrato visual vinculante: issue [#394](https://github.com/izc05/magina-olivo-v20/issues/394)

## Decisión

La nueva aplicación se crea en `/web` desde `main`. De V20/#1, Web V1/#202 y Web V2/#204 se recuperan técnicas y piezas de infraestructura después de portarlas de forma explícita. No se fusiona ninguna rama histórica ni se hereda su composición visual.

La referencia visual de cualquier implementación es #394: Home crema y verde con fotografía premium, composición editorial y hero cinematográfico, transición campo → móvil → producto; después del acceso, dashboard con sidebar a la izquierda y los módulos descritos en #394. No se cambia esa jerarquía ni se propone aquí un diseño alternativo. Los prototipos históricos no autorizan estética, datos o funciones adicionales.

**KEEP** significa conservar una técnica, contrato o configuración útil como referencia de portabilidad; no incorporar automáticamente su implementación. **ADAPT** significa portar solo tras recortar, validar y alinear con #394. **DROP** significa no llevarlo a la nueva baseline.

## Fuentes auditadas

| Fuente | Revisión fijada | Hallazgo relevante |
|---|---|---|
| Issue #395 | Coordinación Android/Web | Codex trabaja solo en `/web`, docs web y CI/preview web aislados. No tocar Android, Room, Supabase productivo, Auth real, RLS ni Sync. |
| Issue #386 | Master WEB-0 | Baseline nueva desde `main`; fases #387–#393; fixtures demo hasta WEB-1; Pages, Docker, Playwright, CI y reduced motion forman parte del stack a recuperar. |
| Issue #394 | Visual Lock aprobado | Especifica Home pública, sidebar y panel privado. Es la única referencia visual que se debe implementar. |
| Issue #387 | WEB-0A | Pide KEEP/ADAPT/DROP, inventario de assets/componentes/rutas y recomendación para la baseline. |
| PR #1 | `99fcd6ed3eb3a6491467210fe8c45737b0c94794`, `feat/v20-visual-prototype` | PWA/App Router y prototipo de portal territorial; incluye lógica y pantallas de producto que no se copian a V3. |
| PR #202 | `ba672818bd18f1cf868d1380eceed9400d0e6d69`, `feat/web-magína-olivo` | Next 16, TypeScript, Tailwind, rutas públicas, Docker, Playwright, CI y Pages; Home y assets con dirección V1. |
| PR #204 | `7ec86636fe146f7a9b3184000ad6f2b9e9abeb01`, `feat/web-v2-cinematic` | Motor scroll→frame/canvas, manifiesto de keyframes, review board y specs cinematográficas; dirección visual V2 sustituida por #394. |

La PR #1 contiene una aplicación amplia (`apps/web`, API, autenticación, datos, GIS y muchos flujos), por lo que se revisaron su dirección visual, Home, ruta Explorar, package y workflow Pages más el inventario de rutas/assets. No se trasladó ni se auditó funcionalmente cada flujo de ese producto: está fuera del propósito de recuperación web y varias áreas están expresamente excluidas por #395.

## Matriz KEEP / ADAPT / DROP

| Archivo, componente, asset o grupo | Origen | Decisión | Motivo | Dependencias | Riesgo / control | Destino nuevo |
|---|---|---|---|---|---|---|
| Next.js, React, App Router, TypeScript | #1, #202, #204 | **KEEP** | Stack adecuado y probado para la superficie independiente `/web`. | Node y gestor de paquetes fijados por baseline. | No copiar versiones viejas sin validar contra `main` y CI vigente. | `web/package.json`, `web/src/app/` nuevos. |
| Tailwind CSS | #202, #204 | **KEEP** | Utilidad de estilos y build integrada. | PostCSS/Tailwind compatibles con Next. | Tokens y CSS de V1/V2 no fijan la apariencia de V3. | Configuración mínima nueva de `/web`. |
| Typecheck, build Next y CI web aislada por rutas | #202, #204 | **KEEP** | Gates repetibles de calidad sin implicar cambios Android. | Workflow filtrado a `web/**` y su propio fichero. | No editar workflow global Android ni usar `npm install` no reproducible si existe lockfile. | `.github/workflows/web.yml` en PR separada/scope web. |
| Playwright, Chromium, smoke/responsive y evidencia | #202, #204 | **KEEP** | Comprueba navegación y comportamiento escritorio/móvil; útil para Home y shell. | Proyecto Playwright y servidor base URL. | Selectores acoplados a `.v2-*`, rutas antiguas y copy V2 deben reemplazarse. | `web/playwright.config.ts`, `web/tests/` nuevos. |
| Docker multi-stage, standalone, healthcheck | #202 | **KEEP** | Camino reproducible de despliegue/previsualización. | Next standalone, endpoint de salud del propio web. | Mantener aislado; comprobar build context/variables sin secretos. | `web/Dockerfile`, `.dockerignore`, compose de staging si aplica. |
| GitHub Pages static preview y `noindex` | #1, #202 | **KEEP** | Preview útil y ya desplegada históricamente. | `output: export`, base path, permisos Pages. | El workflow histórico de PR #1 seguía su rama; debe activarse para la rama/flujo actual y preservar noindex. | `.github/workflows/web-pages-preview.yml` (o job aislado de web CI). |
| SEO técnico: metadata, canonical, robots, sitemap, OG, manifest | #1, #202 | **ADAPT** | Mantener las capacidades públicas y actualizarlas a rutas y dominio aprobados. | App Router metadata y URL de sitio. | Canonicidad de producción no definida por estos prototipos; preview debe seguir fuera de índice. | `web/src/app/layout.tsx`, `robots.ts`, `sitemap.ts` nuevos. |
| Security headers y 404/health route | #202 | **ADAPT** | Controles y endpoint sencillos son portables. | Config Next/headers y runtime propio. | Revalidar CSP/headers con assets y build reales; health no debe exponer configuración. | `web/next.config.ts`, route health nueva. |
| Patrón `prefers-reduced-motion`, fallback estático y copy fuera del canvas | #202, #204 | **KEEP** | Requisito explícito de accesibilidad y resiliencia. | CSS media query y lógica de animación. | No basta con ocultar canvas; contenido/CTA deben seguir disponibles. | Componentes nuevos de Home y pruebas Playwright. |
| Motor canvas `scroll → frame`, `requestAnimationFrame`, progreso acotado | #204 `CinematicScrollCanvas.tsx` | **ADAPT** | Mecanismo validado para el hero cinematográfico exigido por #394. | Canvas, scroll/resize observer, carga de imagen. | El componente actual contiene supuestos, nombres y estructura de escenas V2; portar algoritmo aislado y probar reduced-motion, móvil y memoria. | Motor pequeño nuevo en `web/src/components/` durante #389. |
| Precarga progresiva y cache de imágenes alrededor del frame | #204 specs/spike; #202 `SceneImage` | **ADAPT** | Poster inmediato, precarga cercana y manejo de fallo son patrones válidos. | Manifiesto desktop/móvil y límite de concurrencia. | No descargar toda la secuencia al entrar; establecer presupuesto y medir antes de ampliar. | `web/src/lib/` o hook del motor; especificar en #388/#389. |
| Separación de manifiestos y composición desktop/móvil | #204 `cinematicMedia.ts`, `keyframes.ts` | **ADAPT** | Evita recortes deficientes y hace trazables los frames. | Assets de origen aprobados y claves de escena nuevas. | Keyframes, focales y cantidad actuales describen la película V2; ningún frame se aprueba por herencia. | Nuevo manifiesto tras aprobación de assets V3. |
| Review board de keyframes `/v2-review` | #204 `src/app/v2-review/page.tsx` | **ADAPT** | Herramienta interna útil para comparar secuencia y detectar continuidad. | Datos de keyframes y assets cargables. | No publicar/indexar como página de producto; actualizar labels, escenas y control de acceso/visibilidad acorde a preview. | Ruta interna de revisión en `/web` con `noindex`, solo si #389 la necesita. |
| Next config, lockfile, package scripts, TS/PostCSS config | #202 | **ADAPT** | Base de configuración práctica. | Versiones, npm scripts, static export y standalone. | La configuración alterna entre export y standalone; validar compatibilidad antes de portarla. | Archivos equivalentes nuevos bajo `/web`; generar lockfile en #388. |
| V1 layout/footer/header y componentes `SiteHeader`, `SiteFooter`, `MarketingPage`, `LegalPage`, `OliveDecor` | #202 | **ADAPT** | Algunas responsabilidades estructurales (nav, footer, legal) son reutilizables. | CSS V1, enlaces, rutas y tokens. | DOM/CSS contiene dirección V1 y territorio; extraer estructura semántica, implementar visual #394. | Componentes nuevos pequeños en `web/src/components/`. |
| V1 rutas `/producto`, `/beneficios`, `/territorio`, `/contacto`, legales | #202 | **ADAPT** | Inventario inicial de contenido público, no contrato visual final. | Contenido real y taxonomía de #386/#394. | Las rutas/copy no deben añadir funciones ni omitir páginas canónicas; territorios se separan de núcleo de producto. | Rutas nuevas en la estructura acordada por #386; port legal solo tras revisar contenido. |
| V1 `WEB-ASSET-MANIFEST`, `WEB-CINEMATIC-STORYBOARD`, `WEB-MASTER-SPEC`, `WEB-MOTION-SPEC` | #202 | **DROP** como autoridad | Fueron fuente V1, algunas sustituidas por V2 y todas preceden al Visual Lock #394. | No aplicable. | Reutilizarlas como guía visual contradice #394. | No portar a baseline; mantener solo como historial de PR. |
| V1 `hero-farmer.webp`, `hero-farmer-panel.webp`, `heritage-farmer.webp`, `phone-in-hand.webp`, `benefits-olives.webp`, `territory-intro.webp`, `territory-final.webp`, `cta-olive-branches.webp`, `hero-scene.svg` | #202 `web/public/media/home/`, `web/public/brand/` | **DROP** como assets aprobados; volver a evaluar archivo por archivo | Etiquetas `final` y descripción alt no acreditan licencia, resolución, continuidad ni encaje exacto con #394. | Fuente/licencia, dimensiones, composición desktop/móvil. | Evita fotografía no premium/territorial heredada. Ninguna foto existente queda aprobada en WEB-0A. | Reemplazar con selección aprobada para #394; solo portar si pasa asset gate y lock visual. |
| Visual reference `landing-master-reference.jpg` | #202 | **DROP** como referencia | Es referencia de V1 y no Visual Lock actual. | Ninguna. | Puede inducir a restaurar una composición antigua. | No copiar a `/web`; #394 manda. |
| `CinematicHome.tsx`, `FieldToPhoneSequence.tsx`, `SceneImage.tsx` y CSS global V1 | #202 | **DROP** como componentes completos | Contienen capas, copy, escena, teléfono y tokens V1 ya compuestos. | V1 CSS y `visualAssets`. | Portarlos completos reproduciría estilo viejo y mocks de teléfono. | Reimplementar semántica/efectos necesarios en componentes compatibles con #394. |
| Storyboard, Art Direction, Continuity Bible, master spec V2 | #204 docs `WEB-V2-*` | **DROP** como contrato de diseño | Fueron canónicos para V2; #394 fija otra composición, copy, jerarquía y capa territorial. | Ninguna. | Usar su secuencia K01–K24 o copy como obligación reinterpreta el lock. | Mantener como archivo de referencia histórica, no copiar como spec activo. |
| Secuencia y keyframes K01–K24, `CinematicHomeV2`, takeover del teléfono, scrub de vídeo | #204 | **DROP** como contenido/implementación | Son decisiones creativas y de storyboard de V2; la técnica canvas se rescata por separado. | Fotos, vídeo y copy V2. | Puede encadenar producto/funciones en orden no aprobado y aumentar peso de carga. | Crear historia V3 conforme a #394; no portar componente ni frames. |
| `hero-keyframe-01.webp`, `hero-photo-clean.webp`, logos `v2-lockup.svg`, `v2-mark.svg` | #204 | **DROP** como assets aprobados; solo reconsiderar bajo revisión | La propia documentación los declara candidatos G1, no finales; las fotos de origen se describen con dimensiones insuficientes para fullscreen. | Reproducción/fuente y composición requerida. | Assets pobres o no aprobados degradan hero y continuidad. | Sustituir por fotografía premium aprobada para #394; no portar ahora. |
| URL `/v2-review`, smoke selectors `.v2-*`, ocho pasos y hero copy V2 | #204 | **DROP** del producto; adaptar únicamente el banco interno | Rutas y pruebas validan una Home que #394 reemplaza. | Playwright. | Mantenerlas haría pasar CI a una composición obsoleta. | Nuevas pruebas con semántica, rutas y escenas #394; revisión interna opcional separada. |
| PWA App Shell, `BottomNav`, `Topbar`, `HomeDailyCenter`, `demoContext`, `WeatherHero`, `MyFieldSummary`, `FarmCard`, flujos `/mi-campo/*`, radar y perfil | #1 `apps/web` | **DROP** como implementación para V3 | Son UI de aplicación/prototipo anterior, navegan con jerarquía móvil propia y mezclan superficies pública/privada. | PWA, datos/demo, lógica de dominio y posiblemente API/Auth. | Riesgo alto de arrastrar Auth real, datos locales, lógica duplicada o pantallas Android obsoletas. | Diseñar `/mi` desde cero en WEB-0E según sidebar y composición exacta #394. |
| `apps/web/src/lib/*` data sources, demo stores, domain, API client, Auth providers, GIS/weather adapters | #1 | **DROP** | #395 excluye Auth/Sync/Room/Supabase productivo y #386 fija fixtures explícitos con adapters de UI. | Backend, modelo Android, credenciales o APIs. | Acoplamiento o duplicación de dominio; no reutilizar en la nueva baseline. | Fixtures marcados Demo y contratos de lectura nuevos solo en fases #391/#393. |
| Rutas `/explorar`, `/mi-campo`, `/radar`, `/perfil`, `/documento-publico`, formularios OCR/profesional | #1 | **DROP** como rutas/código heredado | No coinciden con la arquitectura pública/privada de #386 ni la lista canónica de #394. | Componentes y stores de #1. | Reintroducen navegación/páginas no aprobadas o funcionalidades que no deben abrirse. | No portar. Crear solo las rutas nombradas por #386/#394. |
| Conceptos SVG `hero-huelma-concept.svg`, `farm-las-cenillas-concept.svg`, `delivery-ticket-concept.svg`, `olive-sprig.svg`, `app-icon.svg` | #1 `apps/web/public/assets/` | **DROP** como imagen/fuente visual; conservar archivo solo si identidad/uso se valida | Ilustraciones conceptuales, lugar/entidad concretos e iconografía previos. | Licencia y revisión de marca. | No afirmar que son fotografías premium ni que representan datos geográficos. | Reemplazar hero por fotografía; evaluar logo/icono solo contra marca y #394. |
| Service worker/offline/cache PWA y dashboard municipality/GPS del prototipo #1 | #1 | **DROP** | Comportamiento PWA y personalización local no está en el alcance visual WEB-0 ni habilitado por contrato aquí. | APIs de geolocalización, cache y datos locales. | Puede comunicar funciones/datos no aprobados. | Nada en baseline WEB-0. |
| Workflows `visual-prototype-check.yml`, workflows generalistas de PR #1 | #1 | **DROP** | Cubren el sistema amplio `apps/web`, API, GIS y otros dominios, no un CI aislado de `/web`. | Monorepo V20, pnpm, base de datos/servicios. | Ejecutarlos o adaptarlos directamente puede tocar Android u otros carriles. | Crear gates estrechos `web/**` desde requisitos de #386. |
| Workflow de preview V1/V2 y workflow Docker/Pages combinados | #1, #202 | **ADAPT** | El despliegue se puede conservar, simplificando triggers y limitando paths. | GitHub Pages environment y build estático. | No desplegar desde PR no aprobada ni permitir indexación del preview. | Workflow web independiente; cambios globales en PR pequeña web separada según #395. |
| Home pública territorial de PR #1 y directorio local | #1 | **DROP** como composición | Sitúa clima, municipio, actualidad, turismo y comunidad antes del producto; #394 exige hero editorial y recorrido de producto, con territorio como capa separada. | Copy/fotos/contenido municipal. | Territorialización excesiva y alcance comercial limitado. | Home #394; contenido cooperativas/empresas/noticias/eventos solo en el bloque de territorio aprobado. |

## Inventario de assets y regla de aprobación

| Grupo histórico | Estado para portar |
|---|---|
| #1 concept art SVG de Huelma, finca Las Cenillas, recibo y rama | Ninguno aprobado como fotografía o hero. `app-icon.svg`/`olive-sprig.svg` solo se podrían reutilizar tras comprobar marca, tamaño y encaje; no son dependencias de baseline. |
| #202 ocho assets en `web/public/media/home/` y `brand/hero-scene.svg` | Todos requieren reemplazo o inspección visual/licencia/resolución contra #394. No están aprobados para V3. `visualAssets.ts` clasifica varios como `final`, pero esa etiqueta solo refleja V1. |
| #202 `landing-master-reference.jpg` | Referencia visual descartada para diseño. |
| #204 `hero-keyframe-01.webp`, `hero-photo-clean.webp` y lockups V2 | Candidatos históricos, no final. El gate V2 exige mínimo 1600×900 para desktop fullscreen y composición móvil propia; su propia documentación identifica imágenes pequeñas/candidatas. Reemplazar para el hero V3 salvo nueva verificación completa y aprobación. |
| Frames externos Pexels y vídeo 4K referidos en storyboard/tests V2 | No copiar ni cargar por defecto; revisar licencia, encuadre, disponibilidad, peso y continuidad. El requerimiento premium de #394 gobierna la selección. |
| Screenshots/mockups embebidos en el teléfono V1/V2 | No aprobados como UI real; usar representaciones fieles solo cuando coincidan con la app vigente y con la especificación de #394. Datos de cualquier mock deben estar marcados Demo donde corresponda. |

**Conclusión de assets:** WEB-0A no valida visualmente ningún asset fotográfico histórico para producción. En #389 se debe seleccionar o producir hero y visuales que pasen el contraste con #394, licencia, dimensiones, composición desktop/móvil, accesibilidad y carga. Conservar un archivo solo por comodidad no constituye aprobación.

**Assets válidos para portar ahora:** ninguno de los assets de marca/fotografía de #1/#202/#204 queda aprobado en esta auditoría. Los candidatos iconográficos listados arriba solo podrían portarse después de revisión de marca; no bloquean el scaffold ni justifican importarlos.

## Rutas antiguas: retirar, renombrar o sustituir

| Origen | Acción para la baseline |
|---|---|
| #1 `/`, `/explorar`, `/mi-campo/*`, `/radar`, `/perfil`, `/documento-publico` y flujos OCR/profesional | No portar rutas ni sus componentes. Definir las rutas públicas y `/mi` exclusivamente desde #386, con la estructura visual privada de #394. |
| #202 `/`, `/producto`, `/beneficios`, `/territorio`, `/contacto`, `/privacidad`, `/terminos`, `/aviso-legal` | Reutilizar solo el contenido legal/informativo tras revisión; mapear nombres y navegación al inventario actual #386. No mantener página vieja duplicada con UI V1. |
| #204 `/v2-review` | No exponer/indexar como producto. Si se necesita, crear herramienta de QA de keyframes claramente interna, `noindex`, contra manifiesto nuevo. |
| Todas las rutas privadas o de auth antiguas | No conectar Auth ni adaptar lógica. En fases de UI usar fixtures demo y contratos reemplazables según #386. |

## Archivos a portar a la nueva baseline

Portar de manera individual y con revisión, nunca copiando una rama completa:

1. **Scaffold (#388):** nuevos `web/package.json`, lockfile, `tsconfig.json`, `next.config.ts`, `postcss.config.mjs`, App Router y scripts de Next/TypeScript/Tailwind. Tomar como referencia de stack los ficheros equivalentes de #202, resolviendo versiones en `main` y fijando instalaciones reproducibles.
2. **CI y QA (#388/#392):** `.github/workflows/web.yml`, `web/playwright.config.ts`, smoke tests, Dockerfile, `.dockerignore`, compose de staging y endpoint health de #202. Adaptar `paths`, ramas, artefactos, URLs, base path y checks. No portar selectores de V1/V2. Verificar que workflows compartidos no ejecuten Android.
3. **Preview:** job/workflow GitHub Pages y safeguards `noindex`/`.nojekyll` de #202; usar Pages deployment history como referencia operativa. Conservar un solo workflow web y base path correcto.
4. **SEO/seguridad pública:** capacidades de metadata, sitemap, robots, manifest, OG, 404 y security headers de #202, reescritas sobre rutas y dominio definidos por la baseline.
5. **Cinemática (#389):** solo la técnica de mapeo de progreso de scroll a índice, scheduling con `requestAnimationFrame`, dibujo a canvas, carga progresiva, cache acotada y fallback poster/reduced-motion de #204. No portar `CinematicHomeV2`, el storyboard ni sus frames. Un review board solo si ahorra QA y permanece `noindex`.
6. **Estructura pública (#389/#390):** reaprovechar responsabilidades semánticas (header/footer/legal) de #202, con markup/CSS nuevos conforme a #394. Implementar Home, secciones y sidebar privada según Visual Lock, no según `CinematicHome.tsx`.

No portar archivos de `apps/web` de #1, stores, API, proveedores de Auth, GIS, Room/Sync, SQL, rutas de registro ni workflows generalistas. Los fixtures y adapters de UI futuros se implementan expresamente desde #386, sin duplicar el dominio Android.

## Preview histórica verificada

GitHub identifica el último deployment `github-pages` como exitoso el **2026-09-21 12:32:33 UTC**, asociado al commit `7ec86636fe146f7a9b3184000ad6f2b9e9abeb01`, que coincide con HEAD de la PR #204. La URL histórica declarada por #386/#202 es <https://izc05.github.io/magina-olivo-v20/>. Esto confirma qué commit se desplegó por última vez, no que la preview represente `main` actual ni que su contenido cumpla #394; actualizarla en la nueva línea web antes de presentarla como preview V3.

## Primera entrega recomendada para #388

Crear una rama de baseline desde `main` y entregar una PR pequeña que añada únicamente el scaffold Next/App Router + TypeScript + Tailwind dentro de `web/`, CI web filtrada, Playwright mínimo, Docker/healthcheck y preview con `noindex`. No copiar Home/assets antiguos ni añadir Auth, backend, lógica de dominio o datos reales. El sistema de tokens y componentes debe reflejar #394 cuando se implemente el primer UI, no reconstruir V1/V2.

## Pruebas / evidencia de WEB-0A

- Revisados los cuerpos vigentes de #395, #386, #394 y #387.
- Revisados los inventarios y ficheros focalizados de PR #1, #202 y #204; commits fijados arriba.
- Verificado en GitHub el deployment Pages más reciente y su SHA/estado/fecha.
- No se ejecutaron pruebas de código ni se modificaron workflows: esta entrega es una auditoría documental sin cambios de runtime.
- No se hizo ningún cambio a Android, Room, Supabase productivo, Auth real, RLS o Sync.

## Siguiente paso

Continuar con **#388 WEB-0B**: abrir PR de baseline nueva desde `main` y portar únicamente el scaffold/CI/preview mínimo enumerado, respetando la separación del carril Android y los límites de #395.
