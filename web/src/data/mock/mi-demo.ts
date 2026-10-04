export const DEMO_DATA_LABEL = "Demo" as const;

export type DemoFarm = Readonly<{
  id: string;
  name: string;
  surfaceHa: string;
  parcelCount: number;
  oliveCount: number;
  campaignKg: string;
  campaignName: string;
  parcelIds: readonly string[];
}>;

export type DemoParcel = Readonly<{
  id: string;
  name: string;
  farmId: string;
  farmName: string;
  surfaceHa: string;
  oliveCount: number;
  variety: string;
  status: string;
}>;

export type DemoCampaign = Readonly<{
  id: string;
  name: string;
  state: "Activa" | "Histórica";
  harvestKg: string;
  grossYield: string;
  costPerKg: string;
  expenses: string;
  farmCount: number;
}>;

const farms: readonly DemoFarm[] = [
  {
    id: "demo",
    name: "Estacas",
    surfaceHa: "0,76",
    parcelCount: 1,
    oliveCount: 84,
    campaignKg: "3.150",
    campaignName: "2026–2027",
    parcelIds: ["demo"],
  },
  {
    id: "salinillas",
    name: "Salinillas",
    surfaceHa: "0,38",
    parcelCount: 2,
    oliveCount: 61,
    campaignKg: "—",
    campaignName: "2026–2027",
    parcelIds: ["las-lomas", "el-cerrillo"],
  },
];

const parcels: readonly DemoParcel[] = [
  {
    id: "demo",
    name: "Los Llanos",
    farmId: "demo",
    farmName: "Estacas",
    surfaceHa: "0,76",
    oliveCount: 84,
    variety: "Picual",
    status: "En producción",
  },
  {
    id: "las-lomas",
    name: "Las Lomas",
    farmId: "salinillas",
    farmName: "Salinillas",
    surfaceHa: "0,23",
    oliveCount: 37,
    variety: "Picual",
    status: "En producción",
  },
  {
    id: "el-cerrillo",
    name: "El Cerrillo",
    farmId: "salinillas",
    farmName: "Salinillas",
    surfaceHa: "0,15",
    oliveCount: 24,
    variety: "Picual",
    status: "En producción",
  },
];

const campaigns: readonly DemoCampaign[] = [
  {
    id: "demo",
    name: "2026–2027",
    state: "Activa",
    harvestKg: "3.150",
    grossYield: "21,63 %",
    costPerKg: "0,21 €",
    expenses: "65,00 €",
    farmCount: 2,
  },
  {
    id: "2025-2026",
    name: "2025–2026",
    state: "Histórica",
    harvestKg: "2.860",
    grossYield: "20,90 %",
    costPerKg: "0,24 €",
    expenses: "71,40 €",
    farmCount: 2,
  },
];

export const miReadAdapter = {
  getFarms: () => farms,
  getFarm: (id: string) => farms.find((farm) => farm.id === id) ?? farms[0],
  getParcels: () => parcels,
  getParcel: (id: string) =>
    parcels.find((parcel) => parcel.id === id) ?? parcels[0],
  getCampaigns: () => campaigns,
  getCampaign: (id: string) =>
    campaigns.find((campaign) => campaign.id === id) ?? campaigns[0],
};

export const demoDashboard = {
  location: "Bedmar · Jaén",
  kpis: [
    { label: "Fincas", value: "2", note: "En la explotación" },
    { label: "Parcelas", value: "3", note: "Registradas" },
    { label: "Superficie", value: "1,14 ha", note: "Superficie total" },
    { label: "Olivos", value: "145", note: "Registrados" },
    { label: "Kg campaña", value: "3.150 kg", note: "Campaña 2026–2027" },
    { label: "Rendimiento", value: "21,63 %", note: "Dato demo de campaña" },
    { label: "Coste/kg", value: "0,21 €", note: "Dato demo de campaña" },
  ],
  weather: {
    temperature: "23°",
    condition: "Lluvia",
    rain: "2,7 mm",
    wind: "15 km/h",
    humidity: "73 %",
    updated: "Previsión ilustrativa · Demo",
  },
  market: {
    price: "4,25 €/kg",
    change: "+6,2 %",
    source: "Indicativo · datos de demostración",
  },
  production: [
    { year: "2021", height: 40, value: "1.200 kg" },
    { year: "2022", height: 48, value: "1.450 kg" },
    { year: "2023", height: 56, value: "1.680 kg" },
    { year: "2024", height: 71, value: "2.130 kg" },
    { year: "2025", height: 83, value: "2.860 kg" },
    { year: "2026", height: 100, value: "3.150 kg" },
  ],
};

export const demoNotebookEntries = [
  {
    id: "harvest",
    kind: "Pesada",
    title: "Recolección mecanizada",
    detail: "Parcela Los Llanos · Estacas",
    date: "2 oct 2026",
    value: "1.620 kg",
  },
  {
    id: "pruning",
    kind: "Trabajo",
    title: "Poda",
    detail: "Finca Estacas",
    date: "28 sep 2026",
    value: "4 jornales · 6 h",
  },
  {
    id: "treatment",
    kind: "Tratamiento",
    title: "Aplicación de tratamiento",
    detail: "Parcela Las Lomas · Salinillas",
    date: "20 sep 2026",
    value: "3 jornales · 3 h",
  },
] as const;

export const demoDocuments = [
  {
    id: "parcel-sheet",
    title: "Certificado de parcela · Los Llanos",
    kind: "Metadatos de ejemplo",
    date: "2 oct 2026",
  },
  {
    id: "campaign-summary",
    title: "Certificado · Resumen de campaña 2026–2027",
    kind: "Vista de ejemplo · PDF no disponible",
    date: "1 oct 2026",
  },
  {
    id: "farm-notes",
    title: "Notas de finca · Estacas",
    kind: "Metadatos de ejemplo",
    date: "28 sep 2026",
  },
] as const;
