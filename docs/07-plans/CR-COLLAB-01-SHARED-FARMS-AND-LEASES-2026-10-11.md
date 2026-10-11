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
