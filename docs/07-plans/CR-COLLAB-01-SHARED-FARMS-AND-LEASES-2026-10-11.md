# CR-COLLAB-01 — Fincas compartidas, contratos y sincronización entre cuentas

**Estado:** BORRADOR PARA REVISIÓN (decisión de producto aprobada en concepto por propietario 2026-10-11; contrato técnico, despliegues y PR productivas pendientes). 
**Requisitos:** #735 (MVP multiusuario y reparto) · #737 (contratos) · #725 (Perfil) · #726 (Auth/sync) · #322 (RLS) · #731 (G0 Admin) · #559 (Supabase actual).
**Control de cambios:** `docs/00-master/CHANGE-CONTROL.md`. Este documento **no** despliega Supabase, no cambia Room ni concede acceso a datos.

## 1. Motivación y solución propuesta

Mágina Olivo debe ser fácil para: (A) propietario que trabaja su finca, (B) dueño y explotador/arrendatario distintos, con reparto opcional de cosecha (ejemplo 70/30), y (C) dueño y empresa/persona que ejecuta labores por encargo. Es prioritario frente al bloque de noticias/pueblos/cooperativas/publicidad.

Principio único: **una finca lógica, una Pesada real y una campaña; cuentas personales independientes, vistas y permisos limitados**. La relación declarada de propiedad/arrendamiento no es autorización técnica ni acreditación legal. El operador registra offline primero; el dueño ve los registros cuando se sincronizan.

Tres capas:
- Identidad `auth.users` y perfil privado, con inicio de sesión Android y web.
- Partes relacionadas y **contratos/acuerdos** por finca, vigencia/campaña y consentimientos.
- `farm_access_grant`/membership para acciones autorizadas, independiente de relación declarada, comprobado mediante RLS en backend y almacenamiento.

**No** usar la contraseña del propietario para un operario; no duplicar la misma finca bajo varias cuentas, ni dejar que Admin editorial vea datos agrarios por ser Admin.

## 2. Estado real y dependencias técnicas (11/10/2026)

`main` baseline observado: `d536a6b6` (Gate21). Room contiene `FarmEntity.workspaceId`, `DeliveryEntity.netGrams/deliveryDate`, UUIDs locales y outbox. Supabase conectado y consultado en lectura:
- proyecto `magina-olivo` activo, solo tablas `public.farms` y `public.plots` con RLS, sin campaigns/deliveries/memberships/contracts; ambas con 0 filas al comprobar;
- dos migraciones antiguas Gate D (`gate_d_farms_plots_rls`, `gate_d_fk_indexes`), prototipo **no canónico**;
- Storage actual sin bucket de documentos agrarios privados; no adaptar Room a ese prototipo;
- Security Advisor advierte policies compatibles con inicio anónimo vía rol authenticated (revisar antes de compartir) y leaked-password-protection desactivada. Es un aviso de configuración, no evidencia de lectura pública sin credenciales.
- #559 contiene auditoría previa, #721 prepara Room v26 territorial aún por integrar. No asignar nuevo número de migración ni alterar esquema sin coordinar.
- #731/PR docs #732/#733 contienen gate G0 recuperación Admin histórico; `web/` no debe asumirse presente en main.

## 3. UX mínimo dentro de navegación actual

Cuenta/Perfil: `Mis datos`, `Mis fincas`, `Invitaciones`, `Estado de sincronización`; registro mínimo email, verificación y contraseña; cambios/offline después del primer login seguro.

`Mi Campo > Finca > Personas y acuerdos`:
- quién es propietario, quién explota, quién lleva trabajos; actividad por campaña sin confundir ownership jurídico;
- invitar por email a persona con cuenta verificada, aceptar/rechazar y retirar acceso;
- permisos entendibles: «Ver campaña y pesadas», «Registrar pesadas y labores», «Anotar trabajos y proponer costes» y «Gestionar finca» con autorizaciones verificadas servidor; sensibilidad mayor para gastos, documentos o identificación;
- acuerdo: tipo (arrendamiento, aparcería/reparto, cesión, encargo, otro), fechas inicio/fin y 1/2/3/5 años/personalizada, notas y condiciones, renovaciones/versiones, responsable de costes separado de cuota de cosecha;
- contrato: ambas partes pueden subir PDF/foto en área compartida **privada** cuando tengan acceso. Cada archivo conserva uploader, fecha, checksum, versión y estado; subir ≠ firmar ni aceptar condiciones. Permitir lectura/aceptación por parte y discrepancia.
- campaña: kg recogidos y lista de Pesadas de una única fuente; cuota de cosecha mediante acuerdo **aprobado** y versionado; 10.000 kg, 70/30 -> 7.000/3.000 kg (reparto teórico; no liquidación ni pago). Una corrección del peso se audita y repercute solo conforme versión del acuerdo, sin reescritura silenciosa de histórico.
- prestador: registra trabajo y **gasto propuesto**; dueño revisa/acepta y contabiliza una sola vez por Expense ledger canónico. Avisos cuando servidor recibe Pesada/trabajo y propietario optó por alertas; si offline, estado «pendiente» y aviso diferido.

## 4. Contrato de datos conceptual (nombres no vinculantes)

- `farm_party_relationship`: farm_id, party/user_id opcional, relation_type, operational_roles, valid_from/to, source, status/version; no cambia el `workspaceId` de fincas ya existentes.
- `farm_access_grant`: account_id, farm_id, capabilities, invited_by, accepted_at, revoked_at, scopes y auditoría; RLS por servidor, no user_metadata editable.
- `farm_agreement`: farm_id, agreement_type, parties, vigencia, condiciones, terms_version, status y aprobaciones por parte; diferenciar contrato de acuerdos de porcentaje variables por campaña.
- `farm_agreement_document`: agreement_id, uploader_user_id, bucket_key/size/checksum/mime, version, uploaded_at, status, retention; acceso Storage privado por permiso explícito, URLs caducables. Revisar documentos ya descargados tras revocación.
- `campaign_harvest_share`: campaign_id, participants, percent decimal exacto, status, approvals, valid_period, version; validar suma 100 % (o saldo documentado), derivar kg de `DeliveryEntity.netGrams` evitando redondeos/duplicados.
- `farm_work_submission`/actuación existente reutilizada: author, activity_id, amount_proposed, review_status, accepted_by, canonical_expense_id opcional; no crear una segunda contabilidad.
- `sync_outbox`, idempotency_key, versiones, tombstones y detección de conflictos; Storage adjuntos por workspace y control de acceso por finca, con aislamiento A/B/C/D.

## 5. Plan por entregas — puertas obligatorias

**G0 — PR documental y matriz de ownership (antes de DDL):** conciliar RC1.2/CR-HYBRID-01 (#731), inventariar módulos Auth/Room/outbox/Supabase y docs, decidir source-of-truth/permisos por entidad, anotar riesgos, coste y quién edita cada carpeta. Revisión con propietario. Este PR es solo propuesta.

**G1 — Auth y RLS para cuenta individual:** registro/verificación/recuperación/logout Android+web, dos usuarios aislados, sync individual, migración Gate21 con backup y vínculo consentido. Evitar compartir fincas mientras persistan vulnerabilidades.

**G2 — Finca compartida:** invitación, aceptación, revocación y acceso a misma finca/campaña/pesadas sin duplicar; offline posterior al primer login, sin exponer otras fincas o gastos; unit/RLS/instrumented, CI SHA.

**G3 — Contratos y acuerdos:** fechas/renovaciones, carga de PDF/fotos por ambos en Storage privado, estados/aprobación, 70/30 por campaña con cálculo exacto, historial intacto. No «firma digital» fingida.

**G4 — Trabajos/costes/avisos:** permiso empleado/contratista, reporte actividad, gasto propuesto y revisión, Expense único, notificación pos-sync.

**G5 — Web privada + ADMIN mínimo:** web ver fincas compartidas según RLS, contratos autorizados, estado de sincronización. ADMIN plataforma únicamente roles, salud técnica de sync, cuentas pseudonimizadas cuando sea posible, auditoría y luego CMS; **no acceder a contratos/finanzas privados por defecto**. Recuperar selectivamente componentes PR #16/#66/#117 tras KEEP/ADAPT/DROP, no merge masivo.

**G6 — QA y producto:** dos personas y tres dispositivos, pruebas offline→online, invitación falsa/caducada, revocación, documento compartido, otro usuario 403, adjuntos, migración/backup, dos campañas, cuota que cambia, gastos y push, accesibilidad. El propietario probará APKs DEV solicitadas con migraciones seguras; no publicar candidata final sin gate #727.

**Orden ejecutivo:** G0, G1, G2, G3, G4 y G5; documentación de G5/Admin puede adelantarse sin product DDL conflictiva. Pueblos/noticias/cooperativas después.

## 6. Contrato de seguridad y privacidad

- Usuarios nunca ganan acceso por seleccionar «Soy propietario».
- Accesos por invitación autenticada, aceptación, scope limitado, revocación y log. No mostrar precios, DNI o documentos a transportistas/operarios sin grants.
- Admin plataforma/editor/anunciante no ven explotaciones ni contratos sin un flujo de soporte específico con base legal y auditoría.
- Storage privado, validación de archivos y límites de tamaño, protección contra URLs públicas, token handling seguro. No poner `service_role` en móvil.
- Distinguir contrato documental, aceptación in-app y firma electrónica jurídicamente válida. Retención conforme política y revisión legal, sin afirmar que la app formaliza legalmente contratos por defecto.
- No generar credenciales secretas en GitHub, capturas ni prompts; usar entornos DEV/STAGING con usuarios de prueba y nunca documentos reales para E2E.

## 7. Impacto del cambio sobre baseline

Afecta alcance RC1.2 «solo owner», colaboración post-RC1.2, flujo Cuenta obligatorio (decisión 10/10), modelo Farm/Workspace y Auth/Sync, Storage, permisos y gates. Requiere revisión y versionado formal antes de código productivo según `CHANGE-CONTROL.md`. Se mantiene Android offline-first y la barra `Inicio · Mi Campo · [+] · Cuaderno · Perfil` y la arquitectura híbrida C.

## 8. Decisión

**APPROVED IN PRINCIPLE (product), NOT APPROVED FOR PRODUCTION DEPLOYMENT.**
Pendiente: contrato de identidad + migración Room + permisos RLS + amenaza/evidencia + ownership + validación cambio baseline. Preparar G0 con pruebas y después PRs pequeñas; nadie modifica main directamente.


## 9. CR-COLLAB-01 — Invitación por QR, WhatsApp y enlace (#739)

La vía preferente de onboarding de finca compartida será **Finca > Compartir finca**, sin reintroducir parcelas ni crear varias copias. La finca puede crearla propietario o explotador con control de acceso legítimo: seleccionar «Soy propietario» no otorga permisos. El emisor decide el **alcance específico de la invitación** (ver Pesadas; registrar Pesadas; registrar trabajos; proponer gastos; permisos extra para contratos), muestra QR presencial o comparte enlace por WhatsApp/correo; el destinatario registrado entra con su cuenta o la crea y retoma el flujo, lee permisos, acepta y —si el enlace era abierto— el emisor confirma la identidad. Solo entonces servidor emite un grant RLS verificado para la finca canónica.

Seguridad: QR temporal de **un uso**, no QR permanente pegado en la finca; token opaco aleatorio, almacena hash (no secretos), expiración recomendada inicial 10-15 min presencial / hasta 7 días para link remoto bajo aprobación de emisor, revocable; rate limits y defensa contra screenshot/reenvío; nunca incluir NIF, fotos, geometrías, credenciales o contratos en QR. QR no autoriza operaciones offline: se reclama con conexión y Auth; una vez concedido el acceso, el trabajo offline se sincroniza con política de revocación documentada. Android App Links verificados y web HTTPS fallback, enlace con token de invitación diferente de tokens de sesión OAuth; código manual como alternativa. Sin permiso explícito de contratos, el nuevo invitado no ve PDFs de arrendamiento.

Prueba piloto: Antonio crea El Barranco y da QR a Manuel como operador, Manuel acepta con su propia cuenta, ambos ven misma finca, Manuel registra una Pesada offline y Antonio la ve una vez al sincronizar. Caso inverso: explotador crea finca e invita al propietario, sin atribuir propiedad legal automática. Caso tercero: recolector solo ve sus capacidades, ningún gasto/contrato ajeno. Casos negativos obligatorios: QR caducado/reusado/revocado, otra cuenta reclama, screenshot reenviado, carreras concurrentes, invitado sin Auth, fuga de Storage y revocación. Registro existente en otra cuenta con Catastro coincidente nunca dispara merge automático.

Issue canónica de UX y contrato: https://github.com/izc05/magina-olivo-v20/issues/739. **No implementar al margen de #726 Auth/RLS, #735 colaboración y G0 #731; esta PR sigue siendo exclusivamente documental.**


## 10. Acta de arranque y reparto de ejecución — 11/10/2026

**Mandato recibido:** empezar ya la fase cuenta/roles/sync; la confirmación es para organizar y ejecutar por PRs seguras, **no** para cambiar directamente Supabase de producción ni fusionar ramas existentes sin revisión. Siguiente hito visible: dos cuentas comparten una finca y una Pesada se sincroniza una sola vez.

### Inventario contrastado en GitHub, SHA de arranque `d536a6b6`
- Android: `LocalWorkspaceRepository.ensureLocalWorkspace()` genera `workspaceId` y `ownerUserId` con UUIDs locales sin Auth cloud; `AppCompositionRoot.createAndroid()` monta repositorios Room; `SyncOutboxEntity` y `OutboxWriter` crean cambios pendientes. No hay evidencia de un motor WorkManager pull/push multiusuario operativo.
- Room `MaginaOlivoDatabase` está en versión **25 en main**; PR territorial #721 introduce v26 y toca `MaginaOlivoDatabase.kt`, `DatabaseMigrations.kt`, `RoomMigrationTest.kt`. Esos archivos quedan **reservados** a #721 hasta su revisión/merge; luego cualquier evolución debe migrar desde v26 y preservar datos v25.
- `FarmEntity` tiene un `workspaceId` y `DeliveryEntity` un `farmId`, `campaignId`, `netGrams`, `deliveryDate`: el esquema actual opera localmente; agregar miembros sin clonar pesadas requiere planificación de acceso remoto y caché por cuenta.
- Supabase real `magina-olivo`: public `farms` y `plots` (prototipo Gate D) con 0 filas y RLS enabled; sin esquema cloud para campañas, pesadas ni permisos. Migraciones existentes son Gate D; no hay ramas dev de Supabase registradas al observar. Storage agrícola privado no configurado.
- PR Android #736 Mercado usa `feature/home/OilMarketScreen.kt` y pruebas, y tiene fallos CI de gate3-emulator/gate3-evidence; PR #734 weather modifica `supabase/functions/weather-forecast/**`; PR docs #732/#733 para Admin/híbrida; PR #721 territorial Room. **Nunca incorporar archivos de esas PRs de forma implícita**.

### Matriz de propietarios de cambios / trabajo seguro en paralelo

| Frente | Responsable sugerido al aceptar la tarea | Alcance inicial | Archivos reservados / condición |
|---|---|---|---|
| G0 Identidad/Backend (prioridad absoluta) | **Antigravity** cuando confirme disponibilidad, o Codex si el usuario lo reasigna | Inventario con pruebas, CR, identidad Auth, RLS grants y esquema propuesto, tests A/B/C/D, políticas Storage privado **en rama propia** | Propietario exclusivo de nuevo SQL/Auth/sync; no tocar v25→v26 ni `supabase/functions/weather-forecast/**` |
| Android UX agrícola | **Claude** tras corregir CI/PR Mercado y cerrar su siguiente slice | Pantallas Cuenta/Invitaciones/Personas y acuerdos; prototipo Compose no conectado y contrato de repository para futuro backend; tests accesibilidad | `feature/profile/**`, nueva `feature/sharing/**`, `feature/farm/**` tras comprobar dueño; no editar `DatabaseMigrations.kt` ni prometer login real |
| Room territorial | **Antigravity — PR #721 vigente** | Terminar v26 y pruebas de migración/CI | Único dueño de `MaginaOlivoDatabase.kt`, `DatabaseMigrations.kt` hasta merge; no mezclar con Auth |
| ADMIN y web | **Codex** cuando esté disponible | Auditoría de PRs #16/#66/#117, matriz KEEP/ADAPT/DROP, primer contrato `/admin` con acceso restringido, dashboard de salud técnico; rama documental separada al inicio | No introducir una segunda identidad ni usar service_role desde el cliente; sin acceso general a finca/contratos |
| Coordinación | **ChatGPT + propietario** | Auditar SHA/PR/CI, gate, preservar prioridades y reportar dependencias | PR #738 documental, issue master #727; nadie trabaja en `main` |

**Si un agente ya tiene archivos asignados:** no interrumpir a mitad de una PR ni asumir que una issue la activa; aceptar el traspaso explícitamente. No iniciar dos motores de sync o dos migraciones Room simultáneamente.

### Secuencia de trabajo con PR pequeñas

1. **G0-Auditoría** (se puede hacer hoy): comparar `main` y PRs, RLS/Storage/Auth live, inventario edge functions, threat model, formalizar CR, matriz de quién puede leer/escribir `farm/campaign/delivery/expense/agreement/document`; declarar HECHO/PARCIAL/FALTANTE. Deliverable: PR solo docs con referencias por SHA.
2. **G1-Identidad** (primera PR productiva tras G0): Supabase Auth email verificado, sesión segura y recuperación; user_id estable, workspace claim de Room v25/v26 con backup y consentimiento. Pruebas de 2 usuarios sin datos cruzados; no mostrar sincronizado si no pasó roundtrip.
3. **G2-Sync propia**: modelo remoto canónico compatible Room, outbox persistente WorkManager pull/push, fotos, idempotencia y reconexión; 1 usuario/2 dispositivos con fincas/Pesadas y E2E.
4. **G3-Compartir finca**: `farm_invitation` QR o enlace one-time con aceptación y permiso servidor; caché por usuario y RLS. Dos cuentas ven la **misma** pesada tras sync. Aun sin acuerdos 70/30 en este gate.
5. **G4-Acuerdos y contratos**: porcentajes de cosecha aceptados y versionados por campaña, PDF/foto contrato ambos lados (privado), periodo de vigencia/renovaciones, aceptación separada de firma.
6. **G5-Contratistas y gastos**: enviar actuación y coste propuesto, aprobación de propietario, Expense canónico único, avisos push/in-app por evento confirmado y permisos; auditoría.
7. **G6-Web privada y ADMIN**: acceso a finca según las mismas grants; Admin plataforma solo controles de cuentas, avisos de fallos, métricas técnicas y CMS autorizado, **nunca lectura general de contratos**. Entrega final tras QA y aprobación del propietario.

**Gates de producto obligatorios por PR:** SHA HEAD, diff delimitado, cambios Room/SQL migraciones con rollback, pruebas unitarias/RLS A-B-C-D/instrumentadas, CI verde, capturas (si UI), evidencias offline/multidispositivo. No merge automático.

### Matriz mínima de autorización a validar (los permisos reales serán por capabilities)

| Datos/acción | Propietario autorizado | Explotador autorizado | Trabajador externo autorizado | ADMIN plataforma |
|---|---|---|---|---|
| Ver finca, campaña y pesadas | Sí | Sí | Solo lo estrictamente concedido | No |
| Crear Pesada | Solo si delegado | Sí | Opcional por trabajo | No |
| Ver cuota 70/30 aceptada | Sí | Sí | No | No |
| Ver/subir contrato | Requiere permiso específico | Requiere permiso específico | No por defecto | No |
| Ver gastos económicos | Solo gastos autorizados | Solo gastos autorizados | Solo propios/autorizados | No |
| Proponer trabajo/gasto | Si autorizado | Si autorizado | Sí, ámbito propio | No |
| Conceder/revocar permisos | Con privilegio de gestión verificable | Solo si se le delega | No | No (salvo soporte especial reglado y auditado) |
| Estado técnico de sync agregado | Propio | Propio | Propio | Sí, sin datos agrícolas privados |

**Riesgos de decisión pendientes del equipo antes de deploy:** titular jurídico vs creador del workspace, consentimiento de cesión/revocación, convivencia de finca preexistente en ambos móviles, protección de contratos contra accesos cruzados, control de versiones y discrepancias en un 70/30, comportamiento offline con grant revocado, coste de Storage y avisos. Se requiere diseño específico para cada uno, no solo una pantalla.

### Primer test de aceptación a preparar desde G0

A crea finca F y asigna a B con QR temporal. B acepta con Auth verificada. B registra sin red una Pesada de 1.200 kg y fecha local; A ve **exactamente una Pesada** al sincronizar, fecha y origen correctos. C no invitado recibe denegación incluso con URL o ID conocido. B no ve contratos ni gastos que no se le han concedido; revocación bloquea nuevos pulls; se documenta el estado offline del dispositivo B hasta la próxima validación. No hay ningún dato histórico Gate21 perdido ni fincas reetiquetadas como «en propiedad» sin consentimiento.

### Estado del arranque
- **COMENZADO:** inventario contrastado, definición funcional y plan operativo PR #738 + issues #735/#726/#737/#739.
- **PENDIENTE:** aceptación de dueño de archivos por agentes, G0 de seguridad/CR formal, primeras PRs productivas, pruebas E2E; no afirmar app sincronizada.


## 11. Change Request formal — conciliación con `CHANGE-CONTROL.md` (G0)

### Motivation
La baseline RC1.2 de workspace local individual no soporta las necesidades explícitas del propietario (cuentas distintas, una finca canónica, acuerdos de cosecha, contratos privados, trabajadores autorizados). Esta nueva función tiene prioridad de producto sobre el CMS territorial, sin eliminar ese futuro módulo.

### Proposed change
Extender la arquitectura Android offline-first con Supabase Auth y membership/grants por finca y capacidad; invitación QR/link verificada; permisos diferentes para propietario, explotador y prestador; sincronización idempotente de la misma finca/Pesadas a cuentas autorizadas; contratos documentales privados, reparto por campaña sin falsear liquidaciones; web privada y Admin plataforma separado.

### Affected baseline sections
- `RC1.2-PRODUCT-LOCK.md`: propiedad individual/no colaboración en 1.0; necesidad de registrar revisión explícita para compartir fincas.
- `RC1-BASELINE.md`, `SINGLE-TRACK-EXECUTION.md` y `AGENTS.md`: fase/gates, ownership de trabajo, alcance permitido y no duplicación.
- `DATA-MODEL` Farm/Workspace/Campaign/Delivery/Expense/Document: identidad, autorización, relaciones históricas y ledger único.
- `CURRENT-STATE.md`, `ROADMAP-RC1.2.md`, docs #325/#322/#330/#335, arquitectura híbrida #730 y gate G0 #731.
Referencias definitivas de apartados/líneas deben verificarse por SHA durante revisión de esta CR; no modificar locks en PR docs sin revisión de owners.

### Affected phases
Añadir gates **G0–G6** del apartado 10 y conectar con Fase 22 (backend) y Fase 23 (Sync), #725 Perfil y #731 Admin. La ampliación de pueblos/cooperativas/publicidad pasa a después del piloto de cuentas compartidas, sin cancelar investigación.

### Data impact
Esquema remoto Auth/RLS + grants/invitaciones/relaciones/acuerdos/adjuntos; mapping desde Room v25 y la futura migración v26 de #721. Preservar UUIDs y campañas, copias/restauración, importación idempotente, acceso por cuenta, data minimization; no reinterpretar finca registrada como propiedad legal. Puntos de rollback y migraciones seguras antes de tocar producción.

### Offline/sync impact
Room sigue siendo fuente local de trabajo. Outbox/WorkManager + push/pull/delta/version/tombstones, reintentos y políticas de conflicto; acceso compartido solo tras invitación aceptada online. Supabase sirve verdad remota de permisos y difusión. Cache local por cuenta segregada, controles de expiración y revocación; no prometer revocación retroactiva de datos que otro ya descargó.

### UX impact
No cambiar barra principal. Finca > Compartir, Personas y acuerdos, Contrato, Invitaciones y estados de sincronización. Cuenta mínima con Auth y registro obligatorio normal (decisión de 10/10); datos agrícolas antiguos conservados mediante vínculo consentido, no pedir reentrada. Adaptar 360dp, modo oscuro y accesibilidad; sin botones que simulen sync.

### Risks
Acceso cruzado a datos agrícolas privados; contratos/identidad/NIF sensibles; confianza falsa por autodeclaración; gestión de transferencias y vencimientos de concesiones; fugas por Storage/signed URLs; schema viejo incompatible y pérdida Gate21; doble conteo de kilos/gastos; sobrecarga de agentes/migraciones concurrentes; Auth sin protección de cuenta. Mitigaciones: G0, pruebas negativas RLS, Storage privado y 4 cuentas, backup/migración validada, PR chicas, owner único, QA de dos dispositivos/offline.

### Alternatives considered
Sin colaboración (requerimiento no satisfecho); enviar PDF/Excel/WhatsApp manualmente y duplicar fincas (datos contradictorios); usuarios compartiendo login (inseguro); grant automático al marcar propietario (escalada de privilegios); modelo canónico con invitaciones y permisos verificados (elegido).

### Decision
**PRODUCTO: APPROVED IN PRINCIPLE** por propietario el 11/10/2026. **CR TÉCNICA: PENDING G0 REVIEW** de data, RLS, continuidad local, ruta Admin y plan de migración. **PRODUCTION DEPLOYMENT: NOT AUTHORIZED**, debe aprobarse al pasar G0/seguridad y entregarse pruebas por SHA. No fusionar PR docs #738 por automatismo; revisión expresa de contradicciones con locks históricos. No editar config Auth/SQL de Supabase vivo por una autorización general de «empezar».

### New baseline version
**TBD tras revisión G0.** No declarar RC1.3 ni actualizar `AGENTS.md` hasta resolver cambios y aceptación de la revisión. La denominación interna `CR-COLLAB-01` no sustituye la numeración RC1.x vigente.
