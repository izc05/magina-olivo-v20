export const harvestHistory = [
  { campaign: '2026/27', kg: 1425, kgPerTree: 62.0, yieldPercent: 21.3, date: '15 diciembre 2026' },
  { campaign: '2025/26', kg: 1316, kgPerTree: 57.2, yieldPercent: 20.4, date: '15 diciembre 2025' },
  { campaign: '2024/25', kg: 1580, kgPerTree: 68.7, yieldPercent: 22.1, date: '11 diciembre 2024' },
  { campaign: '2023/24', kg: 1240, kgPerTree: 53.9, yieldPercent: 19.8, date: '18 diciembre 2023' },
] as const;

export const irrigationHistory = [
  { date: '3 septiembre 2026', cost: 22 },
  { date: '18 agosto 2026', cost: 18 },
  { date: '2 agosto 2026', cost: 24 },
  { date: '14 julio 2026', cost: 20 },
] as const;

export const treatmentHistory = [
  { date: '18 agosto 2026', reason: 'Mosca del olivo', product: 'Producto demo', detail: 'Dosis registrada · revisión recomendada' },
  { date: '14 mayo 2026', reason: 'Repilo', product: 'Producto demo', detail: 'Tratamiento preventivo' },
  { date: '20 octubre 2025', reason: 'Cobre', product: 'Producto demo', detail: 'Tratamiento de otoño' },
] as const;

export const fertilizationHistory = [
  { date: '7 septiembre 2026', product: 'NPK 15-15-15', quantityKg: 75, cost: 42 },
  { date: '12 marzo 2026', product: 'Urea', quantityKg: 40, cost: 28 },
] as const;

export const pruningHistory = [
  { date: '14 febrero 2026', type: 'Poda completa', workers: 2, hours: 6, cost: 120 },
  { date: '18 febrero 2024', type: 'Poda de mantenimiento', workers: 2, hours: 5, cost: 100 },
] as const;

export const costBreakdown = [
  { label: 'Riego', amount: 164, symbol: '💧' },
  { label: 'Abonado', amount: 70, symbol: '🧪' },
  { label: 'Tratamientos', amount: 86, symbol: '🌿' },
  { label: 'Poda', amount: 120, symbol: '✂' },
  { label: 'Labores', amount: 95, symbol: '🚜' },
] as const;

export const farmHistory = [
  { date: '7 sep 2026', symbol: '🧪', title: 'Abono', detail: 'NPK 15-15-15 · 75 kg' },
  { date: '3 sep 2026', symbol: '💧', title: 'Riego', detail: '22 €' },
  { date: '18 ago 2026', symbol: '🌿', title: 'Tratamiento', detail: 'Mosca del olivo' },
  { date: '14 feb 2026', symbol: '✂', title: 'Poda', detail: 'Poda completa · 120 €' },
  { date: '15 dic 2025', symbol: '🫒', title: 'Cosecha', detail: '1.316 kg · 20,4 %' },
  { date: '20 oct 2025', symbol: '🌿', title: 'Tratamiento', detail: 'Cobre · tratamiento de otoño' },
] as const;

export const farmCalendar = [
  { day: '14', month: 'SEP', symbol: '💧', title: 'Riego', time: '08:00', detail: 'Avisos: día anterior y 1 h antes', tone: 'blue' },
  { day: '21', month: 'SEP', symbol: '🌿', title: 'Revisar mosca', time: 'Sin hora', detail: 'Seguimiento del tratamiento', tone: 'rose' },
  { day: '05', month: 'OCT', symbol: '🧪', title: 'Abonado previsto', time: '08:30', detail: 'Planificación de demostración', tone: 'green' },
  { day: '15', month: 'NOV', symbol: '🫒', title: 'Inicio estimado cosecha', time: 'Sin hora', detail: 'Fecha orientativa', tone: 'gold' },
] as const;

export const farmDocuments = [
  { type: 'Albarán', title: 'Entrega SCA San Isidro', date: '12 dic 2026', meta: '1.842 kg · nº 008421' },
  { type: 'Factura', title: 'Abono NPK', date: '7 sep 2026', meta: '42 €' },
  { type: 'Foto', title: 'Revisión tratamiento', date: '18 ago 2026', meta: 'Mosca del olivo' },
  { type: 'Factura', title: 'Poda', date: '14 feb 2026', meta: '120 €' },
  { type: 'Documento', title: 'Referencia de terreno', date: 'Demo', meta: 'Pendiente de validación oficial' },
] as const;

export const moduleSlugs = ['cosechas','riegos','tratamientos','abonos','poda','gastos','calendario','historia','documentos','terreno'] as const;
export type FarmModuleSlug = (typeof moduleSlugs)[number];
