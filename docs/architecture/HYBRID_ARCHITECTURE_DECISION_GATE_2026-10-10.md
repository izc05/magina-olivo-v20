# Revisión arquitectónica previa al desarrollo híbrido — 2026-10-10

Estado: **arquitectura propuesta aprobada por el propietario, integración técnica condicionada a gates**. Issue #731, decisión #730, plan #727. Esta revisión **no certifica** que Web/Admin/Auth/Sync estén integrados ni que CI pase.

## Hallazgos verificados directamente en GitHub
1. `main/README.md` define Android-first, fuente local/offline-first y ejecución por Gates. `main/docs/00-master/CURRENT-STATE.md` (revisado 2026-10-10) aún declara «Android only; Web V3 paused», pese a decisión posterior #730 de reactivar **solo la web funcional**. Es una contradicción documental que debe resolverse mediante change control.
2. La búsqueda directa de `main/web/package.json` dio 404, mientras que `codex/web-0e-private-shell/web/package.json` sí existe: Next.js 16.3.8, React 19.3, Playwright, Biome, TypeScript. **No está demostrada web desplegada ni consolidada en main**. En `main` tampoco está `supabase/config.toml` en esa ruta concreta: eso no demuestra inexistencia de backend, solo obliga a inventariarlo.
3. `main/docs/00-master/CHANGE-CONTROL.md` obliga CR cuando cambian modelo de entrega, sincronización, alcance, orden/gates, navegación, dependencias. La opción híbrida C y cambio a registro obligatorio requieren una CR explícita y revisión de baseline, **no basta un issue de intención**.
4. `main/docs/00-master/RC1.2-PRODUCT-LOCK.md` indica producto agrícola nativo, neutral respecto a geografía; el núcleo NO debe convertirse en portal regional obligatorio ni hardcodear Jaén, moneda o códigos provinciales. **Resolución propuesta**: capa pública opcional por municipio/país, sin modificar modelo nuclear; la cuenta de Mágina Olivo no presupone REAFA.
5. Evidencias **históricas no verificadas en main**: #13 / PR #16 Admin, PR #66 operaciones, PR #117 hardening, #327 contrato Admin, #337 anuncios. No fusionar candidate V20 antiguo completo. Revisar también WEB V3 #493/#552/#598 y territorial #721.
6. Decisión actual: usuario requiere **registro obligatorio para nuevas instalaciones**, con primera autenticación online, uso offline después y **migración segura** de datos Gate21 anteriores al login. Decisión #725/#726. No borrar fincas ni trabajos al cerrar sesión.

## Registro formal de Cambio — propuesta CR-HYBRID-01
### Motivo
Acelerar contenido territorial y publicidad web, evitar recargar el Android ya aprobado, conectar cuenta única web/app.
### Cambios exactos solicitados
- Android agrícola nativo y offline-first **permanece**. Sin nueva navegación raíz ni rediseño.
- Web funcional pública + ADMIN + Mi explotación (lectura inicialmente) reaparecen en roadmap; Web V3 cinematográfica/estética amplia fuera del gate actual.
- Servicio público territorial modular y opcional (municipios y noticias) no altera jerarquía agrícola; finca y cuaderno funcionan sin datos territoriales.
- Autenticación requerida en primera instalación normal y acceso posterior offline sujeto a política definida; migración de usuarios/dev con Room anterior a cuenta.
- Tres planos de datos no intercambiables: privado agrario, contenido público editorial, integración administrativa oficial CUE/IUWS detrás de adaptador y autorizaciones independientes.
### Documentos afectados
RC1.2-PRODUCT-LOCK, CURRENT-STATE, CHANGE-CONTROL (sin editar sus reglas), #340, #591, #327, #326, #730, #727, #726.
### Impacto datos
Conservar IDs Room, outbox/tombstones, snapshots, fotos y costes al asociar cuenta; migraciones aditivas probadas; no convertir municipio libre en ID territorial sin reconciliación.
### Impacto permisos
Auth única; roles de plataforma no equivalen a rol de workspace agrario. RLS + checks server-side, archivos privados, auditoría de admin; consentimiento/licencias de imágenes.
### Alternativas
A) todo nativo: más APK y duplicación; B) WebView total: debilita offline/UX; C) Android agrícola nativo + tarjetas API + Custom Tabs + portal CMS/admin: **seleccionada**; D) solo links sin CMS: mínimo rápido, pero peor gestión/publicidad.
### Riesgos y mitigaciones
- Mezcla con Web/Admin antiguos: matriz SHA/archivos KEEP/ADAPT/DROP y ports mínimos basados en main.
- Auth obligatoria rompe usuarios de Gate21: migration claim explícito, backup e integración E2E sin pérdida y cobertura offline.
- Datos privados filtrados vía Admin: pruebas A/B, consultas mínimas, auditoría.
- Noticias/imagen de terceros: procedencia/licencia/fecha, opción enlace oficial en vez de copia.
- Dependencia IUWS oficial: separar exportación compatible de envío administrativo confirmado.
- Scope creep: primera vertical solo Bedmar-Garcíez, directorio enlaces y una noticia manual; publicidad en segunda iteración.
### Decisión de implementación
Producto aprobado por propietario en #730; **CR técnica pendiente de reconciliación con baseline por responsables del código y revisión de impacto**. No saltar los gates por este texto.

## Orden recomendado con puertas de salida
**G0 (bloqueante) Inventario main + baseline/CR**: SHA main y matriz de módulos (Android, web, ADMIN, Supabase, CUE); PRs antiguas y esquema; responsables por archivo; documento de CR aprobado. No código productivo antes de completar.
**G1 Portal/Admin mínimo**: seleccionar baseline Web V3 compatible, importar solo carpetas necesarias desde main a rama aislada; lint/typecheck/build/E2E y protecciones 401/403 + A/B; no tocar Android.
**G2 Primer vertical público**: localidad Bedmar/Garcíez + cooperativa verificada, enlace oficial, noticia manual publicada con fuente/licencia y caducidad, API solo publicada y pruebas; portal web público real.
**G3 Android integración mínima**: contrato de tarjetas versionado, caché con frescura, Custom Tabs HTTPS y retorno a Inicio; sin cambiar diseño; CI Android por SHA.
**G4 Publicidad**: leads, validación, campañas por municipio/vigencia, etiquetado inequívoco, previews y métricas agregadas sin datos privados.
**G5 Identidad y sync**: Auth/roles, claim de datos históricos, outbox, archivos, recuperación, dos cuentas y web privada (lectura); semántica de logout/eliminar cuenta probada.
**G6 Cuaderno Andalucía**: formato V9 y requisitos vigentes verificados; exportación y solo cuando proceda conexión IUWS tras alta/certificados/autorización.
**G7 Release**: QA multisuperficie, PRs y CI verdes por SHA HEAD y main; migración Gate21, modo avión, 360/390/430, accesibilidad, legal y licencias. **No generar APK candidata nueva hasta gate completo**.

## Primer encargo ejecutable para Codex
Recuperar **solo matriz de código real** y CR-HYBRID-01 sobre una rama de auditoría, leyendo cambios #16/#66/#117/#493/#552/#598, localizar rutas de web/admin reales y dependencias, verificar schema y controles de acceso. Entregar diff de rutas, HEAD SHA, pruebas y riesgos. **No construir un nuevo Admin** hasta G0; no tocar main ni app.
Claude puede continuar pequeñas correcciones aisladas Android #722/#723, con ownership de archivos y CI por SHA, sin fusionar cambios conflictivos.

## Cinco decisiones explícitas ya cerradas
- Diseño Android intacto.
- Android agrícola nativo, web portal/Admin y enlace externo Custom Tabs.
- Registro requerido al incorporarse (respetar datos heredados y funcionamiento offline).
- Publicidad solo local por municipio/categoría, sin datos privados agrícolas.
- Ni APK nueva ni merge de ramas históricas en bloque.

## Evidencia repositorio
- https://github.com/izc05/magina-olivo-v20/blob/main/README.md
- https://github.com/izc05/magina-olivo-v20/blob/main/docs/00-master/CURRENT-STATE.md
- https://github.com/izc05/magina-olivo-v20/blob/main/docs/00-master/CHANGE-CONTROL.md
- https://github.com/izc05/magina-olivo-v20/blob/main/docs/00-master/RC1.2-PRODUCT-LOCK.md
- https://github.com/izc05/magina-olivo-v20/blob/codex/web-0e-private-shell/web/package.json
