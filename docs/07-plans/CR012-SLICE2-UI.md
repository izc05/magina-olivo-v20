# Task 2B — CR-012 Jornales and payments UI

Norma Issue #309 completo y contratos/data task2A existentes. Lee AGENTS.md y
CURRENT-STATE; no alterar navegación raíz ni maquinaria. No subagents.

Sustituir LabourSheet: elegir UNA persona existente o crearla con Nombre y
apellidos; mantener reutilización. Duración completa/horas (no nuevo HALF_DAY
ni Solo número); tarifa aplicada editable precargada usual de finca, currency
coherente contexto. Pago inicial opcional: Sin pagar por defecto / Pago parcial
importe / Pagado completo. Guardar CrewDraft de una persona con snapshot y
LabourPayment estable entre reintentos (rememberSaveable UUID). No inferir que
POSTED significa paid. Mostrar error real si falta precio o importe inválido.
Jornal legacy nullprecio queda visible como precio sin confirmar, con edición
explícita duración/tarifa para confirmar snapshot, nunca relleno automático.

Detalle de persona accesible desde Campaña/Cuaderno > Jornales y del Día. Mismos
datos canónicos: fechas trabajadas, completas/horas, generado/pagado/pendiente,
Trabajo y Pagos separados; moneda por grupo sin conversiones implícitas. Cards
compactas por persona; legado Sin identificar no recibe pagos. No largas listas
en dashboard principal (Slice4 hará limpieza final, puedes preparar callback).

Registrar pago: persona contextual bloqueada, pendiente actual, importe, fecha
editable con hoypor defecto, nota opcional, Guardar y Pagar todo. Permite
campaña cerrada; precio/coste histórico bloqueado. Sobrepago explica pendiente.
Pago correction soft-delete solo explícito desde movimiento con confirmación.
Pantallas usan flows con loading/error/empty, botones disabled durante save;
rotación conserva draft y UUID; repetir action saving no duplica movimientos.
UI nunca calcula deuda sumando snapshots aparte del ledger: usa allocation de
Expense POSTED y settlement del dominio; no inventar cero para no atribuible.

Semántica visual RC012: jornales terracota, pendiente MoWarning, parcial MoInfo,
pagado MoSuccess; texto+icono+color, superficies claras. Reutilizar componentes
Mo y tokens existentes; si SuccessTint falta añadir al sistema, no hex locales.

Tests TDD Compose para selección persona obligatoria, precio y completo/horas,
sin opciones anonimas/media nuevas; inicialsin/parcial/completo y validación;
vista por persona 240/180/60 y Trabajo/Pagos; estado con texto/icono y pago
trascierre. Adaptar pruebas antiguas de alta anónima a legado directo preservado,
no quitar assertions. Capturas emulador del formulario/persona/pago.

No editar data/domain/writers de2A salvo defecto comunicado primero al root.
No push/merge. Compilar suite completa una vez y reportar tests reales; commit
solo feature/theme/Compose tests. Reporte full al archivo indicado por root.
