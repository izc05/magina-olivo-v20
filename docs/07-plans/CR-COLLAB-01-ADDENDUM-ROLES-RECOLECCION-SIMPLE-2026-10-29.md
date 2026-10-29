# CR-COLLAB-01 — Adenda de producto: roles simples, recolección y colaboración mínima

**Fecha de decisión:** 2026-10-29 · **Prioridad:** P0 de producto · **Aprobación:** concepto funcional expresamente elegido por el propietario.  
**Estado técnico:** especificación/contrato para revisión; NO implementado, NO desplegado, NO fusionado y sin permiso implícito para modificar Room, RLS o G2.6.  
**Referencia canónica:** issue #735 (CR-COLLAB-01). Antecedentes #738 (CR extensa), #737 (contratos), #739 (invitaciones), #322 (permisos), #726 (sincronización), #727 (cierre 1.0), #744 (coordinación).  
**Precedencia:** esta adenda de 29/10/2026 **acota y simplifica la visión funcional** de #735/#738 para la primera entrega colaborativa; conserva sus requisitos de integridad y seguridad. No reescribe el historial ni modifica por sí sola el baseline RC1.2. Toda implementación estructural seguirá CHANGE-CONTROL.md.

## 1. Decisión del propietario: producto sencillo para cualquier agricultor

Mágina Olivo debe servir tanto al pequeño particular que trabaja solo como al titular de varias fincas, al explotador/arrendatario y a una empresa **o particular** que recoge aceituna.

**Regla de uso:** una persona se registra con su cuenta, usa Mi Campo, crea sus fincas/parcelas, registra su campaña y no necesita colaborar con nadie. **No crear tipos de cuenta obligatorios ni más pestañas principales** para activar la app. Las funciones de colaboración solo aparecen cuando existe una relación/acuerdo/encargo.

La complejidad de autorizaciones queda debajo de una UX sencilla: «Mi relación con esta finca», «Recolección», «Compartir encargo», «Pesadas recibidas». Conservar navegación actual, tema crema/verde, offline-first y un único Cuaderno.

## 2. Tres roles visibles y relaciones jurídicas separadas

**Roles funcionales por finca o encargo (NO globales de la cuenta):**

| Relación visible | Función | Información compartida por defecto |
| --- | --- | --- |
| **Titular** | Consulta y organiza su finca; si la explota directamente registra pesadas, gastos, trabajos y jornales | Datos propios; si cede la explotación, ve pesadas, cosecha y su cuota según acuerdo, no la contabilidad privada ajena |
| **Explotador** | Unifica en la UX a arrendatario y gestor; gestiona campaña, pesadas, trabajos, riego y contabilidad operativa | Comparte con titular pesadas confirmadas, justificantes y reparto pactado; **no** revela por defecto sus pagos a trabajadores, facturas privadas o costes internos |
| **Recolector** | Empresa o particular contratado para una recolección concreta, por campaña y parcelas | Ve solo su encargo y sus propios partes/tickets; comunica pesadas y, si procede, resúmenes de jornales/maquinaria; nunca todas las fincas ni la contabilidad del cliente |

Un titular puede ser simultáneamente explotador. Una cuenta puede ejercer distintos roles en distintas fincas. Un particular sin empresa puede actuar como recolector. **Jornalero no es un rol de acceso obligatorio**: se mantiene como trabajador registrado en jornales por el titular/explotador/recolector; una cuenta propia es opcional para casos futuros.

**Distinción obligatoria:** «arrendatario», «gestor», «cesión», «aparcería» y «propietario» pueden seguir siendo relaciones declaradas/contractuales diferentes, aunque la UX de explotación sea única. Marcar «Soy titular» no verifica legalmente propiedad ni concede permisos. No confundir titular civil, titular administrativo CUE/REAFA y usuario técnico.

## 3. Dos modelos de trabajo, con progresividad

**A. Individual (predeterminado).** Cada cuenta gestiona su información sin colaboración: fincas, parcelas, campaña, pesadas, jornales y maquinaria, con sincronización personal entre dispositivos cuando G2 esté certificado. No requiere acuerdos ni invitaciones.

**B. Colaborativo optativo, mínimo.** Solo se comparten hechos relevantes para un vínculo concreto:
- **Titular + explotador:** Pesadas canónicas verificadas de la campaña y reparto de kg aprobado. La contabilidad particular de cada uno sigue privada, salvo acuerdo explícito.
- **Titular o explotador responsable + recolector:** un *encargo de recolección* limitado a finca, campaña y, si aplica, parcelas; el recolector puede enviar partes de pesadas, fotos de tickets y resúmenes de trabajos. No recibe otros datos agrarios.
- La empresa recolectora mantiene su gestión de personal, costes y maquinaria en su propio ámbito. El cliente solo registra **el coste del servicio contratado** y, si está pactado, un resumen de trabajo, no su nómina interna.

No se exige una plataforma empresarial completa, contratos firmados electrónicamente, cuenta a cada jornalero, notificaciones instantáneas, colaboración general de todo el workspace ni distribución automática de gastos.

## 4. Reparto de cosecha: independiente del coste de recogida

Puede haber un acuerdo de campaña aceptado por las partes con porcentajes sobre kg **netos verificados** (ejemplo: 70 % explotador / 30 % titular). En 10.000 kg, las cuotas informativas serían 7.000/3.000 kg, sin atribuir automáticamente pagos, beneficios, propiedad ni salida física de producto.

- Base única: Delivery/Pesada canónica asociada a finca y campaña; una Pesada no se copia ni se cuenta dos veces por tener varios lectores.
- Suma 100 % para reparto completo; cálculo exacto con gramos y decimales, redondeo determinista.
- Estado borrador/propuesto/aceptado, aprobación bilateral, vigencia y versión; no inventar cuotas sin aceptación.
- Correcciones posteriores afectan al resultado conforme reglas versionadas y trazables; no reescribir acuerdos pasados en silencio.
- **Reparto de KG ≠ reparto de costes ≠ liquidación/pago.** Los gastos y el importe económico de la aceituna quedan separados.

## 5. Dos modalidades de coste de recolección

La **modalidad del servicio** es una propiedad del encargo/recolección, NO un rol nuevo.

### 5A. Precio fijo por kilogramo (precio cerrado)

El contratante acuerda un precio en EUR/kg sobre los kg aceptados. Ejemplo 3.000 kg × 0,30 EUR/kg = **900 EUR** de coste de recolección.

- Por defecto incluye medios de la empresa (sus jornales, maquinaria, combustible y transporte). No registrar otra vez estos costes como gastos adicionales del titular.
- Adicionales excepcionales **solo si se pactan y registran expresamente**, sin sumar dos veces la misma factura.
- Cálculo y propuesta con precisión decimal; contabilidad canónica se asienta una sola vez en Expense cuando el gasto corresponde/queda aprobado. Distinguir estimado, facturado y pagado.
- Corrección del peso debe recalcular la propuesta e identificar si existe factura ya emitida/contabilizada (no modificar dinero asentado sin conciliación).

### 5B. Por jornales, maquinaria y gastos desglosados

El contratante registra/importa gastos según acuerdo:
- Personas × jornales enteros, medias jornadas u horas × tarifa pactada;
- máquina vareadora y otras máquinas (horas/días/tarifa);
- combustible, acarreo/transporte y conceptos adicionales.

Ejemplo ilustrativo: 3 personas × 2 días × 65 EUR = 390 EUR, maquinaria 150 EUR, combustible 45 EUR y transporte 60 EUR: **645 EUR**. Son cifras de ejemplo, no tarifas por defecto.

- Reutilizar HarvestLabour/Worker, maquinaria y Expense existentes; **un único libro de gastos**.
- Los partes de trabajadores de la empresa son propios; el titular **no paga por duplicado** esas nóminas si abona una factura de servicio.
- Separar **quién realiza**, **quién asume el coste**, **quién paga** y **quién presenta el parte**.
- Modalidades mutuamente excluyentes en el cálculo base; cambio entre ellas requiere revisión de gastos previos y advertencia de duplicados.

## 6. Flujo mínimo de empresa recolectora (prioridad colaborativa)

1. El titular o el explotador autorizado crea un encargo con finca, campaña, parcelas opcionales, recolector, fechas, modalidad de precio y permisos.
2. La empresa o particular recibe/acepta la invitación con cuenta autenticada. La aceptación online activa un permiso **limitado al encargo**, no a todo el workspace ni a campañas anteriores.
3. Recolector registra **parte de pesada**: kg netos, fecha/hora opcional, cooperativa, parcelas/origen y foto del ticket; puede adjuntar resumen de jornales/maquinaria.
4. Se guarda localmente, queda **pendiente de sincronización** y se envía con ID estable. El servidor valida cuenta, encargo, campaña, parcelas, vigencia y permisos.
5. El responsable operativo designado (titular o explotador según contrato) **verifica o rechaza/corrige con trazabilidad**. Una aceptación vincula exactamente una Pesada oficial y sus adjuntos. Los demás autorizados ven la misma Pesada tras sincronización.
6. El titular recibe la información verificada y ve su cuota si existe acuerdo; no se le exponen por defecto sueldos/facturas internos de la empresa.
7. Al vencer o revocarse el encargo, quedan bloqueados los nuevos envíos/accesos no autorizados. Se conserva histórico permitido y procedencia; datos descargados offline con anterioridad requieren política específica.

No afirmar que la subida se recibe instantáneamente: depende de cobertura, permisos y verificación. No atribuir un documento/foto a otra cuenta sin autorización.

**Decisión abierta de diseño:** quién confirma una pesada cuando existe titular + explotador. Propuesta: un responsable operativo autorizado por campaña, con visibilidad de correcciones para las partes interesadas; evitar exigir doble confirmación por defecto. Requiere validación de producto antes de implementar.

## 7. Modelo técnico mínimo y prohibición de duplicaciones

**Conservar IDs existentes** de finca, parcela, campaña, pesada, jornada, gastos y adjuntos. No crear versiones paralelas de Cuaderno ni duplicar registros entre workspaces.

Para colaboración nueva, estudiar una entidad aditiva «encargo_recoleccion» (o contrato equivalente) con ID, finca, campaña, parcelas permitidas, usuario/organización, modalidad, vigencia, estado y capacidades; y «parte_recoleccion» inmutable/idempotente con autor, operación, datos, documentos y estado de revisión, vinculado opcionalmente a la Pesada canónica aceptada. Los nombres no son una migración aprobada.

**Separar cuatro capas:**
1. Relación declarada/contractual (titular/explotador/recolector; no confiere acceso).
2. Permiso real verificado y revocable por servidor (capabilities + límites por encargo/campaña/parcela).
3. Partes comunicados por terceros (SUBMITTED/PENDING/VERIFIED/REJECTED o equivalentes; no mutan de entrada las Pesadas oficiales).
4. Operaciones canónicas existentes (Delivery, Labour/Harvest, Expense, Document), cada una con una sola fuente de verdad.

## 8. Auditoría de arquitectura observada 29/10/2026 (NO certificación E2E)

- main: 3224ac97489537c5b1b1812dab52df133de5b9d3. Farm.workspaceId y Parcel.farmId; FarmListScreen/FarmDetailScreen existentes; DeliveryEntity lleva farmId, campaignId, netGrams; HarvestLabour/Worker almacenan jornales; OrganizationRole.SERVICE_PROVIDER; adjuntos locales y outbox.
- Supabase DEV magina-olivo-dev: tablas farm_access_grants, farm_invitations, farm_contracts, farm_contract_attachments, campaign_harvest_share_agreements, con RLS habilitado; buckets privados farm-attachments y farm-contracts. Su existencia **NO** demuestra que la app los use.
- Las políticas actuales de lectura de Deliveries usan capacidades de finca y no prueban por sí solas restricción a una **campaña** concreta. No existe aquí evidencia de backend completo de jornales de recolectora. Hay que definir autorización por encargo y pruebas negativas.
- G2.6 (PR #779/#780/#782/#786/#788) prepara selección/autorización de workspace y CAS Campaign, en Draft. La propuesta de workspace_memberships #780 **NO** implica automáticamente alcance por finca/campaña; acceso a una finca no debe conceder acceso a todo el workspace. Sus SQL nuevos no se consideran aplicados a DEV por estar en PR.
- #738 contiene arquitectura colaborativa amplia, hoy demasiado grande como requisito de la primera versión. Esta adenda fija la variante **simple y progresiva** sin descartar capacidades posteriores.

## 9. Riesgos P0 y barreras

| Riesgo | Barrera obligatoria |
| --- | --- |
| Empresa ve otras fincas, campañas, costes o contratos | RLS/RPC y Storage limitados a encargo/campaña/parcela; probar usuario tercero sin acceso |
| Permiso de finca implica permiso de workspace completo | Integración explícita de las ACL; nunca asignar workspace_memberships a un recolector solo por encargo |
| Pesada o foto duplicada al reintentar | UUID estable + idempotencia de recibo + checksum/relación documental + unicidad contable |
| Conflicto de kilos de dos operadores | Estados de propuesta/verificación, versiones y corrección auditada |
| Jornales de empresa contados otra vez en titular | Contabilidad separada por actor y modalidad; factura de servicio es gasto canónico del contratante |
| Revocación offline o cambio de cuenta | Bloqueo seguro de escrituras remotas, cuarentena y tratamiento explícito de cachés previas |
| Migración Room mal combinada con G2.6/Riego | Cambio aditivo posterior a integración de G2; backup restaurado y pruebas de upgrade |
| Campos legales confundidos con autodeclaración | Relación indicada como declarada, sin acreditación registral ni firma legal fingida |

## 10. Orden de entregas — prioridad de producto P0

**P0-DOC (esta adenda):** decisión de alcance y matriz de escenarios aceptada; no ejecución técnica ni cambios de app. La revisión de roles y costes debe anteceder cualquier nuevo desarrollo estructural de colaboración.

**P1 — G2.6 individual seguro:** seguir cerrando la sincronización personal y sus pruebas reales de recuperación (no reabrir ni alterar ramas G2.6 por este documento). El cierre de G2.6 es dependencia de colaboración.

**P2 — Roles visuales sin colaboración:** mostrar «Tu relación con esta finca» en Mi Campo, inicialmente informativo y sin permisos remotos autoconcedidos; mantener finca antigua en «No especificado». No añadir una columna legal que reclame propiedad sin validación.

**P3 — Modalidades económicas locales:** precio por kg o gastos desglosados, cálculo con partidas existentes, sin doble cómputo; pruebas de correcciones, reversión y cambios de modalidad.

**P4 — Titular + explotador:** compartir solo campaña/pesadas verificadas, acuerdos porcentuales aceptados, visibilidad mínima y contabilidad privada; prueba A/B, conflicto y offline.

**P5 — Recolector por encargo:** un titular + una empresa/particular + una campaña + una parcela + una pesada con foto, revisión y autorización acotada, sin duplicados; añadir resúmenes de jornales/maquinaria en otro bloque.

**P6 — Evolución posterior:** varios encargos, distintas empresas, empresas con múltiples operarios, contratos PDF avanzados, facturación, notificaciones y web ampliada. No bloquear P1–P5 por estas capacidades.

**Plan de simplificación si P4/P5 resulta excesivo:** mantener individual plenamente funcional y ofrecer intercambio controlado de parte/ticket con revisión/importación manual y trazabilidad; **no** simular sincronización automática ni fusionar por Catastro; no renunciar a integridad para acortar plazos.

## 11. Gate de aceptación por fases

- Producto: un agricultor que trabaja solo no ve pantallas obligatorias de empresas, contratos o roles. Puede registrar sus fincas, pesadas y gastos igual que antes.
- Roles: titular y explotador tienen relación por finca; recolector solo por encargo. Ninguna declaración/autoselección otorga RLS.
- Economía: 3.000 kg × 0,30 EUR/kg = 900 EUR; modalidad desglosada admite jornales, medias jornadas, horas, maquinaria, combustible y transporte; no doble gasto.
- Reparto: 10.000 kg → 70/30 = 7.000/3.000 solo si acuerdo aceptado; no aplicar porcentajes automáticamente a costes ni beneficios.
- E2E colaboración: dos cuentas aisladas, foto privada, una sola Pesada por UUID, offline/reintento, revisión, revocación, tercera cuenta denegada, campañas anteriores no visibles, acceso por parcela autorizado, cero duplicados de Expense.
- Toda integración exige CI verde por SHA, pruebas Android API35 y RLS con identidades A/B/C; backup/restauración sin pérdida; prueba física antes de APK candidata.
- Ningún merge, SQL DEV/PROD, generación de APK candidata o despliegue sin puerta separada.

## 12. Coordinación y precedencia

- **Producto:** esta adenda + #735 como decisión vigente sobre *alcance simplificado*. #738 se conserva como contexto histórico/visión ampliada y requiere actualizar antes de ejecución si contradice esta decisión.
- **Sincronización:** #726 y G2.6 siguen siendo los contratos técnicos activos. No ordenar a Codex/Claude/Antigravity modificar G2.6 solo por registrar esta adenda.
- **Economía agrícola:** reutilizar contratos actuales CR-010/CR-012 y Expense; #737 contratos/70:30 debe adaptar su primera entrega a la versión simplificada.
- **Navegación:** no nuevas pestañas, no rehacer Mi Campo ni crear Cuaderno paralelo.
- **Prioridad:** cerrar conceptos básicos **antes** de iniciar nuevos módulos grandes; ejecución progresiva por PR pequeñas tras la integración de G2.6.

## 13. Decisión y reservas

**APPROVED — producto y alcance simplificado, 29/10/2026, por el propietario.**  
**PENDING — arquitectura exacta, permisos por campaña y parcela, UX de confirmación, contratos/versiones, migraciones y su aprobación técnica.**  
**NO-GO — afirmar multiusuario funcional o desplegar sin gates.**  

Esta adenda no modifica ni sustituye código; se propone mediante PR documental Draft para revisión e integración expresa en el registro de decisiones.
