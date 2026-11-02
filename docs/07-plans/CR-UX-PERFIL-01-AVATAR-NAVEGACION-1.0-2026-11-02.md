# Mágina Olivo 1.0 — CR-UX-PERFIL-01: Perfil en avatar superior, navegación y secciones integradas

**Fecha:** 2026-11-02.  
**Estado:** decisión funcional aprobada por el propietario; documento de implementación y QA, **no implementado**.  
**Prioridad:** P0 de experiencia y cierre 1.0, una vez estabilizada G2.6 y antes de la APK unificada de revisión visual.  
**Rama de esta propuesta:** documental y aislada, basada en main; no representa la integración del código de PR Draft.

> **Regla principal:** sacar Perfil de la pestaña inferior y abrirlo desde un **avatar arriba, junto a la campana**, sin perder ni duplicar ninguna funcionalidad que ya existe. El hueco inferior se reserva para **Asistente IA (futuro)**. La navegación, la Cuenta y las invitaciones deben formar un conjunto coherente. No fusionar ni desplegar por el mero hecho de aprobar esta especificación.

## 1. Decisiones de producto fijadas el 01–02/11/2026

1. **Avatar en la barra superior:** acceso global reconocible a «Mi cuenta / Perfil». Mismo lugar en los destinos raíz que dispongan de cabecera, junto a Avisos; en pantallas anidadas debe mantener un patrón consistente que no tape título, Atrás ni acciones. No confundir tocar el avatar con cerrar sesión.
2. **Perfil deja de ser destino de la barra inferior.** Conservar la ruta de Perfil como destino secundario real (para enlaces internos, Atrás y restauración), no borrar su funcionalidad.
3. **Barra inferior:** Inicio · Mi Campo · Cuaderno · **Asistente IA**; conservar el botón central **+** de registro. Actualmente CR-014 ya tiene cuatro pestañas (Inicio, Mi Campo, Cuaderno, Perfil) y **+** central: no añadir una quinta por error.
4. **Campana de Avisos:** mantener acceso superior a Avisos/Calendario, la ruta y los recordatorios; NO eliminar ni duplicar una pestaña inferior de Avisos que en el shell actual ya no existe. Respetar el contexto y navegación de los avisos.
5. **Asistente IA inferior:** solo reservar icono y destino para una futura integración. Si es pulsable antes de existir el agente, mostrar una pantalla inequívoca «Asistente en preparación», sin chat ficticio, recomendaciones inventadas ni promesa de IA operativa. El futuro botón/burbuja contextual de Inicio se diseñará aparte.
6. **Reorganización sin sustitución:** aprovechar ProfileRoute, ProfileScreen, MyProfile, PersonalProfile, Cuenta, Ayuda y ajustes actuales. No crear una segunda fuente de perfil, usuarios, cooperativas, permisos o maquinaria.
7. **Colaboración:** «Invitaciones recibidas / Mis accesos» es global y va en Perfil; «Personas y acuerdos / Invitar / Administrar permisos» corresponde a una finca y va en Mi Campo > Finca. Los roles visibles acordados son titular, explotador/gestor y recolector. Jornalero manual, sin cuenta obligatoria en 1.0.
8. **Secuencia:** G2.6 personal segura y verificada -> integración de Perfil/Cuenta -> roles/invitaciones y navegación -> inventario ligero y otros módulos listos -> QA global -> APK unificada de revisión. Se admiten APK desechables de pruebas durante el trabajo, pero no llamar «APK final de revisión» a una compilación de ramas parciales.

## 2. Inventario real que debe preservarse

Auditoría de código base para esta especificación: rama Draft #796 @ af652410886d (02/11/2026), que incluye ProfileScreen.kt, MyProfile.kt, PersonalProfile.kt, AccountScreen.kt, HelpScreens.kt y AppNavigation.kt. Este inventario demuestra **código presente en una rama**, no garantiza integración final, navegación aprobada ni funcionalidad desplegada.

| Función/elemento existente | Ubicación / referencia | Destino propuesto después de reorganizar | Cuidado especial |
| --- | --- | --- | --- |
| Cabecera personal, iniciales, foto privada | PersonalProfileHeader, PersonalProfile, ProfileAvatarStore; #781/#794 | Cabecera del nuevo menú y «Mi perfil» | Foto privada por cuenta; no mostrar avatar de otra identidad A -> B -> A |
| Municipio y provincia preferidos | MyProfileSection, MyProfileViewModel, ProfileRepository | «Mi perfil > Datos del campo» | La preferencia actual está asociada a workspace; **no confundir con domicilio ni identidad legal de usuario** |
| Cooperativa preferida / alta | MyProfileSection y OrganizationRepository | «Mi perfil > Cooperativa» o acceso directo desde datos del campo | Reutilizar organización por ID; no duplicar cooperativa |
| Mis máquinas | ProfileScreen -> onMachinery -> MachineryRoute; PR #211 ya fusionada | «Mi Campo > Maquinaria» **y/o** entrada «Herramientas del campo» dentro de Perfil | Conservar ruta e historial; futuro inventario fotográfico extiende la ficha, no crea otro catálogo |
| Aplicadores y asesores | ProfileScreen -> onAgronomicPeople, recursos CUE | «Herramientas del campo > Aplicadores y asesores» (o acceso equivalente de Cuaderno) | Nunca desaparecer al quitar antigua pestaña; NIF y credenciales protegidos |
| Apariencia / tema | AppearanceSettings, AppearanceStore | «Preferencias > Apariencia» | Persistencia, sistema/claro/oscuro, reinicio y cambio de cuenta |
| Notificaciones reales Android | ProfileScreen, NotificationPermissionAccess | «Preferencias > Notificaciones» | Diferenciar permiso del sistema/canal y preferencias de recordatorios |
| Avisos / recordatorios (hora y activación) | ReminderSettings, MyProfileViewModel | «Preferencias > Recordatorios» | Cambios de preferencias reconcilian alarmas; no activar permiso automáticamente |
| Modo sin conexión | ProfileStatusRow y HelpTopic.OFFLINE | «Preferencias > Uso sin conexión» + «Ayuda» | La actividad local funciona offline dentro del permiso válido; clima, mapas y sincronización precisan red |
| Cuenta: registro, login, recuperación y logout | AccountRoute, AccountRepository, AuthGateHost; #795 | «Cuenta y seguridad» | Conservar Auth Gate y estado SIGNED_IN/OFFLINE/EXPIRED; nunca saltarse autenticación |
| Selección de workspace, vinculación y consentimiento | AccountRoute, WorkspaceRepository, FarmLinkPanel; #796 y G2.6 | «Cuenta y seguridad > Espacio de trabajo y datos» | Selección y permisos fail-closed; no atribuir fincas legacy silenciosamente |
| Ayuda: novedades, privacidad, offline | HelpTopic.NEWS, PRIVACY, OFFLINE | «Ayuda y soporte», «Privacidad y datos» | Reutilizar rutas, actualizar textos que ya no describan correctamente las cuentas |
| Versión, fuente Catastro/INSPIRE | ProfileScreen sección Acerca de | «Acerca de Mágina Olivo» | Conservar aviso de que copia Catastro no es certificación oficial |
| Catálogo visual, DEMO y simulaciones DEV | ProfileScreen y devTools | «Herramientas de desarrollo» solo DEV | Jamás mostrar a usuarios finales; pruebas no tocan datos operativos |

**Pendiente obligatorio antes de modificar navegación:** revisar cada componente anterior sobre la **rama integradora definitiva** y marcar su ruta real, estado, prueba de apertura, persistencia y retorno. La lista deriva de la rama de #796 y puede cambiar con otros merges. La implementación no puede cerrar este paso basándose solo en la presente tabla.

## 3. Estructura visual objetivo

### 3.1 Cabecera

- Logo/nombre Mágina Olivo según la pantalla, campana accesible y **avatar circular** alineado a la derecha.
- Foto real privada cuando exista y pertenezca a la cuenta actual; iniciales como alternativa; icono neutro si no hay perfil personal verificado.
- Etiquetas accesibles «Abrir perfil y cuenta» y «Abrir avisos»; tamaño de toque adecuado, modo oscuro, texto grande y lectores de pantalla.
- El avatar abre una **superficie de menú o pantalla secundaria organizada**. La elección exacta (panel desde arriba/hoja o nueva pantalla) se valida por navegación, accesibilidad, tamaño de contenidos y Android Atrás; no construir un menú minúsculo con siete rutas apiladas.
- El acceso no puede revelar nombre, email, foto ni finca de una cuenta anterior durante logout, cambio de identidad, sesión caducada o restauración.
- Si la aplicación se encuentra en Auth Gate, **no** exponer shell agrícola ni avatar que lleve a datos protegidos.

### 3.2 Barra inferior actual y futura

| Posición | Actual CR-014 | Propuesta aprobada |
| --- | --- | --- |
| 1 | Inicio | Inicio (sin cambiar su ruta) |
| 2 | Mi Campo | Mi Campo (sin cambiar su ruta) |
| Centro | + (acción global) | + (misma acción; no pestaña ni destino nuevo) |
| 3 | Cuaderno | Cuaderno (sin cambiar su ruta) |
| 4 | Perfil | **Asistente IA** (reserva de destino) |

**Avisos** sigue accesible por la campana y conservando su ruta propia. No reordenar destinos ni reinterpretar índices de navegación como permisos; conservar deep links, estado de cada raíz, selección de finca, cuaderno y navegación de Atrás.

### 3.3 Árbol propuesto del nuevo Perfil

**Cabecera Mi cuenta:** foto/iniciales, nombre visible, correo solo si la sesión está verificada; estado breve «Sin conexión» cuando corresponda; opción «Editar mi perfil».

**A. Mi perfil**
- Foto y nombre visible (si la edición de nombre no existe, marcar «pendiente» hasta implementarla; no fingir guardado).
- Municipio y provincia usados por el campo; cooperativa preferida.
- Mi relación con cada finca (solo información declarada; los permisos efectivos se obtienen de autorización real).

**B. Cuenta y seguridad**
- Estado de sesión, correo, iniciar sesión/registro/recuperación, cambio de contraseña mediante flujo real.
- Workspace activo, selección autorizada y preparación de vinculación con los controles ya existentes.
- Cerrar sesión (acción diferenciada, confirmación si hay riesgo de trabajo local pendiente).
- «Cerrar/eliminar cuenta» solo si existe flujo extremo a extremo real, con protección y política de datos aprobadas; jamás enlazar a una acción destructiva simulada.

**C. Personas y colaboración**
- «Invitaciones recibidas»: pendientes, aceptadas, rechazadas, caducadas y posibilidad de introducir código; conserva la invitación tras registrarse.
- «Mis accesos y roles»: titular, gestor o recolector en las fincas autorizadas; estado de acceso y vigencia.
- «Fincas compartidas»: acceso a la finca canónica desde Mi Campo; no clonar datos en Perfil.
- Para **enviar** invitaciones, editar permisos y acuerdos: dirigir a Mi Campo > Finca > Personas y acuerdos. Mostrar esta opción únicamente si el permiso efectivo lo permite.
- Todas estas entradas son **especificación pendiente de implementación** conforme a #789/#806; no habilitar falsas invitaciones ni permisos antes de backend seguro.

**D. Preferencias**
- Apariencia (sistema / claro / oscuro).
- Notificaciones (permiso Android y canal) y recordatorios (preferencias guardadas): controles distintos.
- Uso sin conexión, ámbito y límites reales de sincronización.

**E. Herramientas del campo**
- Mis máquinas, historial y selección en trabajos (reutilizar módulo existente).
- Aplicadores y asesores / credenciales de cuaderno.
- No crear una segunda base de maquinaria ni mover datos a identidad personal por defecto.

**F. Ayuda y privacidad**
- Cómo usar la app / ayuda y soporte (el contacto deberá ser real antes de publicarse).
- Novedades; privacidad y datos; información sobre modo avión.
- Acerca de, versión/build y advertencia de datos Catastro/INSPIRE.
- Exportación, copia, restauración y eliminación: accesos solo cuando exista implementación verificable y aviso claro de sus límites.

**G. Zona DEV exclusiva**
- Catálogo de diseño, finca demo y simulador, con guardas de build y sin fuga a producción.

**La estructura agrupa y enlaza pantallas existentes**: no exige siete bases de datos ni siete pantallas nuevas. Subpantallas únicamente donde aporten claridad.

## 4. Personas y acuerdos: separación global / por finca

La propuesta de detalle aprobada está en PR #806 (CR-COLLAB-02) y las plantillas de roles en PR #789. Esta CR es su **punto de entrada visual**, no sustituye los contratos ni autoriza despliegues.

- Perfil > Invitaciones recibidas / Mis roles: quién me ha invitado, qué finca, qué permite, vigencia, aceptar/rechazar y estado.
- Mi Campo > Finca > Personas y acuerdos: Conectados, Pendientes, Permisos, Acuerdos/encargos, Invitar, Revocar e historial. Solo fincas con alcance verificado.
- Invitar: rol -> finca/campaña/parcelas/encargo y capacidades -> compartir por WhatsApp, QR, enlace HTTPS, código (correo opcional). Aceptación bilateral cuando corresponda; token de un uso y vencimiento; el QR no autoriza por sí solo.
- Titular, explotador/gestor y recolector son **plantillas visibles**: cada capacidad se verifica por cuenta, finca y permiso real. Jornalero manual sin cuenta forzosa.
- **Un farm_id y una pesada canónica** entre los usuarios autorizados; no duplicar finca, jornales, importes, fotografía ni contrato en la aceptación.
- Trabajo offline solo dentro de una autorización válida; revocación y cambio de cuenta deben bloquear acceso. Antes de implementar colaboración corregir/revisar riesgo de grants #804 y cerrar G2.6 A <-> B.
- Recolector limitado al encargo, sin acceso indiscriminado a finanzas; reparto 70/30 de kilos es acuerdo distinto de gastos o autorización de aplicación.

## 5. Reglas de navegación y seguridad

1. No se elimina ProfileRoute ni AppDestination.Account: se cambian entradas, jerarquía y dueño de ruta, con compatibilidad de deep links y estado persistido.
2. La raíz de Perfil deja de ser **tab seleccionable**, pero las rutas internas siguen accesibles desde avatar. Ajustar rootForRoute y navigateToRoot cuidadosamente, sin romper botón + ni volver accidentalmente a una tab extinta.
3. Pulsación real de **Atrás**: cerrar primero teclado/ModalBottomSheet cuando esté visible; después subpantalla y finalmente volver al destino agrícola anterior. No despachar directamente al NavHost si un panel modal está activo.
4. El enlace Cuenta desde Perfil, la vuelta tras login/recuperación y la invitación pendiente deben funcionar sin dejar rutas inaccesibles ni duplicar pantallas.
5. Un usuario no autorizado no ve ni abre datos por deep link, restauración del proceso, cache, botón superior, un cambio de cuenta A -> B -> A, ni navegando en offline con permiso vencido.
6. Foto/avatar y preferencias por cuenta permanecen separados de los ajustes del workspace y de los datos de cada finca; no asumir que municipio/cooperativa son información legal personal.
7. El acceso IA futuro no solicita datos de finca o sesión a servicios externos, ni crea peticiones, gastos o registros. Un placeholder no se presenta como chat real.
8. Mantener y comprobar rutas de máquinas, asesores, ayuda, Cuenta, privacidad, Avisos, Catastro, DEV, además de las rutas agrícola existentes.

## 6. Diseño y experiencia

- Paleta Mágina Olivo crema / verde olivo; misma tipografía, iconos y componentes reales, sin calcar la competencia.
- Cabecera compacta y menú con tarjetas/secciones legibles, no una lista interminable sin jerarquía.
- En pantallas pequeñas 360 y 390 dp, fuente 1,3, claro y oscuro: avatar y campana no se solapan; texto y estados no se cortan; zonas táctiles accesibles.
- Mostrar solo acciones que existan: si una función requiere internet o no está desarrollada, estado «Necesita conexión» / «En preparación», nunca feedback falso de éxito.
- Evitar que el menú sustituya las acciones contextuales de finca: «Invitar» no debe abrirse sin finca y permisos.
- No mezclar cuenta personal, ajustes de workspace, permisos de terceros ni datos productivos en la misma fuente de verdad.

## 7. Secuencia de trabajo y condiciones de entrada

| Puerta | Alcance | Condición mínima |
| --- | --- | --- |
| G0 | Congelar inventario de Perfil/Cuenta en **rama integrada** | Tabla §2 marcada función a función, con rutas y controles verificados |
| G1 | Terminar base G2.6 / #795 / #796 y riesgos #807/#808/#810 | Seguridad, CI, migraciones y pruebas reales A -> Supabase -> B; sin declarar GO por suites aisladas |
| G2 | Nuevo shell Avatar + IA placeholder | Pantallas actuales accesibles, botón +, campana y Atrás equivalentes; regresiones de navegación verdes |
| G3 | Reorganizar Perfil/Cuenta conservando lo existente | Persistencia por cuenta/workspace, ayuda, recordatorios, DEV y accesos probados |
| G4 | Roles e invitaciones de #789/#806 | Contrato y RLS revisados, #804 cerrado, permisos e invitaciones seguros en servidor; pruebas A/B/C/D |
| G5 | Inventario fotográfico ligero y selección en trabajos; integrar riego/tiempo aprobados | Reusar PR #211, no duplicar; revisar PR #790 y #734 sobre base definitiva |
| G6 | Integración global y APK unificada de revisión | CI completo en SHA final, sin errores Gate 3, pruebas de campo/QA offline, cuentas, privacidad y pantallas |

**Trabajo paralelo permitido:** documentar, diseñar y probar en ramas/worktrees aislados; no fusionar cambios que dependan de la migración/autorización hasta cerrar G1. No convertir este documento en orden de programación automática.

## 8. Casos de aceptación que debe cubrir la QA

- **UX-01** Inicio -> avatar -> Perfil -> Mi perfil -> atrás -> Inicio. Repetir desde Mi Campo, Cuaderno y Avisos.
- **UX-02** Barra inferior de cuatro destinos (Inicio, Mi Campo, Cuaderno, Asistente) + acción central + conserva los flujos de registro; «Perfil» ya no es tab.
- **UX-03** Campana sigue abriendo Avisos/Calendario con vuelta correcta; sin duplicar pestañas.
- **UX-04** Asistente muestra solo «En preparación» sin tráfico de IA ni escrituras hasta que se implemente.
- **UX-05** Avatar/foto/identidad A -> logout -> B -> logout -> A; foto y email de la otra cuenta nunca aparecen.
- **UX-06** Municipio, provincia y cooperativa persisten en su workspace; cambio de cuenta/workspace no intercambia datos ni duplica organizaciones.
- **UX-07** Mis máquinas y Aplicadores/asesores siguen accesibles; navegación y volver funcionan; máquinas archivadas conservan histórico.
- **UX-08** Apariencia y recordatorios persisten tras reinicio; permiso Android denegado no se presenta como notificaciones activadas.
- **UX-09** Cuenta: registro/login/recuperación, sesión offline, expiración, workspace autorizado y logout; no saltar Auth Gate ni hacer vinculación implícita.
- **UX-10** Menú con teclado abierto y con bottom sheet activo: Atrás cierra primero la capa superior, sin salir de la pantalla agrícola.
- **UX-11** Ayuda, privacidad, novedades, versión y Catastro siguen accesibles, con texto veraz según Auth y sync reales.
- **UX-12** Un invitado recibe por WhatsApp/QR/código, se registra y recupera su invitación; no recibe datos antes de aceptar/confirmar.
- **UX-13** Concesión revocada, vencida, usuario C/D ajeno y deep link incorrecto: fail-closed, sin datos privados.
- **UX-14** Titular/gestor/recolector comparten una única finca canónica; pesada/ticket se registra una vez; finanzas restringidas.
- **UX-15** Sin cobertura, recreación de Activity, rotación, fuentes grandes y modo oscuro; no se pierden cambios locales ni posición de menú.
- **UX-16** Demo/galería/simulación solo en DEV; ausentes en builds de distribución.
- **UX-17** No hay pantallas huérfanas: inventario de todos los accesos de Perfil previo vs posterior, con verificación de destino y vuelta.
- **UX-18** Gate 3 Android CI y Gate 3 Evidence completos en el SHA exacto, más pruebas específicas de menú/modal/back y A/B.

**Criterio NO-GO:** cualquier funcionalidad preexistente desaparecida; cuenta equivocada visible; control de colaboración sin permisos reales; navegación Atrás que salta un modal; sincronización incompleta afirmada como completa; QA completo fallido; una función IA prometida pero simulada.

## 9. Integración / alcance excluido

- Este PR: **un archivo Markdown**, sin Android, Room, SQL, RLS, Supabase, scheduler, producción, descargas ni APK.
- No merge a main sin aprobación; no tocar ramas de Claude/Antigravity ni los emuladores QA históricos A/B.
- La futura IA completa, administración web, préstamos/QR de herramientas y gestión avanzada de empleados quedan fuera de esta CR.
- La «eliminación de cuenta», soporte por email y exportación/copia se mostrarán como acciones solo cuando existan flujos reales probados y política de datos/publicación revisada.
- Si los contratos de #789/#806 necesitan cambios, documentar la decisión allí, no improvisar permisos en la UI.

## 10. Trazabilidad y referencias

- **Estado actual en código:** #781 (cabecera/Foto Perfil), #794 (endurecimiento y aislamiento avatar), #795 (Auth Gate/Cuenta), #796 (integración Cuenta + G2.6).
- **Auditoría previa:** docs/07-plans/ISSUE-725-PROFILE-AUDIT-CONTINUATION.md; docs/07-plans/PHASE21-PROFILE.md; docs/07-plans/PHASE4-NAVIGATION-SHELL.md; CR-014 y AppDestination.kt.
- **Roles e invitaciones:** #735, #739, PR #789 y PR #806 (docs/07-plans/CR-COLLAB-02-INVITACIONES-UX-2026-11-01.md).
- **Base de maquinaria:** PR #211 (fusionada); protección por workspace y evolución de «Mis máquinas»; futuras fotos y selección.
- **Sincronización:** #807 (2b), #808 (riesgo de pesadas/gastos históricos), #809 (integrada en #807), #810 (plan del receptor 2c), #804 (riesgo de lectura de grants revocados).
- **Riego/tiempo y publicación:** PR #790, #734 y #769.
- **Norma de revisión:** primero verificar sobre rama integradora y pruebas exactas; después PRs aisladas y aprobación de cada gate. Ninguna PR Draft documental prueba funcionamiento real.

## 11. Decisiones pendientes de detalle (no bloquean esta documentación)

- Si la cabecera común mostrará avatar y campana en **todas** las pantallas anidadas o solo raíces y subpantallas con acción explícita; elegir una regla única tras prototipo accesible.
- Si «Mi cuenta» abre directamente una pantalla jerárquica o una hoja compacta con accesos; las dos opciones deben respetar Atrás y accesibilidad.
- El texto definitivo del placeholder «Asistente IA», sin prometer capacidad inexistente.
- Los elementos de ayuda/soporte/eliminación aún no implementados no deben activarse por la mera reestructuración.

**Próxima acción autorizable tras G2.6:** auditar el inventario sobre la rama integrada, implementar navegación y perfil preservando rutas y pruebas, y después conectar colaboración con backend seguro. La APK unificada de revisión se genera cuando estos bloques estén integrados y verificados.
