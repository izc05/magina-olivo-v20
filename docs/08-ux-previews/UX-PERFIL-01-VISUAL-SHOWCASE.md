# Showcase Visual de Prototipos: Perfil, Cuenta, Roles y Mágina IA 1.0

**Documento:** `docs/08-ux-previews/UX-PERFIL-01-VISUAL-SHOWCASE.md`  
**Referencia técnica:** `CR-UX-PERFIL-01` (PR #811) y `CR-COLLAB-02`  
**Pull Request:** [Draft PR #830](https://github.com/izc05/magina-olivo-v20/pull/830)  
**Estado:** Prototipos visuales independientes en Jetpack Compose Preview.  

> **AVISO DE ALCANCE:** Todos los datos mostrados en este documento y en los prototipos son 100% ficticios. Los roles, flujos de invitación y pantallas son prototipos de experiencia de usuario (UX) y **no constituyen lógica de negocio activa, persistencia real en Room ni autorizaciones activadas en Supabase backend**. La navegación principal en `AppNavigation.kt` permanece inalterada hasta la integración autorizada.

---

## 1. Muestra Visual Interactiva y Comparativa Lado a Lado

Para revisar interactivamente la comparativa estricta entre las **capturas reales de la APK actual** y la **propuesta evolucionada** (conservando el 100% de los componentes, colores, tipografía y el botón central «+» de `MoBottomBar`), consulte:
- [docs/08-ux-previews/profile_redesign_visual_preview.html](profile_redesign_visual_preview.html)
- Capturas reales de referencia del emulador Pixel API 35 archivadas en: `docs/08-ux-previews/captures/`
  * `actual_inicio_home.png` (Pantalla de Inicio con `MoBottomBar` y botón central «+»)
  * `actual_perfil_screen.png` (Pantalla actual de `ProfileScreen.kt`)
  * `actual_mi_olivar.png` (Pantalla actual de `Mi Campo`)
  * `actual_cuaderno.png` (Pantalla actual de `Cuaderno`)
- Capturas de la propuesta evolucionada en dispositivo Pixel 393dp (xx-hdpi):
  * `propuesta_inicio_con_avatar_y_magina_ia.png` (Inicio con avatar superior derecho de 48dp y 4ª pestaña Mágina IA, manteniendo el botón central «+» elevado)
  * `propuesta_panel_perfil_claro_top.png` (Nuevo panel Perfil/Cuenta - Vista superior: cuenta, fincas, datos y herramientas en modo claro)
  * `propuesta_panel_perfil_claro_scrolled.png` (Nuevo panel Perfil/Cuenta - Vista inferior: herramientas de olivar, ajustes, offline «Siempre», ayuda y cerrar sesión)
  * `propuesta_panel_perfil_oscuro.png` (Nuevo panel Perfil/Cuenta - Modo oscuro contrastado)
  * `propuesta_panel_perfil_local_guest.png` (Nuevo panel Perfil/Cuenta - Modo local / invitado sin cuenta sincronizada)

---

## 1.1. Matriz de Cambios: Qué permanece idéntico y qué evoluciona

| Pantalla / Componente | Elementos que Permanecen 100% Intactos | Elementos que Evolucionan |
| :--- | :--- | :--- |
| **Inicio (`HomeScreen.kt`)** | Cabecera de fecha/hora, `MoStatStrip` (kilos, labores, avisos), tarjeta del tiempo (AEMET Sierra Mágina), precios de mercado (Pool Red), tarjeta de campaña y labores recientes. | Se añade el avatar circular (42dp) en la esquina superior derecha, inmediatamente junto a la campana de avisos. |
| **Barra Inferior (`MoBottomBar.kt`)** | Pestaña 1 (Inicio), Pestaña 2 (Mi Campo), **Botón central «+» (52dp circular elevado con `MoIcons.Plus` para añadir registro rápido)** y Pestaña 3 (Cuaderno). | La Pestaña 4 (antiguo Perfil) pasa a denominarse «Mágina IA» con icono de chispa/olivo, en estado de pantalla informativa de reserva para v1.1. |
| **Perfil / Cuenta (`ProfileScreen.kt` → `ProfileAccountPanel.kt`)** | El 100% de las funciones: Municipio, Cooperativa, Mis máquinas (#211), Aplicadores y asesores CUE (#671), Notificaciones, Recordatorios, Modo sin conexión («Siempre»), Ayuda y privacidad, y versión/Catastro INSPIRE. | Pasa de ser una pantalla fija inferior a un panel modular desplegable (`ModalBottomSheet`) accesible desde el avatar superior desde cualquier pantalla, integrando tarjeta de cuenta en cabecera y selector de fincas delegadas. |
| **Roles de Colaboración** | No interfieren con las operaciones actuales ni alteran la persistencia en Room. | Prototipos visuales de los tres roles permitidos (Titular en verde olivo, Gestor en ámbar, Recolectora en azul) y tarjeta de invitación recibida. |
| **Reserva Mágina IA** | Sin chats simulados, sin respuestas ficticias y sin recomendaciones no contrastadas. | Pantalla informativa de reserva serena, explicando las capacidades planificadas para la v1.1 y el compromiso de rigor agronómico y privacidad. |

---

## 2. Los 5 Diseños de Pantalla y Componentes

### 2.1. Barra Superior con Avatar (`MoTopBarProfileAvatar.kt`)
El avatar de acceso a Perfil/Cuenta se traslada al extremo superior derecho, inmediatamente junto a la campana de Avisos:

```
┌──────────────────────────────────────────────────────────────┐
│  Mi Campo                                        [ 🔔 ] [AD] │
│  4 parcelas · 14,8 ha (Muestra)                              │
├──────────────────────────────────────────────────────────────┤
│  ... (Contenido de pantalla de finca / labores) ...          │
```

- **Sesión Iniciada:** Círculo de 42dp en color verde olivo (`MoOlivePrimary` `#3E5A32`), borde refinado e iniciales «AD» en blanco.
- **Modo Local:** Marca de olivo con indicador discreto en tono ámbar de almacenamiento local.
- **Campana de Avisos:** Conserva su acceso completo con indicador de punto de notificaciones pendientes (*unread dot*).

---

### 2.2. Panel Organizado de Perfil y Cuenta (`ProfileAccountPanel.kt`)
Panel modular que reúne todo el inventario existente sin duplicar ni perder accesos:

```
┌──────────────────────────────────────────────────────────────┐
│  Mi Perfil y Cuenta                                     [ ✕ ]│
├──────────────────────────────────────────────────────────────┤
│  [ AD ]  Agricultor Demo                                     │
│          usuario.demo@example.test                           │
│          [ Sesión iniciada · Sincronizado (Demo) ]           │
├──────────────────────────────────────────────────────────────┤
│  MIS EXPLOTACIONES Y ROLES (PROTOTIPO)                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Finca La Esperanza (Demo)                [Propietario] │  │
│  │ Valle Ficticio · 4 parcelas · 14,8 ha                  │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Olivar Los Cerros (Muestra)                   [Gestor] │  │
│  │ Sierra Demo · 2 parcelas · 6,4 ha                      │  │
│  └────────────────────────────────────────────────────────┘  │
│  [ ✉️ ] Mis invitaciones y accesos          [ 1 pendiente ]  │
├──────────────────────────────────────────────────────────────┤
│  DATOS DEL AGRICULTOR Y CAMPO                                │
│  [ 🗺️ ] Municipio preferido (Valle Ficticio)            [ > ] │
│  [ 🚜 ] Cooperativa vinculada (S.C.A. Olivarera Demo)   [ > ] │
├──────────────────────────────────────────────────────────────┤
│  HERRAMIENTAS DEL OLIVAR                                     │
│  [ 🚜 ] Mis máquinas y aperos (PR #211)                 [ > ] │
│  [ 👨‍🌾 ] Aplicadores y asesores CUE (#671)               [ > ] │
├──────────────────────────────────────────────────────────────┤
│  CUENTA Y SINCRONIZACIÓN                                     │
│  [ 🛡️ ] Gestión de cuenta (usuario.demo@example.test)   [ > ] │
├──────────────────────────────────────────────────────────────┤
│  AJUSTES Y SOPORTE                                           │
│  [ 🔔 ] Notificaciones de labores                 [ Activas ]│
│  [ 🌓 ] Apariencia (Claro / Oscuro)                     [ > ] │
│  [ ✈️ ] Modo sin conexión                         [ Siempre ]│
│  [ ℹ️ ] Ayuda y privacidad                              [ > ] │
│  Mágina Olivo · Versión 1.0.0 (Prototipo)                    │
├──────────────────────────────────────────────────────────────┤
│  [             CERRAR SESIÓN (Destructive)                ]  │
└──────────────────────────────────────────────────────────────┘
```

---

### 2.3. Diseño de los 3 Roles de Colaboración (`CollaborationRolesUi.kt`)

| Rol | Distintivo Visual | Responsabilidad Principal | Alcance Permitido | Límites de Seguridad |
| :--- | :--- | :--- | :--- | :--- |
| **Titular / Propietario** | Verde Olivo (`#3E5A32`) | Control total de la finca | Catastro, parcelas, contratos de entrega, finanzas completas. | Ninguno en sus explotaciones. |
| **Gestor / Arrendatario** | Ámbar / Ocre (`#805B18`) | Gestión agronómica y cuaderno | Labores del olivar, tratamientos CUE, jornales, pesadas de báscula. | Sin acceso a datos bancarios ni contratos del titular. |
| **Empresa Recolectora** | Azul Pizarra (`#4C6876`) | Cosecha y entregas | Partes de cuadrilla, fotos de báscula y propuesta de costes. | Acotado a parcelas y campaña vigentes. Sin finanzas globales. |

---

### 2.4. Flujo Visual de Invitaciones (`InvitationFlowUi.kt`)
- **Emisión en 3 pasos:** Rol (Propietario / Gestor / Recolector) → Alcance (Toda la finca / Parcelas; Campaña vigente) → Vía (WhatsApp, QR presencial, código de 6 dígitos, enlace HTTPS).
- **Aceptación transparente:** Tarjeta en el panel de Perfil con el nombre del emisor, finca, rol propuesto y resumen de permisos antes de pulsar `Aceptar invitación` o `Rechazar`.

---

### 2.5. Reserva de la 4ª Pestaña Inferior: Mágina IA (`AiAssistantPlaceholderScreen.kt`)
Pantalla de reserva elegante, serena y sin promesas ficticias:

```
┌──────────────────────────────────────────────────────────────┐
│                            [ ✨ ]                            │
│                          Mágina IA                           │
│               Asistente inteligente del olivar               │
│             [ En preparación para la versión 1.1 ]           │
├──────────────────────────────────────────────────────────────┤
│  ¿QUÉ PODRÁS HACER CON MÁGINA IA?                            │
│  💬 Consultas en lenguaje natural sobre labores y fitosanitarios│
│  📷 Lectura asistida de albaranes de báscula y facturas      │
│  📊 Resumen comparativo de rendimientos grasos y costes      │
│  🌦️ Alertas agroclimáticas preventivas de Sierra Mágina      │
├──────────────────────────────────────────────────────────────┤
│  COMPROMISO DE RIGOR AGRONÓMICO                              │
│  Mágina IA se basará exclusivamente en los datos de tus      │
│  cuadernos y fuentes oficiales. Nunca inventará datos ni     │
│  sustituirá al técnico asesor ni a la cooperativa.           │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Matriz de Compatibilidad con el Código Base

| Componente Preexistente | Estado en esta PR | Justificación |
| :--- | :--- | :--- |
| `ProfileScreen.kt` | Intacto | Sigue siendo la pantalla funcional activa de la app hasta la integración. |
| `PersonalProfile.kt` | **Retirado de PR #830** | Evita duplicar lógica con PR #781/#794/#796; los prototipos usan mocks aislados. |
| `ProfileAvatarStore.kt` | **Retirado de PR #830** | Evita conflictos de persistencia de avatar con ramas activas. |
| `AppNavigation.kt` | Intacto | Cero alteraciones en las rutas reales o el enrutamiento de producción. |
| `Theme.kt` / `AppearanceMode` | Respetado | Previews adaptados a `MaginaOlivoTheme(mode = AppearanceMode.LIGHT/DARK)`. |
| `MoStatusChip` | Respetado | Llamadas adaptadas a la firma `text = ...`. |

---

## 4. Vistas 3 y 4 — Roles con alcance e invitaciones (prototipo UX)

Archivo: `app/src/main/java/com/isivoltpro/maginaolivo/feature/profile/ui/CollaborationScopeUi.kt` (sin navegación, sin Supabase/Auth/Room).

| Preview | Contenido |
| :--- | :--- |
| `RolesScopePreview_Light` / `_Dark` | Tres roles con alcance finca · parcelas · campaña · validez, y «Podrá (permiso futuro, no activo)» / «No podrá». |
| `CreateInvitationPreview_Light` / `_Dark` | Finca → rol → alcance → duración → compartir (enlace, WhatsApp, QR). Estado visual local. |
| `InvitationsSentPreview_Light` / `_Dark` | Enviadas: pendiente, activa, en cola sin conexión, error, caducada. Acciones cancelar/revocar/reintentar. |
| `InvitationsReceivedPreview_Light` | Recibidas: pendiente (aceptar/rechazar), aceptada confirmando, revocada. |

Regla de diseño: `InvitationLifecycle.grantsAccess` solo es `true` en `ACTIVE_CONFIRMED`. Los estados `QUEUED_OFFLINE`, `AWAITING_SERVER` y `ACCEPTED_AWAITING_SERVER` dicen explícitamente «Aún no da acceso».

## 5. Pendientes de integración de Perfil/Cuenta (no resueltos en #830)

- `ReminderSettings` completo con `MyProfileViewModel.saveReminders(preferences)`, sin reducirlo a un interruptor.
- Selector de municipio y de cooperativa existentes.
- Herramientas DEV (Finca Demo, catálogo) condicionadas a builds DEV.

## 6. Registro de CI

- SHA `2d67bd563`: la ejecución **push** de Android CI tuvo un `ComposeTimeoutException` en un test instrumentado de autenticación (fallo histórico, no corregido por código en esta PR). La ejecución **pull_request** del mismo SHA terminó en verde: Foundation, Gate 3 Emulator y Gate 3 Evidence.

- SHA `b73c35c9d`: Gate 3 Evidence falló en `ArchitectureBoundaryTest.infrastructureOutsideAuthorizedAuthRemainsBlocked` porque un comentario KDoc de `CollaborationScopeUi.kt` nombraba el backend prohibido. Corregido en el commit siguiente; además se retiraron `remindersEnabled`/`onToggleReminders` de `ProfileAccountPanel` (`ReminderSettings` completo sigue siendo requisito de integración) y todas las acciones de invitación quedan deshabilitadas y rotuladas «(prototipo)».

---

## 7. Auditoría Visual en Jetpack Compose Real (H1–H4 y M1–M4) y Galería PNG Accesible

Todas las capturas de esta sección han sido obtenidas desde el motor de renderizado real de **Jetpack Compose** (`createAndroidComposeRule<ComponentActivity>().onRoot().captureToImage()`) en emulador Android API 35 (`393 dp` estándar en claro/oscuro + `320 dp` con fuente ampliada `1.30x`).

### 7.1. Resumen de las 8 Correcciones Visuales Aplicadas

| ID | Problema detectado en Compose real (ANTES) | Corrección aplicada (DESPUÉS) | Archivos modificados |
| :--- | :--- | :--- | :--- |
| **H1** | En `FarmMembershipCard`, el nombre de la finca y el badge `"Principal"` compartían fila con `RoleBadge`, comprimiendo `"Principal"` a 9 líneas de 1 letra y estirando la tarjeta a ~160 dp. | Cabecera superior de metadatos con `RoleBadge` a la izquierda y badge `"Principal"` (`maxLines = 1, softWrap = false`) a la derecha; nombre de finca y superficie (`\u00A0ha`) a ancho completo debajo. | `CollaborationRolesUi.kt` |
| **H2** | Fondos claros fijos (`0xFFFBF8F2`, `0xFFFAF7EE`, `0xFFFDF6E2`, `MoOliveTint`) hacían ilegible el texto claro en Modo Oscuro (Mágina IA, `InviteCollaboratorSheet`, `RoleBadge`, píldora `"1 pendiente"`). | Sustitución por tokens reactivos de `MoColors.current` (`actionTint`/`actionText`, `warningTint`/`warningText`, `infoTint`/`infoText`) y `MoStatusChip(tone = MoStatusTone.Warning)`. | `AiAssistantPlaceholderScreen.kt`, `CollaborationRolesUi.kt`, `CollaborationScopeUi.kt`, `InvitationFlowUi.kt`, `ProfileAccountPanel.kt` |
| **H3** | El punto indicador de sincronización (9 dp) en `ProfileAvatarIconButton` quedaba cortado por el `.clip(CircleShape)` del `Surface(42.dp)` y del `Box(48.dp)` exterior. | Punto indicador de `11.dp` posicionado en el `Box(48.dp)` exterior sin recorte circular y con borde adaptativo `MoSurfaceTokens.cardSurface`. | `MoTopBarProfileAvatar.kt` |
| **H4** | En `SentInvitationCard` e `InvitationLifecycleCard`, los emails largos (`cuadrilla.demo@example.test`) y `RoleBadge` en la misma fila partían `"Recolector\na"` o `.test` huérfano. | `RoleBadge` (y `MoStatusChip` en `FlowRow`) en la fila superior de la tarjeta y el email debajo a ancho completo con `maxLines = 1, overflow = TextOverflow.Ellipsis`. | `InvitationFlowUi.kt`, `CollaborationScopeUi.kt` |
| **M1** | En `ReceivedInvitationCard`, el título `"Invitación de colaboración (Diseño)"` y la fecha `"Recibida hoy, 10:30 (Muestra)"` chocaban en la misma línea. | Cabecera apilada con la marca temporal alineada bajo el título (`padding(start = 14.dp)`). | `InvitationFlowUi.kt` |
| **M2** | Subtítulos largos truncados con `...` en `ProfileAccountPanel` (`Gestión de cuenta`, `Municipio preferido`, etc.). | Subtítulos concisos que entran completos en 1 línea a `393 dp` y en 2 líneas a `320 dp / 1.30x`. | `ProfileAccountPanel.kt` |
| **M3** | Botón de cierre `✕` del panel de `36.dp` con carácter de texto y botón volver `"←"` con texto descentrado en barra superior. | Botón de cierre de `48.dp` con nuevo icono vectorial `MoIcons.Close` y botón volver de `48.dp` con `MoIcons.ChevronLeft`. | `MoIcons.kt`, `ProfileAccountPanel.kt`, `MoTopBarProfileAvatar.kt` |
| **M4** | `ChoiceGroup` en «Crear invitación» usaba `options.chunked(2)`, forzando saltos de fila artificiales en grupos de 3 chips cortos (`Titular`, `Gestor`, `Recolectora` / `Enlace`, `WhatsApp`, `Código QR`). | Uso de `FlowRow` con espaciado uniforme de `8.dp` y altura mínima táctil de `48.dp`. | `CollaborationScopeUi.kt` |

---

### 7.2. Comparativa Visual ANTES / DESPUÉS (Jetpack Compose Real)

#### H3 y M3 — Barra Superior con Avatar (`MoTopBarProfileAvatar.kt`)
| ANTES (Punto de estado cortado y flecha de texto) | DESPUÉS (Punto completo fuera del círculo e icono vectorial `<`) |
| :---: | :---: |
| ![Antes V1 Claro](captures/compose-real/antes/v1_topbar_avatar_states_light.png) | ![Después V1 Claro](captures/compose-real/vista-1-cabecera-ia/v1_topbar_avatar_states_light.png) |
| ![Antes V1 Oscuro](captures/compose-real/antes/v1_topbar_avatar_states_dark.png) | ![Después V1 Oscuro](captures/compose-real/vista-1-cabecera-ia/v1_topbar_avatar_states_dark.png) |

#### H2 — Mágina IA en Modo Oscuro (`AiAssistantPlaceholderScreen.kt`)
| ANTES (Tarjeta inferior crema fija ilegible en oscuro) | DESPUÉS (Superficie adaptativa `MoColors.current.actionTint`) |
| :---: | :---: |
| ![Antes Mágina IA Oscuro](captures/compose-real/antes/v1_magina_ia_placeholder_top_dark.png) | ![Después Mágina IA Oscuro](captures/compose-real/vista-1-cabecera-ia/v1_magina_ia_placeholder_top_dark.png) |

#### H1, H2, M2 y M3 — Panel Perfil y Cuenta (`ProfileAccountPanel.kt`)
| ANTES (Finca principal estirada, `"1 pendiente"` fijo, cierre 36dp y truncamientos) | DESPUÉS (Tarjeta compacta con `"Principal"` íntegro, cierre 48dp vectorial y subtítulos completos) |
| :---: | :---: |
| ![Antes V2 Claro Top](captures/compose-real/antes/v2_panel_signedin_synced_top_light.png) | ![Después V2 Claro Top](captures/compose-real/vista-2-panel-perfil/v2_panel_signedin_synced_top_light.png) |
| ![Antes V2 Oscuro Top](captures/compose-real/antes/v2_panel_signedin_synced_top_dark.png) | ![Después V2 Oscuro Top](captures/compose-real/vista-2-panel-perfil/v2_panel_signedin_synced_top_dark.png) |
| ![Antes V2 Claro Bottom](captures/compose-real/antes/v2_panel_signedin_synced_bottom_light.png) | ![Después V2 Claro Bottom](captures/compose-real/vista-2-panel-perfil/v2_panel_signedin_synced_bottom_light.png) |

#### H1 y H2 — Tarjetas de Explotaciones y Roles en Modo Oscuro (`CollaborationRolesUi.kt`)
| ANTES | DESPUÉS |
| :---: | :---: |
| ![Antes V3 Tarjetas Oscuro](captures/compose-real/antes/v3_roles_cards_top_dark.png) | ![Después V3 Tarjetas Oscuro](captures/compose-real/vista-3-roles/v3_roles_cards_top_dark.png) |

#### H2 y M4 — Crear Invitación (`CollaborationScopeUi.kt`)
| ANTES (Chips cortados en 2 por fila y `MoOliveTint` fijo en oscuro) | DESPUÉS (`FlowRow` con 3 chips en línea y tokens adaptativos) |
| :---: | :---: |
| ![Antes V4 Crear Claro](captures/compose-real/antes/v4_create_invitation_top_light.png) | ![Después V4 Crear Claro](captures/compose-real/vista-4-invitaciones/v4_create_invitation_top_light.png) |
| ![Antes V4 Crear Oscuro](captures/compose-real/antes/v4_create_invitation_top_dark.png) | ![Después V4 Crear Oscuro](captures/compose-real/vista-4-invitaciones/v4_create_invitation_top_dark.png) |

#### H2, H4 y M1 — Invitaciones Enviadas, Detalle y Hoja de Envío (`InvitationFlowUi.kt` y `CollaborationScopeUi.kt`)
| ANTES (`Recolector\na`, `.test` huérfano y texto blanco sobre crema en oscuro) | DESPUÉS (Badges íntegros, emails completos en 1 línea y contraste oscuro correcto) |
| :---: | :---: |
| ![Antes V4 Enviadas Claro](captures/compose-real/antes/v4_invitations_sent_top_light.png) | ![Después V4 Enviadas Claro](captures/compose-real/vista-4-invitaciones/v4_invitations_sent_top_light.png) |
| ![Antes V4 Detalle Oscuro](captures/compose-real/antes/v4_invitation_cards_detail_dark.png) | ![Después V4 Detalle Oscuro](captures/compose-real/vista-4-invitaciones/v4_invitation_cards_detail_dark.png) |
| ![Antes V4 Sheet Oscuro](captures/compose-real/antes/v4_invite_collaborator_sheet_dark.png) | ![Después V4 Sheet Oscuro](captures/compose-real/vista-4-invitaciones/v4_invite_collaborator_sheet_dark.png) |

---

### 7.3. Validación en Pantalla Estrecha (`320 dp`) y Fuente Ampliada (`1.30x`)

| Vista en estrés (`320 dp` + `fontScale = 1.30f`) | Modo Claro | Modo Oscuro |
| :--- | :---: | :---: |
| **Vista 1 — Barra Superior con Avatar** | ![Stress V1 Claro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v1_topbar_light.png) | ![Stress V1 Oscuro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v1_topbar_dark.png) |
| **Vista 2 — Panel Perfil y Cuenta** | ![Stress V2 Claro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v2_panel_top_light.png) | ![Stress V2 Oscuro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v2_panel_top_dark.png) |
| **Vista 4 — Bandeja de Invitaciones** | ![Stress V4 Invitaciones Claro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v4_invitations_light.png) | ![Stress V4 Invitaciones Oscuro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v4_invitations_dark.png) |
| **Vista 4 — Crear Invitación (`FlowRow`)** | ![Stress V4 Crear Claro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v4_create_light.png) | ![Stress V4 Crear Oscuro](captures/compose-real/stress-narrow-font130/stress_narrow320_font130_v4_create_dark.png) |

