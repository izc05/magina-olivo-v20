# Mágina Olivo — CR-COLLAB-02: roles e invitaciones sencillas para fincas compartidas

> **Estado: diseño funcional ampliado, pendiente de revisión / implementación.**
> **Fecha:** 2026-11-01. **Prioridad:** P0, después del cierre seguro de G2.6 individual.
> **Documento complementario**, no sustituye el contrato vigente CR-COLLAB-01 (#735 / PR #789) ni el detalle de seguridad de invitaciones #739.
> **Sin autorización de código, SQL, despliegue DEV/PROD, merge, scheduler ni APK final** por el hecho de documentar esta propuesta.

## 1. Decisión de producto

Mágina Olivo debe funcionar **individualmente por defecto**. La colaboración es opcional por finca, campaña y/o encargo, sin compartir contraseñas ni todo el workspace. Una persona puede ser titular de una finca, explotador de otra y recolector de una tercera desde la **misma cuenta**. Una misma finca lógica conserva un **único ID canónico** y un único registro de pesadas: nunca crear copias paralelas de la finca al aceptar una invitación.

Roles principales visibles, ya acordados en #735 y PR #789:

1. **Titular** (propietario o responsable con legitimación declarada): gestiona solo lo que sus permisos reales permitan, invita y supervisa. Ser dueño legal, titular REAFA, creador de datos y administrador técnico **no son equivalentes**.
2. **Explotador / gestor** (incluye arrendatario): lleva campaña, labores, jornales y pesadas en fincas autorizadas; acceso económico y contractual por permisos separados.
3. **Recolector** (empresa o particular): recibe **encargos** restringidos por campaña y parcela, sube partes, tickets/fotos y costes propuestos, sin ver otras fincas ni finanzas.
4. **Jornalero**: se registra manualmente, **sin cuenta obligatoria**. Un perfil conectado para consultar partes propios o fichar podría explorarse después, sin convertirlo en un cuarto rol obligatorio de 1.0.
5. **Asesor/aplicador**: posible plantilla futura de permiso limitado; no ampliar los tres roles principales ni implementar ahora.

Una relación declarada («soy propietario», «gestor», «trabajo aquí») es informativa, **nunca** un permiso automático. Las relaciones civiles, acuerdos económicos y accesos técnicos son contratos independientes.

## 2. Objetivo de experiencia (idea tomada de la competencia, adaptada)

**Un agricultor debe poder invitar a un colaborador en menos de un minuto, sin conocer términos técnicos.** Flujo propuesto:

**A (emisor, autorizado)**: `Mi Campo > Finca > Personas y acuerdos > Invitar`
1. Elegir persona: **Explotador/gestor**, **Recolector**, o **Trabajador (opcional, futuro)**. Si el interlocutor es el titular de la finca, permitir vínculo titular con verificación y capacidades específicas, sin presuponer la propiedad legal.
2. Elegir alcance: **esta finca / campaña vigente / parcelas concretas / encargo concreto**, y vigencia opcional. Se muestra un **resumen en lenguaje sencillo** de exactamente qué podrá ver, registrar y proponer. Contratos, gastos privados y otros workspaces: **no compartidos por defecto**.
3. Elegir vía: **WhatsApp**, **QR presencial**, **copiar enlace HTTPS**; opcional **correo ligado a destinatario verificado**. Mostrar también **código manual de un solo uso** si no funciona la cámara/deep link. Cada opción representa **la misma invitación** de alcance definido; no fabricar grants independientes.
4. Queda en **Pendientes** con acciones `Reenviar`, `Cancelar` y `Ver permisos`. Reenviar no debe invalidar la seguridad ni multiplicar accesos; cuando se requiera nueva credencial, revocar la anterior.

**B (receptor)**:
1. Abrir enlace de WhatsApp / escanear QR / introducir código. Android App Links verificados: si tiene la app, abrirla; si no, página HTTPS con instalación / creación de cuenta y **retorno a la invitación** después de registrarse.
2. Iniciar sesión en cuenta **propia**. Ver tarjeta de invitación: quién invita, finca y municipio general si procede, rol, duración, parcelas autorizadas, permisos exactos y aviso sobre datos compartidos. **No exponer geometría ni contenido privado antes de validar**.
3. `Aceptar` o `Rechazar`. En enlaces no ligados a destinatario verificado, A revisa identidad del reclamante y confirma **antes** de conceder acceso (doble aceptación inicial).
4. Servidor crea **un único grant auditable** solo si ambos actores siguen autorizados. B encuentra `Fincas compartidas` dentro de Mi Campo, sin duplicación. Ver `Activo / Pendiente de confirmación / Caducado / Revocado` de manera clara.

**A después**: `Personas y acuerdos` muestra **Conectados** y **Pendientes** por separado; cada persona tiene `Cambiar permisos`, `Finalizar encargo`, `Revocar acceso`, `Historial`. El titular ve una sola pesada creada por B tras sincronizar; B no ve contabilidad privada de A. No confundir invitado sin cuenta (texto de un jornal) con usuario conectado.

## 3. Plantillas iniciales de capacidades (siempre validadas servidor)

| Acción | Titular autorizado | Explotador/gestor | Recolector / contratista | Jornalero sin cuenta |
|---|---|---|---|---|
| Ver ficha y parcelas | Según grants efectivos | Solo autorizadas | Solo encargo | No aplica |
| Ver campaña y pesadas | Según permiso | Solo autorizadas | Solo partes autorizados | No aplica |
| Crear pesada | Según permiso | Sí, en campaña autorizada | Parte propuesto o registrada según encargo y revisión | Lo anota el responsable |
| Registrar labores | Sí | Sí | Sí, en su encargo | Registro manual |
| Proponer coste | Sí | Según permiso | Sí, nunca contabilidad automática | No |
| Ver contabilidad privada | Solo cuentas con permiso explícito | No por defecto | Nunca por defecto | No |
| Gestionar acuerdos / contratos | Permiso contractual específico | Solo autorizado | No | No |
| Invitar o revocar | `manage_members` explícito | Solo si recibe ese permiso | No | No |

Roles son **plantillas de interfaz**; los derechos deben expresarse como capacidades específicas por ámbito. Separar permisos técnicos del rol de persona, del vínculo jurídico y del acuerdo 70/30. Para recolección: presupuesto por **€/kg cerrado** o coste por **jornal+maquinaria+combustible+transporte**, evitando contabilizar dos veces los jornales internos de la empresa.

## 4. Pantallas a diseñar (sin nueva pestaña inferior)

- **Mi Campo > Finca > Personas y acuerdos**: cabecera de finca, tarjetas `Conectados`, `Invitaciones pendientes`, `Acuerdos de campaña`, botón `Invitar`; entrada a permisos de cada persona.
- **Asistente Invitar** de tres pasos: rol → alcance/permisos/fecha de fin → enviar (WhatsApp / QR / enlace / correo).
- **Ficha de invitación**: destinatario, estado, caducidad, permisos, origen y opciones cancelar/reinvitar.
- **Invitación recibida**: identidad del emisor, alcance exacto, `Aceptar / Rechazar`; confirmación del emisor si no está ligada a correo verificado.
- **Perfil > Invitaciones recibidas**: para recuperar pendientes sin enlace; opción `Introducir código`.
- **Mi Campo > Fincas compartidas**: badge de relación y selector explícito de finca, sin exponer otras explotaciones.
- Mantener paleta **crema / verde olivo**, tarjetas claras y accesibilidad (texto legible, tamaños táctiles); no mostrar controles inactivos como si fueran funcionales.

## 5. Contrato de seguridad y sincronización (diseño, no migración autorizada)

- Aprovechar Auth, Room/outbox y autorización por cuenta de G2.6; **no** reutilizar un recibo de sincronización como permiso.
- Entidad de invitación tipo `farm_invitation`, identificada por finca, emisor, destinatario ligado opcionalmente a correo verificado, capacidades, alcance, vigencia, estado, historial, versión y uso máximo = 1.
- Token **aleatorio/opaco** de un solo uso y expiración: QR presencial corto (propuesta 10–15 min), enlace remoto con vencimiento (propuesta máximo 7 días), sujeto a revisión de riesgos. Guardar hash, jamás token en claro en base/logs/analytics; proteger reenvío, capturas, reuso y fuerza bruta.
- **Abrir QR no concede acceso**. Verificar autenticación, permisos actuales del emisor, destinatario, aceptación, vigencia y alcance en backend. Crear grants en transacción idempotente con RLS y registros de auditoría.
- Cuentas distintas y una sola finca lógica; `farm_id` canónico, reconciliación de cambios por versión/CAS; nunca duplicar kg, gasto o documentos por reintentos.
- **Invitar y aceptar requiere conexión a servidor**. Trabajo offline posible solo en el alcance y durante la vigencia de autorizaciones previamente verificadas; revocación efectiva en servidor inmediatamente y en cliente según política conservadora (bloquear uso si autorización caduca / cuenta cambia / identidad no verificable). Mostrar `pendiente de sincronizar` sin prometer inmediatez.
- Fotografías, tickets y contratos por Storage privado con autorización de alcance; finanzas y contratos ocultos por defecto. Notificaciones no incluyen kg/importes en pantalla bloqueada por defecto.
- Bloqueo previo específico: riesgo documentado en **#804** de lecturas por concesión revocada solo mediante `revoked_at`; arreglar y probar antes de colaboración real.
- **No** hacer fusión automática de dos fincas por referencia catastral ni atribuir finca histórica al iniciar sesión.

## 6. Casos que deberán funcionar

1. A titular invita por WhatsApp a B gestor sin cuenta → B se registra → recupera invitación → acepta → A confirma → aparece **la misma** finca y campaña.
2. A genera QR frente a B con cuenta → B escanea, acepta y A confirma → una única pertenencia; QR reusado por C falla.
3. B crea pesada offline de 1.200 kg en finca autorizada → vuelve internet → A la ve **una vez**; B no accede a otras fincas/finanzas de A.
4. Titular y explotador acuerdan **70/30 de kg** por campaña con aceptación de ambas partes; no implica 70/30 de gastos ni pago efectivo. Acuerdo separado de la invitación.
5. A invita a empresa C a recoger en parcelas 1 y 2 durante la campaña → C adjunta parte/ticket/foto y propone coste → A revisa y aprueba → un solo `Expense` canónico si procede. C no ve contratos ni otros ejercicios.
6. A revoca a B → B pierde acceso servidor incluso ante cambio de dispositivo, reintento o token viejo; local se bloquea cuando ya no existe autorización válida.
7. Cambio A→B→A, sesiones vencidas, invitación caducada/revocada, simultaneidad en aceptación, captura de QR, destinatario incorrecto: **fail-closed**.
8. No exigir teléfono, DNI, dirección ni número de Seguridad Social para **una invitación de colaboración simple**. Datos laborales extra, fichaje/geolocalización y cumplimiento laboral son flujos independientes.
9. Usuario D no invitado no puede leer finca, pesadas, fotos, contratos, ni mediante RPC directa ni Storage.

## 7. División realista del trabajo — sin interrumpir G2.6

**Paso 0 — Pendiente actual:** cerrar G2.6 **individual** (cuentas, workspace, fincas, parcelas y campañas) + E2E real Android A↔Supabase↔B, QA de autorizaciones, respaldos y revocación. La existencia de PRs Draft y PASS aislados no significa GO de APK ni despliegue.

**Paso 1 — Diseño / preparación sin código:** revisar #735, #739, #789 y este documento; congelar 3 roles visibles, matriz de capacidades y flujo del invitado. Reconciliar el modelo de finca/encargo con los grants actuales y preparar riesgos.

**Paso 2 — Backend seguro (solo tras GO separado):** invitaciones, grants por finca/campaña/encargo, RLS, revocación, App Links y reanudación tras registro; antes corregir riesgo #804. Pruebas A/B/C/D y negativas.

**Paso 3 — Android:** Personas y acuerdos, Invitar, Pendientes, Aceptar/rechazar, Conectados, QR/WhatsApp/código, ver/modificar/revocar permisos; no mostrar botones operativos hasta estar conectados a backend real.

**Paso 4 — Colaboración agrícola:** misma pesada canónica entre titular/gestor; acuerdos 70/30 separados; encargos y propuestas de costes con aprobación, sin duplicación.

**Paso 5 — QA físico y liberación:** dispositivos reales independientes A/B y tercero D, offline, cambios concurrentes, pérdida de sesión, cancelaciones, Storage, CI y regresiones. **Sin APK candidata hasta gates de #727.**

## 8. Inventario — decisión secundaria expresamente conservada

**No desplazar roles/invitaciones ni abrir ahora un desarrollo grande de inventario.** Reutilizar Maquinaria de **PR #211**, ya fusionada (catálogo, horas, uso en actividades y archivado), y la protección cross-workspace de #657.

Cuando corresponda: inventario **ligero** de máquinas y herramientas con **foto**, nombre, categoría, información opcional y selección de equipo ya registrado **desde un trabajo/jornada**. Una máquina puede utilizarse en varias fincas sin duplicar ficha; equipos privados de empresa no se exponen al titular salvo lo relacionado con el encargo. Prestamos, QR de activos, mantenimiento, ITV, seguros y analítica avanzada quedan fuera de este primer incremento.

## 9. Definición de terminado / pendiente

- [ ] G2.6 personal estable y E2E real con dos instalaciones separadas, sin pérdida ni fuga de datos.
- [ ] Modelo roles-permisos-finca-campaña/encargo y matriz final aprobados.
- [ ] Invitación por WhatsApp, QR, enlace, código (correo opcional) segura y sencilla.
- [ ] Aceptación / confirmación / revocación en backend, pruebas de reuso y caducidad.
- [ ] Una finca compartida, una pesada canónica, sin duplicación de kg ni gastos.
- [ ] Negativos de cuentas ajenas y revocación, adjuntos privados y trabajo offline.
- [ ] Encargos de recolector con partes y aprobación, sin finanzas ajenas.
- [ ] Después, inventario fotográfico mínimo + selector en trabajo.
- [ ] QA físico y gates previos a una APK de publicación.

**Vínculos de autoridad:** #735 / PR #789 (roles), #739 (invitación), #726 / PR #796 (sync), #727 (release), #737 / PR #738 (contratos), #804 (riesgo revocación), PR #211 (maquinaria).

**Próximo punto de reanudación:** revisar qué gates G2.6 están verdes en el SHA exacto y abrir únicamente los cambios acordados en Paso 2 cuando la sincronización individual esté aprobada. Este documento no constituye orden para implementar ni fusionar.
