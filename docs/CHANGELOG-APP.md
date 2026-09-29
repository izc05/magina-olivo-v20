# Mágina Olivo (Android) — registro de versiones

Cada APK muestra su versión en **Perfil → Acerca de**, p. ej. «Versión 0.2.0-dev · compilación 531».

## En curso — PR #284 (sin versión publicada)

- Inicio: retiradas la tarjeta «Empieza por tu primera finca» y la cuadrícula redundante «Accesos rápidos»; la creación de fincas sigue en Mi Campo y no se elimina ningún destino.
- Tiempo: temperatura grande directamente sobre la fotografía, municipio y un icono coherente con el estado real del cielo. «Ver previsión» abre el resumen de lluvia/viento, la semana de hasta siete días y el radar existente. La cabecera crece con el texto ampliado; fuente, antigüedad y avisos de caché siguen visibles. El contrato diario es aditivo y conserva cachés antiguas.
- Mercado: la tarjeta de Inicio muestra una gráfica compacta de 12 semanas con colores/leyenda AOVE, Virgen y Lampante; el pulso de AOVE.net permanece en el detalle «Ver mercado».
- Diseño `5cc3f2cc`: CI completa en verde (compilación 598, 300 tests instrumentados y 111 de Room offline). Capturas de Inicio/semana con datos explícitamente sintéticos en 360, 393 y 480 dp y letra al 130 %. La revisión visual detectó recortes de «Superficie» y «5,2 ha» en el resumen existente: ahora apila icono y cifras cuando falta anchura, con un test de texto ampliado; este último ajuste requiere nueva CI. La semana real requiere el despliegue autorizado de la Edge Function; esta PR no despliega Supabase. Pendiente validación en móvil físico.

- **Versión** (`0.2.0`): se sube a mano en `app/build.gradle.kts` (`appVersionName`) cuando se entrega un
  bloque de cambios al propietario, y se anota aquí.
- **Compilación** (`531`): la pone la CI sola (número de ejecución de GitHub Actions, siempre creciente).
  También es el `versionCode`, así que un APK nuevo siempre es «más nuevo» para Android.
  Una compilación local muestra «compilación local».

Para saber qué lleva un APK: mira la versión en el Perfil y busca abajo su bloque; la compilación
concreta está en la ejecución de CI con ese número (Actions → Android CI → #número).

## 0.6.0 — en curso (CR-011, simplificación)

- **Un solo Cuaderno.** Desde una finca, «Cuaderno» abre el Cuaderno principal con esa finca
  elegida (Atrás vuelve a la finca). Desaparece el segundo Cuaderno con Trabajos · Recolección ·
  Resumen: sus trabajos están en Diario (más «Ver todos los trabajos de la finca») y su
  recolección, con «+ Nueva pesada», en Campaña, sin repetir cifras.
- **Seis acciones directas** en el Cuaderno: Trabajo · Riego · Tratamiento · Pesada · Jornal ·
  Gasto, como botones grandes. Desaparece «Registrar hoy», que repetía las mismas opciones en un
  segundo menú. «Documento» y «Maquinaria» dejan el primer nivel: los papeles van con su gasto o
  pesada, y el uso de máquinas con su trabajo o día de recolección.
- «Registrar en esta finca» (finca o parcela) abre el Cuaderno con esa finca y, desde una
  parcela, la parcela visible y quitable («Toda la finca»); el formulario de trabajo la recibe.
- Cada acción conserva la finca del Cuaderno: **Pesada** abre el formulario al momento en esa
  finca; **Gasto** abre Gastos con un nuevo gasto en esa finca; **Jornal**, con la campaña en
  marcha, abre solo el día de recolección de hoy de esa finca (lo busca o lo crea), sin «abrir
  jornada» a mano; sin campaña en marcha, abre un gasto de jornales de la finca.
- Inicio: tocar una campaña en marcha abre el Cuaderno de esa finca en Campaña, no la lista de
  pesadas.
- Retirado el selector antiguo `QuickAddSheet`, que ya no se usaba.
- Recolección ya no tiene «Abrir jornada de hoy»: el día de recolección se crea solo con su primera pesada o con Cuaderno → Jornal. Sin cambios de datos ni de cálculos.

## 0.6.0 — en curso (CR-010, campaña simple)

- Campaña en tres estados: **Borrador → Activa → Cerrada**. Al activarla ya se pueden registrar
  pesadas; desaparece el paso «Iniciar recolección». Una campaña activa se cierra directamente y,
  si se reabre, vuelve a «Activa». Las campañas que ya estaban «en recolección» siguen funcionando
  y se muestran como «Activa».
- El total de kilos de la campaña es siempre la suma de las pesadas. Los kilos antiguos escritos a
  mano en jornadas sin pesada se muestran aparte («registrados sin pesada (histórico)») en la
  campaña y en el Cuaderno: no se pierden y no se suman al total.
- Día de recolección automático: al guardar una pesada (a mano o desde el vale) la app busca o crea
  el día de esa finca y fecha; desaparece la elección «Sin jornada / Nueva jornada». Los kilos del
  día son siempre la suma de sus pesadas: si se borra o se cambia de fecha su última pesada, vuelve a
  «Kg pendientes de pesada» y, si no tiene jornales, maquinaria, gastos ni adjuntos, desaparece.
  Las parcelas del día son las de sus pesadas, sin reparto de kilos. Las jornadas antiguas escritas
  a mano conservan sus kilos y nunca reciben pesadas nuevas. Base de datos del teléfono v17.
- «Añadir vale y leer datos» dentro de Nueva pesada: foto o PDF del vale, se leen los datos y se
  revisan antes de guardar (nada se guarda solo). El lector propone también la **hora** del vale y,
  si su cooperativa coincide sin dudas con una guardada, la deja elegida; si no, queda el texto
  del vale para revisarlo. La pesada confirmada va a su día de recolección como las demás.
- Precios de recolección de cada finca (opcionales): jornada completa (la media es la mitad), hora
  y día de cada tipo de máquina. Con ellos el coste de jornales y maquinaria de cada día se apunta
  **una sola vez** en Gastos («Jornales (calculado)», «Maquinaria (calculada)»), con el precio
  usado guardado en la anotación, y se actualiza al cambiar jornales, maquinaria o precios. Lo que
  no tiene precio queda fuera y se dice («1 tractor sin precio»). Si ese día ya hay un gasto de
  jornales o maquinaria anotado a mano, cuenta ese y el cálculo queda en borrador hasta que eliges
  «Usar el cálculo»; nunca suman los dos. El aceite/lubricante se suma a la maquinaria calculada,
  nunca la sustituye. Las campañas cerradas no se recalculan ni cambian de gasto. Base de datos v18.
- Un gasto de jornales o maquinaria anotado a mano el mismo día pero sin jornada aparece en esa
  jornada con la pregunta «¿Es el mismo coste?»: «Es el mismo coste: enlazar» hace que solo cuente
  uno; si es otro gasto (gasoil, aceite…), se deja aparte y sigue sumando. La app nunca lo enlaza, junta ni borra por su cuenta.
- Resumen de la campaña de un vistazo (Cuaderno → Resumen): días de campaña desde que se activó (o
  hasta su cierre), días con pesadas y con jornales, primera y última pesada, coste contabilizado
  (con los jornales y la maquinaria calculados, contados una sola vez) y coste por kilo pesado. Lo
  que no se sabe se muestra «—», nunca 0.
- Cifras del Cuaderno, de la Recolección y de la Campaña más legibles: valor grande, icono sobre su
  color y borde de color por tipo de dato (pesadas en oro, jornales en oliva, maquinaria en tierra,
  costes en azul; días y trabajos de la campaña en oliva oscuro). El color nunca es la única
  pista: el icono y la etiqueta dicen siempre qué es la cifra, y el valor se parte en líneas en vez
  de cortarse con letra grande.

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
