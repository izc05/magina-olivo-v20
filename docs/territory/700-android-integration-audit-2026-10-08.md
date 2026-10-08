# Auditoría de integración territorial en Android actual — 08/10/2026

**Ámbito:** INVESTIGACIÓN / T0 de #700 y #699, basada en código leído de `main` (revisión del 08/10/2026, SHA observado `9f92faa4ad4f1918b813ff409330c70fc642ece3`). **Sin cambios Android, Room, Product Lock, Web, backend ni Gate.** NO iniciar PR productiva antes de #696 y Gate21 físico. No representa tests ejecutados.

## 1. Integraciones reales identificadas (reutilizar)

| Fuente real de `main` | Hallazgo comprobado por lectura | Implicación para #700/#699 |
| --- | --- | --- |
| `feature/profile/MyProfile.kt` — `MyProfileViewModel`, `LocationSheet` y `CooperativeSheet` | Perfil dispone de `saveLocation(municipality,province)`, `chooseCooperative` y alta de organización, con sheets «Tu municipio» y «Tu cooperativa». | **Extender este selector**, no crear otro selector de tiempo/pueblo ni otro formulario de cooperativa. |
| `data/local/entity/ProfileSettingsEntity.kt` | `profile_settings` tiene **una fila por workspace** (`workspace_id`) y almacena `municipality`/`province` como textos opcionales; no hay código INE en esa entidad. | No asumir que «localidad de la cuenta» ya es global. Acordar ámbito de preferencia y migración antes de cambiar persistencia; la versión actual mantiene perfiles previos. |
| `data/repository/OfflineFirstProfileRepository.kt` | `observe` y `save` resuelven workspace, actualizan una fila y encolan un outbox de PROFILE_SETTINGS. La cooperativa se referencia por UUID existente. | Mantener ownership y outbox; cualquier nueva preferencia debe preservarse en upgrades y no combinar workspaces. |
| `feature/home/HomeViewModel.kt` | Clima de Inicio usa **municipio común de las fincas primero**, con fallback al Perfil; feed meteorológico y mercado se observan sin bloquear datos agrícolas. | «Mi pueblo» no debe modificar silenciosamente el municipio de las fincas ni los datos meteorológicos actuales. Mostrar ámbito de cada tarjeta claramente. |
| `feature/home/HomeScreen.kt` y `HomeWeatherHero.kt` | Hero meteorológico, estadísticas/campaña/avisos, después sección «Mercado y cooperativa». Existe `solarLine` para salida/puesta de sol cuando se dispone de datos válidos; el bloque de cooperativa es hoy informativo, no anuncios funcionales. | Integrar acceso secundario «Mi pueblo» y futuras tarjetas **después de lo agrícola**, sin reemplazar hero ni añadir pestaña raíz. No volver a implementar amanecer/atardecer desde cero. |
| `androidTest/.../ProfileSettingsContractTest.kt` | Ya comprueba guardar/reabrir perfil, outbox colapsado, organización renombrada/archivada y rechazo de cooperativa de otro workspace. | Extender esas pruebas si se cambia el selector, no generar otro modelo de organización incompatible. |

### Decisión pendiente de producto: alcance de «Mi pueblo»
- **Canónico actual:** Perfil «Tu municipio» es la elección existente. #699 pide reutilizar la localidad elegida; AGENTS.md dice que Perfil es fuente única. Sin embargo #700 propone un `preferredNewsMunicipalityId` independiente del municipio meteorológico y la BD existente es por workspace.
- **Propuesta de menor complejidad:** por defecto «Mi pueblo» usa el municipio del Perfil del workspace activo; permite consultar puntualmente cualquier otro pueblo **sin cambiar la preferencia**. No añadir dos favoritos permanentes ni vincular el pueblo a una parcela hasta decisión explícita. Si el propietario pide «pueblo favorito distinto del lugar meteorológico», diseñar un segundo campo con etiqueta clara y test de no alteración del tiempo.
- El municipio meteorológico conserva la regla actual (finca común → Perfil); cambiar de «pueblo que se consulta» **no altera la finca, campaña, mapa, clima ni gastos**.
- Si se migra de nombres libres a código INE, concordancia por **código + provincia**, sin convertir nombres ambiguos automáticamente. Mantener el texto original y pedir confirmación de emparejamiento cuando exista ambigüedad. Testar actualización desde Room v25 y versiones soportadas, sin destruir outbox.

## 2. Catálogo y fuentes de verdad

1. **Municipios:** Junta de Andalucía, dataset «Municipios de la Comunidad Autónoma de Andalucía», CSV/JSON diario y licencia CC BY 4.0: https://www.juntadeandalucia.es/datosabiertos/portal/dataset/datos-abiertos-municipios. API documentada https://datos.juntadeandalucia.es/api/v0/municipalities/openapi.json . Contrastar códigos con INE (provincia Jaén `23`) y cobertura de municipios de la provincia. **AÚN NO** se ha descargado/verificado un snapshot provincial real ni obtenido recuento oficial contrastado.
2. **Núcleos/entidades:** IECA Nomenclátor https://www.juntadeandalucia.es/institutodeestadisticaycartografia/dega/nomenclator-de-entidades-y-nucleos-de-poblacion-de-andalucia ; «Garcíez» se puede mostrar como entidad de población, no inventar un código de municipio nuevo.
3. **Datos del Ayuntamiento:** directorio oficial, URL HTTPS, procedencia, fecha de verificación y fallback al directorio cuando no haya web verificable. No inferir enlaces municipales ni insertar fotos sin licencia.
4. **RAIF:** los datos públicos de estaciones fitosanitarias (incluido olivar) están publicados como conjuntos ZIP https://www.juntadeandalucia.es/datosabiertos/portal/dataset/raif . **No son predicción fiable de plaga para una parcela concreta**. Si se incorporan, pipeline backend/batch con región, cultivo, fuente, fecha y cobertura; avisos informativos, nunca autocompletar tratamiento ni declararlos como datos privados REAFA.
5. **CUE/REAFA/IUWS:** solo integración futura autorizada; documentación https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html . Requiere alta del software, certificado/rol/autorizaciones y contratos oficiales. El Android local NO tiene acceso automático por instalar una API; no almacenar certificados en APK ni declarar cumplimiento hasta sandbox/autorización. Distinguir RAIF pública de REAFA privada.

## 3. Arquitectura de integración — propuesta, NO implementación

```text
Inicio actual (Compose)
 ├─ datos agrícolas Room (prioritarios incluso offline)
 ├─ clima + mercado (servicios existentes con frescura)
 └─ tarjetas territoriales (sólo cuando habilitadas)
       ├─ selector «Mi pueblo» -> catálogo municipal local versionado/INE
       ├─ fichas públicas municipio / noticias / RAIF (Cache TTL + fuente)
       ├─ cooperativas públicas (directorio verificable)
       └─ anuncios locales (identificados, caducidad y frecuencia)
```

**Frontera de propiedad y privacidad:**
- `MunicipalityReference`: referencia pública, código INE, nombre, provincia, fuente y versión. No workspace ni identificador de parcela. Empaquetar snapshot verificado para primer arranque offline; actualización diferencial posterior con hash/versionado.
- `PublicOrganizationDirectoryEntry`: entidad pública verificada (id externo, origen, fecha de revisión, contacto). **NO es el mismo objeto** que la `Organization` creada por agricultor para sus Pesadas: enlazarla voluntariamente, sin sobrescribir nombre histórico ni crear copias cada vez que se consulta.
- `PublicContentItem`: id estable, municipio(s), tipo noticia/evento/aviso, título, URL origen, `publishedAt`, `checkedAt`, `validUntil`, licencia y estado. No contenido editorial sin procedencia; si falla red mostrar «sin datos actualizados» o el cache claramente caducado.
- `SponsorCard`: marcado PUBLICIDAD, anunciante, municipios objetivo públicos, vigencia, etiqueta y CTA externo. Nunca usar kg, coordenadas de fincas, gastos, tratamientos o historial personal para segmentar anuncios.
- IDs de finca/parcela/campaña permanecen privados por workspace; futura nube con RLS en tablas agrícolas, lectura pública separada para catálogo y permisos admin/anunciante limitados. Sin acceso cruzado.

## 4. PRs y orden de fases para ejecutar cuando corresponda

| Tramo | Dependencia | Primera entrega verificable |
| --- | --- | --- |
| **A0** | #696 Android / Gate21 pendiente | M1 agrícola completo → pruebas UI/emulador/API35 + upgrade/foto SHA → APK candidata y aceptación física. **No introducir territorio aquí.** |
| **A1** | Tras Gate21 y decisión de fase aprobada | Contrato del catálogo municipal, código INE/IECA, snapshot Jaén reproducible por importador ya creado, SHA y cobertura oficial; UI selector en Perfil reutilizada, test offline/reinicio/sin regresión clima. Si Phase22 cuenta/seguridad ya está en marcha, programar sin solaparla. |
| **A2** | Backend Auth/RLS (Gate22) + Sync (Gate23) autorizados | Identidad/cuenta opcional y datos agrícolas protegidos; recuperación y segundo dispositivo. Las tablas públicas territoriales no dependen de acceder a fincas privadas. |
| **A3** | catálogo verificado + permisos/fuentes | Ficha «Mi pueblo» con enlace municipal verificable, fecha; primeras tarjetas de información con caché y modo sin conexión, sin feed inventado. |
| **A4** | directorio verificado + pruebas de ownership | Cooperativas/almazaras públicas; elección voluntaria que reutiliza la Organization de Pesadas, sin modificar histórico. |
| **A5** | contrato de fuentes y publicación autorizado | RAIF informativa y contenido local con procedencia, ingestión backend por lote y control de frescura. |
| **A6** | permisos administrativos, legalidad y seguridad comprobados | IUWS/REAFA sandbox, reconciliación parcelas/SIGPAC, envío CUE con estado administrativo distinto del Sync técnico. No prometer conexión hasta autorización real. |
| **A7** | A3/A4 estables, soporte/moderación | Carrusel Inicio y publicidad local moderada/identificada, sin bloquear Cuaderno ni alterar arquitectura Android. Rutas/Aventura Mágina y Web V3 permanecen fuera de este slice. |

La secuencia A1-A7 es **propuesta de dependencias**, no una nueva autorización para saltar Gate22/23 ni sustituye #340/#534/AGENTS. El equipo decide PR por PR conforme al gate vigente.

## 5. QA imprescindible para cada integración

- Municipio/entidad de población: nombres similares, acentos, códigos cero inicial, búsqueda, «Garcíez», URL vacía o caducada, catálogo offline, provincia distinta.
- Seleccionar pueblo desde Inicio NO cambia finca, campaña, preferencias meteorológicas ni cooperativa; al reiniciar conserva solo las preferencias aprobadas.
- Fichas/avisos: feed inexistente, sin cobertura, lento, caducado, fecha origen, enlace seguro, licencia, fuente incorrecta, archivo duplicado, actualización.
- Cooperativa directorio vs privada: misma denominación, archivo, renombrado, otra workspace, Pesada antigua permanece inmutable, elección por Pesada editable.
- Publicidad: etiqueta clara, fecha de caducidad, segmento exclusivamente territorial, accesibilidad y frecuencia limitada.
- Actualización: no se pierden parcelas ni preferencias al migrar Room, no se borra información privada al refrescar referencias públicas.
- Regresión visual 360/390/430dp, texto grande, modo avión; probar sin red desde inicio de la app, no obligar a geolocalización.
- CUE: en fase propia, pruebas de sandbox autorizado, idempotencia y errores administrativos. Nunca presentar «sincronizado con Junta» sin acuse del sistema oficial.

**Conclusión:** preservar la UX y fuentes de verdad que YA existen; incorporar territorio como referencia pública incremental tras cerrar primero el cierre agrícola, sin mezclar estados o permisos.
