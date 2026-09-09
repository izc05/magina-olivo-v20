export type FieldKind = 'text' | 'number' | 'date' | 'time' | 'select' | 'textarea';

export type RecordField = {
  name: string;
  label: string;
  kind: FieldKind;
  placeholder?: string;
  suffix?: string;
  required?: boolean;
  options?: readonly string[];
  inputMode?: 'decimal' | 'numeric';
};

export type RecordType = {
  slug: string;
  label: string;
  shortLabel: string;
  symbol: string;
  description: string;
  question: string;
  essential: readonly RecordField[];
  details?: readonly RecordField[];
  followUp?: readonly RecordField[];
};

export const recordTypes = [
  {
    slug: 'riego',
    label: 'Registrar riego',
    shortLabel: 'Riego',
    symbol: '💧',
    description: 'Fecha, coste y próximo riego.',
    question: '¿Qué riego has realizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
    ],
    details: [
      { name: 'duration', label: 'Duración', kind: 'number', suffix: 'h', inputMode: 'decimal' },
      { name: 'water', label: 'Agua utilizada', kind: 'number', suffix: 'm³', inputMode: 'decimal' },
      { name: 'notes', label: 'Notas', kind: 'textarea', placeholder: 'Algo que quieras recordar…' },
    ],
    followUp: [
      { name: 'nextDate', label: 'Próximo riego', kind: 'date' },
      { name: 'nextTime', label: 'Hora', kind: 'time' },
    ],
  },
  {
    slug: 'tratamiento',
    label: 'Registrar tratamiento',
    shortLabel: 'Tratamiento',
    symbol: '🌿',
    description: 'Cura, producto, dosis y seguimiento.',
    question: '¿Qué tratamiento has realizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'reason', label: 'Motivo', kind: 'select', required: true, options: ['Mosca del olivo', 'Repilo', 'Prays', 'Cochinilla', 'Preventivo', 'Otro'] },
      { name: 'product', label: 'Producto', kind: 'text', placeholder: 'Nombre comercial', required: true },
      { name: 'dose', label: 'Dosis', kind: 'text', placeholder: 'Ej. 250 ml / 100 L' },
    ],
    details: [
      { name: 'quantity', label: 'Cantidad usada', kind: 'text', placeholder: 'Ej. 1,5 L' },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
      { name: 'notes', label: 'Notas', kind: 'textarea', placeholder: 'Estado del olivar, observaciones…' },
    ],
    followUp: [
      { name: 'reviewDate', label: 'Revisar resultado', kind: 'date' },
    ],
  },
  {
    slug: 'abono',
    label: 'Registrar abonado',
    shortLabel: 'Abono',
    symbol: '🧪',
    description: 'Producto, kilos y coste.',
    question: '¿Qué abonado has realizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'product', label: 'Abono', kind: 'text', placeholder: 'Ej. NPK 15-15-15', required: true },
      { name: 'quantity', label: 'Cantidad', kind: 'number', suffix: 'kg', inputMode: 'decimal', required: true },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
    ],
    details: [
      { name: 'method', label: 'Aplicación', kind: 'select', options: ['Suelo', 'Fertirrigación', 'Foliar', 'Otro'] },
      { name: 'notes', label: 'Notas', kind: 'textarea', placeholder: 'Reparto, estado del suelo…' },
    ],
  },
  {
    slug: 'poda',
    label: 'Registrar poda',
    shortLabel: 'Poda',
    symbol: '✂️',
    description: 'Tipo, tiempo y coste de poda.',
    question: '¿Qué poda has realizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'type', label: 'Tipo de poda', kind: 'select', required: true, options: ['Mantenimiento', 'Renovación', 'Formación', 'Limpieza', 'Otro'] },
      { name: 'workers', label: 'Personas', kind: 'number', inputMode: 'numeric' },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
    ],
    details: [
      { name: 'hours', label: 'Horas', kind: 'number', suffix: 'h', inputMode: 'decimal' },
      { name: 'notes', label: 'Notas', kind: 'textarea', placeholder: 'Árboles afectados, restos, incidencias…' },
    ],
    followUp: [
      { name: 'nextDate', label: 'Próxima poda estimada', kind: 'date' },
    ],
  },
  {
    slug: 'jornal',
    label: 'Registrar jornal',
    shortLabel: 'Jornal',
    symbol: '👷',
    description: 'Trabajo, personas, horas y coste.',
    question: '¿Qué trabajo se ha realizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'task', label: 'Trabajo', kind: 'text', placeholder: 'Ej. desbroce, vareo, limpieza', required: true },
      { name: 'workers', label: 'Personas', kind: 'number', inputMode: 'numeric' },
      { name: 'hours', label: 'Horas totales', kind: 'number', suffix: 'h', inputMode: 'decimal' },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
    ],
    details: [
      { name: 'crew', label: 'Cuadrilla / trabajador', kind: 'text', placeholder: 'Opcional' },
      { name: 'notes', label: 'Notas', kind: 'textarea' },
    ],
  },
  {
    slug: 'maquinaria',
    label: 'Registrar maquinaria',
    shortLabel: 'Maquinaria',
    symbol: '🚜',
    description: 'Uso de tractor, aperos y combustible.',
    question: '¿Qué maquinaria has utilizado?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'machine', label: 'Máquina', kind: 'text', placeholder: 'Ej. tractor + atomizador', required: true },
      { name: 'hours', label: 'Horas de uso', kind: 'number', suffix: 'h', inputMode: 'decimal' },
      { name: 'cost', label: 'Coste', kind: 'number', suffix: '€', inputMode: 'decimal' },
    ],
    details: [
      { name: 'fuel', label: 'Combustible', kind: 'number', suffix: 'L', inputMode: 'decimal' },
      { name: 'task', label: 'Trabajo realizado', kind: 'text', placeholder: 'Ej. tratamiento, desbroce…' },
      { name: 'notes', label: 'Notas', kind: 'textarea' },
    ],
  },
  {
    slug: 'gasto',
    label: 'Registrar gasto',
    shortLabel: 'Gasto',
    symbol: '€',
    description: 'Un gasto que no venga de otro registro.',
    question: '¿Qué gasto quieres añadir?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'category', label: 'Categoría', kind: 'select', required: true, options: ['Material', 'Combustible', 'Reparación', 'Transporte', 'Cooperativa', 'Otro'] },
      { name: 'concept', label: 'Concepto', kind: 'text', placeholder: '¿En qué se ha gastado?', required: true },
      { name: 'amount', label: 'Importe', kind: 'number', suffix: '€', inputMode: 'decimal', required: true },
    ],
    details: [
      { name: 'notes', label: 'Notas', kind: 'textarea' },
    ],
  },
  {
    slug: 'observacion',
    label: 'Añadir observación',
    shortLabel: 'Observación',
    symbol: '📷',
    description: 'Foto, incidencia o algo que quieras recordar.',
    question: '¿Qué has visto en la finca?',
    essential: [
      { name: 'date', label: 'Fecha', kind: 'date', required: true },
      { name: 'type', label: 'Tipo', kind: 'select', options: ['Estado del olivo', 'Plaga o enfermedad', 'Suelo', 'Riego', 'Daño', 'Otro'] },
      { name: 'notes', label: 'Observación', kind: 'textarea', placeholder: 'Describe lo que has visto…', required: true },
    ],
    followUp: [
      { name: 'reviewDate', label: 'Recordarme revisarlo', kind: 'date' },
    ],
  },
] as const satisfies readonly RecordType[];

export type RecordSlug = (typeof recordTypes)[number]['slug'];

export function getRecordType(slug: string) {
  return recordTypes.find((item) => item.slug === slug);
}
