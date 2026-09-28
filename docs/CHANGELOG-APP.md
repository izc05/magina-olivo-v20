# Mágina Olivo (Android) — registro de versiones

Cada APK muestra su versión en **Perfil → Acerca de**, p. ej. «Versión 0.2.0-dev · compilación 531».

## En curso — PR #284 (sin versión publicada)

- Inicio: retiradas la tarjeta «Empieza por tu primera finca» y la cuadrícula redundante «Accesos rápidos»; la creación de fincas sigue en Mi Campo y no se elimina ningún destino.
- Tiempo: tarjeta sobre la foto con municipio, proveedor y antigüedad cuando hay datos; abre una semana de hasta siete días y reutiliza el radar existente. El contrato diario es aditivo y conserva cachés antiguas.
- Mercado: la tarjeta de Inicio muestra una gráfica compacta de 12 semanas con colores/leyenda AOVE, Virgen y Lampante; el pulso de AOVE.net permanece en el detalle «Ver mercado».
- Pendiente de validación Android/visual y de instalación en dispositivo. La semana real requiere el despliegue autorizado de la Edge Function; esta PR no despliega Supabase.

- **Versión** (`0.2.0`): se sube a mano en `app/build.gradle.kts` (`appVersionName`) cuando se entrega un
  bloque de cambios al propietario, y se anota aquí.
- **Compilación** (`531`): la pone la CI sola (número de ejecución de GitHub Actions, siempre creciente).
  También es el `versionCode`, así que un APK nuevo siempre es «más nuevo» para Android.
  Una compilación local muestra «compilación local».

Para saber qué lleva un APK: mira la versión en el Perfil y busca abajo su bloque; la compilación
concreta está en la ejecución de CI con ese número (Actions → Android CI → #número).

## 0.5.0 — 28/09/2026

- Mercado del aceite con datos oficiales (Fase 20D-3):
  - La «Tendencia oficial semanal» de Inicio se enciende: AOVE, Virgen y Lampante de la Junta de
    Andalucía (precio en almazara o bodega, €/kg) con su cambio frente a la semana anterior, leídos
    por la función `oil-market` del servidor y guardados en el teléfono para verlos sin cobertura.
  - Nueva pantalla «Mercado del aceite» («Ver mercado» en la tarjeta de Inicio): pulso diario de
    AOVE.net, tendencia oficial y gráfico de las últimas 12 semanas, una línea por categoría. Una
    semana no publicada es un hueco en la línea y se dice («1 semana sin dato»); nunca se rellena.
    Debajo, la fuente y cuándo se consultó.
- Arreglos de la prueba de Gate 20 en emulador (compilación 575):
  - Recolección → «Abrir jornada de hoy»: la jornada se puede abrir antes de tener pesadas. Sus
    kilos pasan a ser la suma de las pesadas que se le añaden («Añadir pesada» dentro
    de la jornada). Abrirla otra vez el mismo día devuelve la misma jornada; con varias fincas en
    campaña se pregunta en cuál.
  - Mientras no tiene pesadas, la jornada muestra «Kg pendientes de pesada» (nunca «0 kg») y no
    cuenta en los kilos de la campaña, de Inicio ni en la gráfica.
  - La ficha de la finca ya no dice «Sin campaña activa» cuando su campaña está en «Recolección».

## 0.4.0 — 28/09/2026

- Mercado del aceite en Inicio (Fase 20D-1, Issue #271):
  - «Pulso diario»: el widget gratuito de AOVE.net tal como lo publica su autor (solo con conexión,
    con su crédito; sus cifras no se copian a la app).
  - «Tendencia oficial semanal»: AOVE, Virgen y Lampante de la Junta de Andalucía con su cambio
    respecto a la semana anterior («AOVE ↓ 5,7 % esta semana»), semana y fuente; «Dato antiguo»
    cuando ya debería haber una semana nueva. Se activa cuando esté desplegada la función del
    servidor (20D-2); hasta entonces dice «Sin fuente configurada».
  - El recuadro del «Pulso diario» toma la altura del widget de AOVE.net (antes quedaba cortado en
    Lampante y había que desplazarse dentro).
- Revisión UX con el propietario (Codex, `docs/06-testing/UX-OWNER-FEEDBACK-2026-09-27.md`):
  - Mi Campo vacío: una sola acción «Crear mi primera finca», sin totales a cero; «Maquinaria» sale
    de Mi Campo y queda solo en Perfil.
  - Nueva finca: solo el nombre a la vista; municipio, provincia, descripción y notas bajo «Más
    detalles» (abiertos al editar si ya tienen datos).
  - Parcelas: un único «Añadir» que ofrece «A mano» o «Desde el mapa y Catastro»; el formulario
    manual pide alias y superficie y pliega olivos, riego y Catastro; al importar todo el lote
    desde el mapa se vuelve a la lista de parcelas.
  - «Registrar hoy»: primero el tipo de trabajo, luego su pantalla propia con los datos del tipo
    plegados («Detalles de poda»…); lo registrado hoy o antes se guarda como **Completado** (una
    fecha futura o un recordatorio lo dejan como Planificado).

## 0.3.0 — 27/09/2026

- Jornales por persona en el resumen de la campaña: una fila por trabajador (por su identificador,
  aunque cambie de nombre) y los jornales anotados solo como número, aparte («Sin nombre»); la
  tarjeta muestra el desglose real («4 jornadas · 1 media») y «M personas con nombre» (254-D, #268).
- Sin duplicados en Diario y Recolección: las pesadas y gastos de una Jornada se leen dentro de su
  fila (con su rendimiento, «rend. 21 %» o «rend. pendiente»), no repetidos debajo; los totales no
  cambian (254-E).
- Prueba completa del flujo de recolección: Campanil · 2.390 kg · Árbol/vuelo · Bedmarense · vale +
  foto → Jornada → 5 jornales → gasto → totales, sobreviviendo a un reinicio (254-E).

## 0.2.0 — 27/09/2026 (compilación 532)

Recolección, Cuaderno y formularios, tras las pruebas del propietario en el móvil.

- Marca oficial: icono de la app, logotipo y monocromo (PR #261).
- «Pesada» como única acción de recolección; vocabulario Jornada/Pesada (#261).
- Formulario con el tipo ya elegido desde «Registrar hoy» (#253 → #261).
- Origen de la aceituna en cada Pesada: Árbol/vuelo · Suelo; pesadas antiguas «Sin indicar» (#262, base de datos v16).
- Recolección: resumen 2×2 (kg pesados, pesadas, rendimiento medio ponderado, gastos) y «+ Nueva pesada» (#263).
- Jornada: rendimiento de cada pesada y rendimiento ponderado de la jornada (#264).
- Cuaderno: línea de día «Hoy / Ayer / Mañana», hecho frente a planificado con marca ✓/🕒, colores por tipo en Recolección (#265).
- Formulario de actuación corto: tipo en fichas, parcelas en fichas, «Más opciones» plegado (#266).
- Versión y número de compilación visibles en el Perfil (#267).

## 0.1.0 — hasta el 26/09/2026

Base RC1.2: fincas, parcelas y mapa, campañas, actuaciones tipadas, cosecha/jornadas, pesadas y
rendimientos, jornales y maquinaria de jornada, gastos, agenda y avisos, Cuaderno (Diario,
Fitosanitario, Gastos, Campaña), tiempo y radar. Todas las compilaciones mostraban «0.1.0-dev».
