export type AdviceTopic =
  | 'Observación'
  | 'Riego'
  | 'Suelo y nutrición'
  | 'Sanidad vegetal'
  | 'Poda'
  | 'Cosecha'
  | 'Seguridad';

export type FieldAdvice = {
  id: string;
  title: string;
  summary: string;
  topic: AdviceTopic;
  moment: string;
  observe: readonly string[];
  record: readonly string[];
  askForHelp: readonly string[];
  caution?: string;
};

export const adviceTopics: readonly AdviceTopic[] = [
  'Observación',
  'Riego',
  'Suelo y nutrición',
  'Sanidad vegetal',
  'Poda',
  'Cosecha',
  'Seguridad',
];

export const fieldAdviceCatalog: readonly FieldAdvice[] = [
  {
    id: 'recorrido-observacion',
    title: 'Haz siempre el mismo recorrido de observación',
    summary: 'Comparar puntos parecidos ayuda a detectar cambios sin convertir una impresión aislada en un diagnóstico.',
    topic: 'Observación',
    moment: 'Todo el año',
    observe: [
      'Aspecto general de varios olivos representativos, no solo del árbol que más llama la atención.',
      'Cambios visibles en hojas, brotes, fruto, suelo y zona de raíces respecto a la visita anterior.',
      'Si el cambio aparece en un árbol, en una zona concreta o se repite por toda la finca.',
    ],
    record: [
      'Fecha, finca y zona recorrida.',
      'Una foto comparable y una nota breve de lo observado.',
      'Qué ha cambiado desde la última revisión y si el problema avanza, se mantiene o remite.',
    ],
    askForHelp: [
      'Cuando el síntoma se extiende, se repite o no puedes identificar con seguridad la causa.',
      'Antes de atribuir un mismo síntoma a plaga, enfermedad, nutrición o falta de agua sin confirmación.',
    ],
  },
  {
    id: 'antes-de-regar',
    title: 'Antes de regar, comprueba la finca y el sistema',
    summary: 'El riego se decide mejor con observación real del suelo y del sistema, no solo por costumbre o calendario.',
    topic: 'Riego',
    moment: 'Antes de regar',
    observe: [
      'Estado del suelo en varios puntos representativos y señales visibles de estrés o exceso de agua.',
      'Fugas, emisores obstruidos, diferencias evidentes de caudal o sectores que no trabajan igual.',
      'Si ha habido aportes recientes de agua y si la situación de la finca ha cambiado desde el último riego.',
    ],
    record: [
      'Sector regado, fecha y duración o lectura disponible del sistema.',
      'Incidencias detectadas y reparaciones realizadas.',
      'Cualquier medición disponible de humedad, volumen o contador, sin estimarla si no existe.',
    ],
    askForHelp: [
      'Si aparecen síntomas persistentes pese a regar o sospechas problemas de calidad del agua, salinidad o drenaje.',
      'Si necesitas ajustar una estrategia de riego a suelo, dotación, variedad o condiciones concretas de la finca.',
    ],
  },
  {
    id: 'antes-de-abonar',
    title: 'Antes de abonar, separa necesidad de costumbre',
    summary: 'Registrar antecedentes y apoyar la decisión en análisis o asesoramiento evita tratar la nutrición como una receta fija.',
    topic: 'Suelo y nutrición',
    moment: 'Antes de abonar',
    observe: [
      'Vigor y uniformidad de la parcela, zonas distintas y antecedentes de producción.',
      'Resultados disponibles de suelo, hoja u otras analíticas, si existen y son aplicables.',
      'Aportes anteriores y cualquier cambio relevante de manejo desde la última campaña.',
    ],
    record: [
      'Producto o enmienda realmente utilizada, fecha, finca y superficie tratada.',
      'Cantidad aplicada solo a partir del dato real del trabajo; no rellenarla de memoria si no se conoce.',
      'Documento de compra, recomendación o análisis relacionado cuando exista.',
    ],
    askForHelp: [
      'Antes de corregir una supuesta carencia solo por síntomas visuales.',
      'Cuando haya resultados analíticos que no sepas interpretar o problemas repetidos de suelo/nutrición.',
    ],
  },
  {
    id: 'antes-de-tratar',
    title: 'Antes de cualquier tratamiento, confirma qué estás viendo',
    summary: 'Identificar el problema y justificar la intervención es más importante que empezar por elegir un producto.',
    topic: 'Sanidad vegetal',
    moment: 'Antes de tratar',
    observe: [
      'Síntomas, presencia real del agente sospechado y extensión dentro de la finca.',
      'Evolución respecto a observaciones anteriores y si existen zonas claramente más afectadas.',
      'Condiciones de trabajo que puedan hacer insegura o inadecuada una intervención.',
    ],
    record: [
      'Qué se ha observado y cómo se ha confirmado la necesidad de actuar.',
      'Si finalmente se interviene: producto utilizado, fecha, superficie, operario y documento asociado según corresponda.',
      'Resultado de la revisión posterior, separado de la observación inicial.',
    ],
    askForHelp: [
      'Si no puedes identificar con seguridad el problema o justificar la intervención.',
      'Si existen restricciones, requisitos de uso o dudas sobre compatibilidad, plazo o condiciones de aplicación.',
    ],
    caution: 'Mágina Olivo no prescribe fitosanitarios ni calcula dosis. Antes de utilizar un producto, comprueba que su uso esté autorizado para tu caso, sigue su etiqueta y las indicaciones profesionales y normativas que correspondan.',
  },
  {
    id: 'planificar-poda',
    title: 'Planifica la poda antes de empezar a cortar',
    summary: 'Recorrer la finca y fijar el objetivo evita que la poda se convierta en una sucesión de decisiones improvisadas.',
    topic: 'Poda',
    moment: 'Antes y durante la poda',
    observe: [
      'Vigor, estructura, madera seca o dañada y diferencias entre zonas de la finca.',
      'Qué objetivo concreto persigue el trabajo en esa parcela y qué árboles requieren una atención distinta.',
      'Estado y seguridad de herramientas, maquinaria y zona de trabajo.',
    ],
    record: [
      'Fecha, zona, tipo de trabajo y personas o maquinaria empleadas.',
      'Incidencias relevantes y fotos cuando ayuden a comparar la evolución.',
      'Destino o gestión de restos cuando forme parte del trabajo realizado.',
    ],
    askForHelp: [
      'Ante daños, decaimiento o síntomas que hagan dudar si un corte es conveniente.',
      'Cuando el objetivo de renovación, formación o recuperación requiera una estrategia específica para esa finca.',
    ],
  },
  {
    id: 'preparar-cosecha',
    title: 'Prepara la trazabilidad antes de que empiece la cosecha',
    summary: 'Finca, entrega, kilos y resultado posterior deben poder relacionarse sin reconstruirlos semanas después.',
    topic: 'Cosecha',
    moment: 'Antes y durante la campaña',
    observe: [
      'Qué finca o fincas participan en cada jornada y si habrá entregas mezcladas.',
      'Dónde se realizará la entrega y qué documento devolverá el receptor.',
      'Qué datos podrás obtener en el momento y cuáles llegarán más tarde, como rendimiento o liquidación.',
    ],
    record: [
      'Fecha, finca, kilos reales de entrega y albarán cuando exista.',
      'Reparto entre fincas cuando una entrega sea mixta, evitando duplicar kilos.',
      'Rendimiento, liquidación y cobro como hechos posteriores separados cuando estén confirmados.',
    ],
    askForHelp: [
      'Si no puedes reconstruir de forma fiable el origen de una entrega o su reparto entre fincas.',
      'Cuando un documento posterior no coincide con los datos registrados y sea necesario corregir la trazabilidad.',
    ],
  },
  {
    id: 'despues-episodio',
    title: 'Después de un episodio intenso, observa antes de concluir',
    summary: 'Lluvia, viento, calor, frío u otros episodios pueden dejar señales distintas; documentar primero ayuda a no confundir causa y síntoma.',
    topic: 'Observación',
    moment: 'Después de un episodio',
    observe: [
      'Daños visibles, zonas encharcadas, erosión, ramas afectadas o cambios claros en el árbol y el suelo.',
      'Distribución del daño: puntual, por orientación, por cota o extendido por la finca.',
      'Riesgos inmediatos para personas, accesos, instalaciones o maquinaria antes de entrar a trabajar.',
    ],
    record: [
      'Fecha de la revisión y qué episodio se conoce que ha ocurrido, sin estimar intensidades que no se hayan medido.',
      'Fotos, zonas afectadas y evolución en revisiones posteriores.',
      'Trabajos de reparación o prevención realmente ejecutados.',
    ],
    askForHelp: [
      'Si existe riesgo para personas o instalaciones.',
      'Si el daño evoluciona, compromete árboles o infraestructuras o su causa no está clara.',
    ],
  },
  {
    id: 'trabajo-seguro',
    title: 'Antes de trabajar, revisa seguridad y equipo',
    summary: 'La tarea agrícola empieza comprobando que personas, herramientas, maquinaria y entorno permiten trabajar con seguridad.',
    topic: 'Seguridad',
    moment: 'Antes de cada trabajo',
    observe: [
      'Estado aparente de herramientas, protecciones y maquinaria antes de usarlas.',
      'Pendientes, terreno, accesos, líneas, obstáculos y presencia de otras personas en la zona.',
      'Si el trabajo requiere formación, autorización, protección o procedimiento específico.',
    ],
    record: [
      'Incidencias o defectos detectados antes del trabajo.',
      'Mantenimiento o reparación realizada cuando proceda.',
      'Quién realizó el trabajo y con qué equipo cuando sea relevante para la trazabilidad.',
    ],
    askForHelp: [
      'Si un equipo presenta un defecto o no puedes confirmar que es seguro utilizarlo.',
      'Si la tarea exige conocimientos, permisos o medidas de seguridad que no dominas.',
    ],
    caution: 'Sigue siempre las instrucciones del fabricante, la formación aplicable y las medidas de prevención que correspondan al trabajo real.',
  },
] as const;
