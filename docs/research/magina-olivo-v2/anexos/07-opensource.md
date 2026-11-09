# Repositorios abiertos reutilizables para arquitectura agrícola móvil/offline

**Corte de verificación: 9 de noviembre de 2026.** Se priorizan repositorios primarios y se distingue el software que puede incorporarse a una app React Native/Expo del que es más útil como referencia funcional o aplicación independiente. Los metadatos de GitHub citados son los consultados en esa fecha; `pushed_at` refleja el último push público del repositorio y no garantiza por sí solo soporte, calidad ni compatibilidad con la versión objetivo.

## Hallazgo de arquitectura

**VERIFICADO.** No aparece una única app agrícola mantenida que resuelva a la vez React Native/Expo, cartografía de parcela, formularios configurables, almacenamiento local y reconciliación/sincronización. Lo más reutilizable es una composición: Expo/React Native como contenedor; MapLibre React Native para cartografía; WatermelonDB o RxDB para datos locales/sync; un formulario de dominio propio o un estándar de formularios; y ODK Collect/QField como referencias maduras de flujo de campo, no como dependencias TypeScript integrables. La propia guía de Expo advierte que el ecosistema local-first sigue en evolución y que permisos/conflictos o una capa de sync pueden requerir trabajo propio. [1] [2] [3]

**INTERPRETACIÓN.** Para Mágina Olivo, registrar sin cobertura (observación, tratamiento, visita, evidencia fotográfica, punto/recorrido y estado pendiente de envío) es separable de descargar cartografía offline y de sincronizar. Conviene modelar cada captura como registro local durable con identificador estable, marca temporal, autor/dispositivo, geometría y estado de envío; sincronizarlo con backend idempotente y estrategia explícita de conflicto. Estas decisiones son una recomendación de diseño, no una capacidad ya demostrada por los repos examinados.

## Repositorios candidatos

### App agrícola de ejemplo — Eport Farmer Management App

- **Enlace:** [micpana/Eport-Farmer-Management-App](https://github.com/micpana/Eport-Farmer-Management-App).
- **VERIFICADO:** es una app React Native con Expo (README especifica Expo ~52.0.31), Flask, MongoDB y SQLite local. Su README describe alta de datos de agricultores online/offline y sincronización cuando vuelve la conexión; el inicio de sesión inicial requiere Internet. [4]
- **Licencia:** el campo `license` de GitHub API es `null`; no se encontró una licencia declarada en los metadatos primarios consultados. Por tanto **no asumir reutilización legal libre** ni copiar código hasta aclarar/obtener autorización. [5]
- **Mantenimiento:** creado el 6-03-2025; último push registrado el 6-08-2025; al corte lleva unos 15 meses sin push. Repo no marcado como fork, cero estrellas y cero issues abiertas en los metadatos consultados. No se halló release publicado en la inspección del repo. [5] [6]
- **Utilidad concreta:** prototipo didáctico cercano al caso de captura de agricultores, almacenamiento SQLite y cola/sync con API propia; sirve para extraer requisitos y revisar un flujo sencillo, no para tomar como base mantenida sin auditoría.
- **Riesgo técnico:** mantenimiento inactivo relativo al corte, licencia ausente, y autenticación que depende de conexión al inicio. README no prueba robustez ante duplicados, conflictos multiusuario, reintentos, cambios de esquema, ni manejo GIS de polígonos. **INTERPRETACIÓN:** tratar como demo de referencia, no como componente productivo.

### MapLibre React Native — cartografía vectorial en Expo/RN

- **Enlace:** [maplibre/maplibre-react-native](https://github.com/maplibre/maplibre-react-native).
- **VERIFICADO:** wrapper oficial de MapLibre Native para mapas vectoriales en Android/iOS, React Native y Expo; TypeScript y licencia MIT. Repo primario, no fork, con cambios recientes. Al corte: `pushed_at` 5-11-2026. Último release GitHub consultado: **v11.5.1**, publicado 29-10-2026; incluyó corrección Android. [7] [8]
- **Utilidad concreta:** mapa base, ubicación, anotación de geometrías y representación de parcelas/capas en la interfaz móvil; buen candidato para integrar la experiencia GIS en una app Expo existente. La existencia del renderer no implica que las teselas/datos se distribuyan offline automáticamente.
- **Riesgo técnico:** integración nativa y compatibilidad deben validarse contra las versiones concretas de Expo/RN y plataformas objetivo; evaluar instalación/prebuild y dispositivos reales. **PENDIENTE:** confirmar con prueba de concepto el flujo de paquetes de teselas offline y formatos requeridos (MBTiles/PMTiles/vector/raster), almacenamiento, atribución y licencias/condiciones del proveedor de datos cartográficos. No confundir MapLibre abierto con datos de mapa o servicio de teselas gratuitos.

### WatermelonDB — SQLite/local-first relacional para React Native

- **Enlace:** [Nozbe/WatermelonDB](https://github.com/Nozbe/WatermelonDB), [documentación de sync](https://watermelondb.dev/docs/Sync/Intro).
- **VERIFICADO:** base reactiva y asíncrona orientada a React/React Native, MIT; repo no fork. GitHub API registra push el 16-09-2026 y actividad del repo el 6-11-2026. Tiene 11.8k estrellas, 663 forks y 314 issues abiertas al consultar. No se obtuvo una respuesta verificable del endpoint de latest release, así que no se atribuye versión/release. [9]
- **Sync:** su documentación indica que WatermelonDB es solo base local: ofrece primitivas de cambios o un adaptador de sync, pero el producto debe implementar frontend y backend; el backend debe exponer endpoints según el protocolo de Watermelon. [10]
- **Utilidad concreta:** entidades relacionales offline (fincas, parcelas, visitas, labores, observaciones) con consultas reactivas y sincronización incremental con backend propio; adecuado cuando se quiere conservar control del servidor y del contrato de datos.
- **Riesgo técnico:** no es solución de backend ni sincronización lista sin implementar el protocolo, autorización, reintentos y resolución de conflictos. Verificar compatibilidad de la versión seleccionada con Expo/arquitectura nativa y ensayar migraciones/esquemas en dispositivos. El número de issues no permite inferir severidad ni falta de mantenimiento por sí solo.

### RxDB — base local TypeScript y replicación

- **Enlace:** [pubkey/rxdb](https://github.com/pubkey/rxdb), [documentación oficial de Expo local-first](https://docs.expo.dev/guides/local-first/).
- **VERIFICADO:** base local-first JavaScript/TypeScript, Apache-2.0; repo no fork. Al corte, `pushed_at` 6-11-2026; latest release GitHub consultado **17.6.0**, publicado 5-10-2026. [11] [12]
- **Expo/RN:** Expo documenta RxDB con Expo mediante adaptador SQLite basado en `expo-sqlite`, y replication plugins para backend HTTP, GraphQL, Supabase o integración propia. [2]
- **Utilidad concreta:** opción con enfoque de replicación configurable si se desea una capa TS común y sincronizar con servicios existentes; valorar esquemas, índices e integración local en un prototipo antes de escogerla frente a WatermelonDB.
- **Riesgo técnico:** estudiar el licenciamiento y coste de cada storage/plugin/servicio por separado; la licencia del repositorio no convierte un backend o complemento de terceros en parte del mismo permiso. Confirmar rendimiento y consumo en dispositivos de campo, funcionamiento en modo avión prolongado, conflicto, borrados/tombstones y límites de SQLite. La documentación Expo dice explícitamente que estas herramientas están en etapa temprana/evolutiva. [2]

### ODK Collect — referencia madura de captura configurable offline

- **Enlace:** [getodk/collect](https://github.com/getodk/collect), [introducción oficial de Collect](https://docs.getodk.org/collect-intro/).
- **VERIFICADO:** aplicación Android nativa (Kotlin), no componente React Native. La documentación describe formularios configurables con lógica, restricciones y grupos repetibles; captura ubicación, audio, imágenes, video, códigos, firmas y respuestas de varios tipos, con diseño offline. [13] El repo GitHub es el original de la organización, no un fork, tuvo push el 28-10-2026. [14]
- **Licencia:** API de GitHub informa SPDX `NOASSERTION`/“Other”; no deducir licencia a partir de la etiqueta. Revisar la licencia y avisos del checkout antes de reutilizar código. [14]
- **Utilidad concreta:** patrón de producto/benchmark para diseñador de formularios, validación, repetición, borradores y cola de envíos; alternativamente, producto separado para campañas de levantamiento si interoperabilidad con el ecosistema ODK satisface el caso.
- **Riesgo técnico:** Android-only y arquitectura nativa impiden tratarlo como librería Expo; integración exige interoperabilidad mediante formatos/API o adaptación significativa. Confirmar licencia explícita del árbol y requisitos del servidor/protocolo antes de adoptarlo.

### QField — GIS agrícola/campo listo para usar, no librería RN

- **Enlace:** [opengisch/QField](https://github.com/opengisch/QField), [documentación oficial](https://docs.qfield.org/).
- **VERIFICADO:** app móvil/escritorio de campo con base QGIS; la documentación señala que QFieldCloud es un servicio opcional para sincronizar y gestionar proyectos de equipo. El flujo documentado empaqueta proyecto QGIS mediante plugin QFieldSync. [15]
- **Actividad/licencia:** repo primario, C++, GNU GPL-2.0; push el 9-11-2026. Latest release consultado **v4.3.7 “Danube”**, 24-10-2026. [16] [17]
- **Utilidad concreta:** solución integral para trabajo GIS de campo y plantilla de capacidades: proyectos/capas, formularios definidos por QGIS, edición geoespacial en sitio y sync opcional. Puede ser alternativa operativa existente para equipos GIS, evitando reconstruir funciones GIS completas.
- **Riesgo técnico:** no es TypeScript ni módulo embebible en Expo; adoptarlo significa utilizar/apoyar otra aplicación o integrar por intercambio de datos/API. GPL-2.0 requiere revisión de obligaciones si se modifica/distribuye código; las condiciones de QFieldCloud/servicios comerciales son independientes del repo abierto. Evaluar formato de proyecto y estrategia de sincronización para parcelas y conflictos.

## Recomendación de selección

**INTERPRETACIÓN.** Para un producto nuevo que debe seguir siendo Expo/TypeScript, shortlist técnico: MapLibre React Native + una sola capa local (WatermelonDB si prima modelo relacional y backend propio; RxDB si se priorizan adaptadores/replicación y se acepta validar su ajuste) + SQLite/archivos locales para evidencias y una cola de subida explícita. Usar QField y ODK como referencias de UX/proceso o productos alternativos, no contabilizarlos como paquetes React Native. El ejemplo Eport solo ayuda como patrón ilustrativo y queda excluido de adopción directa hasta resolver mantenimiento/licencia.

**PENDIENTE antes de decidir:** (1) spike en Android e iOS con Expo SDK/RN previstos; (2) prueba de pérdida de conectividad, reinicio, reintento idempotente y dos usuarios editando el mismo elemento; (3) tamaño real de ortofotos/teselas y política de descarga/actualización/licencia; (4) manejo offline de GPS, fotografías, adjuntos, cifrado y copias; (5) confirmar licencia de ODK en su fichero LICENSE y compatibilidad/alcance de QField GPL con el modelo de distribución; (6) revisar los releases/commits exactos de dependencias en el momento de congelar versiones. La evidencia aquí refleja el corte indicado y no sustituye esa validación.

## References

[1]: https://docs.expo.dev/guides/local-first/ "Expo: Local-first architecture with Expo"
[2]: https://docs.expo.dev/guides/local-first/ "Expo: herramientas local-first, RxDB y desafíos"
[3]: https://watermelondb.dev/docs/Sync/Intro "WatermelonDB: Synchronization introduction"
[4]: https://github.com/micpana/Eport-Farmer-Management-App "Eport Farmer Management App README"
[5]: https://api.github.com/repos/micpana/Eport-Farmer-Management-App "GitHub API: Eport Farmer Management App repository metadata"
[6]: https://github.com/micpana/Eport-Farmer-Management-App/commits/main/ "Eport Farmer Management App: commits"
[7]: https://api.github.com/repos/maplibre/maplibre-react-native "GitHub API: MapLibre React Native repository metadata"
[8]: https://api.github.com/repos/maplibre/maplibre-react-native/releases/latest "GitHub API: latest MapLibre React Native release"
[9]: https://api.github.com/repos/Nozbe/WatermelonDB "GitHub API: WatermelonDB repository metadata"
[10]: https://watermelondb.dev/docs/Sync/Intro "WatermelonDB: synchronization protocol responsibilities"
[11]: https://api.github.com/repos/pubkey/rxdb "GitHub API: RxDB repository metadata"
[12]: https://api.github.com/repos/pubkey/rxdb/releases/latest "GitHub API: latest RxDB release"
[13]: https://docs.getodk.org/collect-intro/ "ODK Collect: official introduction and question types"
[14]: https://api.github.com/repos/getodk/collect "GitHub API: ODK Collect repository metadata"
[15]: https://docs.qfield.org/ "QField: official documentation and project sync"
[16]: https://api.github.com/repos/opengisch/QField "GitHub API: QField repository metadata"
[17]: https://api.github.com/repos/opengisch/QField/releases/latest "GitHub API: latest QField release"
