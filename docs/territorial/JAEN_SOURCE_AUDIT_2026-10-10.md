# Auditoría de fuentes territoriales — arranque controlado (#728)

Fecha: 2026-10-10. Alcance: **solo investigación y contrato de integración**, sin cambios a la UI ni a `main` y sin nueva APK.

## Decisiones de producto
- Conservar diseño Android actual (verde/crema, Inicio y navegación, botón +). Las fichas se incorporan en componentes ya existentes.
- Fuentes públicas **no** son datos privados agrícolas; las noticias, directorios y cartografía no deben mezclarse con la sincronización de la explotación ni con la comunicación administrativa del CUE.
- Empezar con Bedmar y Garcíez y extender después a Jódar, Jimena y Albanchez de Mágina tras validar los contratos.
- La app debe funcionar offline mostrando caché fechada; no transformar contenido antiguo en «última hora».

## Matriz de fuentes contrastadas
| Fuente | Enlace oficial | Confirmado | Pendiente de comprobar antes de reutilización |
|---|---|---|---|
| Diputación, IDEJaén | https://www.dipujaen.es/conoce-diputacion/areas-organismos-empresas/areaF/info-geografica/servicios-ogc/ | Geoportal y WMS con información de 97 municipios y equipamientos; detalle de EIEL a nivel de núcleo en 95 municipios | GetCapabilities concreto, nombres de capas, CRS, términos actualizados, atribución y rendimiento móvil |
| WMS Diputación | https://www.dipujaen.es/conoce-diputacion/areas-organismos-empresas/areaF/info-geografica/servicio_wms/ | Uso libre y gratuito; NO es cartografía oficial para certificaciones | Capas específicas utilizables, cobertura y avisos de licencia |
| Datos publicados IDEJaén | https://www.dipujaen.es/conoce-diputacion/areas-organismos-empresas/areaF/info-geografica/servicio_wms/datos_publicados.html | Divisiones municipales, núcleos, caminos, equipamientos, agua e infraestructuras | Qué capas aportan valor real al agricultor sin llenar el mapa de elementos |
| Jaén Paraíso Interior | https://www.jaenparaisointerior.es/es/w/municipios/bedmar | Ficha de Bedmar y Garcíez con coordenadas, patrimonio y descripción | Derechos de fotos, uso/reproducción de textos, URLs duraderas, otras localidades |
| Junta, CUE | https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html | REAFA previo a CUE, intercambio IUWS/Sga Cex, modelo V9 y marco normativo | Requisitos vigentes del proveedor comercial y sandbox/conexión oficial; no declarar homologación |

## Contrato técnico de integración
1. **Datos de referencia estables**: Municipality(code_ine, name, region), Locality(id, municipality_id, name), POI(id, lat, lon, source), GeoLayer(id, wms_endpoint, layer_name, attribution, min_zoom). Identificadores estables; no fabricar geolocalización.
2. **Contenido dinámico**: Source(id, publisher, url, licence, poll_policy); Publication(external_id, source_id, municipality_id, headline, original_summary, published_at, fetched_at, expires_at, canonical_url, moderation_status).
3. **Ingesta backend única**: fuentes oficiales/APIs/feeds con permiso; normalización, desduplicado, permisos, revisión cuando proceda; cache y TTL; errores de fuente aislados. Nunca scraping sin revisar condiciones o extracción masiva de fotos.
4. **Android y web** consultan la misma API de contenido público, caché local y etiqueta «Fuente/actualizado»; sin exponer secretos administrativos ni datos de fincas privadas.
5. **Riegos**: agenda personal registrada por agricultor distinta de avisos oficiales de comunidad; no inventar turnos. Cooperativas con ficha verificada, ubicación y datos de contacto actualizables.
6. **Mapa**: WMS opcional; Catastro/SIGPAC conservan capas/identidades separadas. Pulsar «Ir» centra el mapa y colapsa el buscador (#722). Si falla WMS, permitir seguir usando parcelas guardadas.

## Pruebas de la primera iteración
- Ficha de Bedmar y Garcíez correctamente unificada administrativamente y con núcleos diferenciados.
- Con/sin red, WMS caído, localidad sin eventos, noticias duplicadas y envejecidas, permisos de fotos, atribución legible, distintos tamaños y modo oscuro.
- E2E público: fuente -> ingesta -> API -> Android y web, con fecha y trazabilidad. Las pruebas automatizadas no suplen permisos de licencia.
- Verificación de que ningún cambio toca entidades privadas Room ni crea una APK candidata.

## Dependencias y límites
- #721: no duplicar entidades territoriales ni migración Room v26; verificar HEAD y checks por SHA antes de integrar.
- #724: ficha de pueblo y mapa; #699/#532: Inicio, cooperativas y noticias; #725/#726: Auth y sync privada; #534: compatibilidad CUE; #727: plan maestro.
- **Primer trabajo ejecutable**: inventario de interfaces/código existente y prueba técnica GetCapabilities WMS, seguida de PR de modelos/conector aislado (sin UI) solo cuando haya validación de permisos y datos.
- Web V3 visual pausada y ninguna nueva APK distribuida hasta gate final aprobado.
