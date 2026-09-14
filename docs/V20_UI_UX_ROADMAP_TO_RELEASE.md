# Mágina Olivo V20 — UI/UX Roadmap to Release

## Objetivo final

Convertir V20 en una única experiencia web/app premium de Sierra Mágina, coherente de extremo a extremo en móvil, tablet y escritorio, sin reescribir la lógica funcional existente ni inventar datos para completar una pantalla.

La rama transversal `feat/v20-ui-ux-premium` actúa como **orquestador visual**. Las ramas especializadas pueden evolucionar primero sus módulos —por ejemplo Mi Olivo o Mágina Aventura— y esta rama debe absorber sus versiones ya validadas en lugar de competir con ellas.

`main` permanece fuera de este frente.

## Producto final esperado

Al terminar, un usuario debe poder recorrer:

`Inicio → Explorar → Pueblo → Ruta → Aventura → Mi Olivo → Recompensa → Almazara/QR → Empresa/Experiencia → Mi Campo → Perfil`

sin percibir saltos entre micrositios o generaciones de interfaz.

Admin debe sentirse parte del mismo producto, aunque con una expresión más densa y operativa.

## Lenguaje visual final

- Sierra Mágina, olivar, piedra, montaña y luz natural como identidad.
- Verde profundo, oliva, arena/papel y acentos dorados.
- Fotografía real/editorial cuando exista una fuente válida.
- Serif editorial en momentos de marca y sans legible en producto/operación.
- Jerarquía fuerte, superficies limpias y menos ruido visual.
- Una sola familia de botones, chips, estados, métricas, tarjetas, formularios y controles de mapa.
- Movimiento discreto y nunca necesario para operar.
- Gamificación más expresiva, pero sin estética de criptomoneda o inversión.

## Fases hasta cierre

### Fase 0 — Fundaciones visuales
Estado: **implementada; mantener y depurar**.

- tokens canónicos `--ui-*`;
- Topbar;
- BottomNav;
- shell común;
- responsive base;
- foco, teclado, touch targets y reduced motion;
- reducción progresiva de cascadas CSS globales.

### Fase 1 — Entrada pública
Estado: **implementada / QA continuo**.

- Inicio;
- hero y contenido administrable;
- Explorar;
- jerarquía de descubrimiento;
- publicidad/slots sin romper composición;
- loading/empty/error reales.

### Fase 2 — Territorio y aventura
Estado: **implementación avanzada; coordinar con ramas especializadas**.

- Pueblos y fichas territoriales;
- Rutas y detalle;
- mapas/GPX/elevación/seguridad;
- Mágina Aventura;
- Mi Aventura;
- colecciones;
- comunidad de rutas.

La rama especializada de Aventura es la autoridad para su experiencia premium. El frente transversal la incorpora cuando esté verde y sincronizada.

### Fase 3 — Identidad personal: Mi Olivo
Estado: **rama especializada activa**.

Objetivo visual:

1. olivo protagonista como activo digital vivo y coleccionable;
2. nivel, fase, XP histórico y saldo de aceitunas legibles desde la cabecera;
3. 10 etapas visuales distinguibles;
4. progresión al siguiente nivel;
5. misiones y logros;
6. memoria de campaña, estaciones, cuidados e historia;
7. recompensas desbloqueables;
8. canjes y QR claramente separados de XP/progreso.

Mi Olivo no es una wallet ni una criptomoneda. El usuario debe entender que:
- XP = progreso histórico y no se gasta;
- aceitunas = saldo interno de fidelización que puede consumirse;
- recompensa = producto/ventaja real con condiciones;
- reserva = derecho temporal sobre stock;
- QR = comprobante firmado de un solo uso;
- canje = entrega física validada por la almazara.

La rama especializada de Mi Olivo es la autoridad visual mientras esté activa; #138 no debe duplicar ese diseño.

### Fase 4 — Almazaras y economía del AOVE
Estado: **pendiente de pasada transversal completa**.

- directorio premium;
- ficha de almazara/cooperativa;
- catálogo de AOVE/recompensas;
- stock y disponibilidad visibles;
- flujo de reserva;
- QR de un solo uso;
- estados reservado/recogido/cancelado/caducado;
- panel de almazara;
- validación del QR;
- historial del canje;
- patrocinio/condiciones claramente identificados.

Objetivo: cerrar visualmente el circuito:

`Mi Olivo → aceitunas → recompensa → almazara → QR → entrega física → historial`.

### Fase 5 — Empresas, Experiencias y Mágina Pass
Estado: **pendiente de unificación transversal**.

- directorio de empresas;
- ficha comercial/territorial;
- experiencias;
- disponibilidad/condiciones cuando existan datos;
- Mágina Pass;
- beneficios y estado del usuario;
- patrocinio no confundido con contenido editorial.

### Fase 6 — Mi Campo y Perfil
Estado: **base existente; pasada de coherencia pendiente**.

- Mi Campo;
- fincas;
- campaña;
- Hoy/Agenda;
- actividad;
- documentos;
- economía;
- herramientas técnicas;
- Perfil y preferencias.

Mi Campo debe ser más operativo que Mi Olivo: menos decoración, lectura rápida, datos primero.

### Fase 7 — Radar, mapas y herramientas técnicas
Estado: **base existente; consolidación pendiente**.

- radar;
- meteorología;
- GIS/Catastro/SIGPAC;
- controles de mapa;
- sheets/paneles móviles;
- estados de permisos/GPS/offline;
- controles táctiles consistentes.

### Fase 8 — Administración
Estado: **base funcional amplia; unificación visual pendiente**.

- dashboard;
- CMS;
- territorio/pueblos;
- rutas/aventuras;
- empresas;
- almazaras/recompensas/canjes;
- experiencias/Mágina Pass;
- usuarios;
- fuentes;
- analítica/operaciones.

Admin hereda tokens y componentes, pero prioriza densidad, filtros, tablas y productividad de escritorio.

### Fase 9 — Limpieza y consolidación
Estado: **pendiente**.

- eliminar overrides globales obsoletos solo cuando ya no tengan consumidores;
- consolidar tokens duplicados;
- retirar estilos inline evitables;
- reducir especificidad accidental;
- comprobar que ninguna rama especializada haya reintroducido patrones antiguos;
- revisar bundle visual y fuentes de assets.

### Fase 10 — Release candidate visual
Estado: **pendiente**.

- resincronizar con `integrate/v20-beta-closure` vigente;
- resolver solapamientos con ramas especializadas ya verdes;
- captura comparativa móvil/tablet/escritorio;
- QA funcional y visual completo;
- mantener PR Draft hasta que todo el Definition of Done esté cumplido;
- absorción controlada al candidate;
- `main` sigue sin tocarse desde este workstream.

## Definition of Done global

La rama visual solo se considera terminada cuando se cumplan todos estos puntos:

### Coherencia
- un único shell reconocible;
- navegación consistente;
- tokens compartidos;
- headers, cards, métricas, chips, botones y formularios coherentes;
- ningún módulo principal parece una web independiente.

### Responsive
Validación obligatoria en:
- 360 px;
- 390 px;
- 430 px;
- 768 px;
- 1024 px;
- 1280 px;
- 1440 px;
- 1920 px.

Escritorio debe tener composición propia, no ser el móvil ensanchado.

### Accesibilidad e interacción
- sin overflow horizontal;
- objetivos táctiles principales >= 44 px;
- foco visible;
- navegación con teclado;
- contraste suficiente;
- `prefers-reduced-motion` respetado;
- ninguna acción crítica depende de hover;
- safe areas correctas;
- navegación persistente no tapa contenido.

### Estados
Las superficies relevantes tienen diseño explícito para:
- loading;
- empty;
- error;
- stale;
- offline;
- sin sesión;
- permiso GPS denegado/no concedido;
- datos parciales.

No se inventan datos para evitar un estado vacío.

### Integridad funcional
- la rama visual no cambia reglas de negocio salvo corrección imprescindible y separada;
- no duplica APIs ni tablas;
- no altera saldos, XP, stock o seguridad desde CSS/UI;
- mapas, GPX, QR, canjes y autenticación mantienen sus contratos.

### QA final
Sobre el mismo HEAD final deben estar verdes, cuando apliquen:
- TypeScript/typecheck;
- build de producción;
- Full Candidate;
- Browser E2E;
- Responsive E2E;
- accesibilidad/smoke visual;
- Environment Contract;
- Staging Readiness;
- Admin;
- Municipios;
- Rutas;
- Aventura;
- Mi Olivo/recompensas;
- cualquier workflow específico afectado.

## Regla de coordinación de ramas

Si existe una rama especializada activa con una experiencia más avanzada:

1. no duplicar su trabajo en `feat/v20-ui-ux-premium`;
2. mantener en #138 el contrato global y el shell;
3. dejar que la rama especializada cierre su visual/QA;
4. integrar o sincronizar esa rama con el candidate;
5. resincronizar #138;
6. hacer solo la pasada transversal necesaria para que encaje con el resto del producto.

Esto aplica especialmente a Mi Olivo y Mágina Aventura.

## Resultado de cierre

V20 debe poder presentarse como una plataforma terminada de Sierra Mágina con tres capas visualmente conectadas:

**Descubrir Mágina** — territorio, pueblos, rutas, aventura, actualidad, empresas y experiencias.

**Mi Mágina** — Mi Olivo, progreso, recompensas, Mi Campo, perfil y preferencias.

**Gestionar Mágina** — Admin, contenidos, operaciones, almazaras, empresas, rutas, recompensas y usuarios.

La sensación final debe ser la de un solo producto con identidad propia, no la suma de muchas ramas.