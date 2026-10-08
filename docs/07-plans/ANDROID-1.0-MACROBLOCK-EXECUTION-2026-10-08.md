# Mágina Olivo Android 1.0 — ejecución por MACROBLOQUES
**Decisión del propietario:** 2026-10-08. **Estado del plan:** preparado para adopción por el ejecutor, no declara ningún Gate superado. **Tracker principal:** [#696](https://github.com/izc05/magina-olivo-v20/issues/696). **Fuente de verdad:** `main` y `AGENTS.md`, Product Lock, `CURRENT-STATE.md`, CR aprobados y sus sucesores. Esta hoja evita órdenes dispersas; NO crea una segunda hoja de ruta ni autoriza saltar fases.

## Qué cambia en la forma de trabajar
**Macrobloque = paquete grande de trabajo comprobable**: objetivo de usuario completo, escenario de reproducción, pruebas, hallazgos, correcciones priorizadas y entrega. Se espera que el agente recorra varias tareas del bloque sin pedir instrucciones rutinarias; **no significa una PR gigantesca**.

**Control de riesgos:** una única implementación productiva Android simultánea bajo Codex (mientras #696 siga vigente). Dentro del macrobloque, defectos **independientes** se reparan en PR pequeñas y secuenciales desde `main`, con test de regresión y CI de tres checks. Claude hace QA, review y preparación sin competir sobre los mismos archivos/branch. Nunca fusionar una PR con checks rojos/en curso o revisión pendiente.

**Hitos:** un bloque NO se cierra porque exista documentación, se abra PR o compile el emulador. La conclusión exige evidencia y —cuando el Gate lo requiere— comprobación del propietario en dispositivo físico. No se reabre Web V3 por este plan. En 1.0, OCR sigue aplazado por CR-013: Pesada manual + foto opcional.

## Preparación transversal: habilidades y reglas
- Revisar primero el estado actual del issue #696, los SHA de `main`, PRs abiertos/fusionados y checks vigentes: no repetir #689/#680/#684/#692/#698 si ya están integradas.
- Skills preparadas: [#701](https://github.com/izc05/magina-olivo-v20/pull/701) `magina-android-finalizer`; [#702](https://github.com/izc05/magina-olivo-v20/pull/702) `gh-fix-ci`, `testing-setup`, `systematic-debugging`, `test-driven-development`, `verification-before-completion`. **Invocarlas desde la rama donde estén presentes; no afirmar disponibilidad en main hasta que cada PR esté fusionada.**
- **Cadena de diagnóstico:** `gh-fix-ci` para logs CI si falla → `systematic-debugging` causa raíz → `test-driven-development` regresión rojo/verde → `testing-setup` si hay huecos estructurales de cobertura → `verification-before-completion` antes de PASS/merge. `magina-android-finalizer` guía alcance, preservación de datos y Gate.
- Skills no autorizan añadir automáticamente librerías, cambiar Gradle, Room, DI, permisos, esquemas ni navegación. Mantener `AGENTS.md`, arquitectura Kotlin/Compose/Room offline-first y diseño verde/crema aprobados.

---

## M0 — Infraestructura y riesgo de datos | inmediato, sin bloquear el resto por documentación
**Objetivo:** el punto de partida es un `main` reproducible, sin regresiones P0 y con pruebas que no destruyen datos.

**Ejecutar:**
1. Leer las PR realmente integradas y los tres checks por su **head SHA**; conservar enlaces a logs, pruebas y fallos restantes.
2. Revisar la integración #698 (aislamiento de carpetas de tests), comprobar que ejecuciones posteriores no eliminan fotos/adjuntos de otro workspace ni directorios compartidos.
3. Revisar #701/#702 por separado; solo fusionarlas cuando `foundation`, `gate3-emulator`, `gate3-evidence` estén en SUCCESS para el SHA final y no haya conflictos. En caso contrario, dejar pendiente y continuar con M1, sin bloquear Android.
4. Documentar SHA inicial de `main`, versión/variant y fuente del artefacto APK; jamás hacer pasar como prueba física una suite de emulador.

**PASS de M0:** hechos confirmados contra GitHub, checks trazables y ninguna regresión P0 conocida ignorada. Los Skills son auxiliares, no un prerequisito para M1.

## M1 — AÑO AGRÍCOLA COMPLETO, de punta a punta | PRIMER MACROBLOQUE PRODUCTIVO
**Objetivo:** confirmar que un agricultor puede llevar una finca durante todo el año sin perder el contexto ni falsear kilos o importes. **Ejecutor Codex**, QA Claude. Basado en #696, #409, #356 y el alcance de #340.

**Escenario reproducible (sin borrar datos personales):**
1. Crear finca F1 con foto y finca F2 sin foto; crear parcelas manuales y Catastro; rellenar/editar alias, superficie, referencia, municipio y provincia **solo si verificados**. Comprobar archivado/restauración.
2. En F1 registrar **Trabajo** (poda), **Riego**, **Tratamiento**, Jornal general, Maquinaria vinculada al trabajo, y Gasto general. F1 y F2 no comparten datos; actividad se mantiene tras reinicio/modo avión.
3. Crear campaña C1 con **dos parcelas**: activación, varios días, **dos Pesadas el mismo día** con pesos distintos, cooperativa, nº de vale, suelo/árbol, una con foto y otra sin foto, jornada automática; evitar asignación inventada de kilos a parcelas.
4. Registrar jornales identificados con jornada completa/media/horas y pagos parciales; maquinaria, gasoil y gastos de recolección; validar que `Expense POSTED` sea el coste canónico y pagos no dupliquen gasto.
5. Añadir un rendimiento posterior, consultar cobertura y coste/kg, cerrar C1, preparar/consultar C2 e histórico sin reescribir C1.
6. Desde Mi Campo → Cuaderno → Campaña → Diario volver con Back; cambiar F1/F2; seleccionar campaña histórica mientras otra está activa: acciones nuevas deben usar el ámbito correcto, nunca escribir en campaña cerrada inadvertidamente.
7. Simular sin cobertura, reiniciar proceso, variar orientación, pasar de una APK anterior a una nueva **sin desinstalar** y comprobar registros **y foto con bytes/hash antes y después**. Preservar bases de QA aisladas; nunca lanzar fixtures destructivas en datos reales del propietario.

**Evidencia exigida:** contratos de dominio, Room/migraciones soportadas, tests Compose/navegación, E2E API35, logs de modo avión y reinicio, trazabilidad de fotos, SHA y fallos reproducibles. Test de regresión antes de cada arreglo. Separar **probado en emulador** de **pendiente teléfono físico**.

**Salida M1:** matriz `PASS/FAIL/PENDIENTE` por escenario y solo las PR de defectos reales confirmados; CI verde de cada PR. En ausencia de fallo, no reescribir código. El recorrido no sustituye Gate21 físico.

## M2 — CALIDAD DE USO DIARIO SIN REDISEÑO | tras M1 estable
**Objetivo:** quitar fricciones de uso real y falsos estados respetando la navegación actual. Antes de actuar, comparar issues y código con el `main` vigente.

**Lotes de QA, no implementación simultánea:**
- **M2-A Contexto/operaciones:** #409 y #697: selección y regreso de finca/parcela/campaña, filtros de diario, Jornal fuera de campaña, Gasto general vs recolección; cero acciones duplicadas.
- **M2-B Perfil/Avisos:** #462 y Gate21: estado real de permisos y canal, aviso 08:00, ajustes ON/OFF, municipio/cooperativa persistentes; no afirmar que una notificación suena si Android la bloquea.
- **M2-C Mapa/Catastro:** #574/#697: recintos L/U, agujeros, MultiPolygon, etiqueta dentro, panel superpuesto, GPS permitido/denegado, 1 y 20 parcelas, zoom/ortofoto y degradación offline. Corregir solo si reproducción demuestra fallo.
- **M2-D Inicio externo:** AEMET/radar/mercado, caducidad y probabilidad de lluvia, sol salida/puesta **si implementado**, fallbacks/errores sin desactivar Cuaderno.
- **M2-E UX:** 360/390/430 dp, texto grande, accesibilidad/semántica, botones y teclado; pruebas visuales sobre el diseño bloqueado, sin nueva pestaña ni copiar Agritech/OleAria.

**Salida M2:** al menos una matriz de QA por lote, capturas referenciadas y pequeños arreglos según severidad; ninguna función adicional sin autorización. Las ideas de municipios #700, carrusel, noticias, negocios y rutas #699 solo preparación documental **hasta autorización después de Gate21**; no mezclar en esta APK.

## M3 — CANDIDATA APK INTEGRADA Y GATE 21 FÍSICO | después de M1 y M2 imprescindibles
**Objetivo:** entregar UNA APK concreta de prueba con todas las correcciones necesarias integradas en `main`, no un ensamblado de ramas.

1. Congelar el conjunto de cambios; confirmar `main` HEAD exacto y todas las PR incorporadas; si el CI de ese SHA está pendiente, **no marcar candidata aprobada**.
2. Ejecutar Gradle CI vigente: `lintDevDebug`, `testDevDebugUnitTest`, `assembleDevDebug`, `assembleStagingDebug`, `assembleProductionDebug`, `assembleDevDebugAndroidTest`, además de `gate3-emulator` y `gate3-evidence`; registrar tests y fallos, no solo título de workflow.
3. Adjuntar: enlace al artefacto GitHub Actions DEV, variant/versión/versionCode, SHA commit, hash SHA256 del APK, resultados reales API35 y E2E. **No inventar descarga ni hash** si no se calcularon.
4. Preparar **una sola sesión física** con `docs/06-testing/PHASE21-GATE-CHECKLIST.md` y los hallazgos #356: instalar encima de versión anterior sin desinstalar; comprobar Perfil, avisos, Pesada+foto, gastos, coste/kg, radar, integridad de finca y cámara.
5. Registrar modelo Android, versión, errores reproducibles, capturas consentidas, resultado de cada paso. Abrir la siguiente PR P0 si falla; no cerrar Gate21 sin confirmación del propietario.

**Salida M3:** `APK lista para prueba` si checks+artefacto, **Gate21 PASS solo con aceptación física real**. No declarar “Android 1.0 completo” todavía.

---

## MACROBLOQUES POST-GATE21 — no autorizados para código productivo antes del Gate
**M4 — Cuenta y seguridad / Gate22.** Según #340 y fases aprobadas: Supabase Auth opcional, sesión/recuperación, esquema Postgres/PostGIS, RLS tenant A/B, Storage privado, sin claves privilegiadas en cliente; cuentas, privacidad y recuperación. Requiere permisos/credenciales del propietario y plan aprobado. Priorizar contrato, migraciones reversibles y pruebas de aislamiento.

**M5 — Sincronización y segundo dispositivo / Gate23.** Outbox Room, WorkManager, reintentos idempotentes, conflictos, adjuntos, datos offline persistentes, recuperación tras reinstalar, estados de sincronización inequívocos. Puerta: convergencia real sin pérdidas, sin confundir `guardado local` con `sincronizado`.

**M6 — Servicios finales / Gates24–25.** Admin privada y mínima necesaria; cooperativas/contenido dentro del alcance aprobado y fuentes legítimas; informes PDF offline (campaña/finca/parcela) conciliados. WEB V3 sigue pausada mientras #696 lo ordene; no adelantar WEB-1 por requerir datos de nube/sync.

**M7 — QA, beta y publicación / Gates26–28.** Stress de miles de registros, permisos/cámara/notificaciones, almacenamiento, migraciones, batería, varias versiones Android, uso real en campo, privacidad y textos legales, Play internal/closed, firma, crash/ANR y rollback. Gate final solo con evidencia y aceptación.

## Ritmo autónomo y reporte: pocas interrupciones, máxima claridad
Codex trabaja **de corrido dentro del macrobloque vigente** con estos subpasos: `auditar → reproducir → escribir prueba → corregir → ejecutar tests → abrir PR pequeña → revisar checks → integrar si procede → seguir siguiente caso`. No pedir confirmación rutinaria por cada test o parche acotado ya autorizado. **Parar y escalar** si hay pérdida de datos, permiso/credencial, CR/arquitectura, migración incompatible, fallo de seguridad, revisión rechazada o cambio de Gate. No saltar un CI que siga rojo/en curso.

Al terminar una iteración, comentario en [#696](https://github.com/izc05/magina-olivo-v20/issues/696):

```text
MACROBLOQUE Mx / escenario:
MAIN SHA inicial → final:
Responsable / rama / PR:
PASS verificados (comandos, cantidades, links Actions, emulador/API):
FAIL y reproducción:
PENDIENTE (especialmente móvil físico / Gate):
¿Foto, kg, gastos, workspace e historial intactos? Evidencia:
Siguiente acción dentro del mismo macrobloque:
Web V3 / nuevas fases: SIN CAMBIOS
```

**Regla final:** los 5–6 macrobloques son **paquetes de resultado** para avanzar más, no permiso para acumular una PR inmanejable ni declarar que funciones sin construir ya están terminadas.
