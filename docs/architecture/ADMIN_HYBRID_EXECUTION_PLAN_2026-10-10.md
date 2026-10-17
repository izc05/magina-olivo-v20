# Mágina Olivo 1.0 — Plan ejecutable para Web/Admin híbrido
**Aprobado 10/10/2026 · Issue operativo #731 · decisión #730 · plan Android #727**
**Estado:** propuesta de implementación; no equivale a código entregado, desplegado ni CI verde.

## 0. Contrato de producto y límites
El diseño Android aprobado sigue intacto: Inicio, Mi Campo, Cuaderno, Campaña, +, Perfil y Avisos; verde/crema y modo oscuro. Android nativo mantiene fincas, pesadas, jornales, gastos, cuaderno, GPS, fotos y offline. Se añaden solo tarjetas/CTA a contenido público actualizado desde web, y cuenta/sync cuando estén verificadas. No se entrega nueva APK antes de cerrar gate #727. Web V3 cosmética/animación global permanece pausada; web funcional/Admin reactivadas según #730.

## 1. Inventario previo — NO DUPLICAR
Contrastar obligatoriamente contra **main actual** y SHA:
- #13 y PR #16: Admin antiguo, CMS, territorio, editor, publicidad, medios, roles y auditoría; el contenido del issue no acredita disponibilidad en main.
- PR #117: hardening de Admin sobre candidate antiguo; #66 operaciones/admin; #58 candidate V20 antiguo. No fusionar árboles antiguos ni asumir compatibilidad.
- #327 Admin canónico y #322 roles (son referencia de permisos). #337 publicidad es diseño operativo ya detallado.
- #591 y Web V3 en PRs apiladas #493/#552/#598: preservar trabajo vigente sin fusionar indiscriminadamente.
- #721 territorio Room v26 pendiente de integración comprobada; #728 fuentes Diputación; #726 cuenta/sync; #534 CUE.

**Entregable 1 obligatorio**: matriz con columnas `superficie | componente | main SHA | PR/branch fuente | evidencia local | evidencia CI SHA | riesgo | KEEP/ADAPT/DROP | próxima PR`, con enlaces a código y checks. Sin este gate no escribir módulo ADMIN nuevo.

## 2. Superficies separadas
### Android
- Inicio: carrusel integrado en layout existente (sin pestaña nueva) con fichas pueblo/cooperativa, noticia, avisos, promociones claramente «Publicidad».
- Seleccionar municipio una sola vez en Perfil; mantener selección sin GPS permanente.
- CTA a web oficial del Ayuntamiento/Cooperativa, noticia exacta o negocio, mediante Custom Tabs HTTPS con retorno natural, dominio visible, enlaces validados y sin tokens ni datos agrícolas en URL. Si no hay red, dar aviso y mantener caché de tarjetas con fecha.

### Web pública
- /pueblos/[slug] ficha local breve con fuente/atribuciones + CTA oficial; /cooperativas, /noticias, /eventos y /anunciate como rutas funcionales solo tras implementar.
- No copiar noticias ni fotografías sin derechos; un resumen editorial original y deep link a fuente.

### Panel ADMIN privado /admin
- Resumen: salud fuentes, pendientes, próximos vencimientos, campañas activas, errores y auditoría.
- Municipios/localidades y fichas: nombre, código INE, entidad administrativa, foto/logo con licencia, fuente, URL oficial y estado de verificación.
- Cooperativas/entidades de riego: ficha, contacto, URL y fuente; riego oficial vs plan privado diferenciados.
- Noticias/eventos/avisos: fuente, título, enlace canónico, `published_at`, fecha real del evento y ventana de publicación, estado DRAFT -> IN_REVIEW -> SCHEDULED/PUBLISHED -> EXPIRED/ARCHIVED. Expiración automática sin borrar historial.
- Publicidad: leads Anúnciate, anunciantes verificados, creatividades con alt, preview móvil, municipios y vigencia, workflow revisión->publicación->pausa. Etiqueta siempre «Publicidad». Enlace externo validado. Sin pago automático en MVP.
- Usuarios y soporte: estado técnico permitido, roles, solicitudes; **sin explorador general de fincas ni fotos privadas**.
- Métricas: impresiones/clics agregados y minimizados, sin segmentación por ingresos, kilos, GPS preciso ni datos agrícolas.
- Fuentes: catálogos oficiales/feeds legales, último éxito, error, frescura, reintentos; ingesta manual como fallback.

## 3. Contratos mínimos API y datos
`municipality(id, ine_code, official_name, province, source_url, checked_at)`
`locality(id, municipality_id, display_name, center_location?, source_url)`
`organization(id, type, municipality_id, display_name, official_url?, verified_at?, status)`
`content(id, type, municipality_id, title, summary, canonical_url, source_url, source_name, published_at, event_start?, event_end?, display_from?, display_until?, status, rights, image_url?, image_alt?, updated_at)`
`ad_campaign(id, advertiser_id, headline, image_id, landing_url, municipality_ids, start_at, end_at, review_status, publication_status)`
`content_card(id, type, title, subtitle, municipality_id, image_url?, image_alt?, badge_text?, destination_url, published_at?, fetched_at, expires_at, sponsored)`

Son **contratos conceptuales** a reconciliar con esquema existente: NO crear tablas duplicadas ni decidir SQL hasta completar inventario. Versionar respuestas API y normalizar municipios/localidades con referencias oficiales. Endpoint público solo registros publicados/validados; no exponer borradores, secrets o metadatos privados.

## 4. Seguridad y permisos
- Auth único Android/web; sesión verificada y renovaciones seguras; offline tras inicio inicial. Usuarios Gate21 sin cuenta conservan datos al migrar y pueden asociarlos explícitamente a su identidad.
- Roles plataforma separados de `workspace` agrícola: support/editor/admin/super_admin según #322; permisos servidor y RLS en tablas accesibles; auditoría de cambios.
- Pruebas A/B: usuario A no lee documentos/fincas de B, editor no publica, advertiser no ve datos privados, cierre sesión no borra datos pendientes; eliminación cuenta separada.
- Imágenes/logo solo con derecho de uso y almacenamiento privado para borradores, público solo lo aprobado. Sanitizar enlaces y no permitir URL peligrosas/redirecciones abiertas.

## 5. Flujo publicitario MVP
1. Negocio solicita publicidad desde /anunciate (datos mínimos y consentimiento de contacto).
2. Admin revisa lead, verifica negocio y derechos del material.
3. Editor prepara tarjeta con imagen, municipio, fecha, URL, descripción y alt.
4. Admin aprueba/publica, puede pausar o cancelar; expiración automática; etiqueta «Publicidad» en web y Android.
5. Mostrar anuncio tras contenido útil, sin pantalla completa ni interrupciones; métricas agregadas, sin perfilado de fincas.
6. Gestión de cobros/contratos manual inicialmente; automatización y facturación más adelante con cumplimiento aplicable.

## 6. Integración de terceros
- Diputación IDEJaén WMS solo como capa opcional; fuente no certificadora y revisar derechos antes de uso.
- Ayuntamientos: enlaces oficiales verificados; noticias solo a URL exacta de artículo y fuentes compatibles (API/RSS/licencias o publicación manual). Nunca inferir actualización.
- Cooperativas: enlace, teléfono y ubicación verificados; turno de riego **oficial** solo cuando entidad publique fuente; no transformar plan personal en dato oficial.
- API común cacheada con TTL, fallback ante fallos y fecha/hora de frescura.

## 7. Secuencia de PR pequeñas y pruebas
| Orden | PR propuesta | Owner recomendado | Gate |
|---|---|---|---|
| 0 | Auditoría `main` vs ADMIN histórico + KEEP/ADAPT/DROP | Codex | enlaces SHA/CI + riesgos y ninguna duplicación |
| 1 | Auth/roles/admin route gate + RLS (si no existente) | Codex backend | 401/403, A/B y auditoría |
| 2 | Municipios/cooperativas piloto Bedmar-Garcíez | Codex web/admin | CRUD revisión, fuente y URL verificadas |
| 3 | Noticias/eventos y API de tarjetas read-only | Codex web/backend | publish->API, offline/cache/TTL, contenido caducado |
| 4 | Enlaces web desde tarjetas Android existentes | Claude Android | abrir/volver, no cambiar UI, no datos privados en URL |
| 5 | Publicidad web/admin + tarjetas patrocinadas | Codex web/backend, Claude Android solo integración mínima | revisión/expiración/etiquetado, métrica agregada |
| 6 | Cuenta + sincronización privada y web /mi según #726 | equipos separados | E2E 2 usuarios, offline, fotos, outbox, migración Gate21 |
| 7 | Auditoría global y cierre #727 | QA independiente | CI verde por SHA main final y aceptación usuario |

**No generar ninguna APK candidata intermedia**, aunque las tareas CI puedan compilar artefactos automáticos para validación. No deploy productivo ni fusionar PR rojas.

## 8. Arranque: tarea inmediata
Codex, si está disponible, **primero ejecuta el inventario** de #731, no programes Admin nuevo. Comparar main + #13/#16/#66/#117 + #327/#322/#337, documentar qué existe realmente, elegir KEEP/ADAPT/DROP y abrir PR documental. Claude trabaja #722/#723 sin tocar `web/**` ni `supabase/**`. Antes de integración de cualquier PR, informar SHA exacto y estado de GitHub Actions. No declarar desarrollo iniciado por el mero hecho de crear issues.

## 9. Definition of Done del primer vertical completo
Un ADMIN autorizado crea ficha y noticia real Bedmar (con enlace y derechos) -> se publica -> API pública sirve solo estado publicado -> Inicio Android enseña tarjeta nativa con fecha -> pulsación abre web oficial/artículo -> retroceso devuelve al punto de partida -> al retirar publicación desaparece de API y caché tras TTL, sin actualizar APK ni afectar fincas. Incluir capturas, pruebas locales, actions por SHA y verificación privacidad.
