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

## 0.7.0 — en curso (fase 21, Perfil)

- **Un peso enorme ya no tumba la Pesada (#497).** Si se escribe un número de kilos tan grande que no
  se puede guardar, el campo lo marca como no válido en vez de cerrar la pantalla. Y un reparto entre
  parcelas cuyos kilos sumados no caben en la cuenta se rechaza como «supera el total», nunca se da
  por bueno.

- **Un total de gastos imposible nunca sale negativo (#500).** Si los importes registrados no caben
  en una suma (datos importados o dañados), el resumen de gastos lo da como no disponible en vez de
  mostrar una cifra negativa, igual que ya hacían el coste de recolección y Mi Campo.
- **Un rendimiento del 100 % ya se puede escribir (#499).** El formulario de rendimiento aceptaba
  hasta 99,99 % aunque el 100 % es válido: ahora admite «100» y «100,00» y sigue rechazando cualquier
  valor por encima. Un rendimiento guardado al 100 % se puede abrir y volver a guardar sin error.
- **«Restaurar» parcela solo restaura (#494).** Restaurar solo vale para una parcela archivada: ya no
  puede mover a otra finca una parcela activa, ni reactivar una parcela cuya referencia catastral
  tiene ahora otra parcela activa, ni una del Catastro sin referencia o contorno. El aviso explica el
  motivo y el historial de fincas de la parcela no se toca.
- **Una máquina de recolección ya no se convierte en «Vibradora» (#521).** Al llevar a una jornada
  una máquina de la categoría «Recolección» (vibrador, paraguas, peine…) se anota con su propio
  nombre, sin tipo inventado y sin la tarifa habitual de vibradora: su precio lo confirmas tú.
  Tractor y remolque siguen reconociéndose como tales.
- **Media jornada en jornales nuevos (#490).** Al registrar o corregir un jornal, Duración ofrece
  Jornada completa, Media jornada y Horas. En media jornada se escribe el precio de la jornada
  completa (por ejemplo 70 €) y el jornal cuesta la mitad (35 €); «Pagado completo» paga esa mitad.
  El coste del día la cuenta una sola vez y las medias jornadas antiguas se conservan tal cual.
- **Tus datos no salen del teléfono por la copia de Android (#461).** La app ya no participa en la
  copia de seguridad en la nube de Android ni en el paso a un teléfono nuevo: la base de datos, las
  fotos y documentos adjuntos, la caché de la cámara y las preferencias quedan solo en este teléfono.
  Así nunca se restaura una finca a medias ni un registro que apunte a una foto que no viajó. Perfil →
  Ayuda y privacidad lo explica tal cual: si borras la app, sus datos o cambias de teléfono, se pierden.
- **El coste/kg conserva las milésimas (#486).** El coste por kilo es una ratio, no un pago: ahora se
  muestra con tres decimales (1.440 € / 5.700 kg = **0,253 €/kg**, antes 0,25) en el día, la campaña,
  la comparativa, el histórico y Mi Campo. Los gastos siguen guardándose en céntimos; solo cambia cómo
  se calcula y se lee la ratio.
- **Corregir un trabajo ya no reconstruye sus parcelas (#440, #546).** Al editar cualquier dato de un
  trabajo, sus parcelas se conservan tal como se registraron: mismo nombre de entonces, misma
  superficie tratada y mismos identificadores. Una parcela archivada después ya no impide corregir una
  nota; solo al añadir una parcela nueva se exige que esté activa en la finca.
- **Editar un trabajo ya no borra datos que el formulario no muestra (#453).** Al corregir un riego,
  su tarifa histórica conserva su moneda, sus notas y su enlace con el gasto real (antes volvía a
  euros y perdía el enlace). Una incidencia resuelta conserva cuándo se resolvió. Cambiar el tipo
  de trabajo sigue sustituyendo los detalles anteriores.
- **Dos personas pueden llamarse igual (#442).** Añadir un nombre que ya existe ya no reutiliza a esa
  persona sin preguntar: la app dice «Ya existe una persona llamada Juan García. ¿Es la misma?» y deja
  elegir «Usar persona existente» o «Crear otra persona con este nombre». Si hay homónimos, se
  distinguen en pantalla («Juan García · 1», «· 2») sin cambiar el nombre guardado, y sus jornales y
  pagos nunca se mezclan.
- **Mi Campo avisa cuando el coste de la campaña no está completo (#449).** En «Resumen de la
  explotación», si alguna campaña del periodo tiene jornales o maquinaria sin precio o gastos sin
  confirmar, el coste/kg (de recogida y total) y la línea de cada finca afectada se marcan
  «(incompleto)», y la nota dice «Costes sin confirmar». Las cifras siguen siendo solo lo contabilizado.
- **Cerrar una campaña con costes sin confirmar avisa (#449).** Al confirmar el cierre, si quedan
  jornales o maquinaria sin precio o gastos pendientes, la app lo dice: «Hay costes sin confirmar.
  Puedes cerrar la campaña, pero el coste/kg quedará marcado como incompleto.» El cierre sigue
  permitido.
- **Las tarjetas de campaña y el histórico avisan de costes sin confirmar (#449).** Si una campaña
  tiene jornales o maquinaria sin precio, o gastos pendientes de confirmar, su tarjeta lo dice
  («Costes sin confirmar») y su coste/kg en «Comparar campañas» y en el histórico se marca
  «(incompleto)». La cifra sigue siendo solo lo contabilizado; nunca se rellena con un 0.
- **Un coste contabilizado ya no se queda desfasado al cambiar la maquinaria (#449).** Si en un día
  con coste de maquinaria contabilizado falta algún precio, ya no se puede añadir con precio, quitar, cambiar de
  cantidad ni de precio una máquina que sí tenía precio: la app pide confirmar antes los precios que
  faltan. Añadir una máquina sin precio sigue permitido (el coste se queda como subtotal conocido,
  marcado como incompleto) y, al confirmar el último precio, se recalcula una sola vez.
- **Se puede anotar a alguien sin saber aún su precio (#449).** En un día con jornales ya pagados a
  precio conocido se puede añadir a otra persona sin precio: queda registrada, no cuenta como 0 € y el coste
  del día se muestra como incompleto hasta confirmar su precio; al confirmarlo se recalcula una sola vez. Lo
  que sigue bloqueado es añadir a alguien con precio mientras otro precio de ese día está pendiente.
- **El coste/kg avisa cuando faltan costes por confirmar (#449).** Si hay jornales o maquinaria sin
  precio, costes del día aún sin calcular o gastos sin confirmar, el total y el coste/kg se llaman
  «contabilizado» y dicen qué falta («Incompleto · jornales sin precio»). Lo desconocido nunca cuenta como
  0 € y pagar no cambia nada. Un cálculo apartado porque un gasto anotado a mano lo sustituye (#475) ya no
  aparece como «sin confirmar».
- **Un día de recolección automático que se queda vacío desaparece (#502).** Si se quita el último
  jornal, máquina, gasto o foto de un día creado automáticamente y no tiene pesadas ni notas, el día ya
  no queda como «Kg pendientes» sin nada detrás; la pantalla del día se cierra. Un día anotado a mano
  nunca se toca.
- **Gastos y días de recolección ya no ocultan otras monedas (#450, gastos).** La pantalla de Gastos
  muestra «Gastos confirmados» y «Este mes» con un total por moneda, y las categorías por moneda (los
  porcentajes solo dentro de cada una). El coste de un día de recolección guarda un total por moneda y
  no inventa un total único cuando hay varias. Nada se convierte.
- **Una máquina archivada sigue en los días en que trabajó (#446).** Al editar la maquinaria de un día,
  la máquina archivada aparece como «Fendt 209 · Archivada» con su tipo, cantidad y precio de entonces,
  y se conserva al guardar (o se quita si el agricultor lo decide). En un día nuevo no se puede añadir.
- **El Cuaderno tampoco oculta otras monedas (#450, Cuaderno).** Cada día de recolección del diario muestra
  su coste por moneda («50,00 € · 30,00 US$»), y los resúmenes internos del Cuaderno (gastos de la campaña,
  recolección, jornales y maquinaria) guardan un total por moneda. Nada se convierte ni se suma entre monedas.
- **Los gastos de una campaña ya no ocultan otras monedas (#450, campaña).** El detalle de la campaña
  muestra cada moneda por separado («800,00 € · 300,00 GBP», sin convertir); una campaña con gastos solo
  en otra moneda ya no dice «Aún no hay gastos». El resumen interno de la campaña guarda un total por
  moneda y el coste/kg global sigue sin calcularse cuando hay varias.
- **Un gasto antiguo sigue editable aunque su parcela se archive o cambie de finca (#476).** Corregir
  importe, nota o concepto conserva la finca y la parcela con que se anotó; el editor la muestra como
  «Parcela 1 · archivada» o «· ahora en otra finca». Para un gasto nuevo, o al cambiar de parcela, solo
  se ofrecen las parcelas activas de esa finca. Solo vale para gastos ya confirmados: un borrador
  todavía no es histórico y, al confirmarlo, vuelve a comprobar que su parcela sigue activa (#456).
- **Gasto del día: «Se añade» o «Sustituye», siempre lo decide el agricultor (#475).** Al anotar jornales
  o un alquiler de maquinaria en un día que ya tiene ese coste calculado, la app pregunta «¿Cómo cuenta
  este gasto?»: «Se añade al cálculo» o «Sustituye el cálculo». Si el día no tiene cálculo de ese tipo,
  se añade sin preguntar; gasoil, aceite, transporte, reparaciones y otros siempre se suman. La decisión
  se guarda (no se deduce del concepto ni de la categoría). Si alguien de ese día ya tiene pagos, los
  jornales solo pueden añadirse: los pagos nunca se tocan. Al sustituir jornales sin pagos, el importe
  cuenta en la campaña sin repartir por persona. Un gasto «Fuera de campaña» ya no se ofrece para
  enlazarlo a una jornada ni se absorbe. Lo que contaba antes de este cambio sigue contando igual.
  Al editar el gasto se ve la misma pregunta, solo con las opciones que ese día permite de verdad.
- **Una persona que ya no trabaja puede cobrar lo que se le debe (#481).** Si un trabajador deja de
  estar activo, ya no se ofrece para nuevos jornales, pero sus jornales pendientes se pueden pagar (y los
  pagos corregir) sin restaurarlo. Nunca se puede pagar más de lo pendiente.
- **«Trabajo» en lugar de «actuación» en toda la app.** Pantallas, botones y avisos hablan de
  «Nuevo trabajo», «Editar trabajo», «Trabajo completado», «Trabajos con esta máquina», «Coste de un
  trabajo»… Solo cambia el texto; los datos y su sincronización no cambian. «Actuación realizada» se
  mantiene en el seguimiento de plagas, donde es la medida adoptada, y en el CUE se respetará la
  denominación oficial.
- **Un día sin pesadas no se atribuye a todas las parcelas (#458).** Un día de recolección abierto
  sin pesadas, o que se queda sin ellas pero conserva jornales, maquinaria, gastos o notas, muestra
  «Origen sin determinar» y no aparece en el historial de ninguna parcela. Al llegar, cambiar o irse
  pesadas, y al corregir un día anotado a mano, las parcelas que ya estaban conservan su fila y el
  nombre con que se guardaron; solo se añaden o quitan las que cambian.
- **Corregir una pesada o un gasto no cambia la cooperativa/proveedor con que se guardó (#451).** Si
  la cooperativa o el proveedor se renombra o se archiva, la pesada o el gasto siguen con el nombre de
  entonces al corregir notas, vale, kilos o importe, y se pueden seguir editando. El editor muestra el
  nombre guardado y, si ha cambiado, «Ahora: …». Solo elegir otra cooperativa o proveedor toma su
  nombre actual.
- **Un día de recolección con pesadas no se elimina (#457).** Si el día tiene pesadas, ya no aparece
  «Eliminar día de recolección»: «Este día existe porque tiene pesadas. Para cambiarlo, corrige o mueve
  las pesadas.». Una pesada viva siempre conserva su día; al moverla de fecha, el día viejo se retira
  solo si se queda sin datos. Un día sin pesadas se puede seguir eliminando como antes.
- **Una pesada no se mueve a después de su análisis de rendimiento (#455).** Al corregir la fecha de
  una pesada que ya tiene análisis fechado, no se puede poner un día posterior al análisis: «La nueva
  fecha de la pesada sería posterior a su análisis de rendimiento. Corrige primero la fecha del
  análisis o mantén la fecha de la pesada.». Un análisis sin fecha no lo impide y el análisis nunca se
  modifica.
- **Las parcelas de un trabajo no dejan gastos colgando (#441).** Al editar un trabajo no se puede
  quitar una parcela (ni pasarlo a toda la finca) si un gasto de ese trabajo la nombra: «Hay gastos
  vinculados a esta parcela dentro del trabajo. Revísalos antes de cambiar las parcelas.», con esos
  gastos a un toque. Añadir parcelas, cambiar el tipo o la fecha del trabajo nunca mueve ni cambia
  sus gastos.
- **Corregir una pesada no cambia el nombre de parcela con el que se guardó (#454).** Editar notas,
  vale o kilos de una pesada conserva sus filas de parcela tal como estaban (mismo identificador y
  mismo nombre de entonces, aunque la parcela se haya renombrado después). Solo una parcela añadida en
  la corrección toma su nombre actual, y quitar una parcela borra únicamente la suya.
- **El Diario ya no oculta los gastos de un trabajo (#478).** Un gasto ligado a un trabajo (a mano,
  de un documento o el coste antiguo del trabajo) sale en el Diario como fila propia, en su fecha, con
  «Relacionado con Tratamiento» (o el trabajo que sea), y se abre como el gasto que es. El Diario
  muestra el dinero, no lo suma; los gastos propios de una jornada siguen dentro de la jornada.
- **Un trabajo con gastos propios no se archiva (#437).** Si un borrador o un trabajo cancelado tiene
  gastos anotados que apuntan a él, «Archivar» queda desactivado: «Este trabajo tiene gastos
  vinculados. Consérvalo cancelado o revisa esos gastos antes de archivarlo.», con cada gasto a un
  toque. Nunca se borra ni se desliga un gasto real al archivar; un trabajo cancelado ya no sale en
  el Diario aunque no se archive.
- **Confirmar un borrador revisa todo otra vez (#456).** Un gasto en borrador solo empieza a contar
  si hoy sigue siendo válido: fecha no posterior a hoy, finca, parcela, trabajo, jornada y campaña
  coherentes y campaña abierta. Si algo cambió mientras esperaba, sigue en borrador y avisa «Este
  gasto necesita revisar su finca/parcela/trabajo antes de confirmarlo.». El nombre del proveedor
  se queda como se anotó.
- **Gasto rápido (#415).** Un gasto nuevo muestra solo importe, concepto, fecha, categoría, la finca
  y, si hay recogida en curso, «Recogida / Fuera de campaña». Desde el Cuaderno de una parcela se
  lee «Parcela · Los Llanos» y no se vuelve a preguntar. Proveedor, «Relacionado con» (el trabajo;
  solo los hechos en esa parcela), factura, líneas y notas esperan en «Relacionar y más detalles»,
  que se abre solo si ya tienen algo. Cuaderno → Gasto abre el formulario a la primera y al guardar o cancelar
  vuelves al Cuaderno; entrar en Gastos desde el listado sigue mostrando la lista. «Guardar y añadir
  foto» guarda y abre el gasto, donde se adjunta la foto del ticket o la factura.
- **Un trabajo no hecho no suma dinero (#429).** Planificar o guardar un borrador ya no puede crear
  un gasto real, venga de la pantalla que venga, y editar un trabajo nunca toca su coste. Completar
  un trabajo no inventa gasto. Si un trabajo hecho tiene un coste anotado antes, «Reabrir» espera:
  «Este trabajo tiene un coste contabilizado. Revísalo antes de volver a planificarlo.» y «Revisar
  gasto vinculado» abre ese gasto, donde eliges «Conservar como gasto independiente» (sigue contando,
  ya sin ligarse al trabajo) o «Eliminar gasto» (deja de contarse). Lo mismo para cancelar, planificar
  o archivar un trabajo antiguo con coste; los gastos anotados a mano nunca bloquean ni se borran.
- **El dinero de un trabajo va en Gastos (#416).** Un trabajo nuevo ya no tiene campo «Coste»,
  para no contar dos veces lo que ya está en Jornales, compras o maquinaria. En un trabajo hecho,
  «Añadir gasto relacionado» abre Gasto con la finca, la parcela (si es una) y el trabajo ya puestos;
  la categoría la eliges tú (no queda en «Otros» sin querer), el trabajo no se puede cambiar y solo
  se ofrecen sus parcelas; al guardar vuelves al trabajo, que avisa «Gasto añadido» (al cancelar
  vuelves sin cambios). Un coste anotado antes sigue
  visible como «Coste histórico vinculado» y «Ver / corregir gasto histórico» abre su gasto; con él
  el botón dice «Añadir otro gasto relacionado». Con la campaña cerrada no se ofrece añadir gasto.
- **El gasto de un trabajo sigue a ese trabajo (#433).** Un gasto ligado a una poda o tratamiento
  general ya no puede ponerse en la campaña de recogida, ni en otra campaña distinta de la del
  trabajo, ni en una parcela donde no se hizo. Si se liga a un trabajo o a una parcela sin indicar
  la finca, toma la suya; nunca queda un gasto con parcela o trabajo pero sin finca. En el
  formulario, al elegir un trabajo desaparece «Recogida / Fuera de campaña» (se muestra la campaña
  del trabajo), la parcela solo ofrece las del trabajo o «Todo el trabajo», y se quita sola una
  parcela, campaña o jornada que ya no encaje; al quitar el trabajo vuelves a elegir.
- **Un número mal escrito ya no desaparece (#473).** Si en horas, operarios, dosis, cantidad,
  volumen, duración, precio o fecha de tarifa se escribe algo que no se entiende («doce»,
  «31/02/2026»), el campo lo marca («Escribe el volumen como 12,5») y no se guarda hasta
  corregirlo; antes se perdía en silencio. Una tarifa sin fecha toma la fecha del riego, nunca «hoy».
- **Un gasto general no cae solo en la campaña (#411).** Desde el Cuaderno, con una recogida en
  marcha, el gasto pregunta «¿Dónde pertenece este gasto?» —Recogida o Fuera de campaña— sin nada
  marcado, y no se guarda hasta elegir. Mientras se comprueban las campañas de la finca tampoco se
  puede guardar, para no dar por hecho que no hay ninguna.
- **Riego, Tratamiento y Trabajo más rápidos (#414).** Desde el Cuaderno, los campos propios
  (horas, m³, producto, dosis…) salen abiertos; la descripción es un «Detalle breve (opcional)»
  salvo en Observación y Otro; una finca con una sola parcela la trae marcada; el botón dice
  «Guardar riego», «Guardar tratamiento»… Lo realizado ya no muestra hora, personas ni avisos
  previstos (eso está en Avisos → Planificar trabajo) ni «Guardar borrador». Con fecha futura no
  se guarda: «La fecha es futura. Para trabajos pendientes usa Avisos → Planificar.» (antes se
  convertía en silencio en un trabajo previsto). Cada tipo muestra primero lo esencial (Riego:
  duración, volumen y sector; Tratamiento: producto, dosis y motivo; Abonado: producto y dosis) y
  el resto en «Más detalles». Una Poda nueva ya no pide operarios ni horas (van en Jornal), un
  Tratamiento nuevo no pide «Equipo» (está Maquinaria) y un Riego nuevo no pide tarifa; los
  registros antiguos que tengan esos datos los conservan y se pueden editar.
- **El Cuaderno funciona sin campaña (#417).** Diario, Fitosanitario y Gastos son ahora los de la
  finca: muestran poda, riegos, tratamientos y gastos de todo el año aunque no exista ninguna
  campaña. Solo la pestaña Campaña (pesadas, días y jornales de recogida) pide crear una. En Gastos,
  «Recogida» (lo vinculado a una campaña) y «Fuera de campaña» se ven por separado, nunca mezclados.
  Solo cuenta lo hecho: un trabajo o tratamiento previsto sigue en Avisos y no aparece en el Diario,
  en Fitosanitario ni como uso de maquinaria hasta que se confirma.
- **La campaña solo cuenta lo que es suyo (#417).** Un trabajo, riego o tratamiento general ya no
  entra en la campaña por caer en sus fechas: solo lo vinculado expresamente a ella. Los días de
  recogida antiguos sin vínculo se siguen leyendo por fecha para no perder el histórico.
- **Finca Demo (solo versión de desarrollo, #399).** Perfil → «Herramientas de desarrollo» →
  «Cargar Finca Demo» crea una finca de prueba completa (3 parcelas, campañas 2025/26 y 2026/27,
  pesadas, jornales, maquinaria, gastos y trabajos) con las cifras de `docs/DEMO-FARM-SCENARIO.md`.
  No existe en las versiones de pruebas ni de producción y nunca se crea sola.
- **No volver a pedir la finca (#375).** Trabajo, Riego y Tratamiento abiertos desde el Cuaderno de
  una finca ya no ofrecen «Cambiar finca», y Gasto/Jornal abiertos con una finca elegida la muestran
  como contexto en lugar del selector «Finca», igual que Pesada. Desde los listados globales se
  sigue eligiendo.
- **Campañas con resumen en cada tarjeta (#246).** Sin entrar, cada campaña muestra kg pesados,
  nº de pesadas, días de recogida, coste de jornales y rendimiento medio, en pequeñas etiquetas con
  icono y texto y un color suave por tipo (producción verde oliva, días ámbar, costes tierra,
  rendimiento verde salvia). Sin datos dice «Sin pesadas», nunca un cero inventado.
- **Mi Campo: periodo visible, gastos generales y buscador (#359).** El resumen muestra siempre
  su campaña («Campaña 2026/27 ▾», con menú si hay varias), para que los kilos nunca parezcan «de
  siempre». «Coste/kg» pasa a **Coste recogida/kg** y, aparte, «Gastos generales del periodo»
  muestra los gastos confirmados sin campaña de esa temporada (septiembre-agosto), el **Coste
  total** y el **Coste total/kg** (solo con una moneda; sin kilos, «—»). Debajo del resumen,
  «Buscar finca…» filtra por nombre o municipio (sin tildes) y «+ Añadir finca» queda junto al
  listado.
- **Añadir jornal desde la campaña (#365).** En Campaña → Jornales, una campaña activa muestra
  «+ Añadir jornal», también cuando ya hay jornales: abre el día de recolección de hoy de esa finca
  con Jornales a mano (el mismo de Cuaderno → Jornal, sin duplicarlo) y al volver la campaña ya
  muestra el jornal. Una campaña cerrada solo se consulta: «Campaña cerrada. Reábrela para añadir
  nuevos jornales.»
- **Los formularios se cierran en cada guardado (#380).** Máquinas, Pesadas, Gastos, Organizaciones,
  Jornadas, Trabajos, Campañas, Parcelas y Fincas cerraban el formulario solo la primera vez: al
  guardar otra vez con el mismo mensaje se quedaba abierto. Ahora cada guardado correcto cuenta y
  cierra el formulario; un error no lo cierra y, tras cerrar Android la app, el borrador restaurado
  sigue abierto. «Guardar y añadir otra» en Pesadas sigue dejando el formulario listo para la siguiente.
- **Resumen de la explotación en Mi Campo (#359).** Bajo los totales de fincas, la campaña elegida
  (2026/27, 2025/26…) de todas las fincas a la vez: kg pesados, rendimiento medio ponderado por kilos
  analizados, coste de recogida y coste/kg calculado con los totales (nunca la media de cada finca).
  Dice cuántas fincas tienen campaña y cuáles no; con varias monedas no hay coste/kg conjunto; lo
  desconocido es «—». «Ver por finca» muestra lo que aporta cada una y abre su detalle.
- **Histórico de campañas en gráficas (#355).** En Cuaderno → Campaña, con dos o más campañas de
  la finca, «Histórico de campañas» muestra kilos pesados, rendimiento graso medio (con su cobertura)
  y coste de recogida por kilo de cada campaña, con el mismo dato en texto debajo. Sin datos es un
  hueco, nunca un cero; el coste no mezcla monedas; los kilos sin pesada (histórico) no entran en
  las barras. Tocar una campaña la abre. Sale de la misma comparación que ya había, sin totales nuevos.
- **Jornal abre los jornales y la campaña los muestra (#365).** Cuaderno → Jornal abre el día de
  recolección de hoy ya en «Jornales de este día», con «Registrar jornales» a mano; Pesadas sigue en
  el mismo día. El detalle de la campaña añade «Jornales» bajo Pesadas («1 persona · 1 jornada ·
  65,00 €», solo coste confirmado; los recuentos históricos sin nombre aparte), que abre el detalle
  de personas y pagos de esa campaña.
- **Añadir parcelas en el mapa, más claro (#361).** Una guía de cuatro pasos mientras la finca no
  tiene parcelas en el mapa; «Añadir de Catastro» es la acción principal. «Mi ubicación» deja un
  punto azul en el mapa y, si falla, dice por qué (sin permiso, ubicación apagada o sin señal) con su
  salida: permitir, activar o escribir coordenadas. La parcela elegida dice «Seleccionada · Guardada»
  y su municipio. El mapa y la foto aérea se dibujan a su resolución real (antes se ampliaban al doble).
- **El Cuaderno de una finca no pide otra finca (#369).** Desde Mi Campo → finca o parcela →
  Cuaderno, la finca ya está elegida: se muestra con su campaña (y la parcela, con «Toda la finca»)
  sin «Cambiar finca», y Atrás vuelve a la finca o la parcela. La pestaña Cuaderno sigue siendo el
  centro general, con «Cambiar finca». Es el mismo Cuaderno; solo cambia el origen.
- **Previsión semanal más visual (#362).** Cada día tiene el color suave de su estado (lluvia azul
  agua, nublado gris, parcialmente nublado azul claro, despejado dorado), además de su icono y su
  nombre. «Hoy» se destaca con borde e icono mayor; la máxima manda y la mínima queda secundaria;
  «Sin lluvia» en lugar de «0 mm»; arriba, un resumen de la semana. La fuente queda como nota discreta.
- **Fichas de finca con más datos (#363, #364, #359).** Cada finca muestra Superficie, Parcelas,
  Olivos y Kg de la campaña en marcha (solo de sus pesadas; «Sin pesadas» o «—» si no hay, nunca 0).
  La cabecera de Mis fincas añade Olivos («≥ N» si falta algún recuento) en una cuadrícula 2×2.
- **Jornal dentro y fuera de campaña (#350, #378).** Con campaña de recogida en marcha, «Jornal»
  abre el día de recolección con el jornal por persona. Sin campaña, abre «Jornal fuera de
  campaña»: la mano de obra de la finca (poda, desbroce, tratamientos…) como gasto de mano de obra
  ya elegido, sin pasar por un «Nuevo gasto» genérico. «Trabajo» ya no ofrece «Jornada de
  recolección», que se planifica desde Avisos.
- **Pestañas sin saltos (#357, #358).** Volver a pulsar la pestaña en la que ya estás no recarga
  la pantalla: se queda como estaba, con la misma vista elegida, sin parpadeo del título.
- **Crear dos fincas seguidas cierra el formulario las dos veces.** Al guardar la segunda finca el
  mensaje era el mismo que el de la primera y, si la escritura terminaba en menos de un fotograma, la
  hoja «Nueva finca» se quedaba abierta aunque la finca sí se había guardado. Ahora cuenta cada
  guardado.
- **Recolección más clara (#366).** «Días de recolección» cuenta fechas: dos fincas el mismo día son
  un día («En 2 fincas»), no dos. Cada día dice su finca («Estacas · 3 oct 2026»); las tarjetas de
  campaña dicen «1 día de recolección» y «Sin pesadas todavía», y los kilos sin repartir se leen
  «3.150 kg pendientes de repartir entre 2 parcelas» («Sin kg asignados» en cada parcela).
- **Inicio más natural y radar más nítido (#360).** La foto de Inicio ya no se tiñe de verde: una
  sombra neutra mantiene sus colores y el texto blanco sigue legible (≥ 4,5:1). El radar de lluvia
  pide a RainViewer su imagen de 512 px, con el doble de detalle que la de 256 px que se ampliaba.
- **La Pesada recuerda de dónde vienes (#373, #375).** Desde una finca, una campaña, una parcela o un
  día de recolección, «Nueva pesada» ya no vuelve a pedir la finca: muestra «Salinillas · Campaña
  2026-2027» como contexto fijo. En una campaña en marcha, «Pesadas» abre la nueva pesada de esa
  finca. Desde el listado general se sigue eligiendo la finca.
- **Cuaderno: finca a la vista.** El encabezado muestra la finca como dato principal y el estado
  de la campaña en una etiqueta aparte («Campaña 2026/27» o «Sin campaña en marcha»); «Cambiar
  finca» queda como acción secundaria.
- **Municipio y provincia desde Catastro.** Al añadir una parcela con su referencia, desde el mapa
  o al ubicar una parcela hecha a mano, Catastro rellena el municipio y la provincia; siempre se
  pueden cambiar y nunca se pisa lo que escribiste. Si Catastro no lo indica, se escribe a mano.
  Al editar una parcela con referencia, «Completar municipio y provincia desde Catastro».
- **Detalle de parcela por bloques.** En «Datos», la superficie es el dato principal junto a su
  mapa, y el resto se agrupa en Datos catastrales, Geometría e Información adicional.
- **Lluvia y radar (#345, #315).** Inicio muestra «Prob. lluvia X %» con su gota cuando AEMET la
  publica; si la fuente no da probabilidad (MET Norway) se muestra la lluvia prevista en mm del día
  y nunca un 0 % inventado. El tiempo se considera actual durante 1 hora y se vuelve a pedir al
  entrar o volver a Inicio si es más antiguo; «Actualizar» en El tiempo reintenta a mano y, si falla,
  conserva el dato guardado con su antigüedad. El radar de lluvia tiene su propio color agua,
  icono y título.
- **Pesada manual + foto (#342).** Una pesada se registra con los kilos netos que escribes,
  la cooperativa o almazara, el vale si lo hay, las parcelas y árbol/vuelo o suelo. La foto o
  archivo del recibo es opcional y se guarda como adjunto de esa pesada: nunca cambia los kilos ni
  crea otra pesada. Se retiran «Leer vale», «Añadir vale y leer datos» y «Ticket o factura»: la
  lectura automática queda aplazada. Los documentos que ya tenías se conservan.
- **Mi perfil (21A).** En Perfil, «Tu municipio» y «Tu cooperativa». Se guardan en el teléfono
  (Room v19, tabla `profile_settings`, una fila por espacio de trabajo) y quedan listos para la
  sincronización futura. La cooperativa se elige de tus cooperativas y almazaras, las mismas que
  usas en pesadas y gastos: no se copia el nombre. Si la renombras, se ve el nombre nuevo; si la
  archivas, deja de ser tu cooperativa. También puedes añadir una nueva desde ahí.
- **Avisos (21B).** En Perfil → Ajustes, un interruptor para todos los avisos de trabajos
  planificados y la hora del aviso «el día anterior»: **08:00 por defecto** (decisión P2; antes
  sonaba a las 19:00). Se puede elegir 07:00, 08:00, 09:00, 19:00 o 20:00. Al cambiar la hora se
  mueven todos los avisos «el día anterior» ya guardados; al desactivarlos no suena ninguno, pero
  no se borra ninguno. Se guarda en el teléfono (Room v20) y se respeta también tras reiniciar el
  móvil. El permiso de notificaciones de Android sigue en «Notificaciones».
- **Ayuda y privacidad (21C).** En Perfil: «Qué hay de nuevo» (estas notas, incluidas en cada
  APK al compilarla, así que siempre corresponden a la versión instalada), «Privacidad y datos»
  (qué queda en el teléfono, que la lectura de vales se hace en el propio teléfono y qué servicios
  externos se consultan y qué reciben: tiempo, radar, mapas, Catastro, mercado del aceite) y «Usar la app sin
  cobertura». «Exportar copia» espera a la fase 25 (decisión P3).
- Inicio y la previsión semanal usan tu municipio cuando tus fincas no tienen municipio o están en
  varios. La tarjeta «Mi cooperativa» muestra la que elegiste; sus avisos siguen pendientes del
  panel de administración.
- Sin cambios en los datos agrícolas ni en los cálculos.

## 0.6.0 — en curso (CR-011, simplificación)

- **Prueba en dispositivo (build 683).** Corregido Cuaderno → Jornal atascado en «cargando»:
  al abrir un día, la lectura de la cuadrilla anterior copiaba el estado de la pantalla antes de
  leer y lo volvía a escribir al terminar; si el día había llegado entretanto, la pantalla volvía
  a «cargando» para siempre. Ahora lee primero y actualiza solo ese dato. Además, si abrir o
  cargar el día de hoy tarda más de 10 s, se
  explica en pantalla («Está tardando más de lo normal»), con «Reintentar» al abrirlo, y queda
  registrado en logcat (etiqueta `MaginaOlivo`). La prueba E2E espera ahora al contenido real del
  día («Registrar jornales»), no solo a su contenedor. A 360 dp y letra grande, las cuatro vistas
  del Cuaderno (Diario · Fitosanitario · Gastos · Campaña) son chips que saltan de línea y se ven
  enteras, y las seis acciones pasan a dos por fila cuando «Tratamiento» no cabe en una línea.
  Sin cambios de datos ni de cálculos.
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
- Textos: el contenedor de recolección se llama «Día de recolección» (antes «Jornada»); «Jornada» queda solo como unidad de jornal (jornada completa, media jornada). «Uso de maquinaria» dentro del trabajo y del día; «Mis máquinas» en Perfil.
- Onboarding: «Importa parcelas desde Catastro, localízalas y consulta sus límites en el mapa.» (la app no dibuja límites).
- Registrar y planificar separados (§12): Cuaderno → «Registrar trabajo» (se guarda como hecho);
  Avisos → «Planificar trabajo» (misma pantalla en modo plan, se guarda como planificado).
- Perfil: «Mis máquinas»; retirada la fila provisional «Cuenta y sincronización · Pronto».
- Gastos: una sola acción principal («Añadir gasto»); «Ticket o factura» queda como secundaria.
- Bloque C (visual): tarjetas casi blancas (#FFFEFA) sobre el fondo crema; colores propios para
  Tratamiento (verde técnico), Jornal (terracota) y Gasto (dorado-marrón), que antes compartían
  familia con Trabajo y Pesada; KPI de jornales y costes con esos mismos colores; una sola acción
  principal en Pesadas («Leer vale» secundaria); «Archivar» parcela como acción destructiva;
  fila de pesada con icono y «kg · cooperativa» primero; tarjeta de día del tiempo en blanco.
- Formularios (§24): en Pesada, bruto/tara, nº de albarán y notas; en Gasto, nº de factura, líneas de
  compra y notas quedan bajo «Más detalles». Se abren solos si ya tienen algo (OCR, edición) o un
  error, así que nada queda oculto sin avisar.
- Contexto (§14): si el Cuaderno se abrió desde una parcela, Pesada la trae como origen (solo si
  está en la campaña en marcha) y Gasto empieza en ella.
- Listas (§9/§23): el día de recolección se lee «12 dic 2026 · Día de recolección» y
  «8.750 kg · 3 pesadas · 5 jornadas · 1 tractor» (jornadas, medias y horas por separado); la fila de gasto lleva icono en su color.
- Color de sección (§20): Avisos en ámbar y la cabecera de Campaña en verde profundo.
- Revisión en emulador (build 680): las pestañas del Cuaderno se desplazan en vez de recortar
  «Fitosanitario»/«Campaña» a 360–480 dp o con letra grande; las personas marcadas en «Registrar
  jornales» se conservan al girar el móvil; el día de recolección dice «Cargando jornales…» hasta
  leerlos (nunca «Sin jornales» antes de tiempo) y su bloque se llama «Jornales de este día», porque
  el resumen del Cuaderno suma toda la campaña.

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
