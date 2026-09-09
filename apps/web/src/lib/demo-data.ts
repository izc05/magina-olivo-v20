export const demoUser = {
  displayName: 'Isi',
  municipality: 'Bedmar',
  roles: ['Agricultor', 'Profesional'],
  profileCompletion: 80,
  points: 1250,
  level: 4,
} as const;

export const demoContext = {
  municipality: 'Huelma',
  province: 'Jaén',
  temperatureC: 24,
  condition: 'Soleado',
  windKmh: 9,
  humidityPercent: 48,
} as const;

export const demoFarmSummary = {
  farms: 6,
  oliveTrees: 248,
  activeAlerts: 1,
  campaignKg: 4000,
  campaign: '2026/27',
} as const;

export const lasCenillas = {
  id: 'las-cenillas',
  name: 'Las Cenillas',
  municipality: 'Huelma',
  province: 'Jaén',
  oliveTrees: 23,
  waterRegime: 'Secano',
  campaign: '2026/27',
  campaignKg: 1425,
  averageYieldPercent: 21.3,
  nextIrrigationLabel: '14 sep',
  nextIrrigationLong: '14 septiembre',
  campaignCostEur: 535,
  documents: 5,
  lastTreatment: '18 agosto',
  lastFertilization: '7 septiembre',
  lastPruning: 'febrero 2026',
} as const;

export const demoFarms = [
  { id: 'las-cenillas', name: 'Las Cenillas', oliveTrees: 23, municipality: 'Huelma', status: 'En buen estado', tone: 'ok', href: '/mi-campo/fincas/las-cenillas' },
  { id: 'el-cerrillo', name: 'El Cerrillo', oliveTrees: 88, municipality: 'Huelma', status: 'Atención', tone: 'warn', href: '#' },
  { id: 'la-loma', name: 'La Loma', oliveTrees: 64, municipality: 'Huelma', status: 'En producción', tone: 'ok', href: '#' },
] as const;

export const demoDelivery = {
  farm: 'Las Cenillas',
  cooperative: 'SCA San Isidro',
  date: '12/12/2026',
  ticketNumber: '008421',
  kilograms: 1842,
} as const;
