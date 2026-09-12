import type {
  ActivityRecord,
  CampaignRecord,
  CrewRecord,
  FarmRecord,
  FieldRecord,
  HarvestDeliveryRecord,
  HarvestResultRecord,
  MachineryRecord,
  MaterialRecord,
  ParcelRecord,
  PartyRecord,
  WorkRecord,
} from '@/lib/domain';

const ACTIVITY_KEY = 'magina:v20:activities';
const FARM_KEY = 'magina:v20:fields'; // legacy key kept to preserve existing preview data
const PARCEL_KEY = 'magina:v20:parcels';
const WORK_KEY = 'magina:v20:works';
const PARTY_KEY = 'magina:v20:parties';
const CREW_KEY = 'magina:v20:crews';
const MACHINERY_KEY = 'magina:v20:machinery';
const MATERIAL_KEY = 'magina:v20:materials';
const CAMPAIGN_KEY = 'magina:v20:campaigns';
const HARVEST_DELIVERY_KEY = 'magina:v20:harvest-deliveries';
const HARVEST_RESULT_KEY = 'magina:v20:harvest-results';

function readArray<T>(key: string): T[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed as T[] : [];
  } catch {
    return [];
  }
}

function writeArray<T>(key: string, value: T[]) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(key, JSON.stringify(value));
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key } }));
}

export function getLocalActivities(fieldId?: string) {
  const items = readArray<ActivityRecord>(ACTIVITY_KEY);
  return fieldId ? items.filter((item) => item.fieldId === fieldId) : items;
}

export function saveLocalActivity(activity: ActivityRecord) {
  const current = readArray<ActivityRecord>(ACTIVITY_KEY);
  const next = [activity, ...current.filter((item) => item.id !== activity.id)];
  writeArray(ACTIVITY_KEY, next);
  return activity;
}

export function removeLocalActivity(id: string) {
  const current = readArray<ActivityRecord>(ACTIVITY_KEY);
  writeArray(ACTIVITY_KEY, current.filter((item) => item.id !== id));
}

export function getLocalFarms() {
  return readArray<FarmRecord>(FARM_KEY);
}

export function saveLocalFarm(farm: FarmRecord) {
  const current = readArray<FarmRecord>(FARM_KEY);
  const next = [farm, ...current.filter((item) => item.id !== farm.id)];
  writeArray(FARM_KEY, next);
  return farm;
}

export function removeLocalFarm(id: string) {
  const current = readArray<FarmRecord>(FARM_KEY);
  writeArray(FARM_KEY, current.filter((item) => item.id !== id));
  writeArray(PARCEL_KEY, readArray<ParcelRecord>(PARCEL_KEY).filter((item) => item.farmId !== id));
  writeArray(WORK_KEY, readArray<WorkRecord>(WORK_KEY).filter((item) => item.farmId !== id));
}

// Compatibility wrappers while API/frontend internals still use `field`.
export function getLocalFields(): FieldRecord[] {
  return getLocalFarms();
}

export function saveLocalField(field: FieldRecord) {
  return saveLocalFarm(field);
}

export function removeLocalField(id: string) {
  removeLocalFarm(id);
}

export function getLocalParcels(farmId?: string) {
  const items = readArray<ParcelRecord>(PARCEL_KEY);
  return farmId ? items.filter((item) => item.farmId === farmId) : items;
}

export function saveLocalParcel(parcel: ParcelRecord) {
  const current = readArray<ParcelRecord>(PARCEL_KEY);
  const next = [parcel, ...current.filter((item) => item.id !== parcel.id)];
  writeArray(PARCEL_KEY, next);
  return parcel;
}

export function removeLocalParcel(id: string) {
  const current = readArray<ParcelRecord>(PARCEL_KEY);
  writeArray(PARCEL_KEY, current.filter((item) => item.id !== id));
}

export function getLocalWorks(farmId?: string) {
  const items = readArray<WorkRecord>(WORK_KEY);
  return farmId ? items.filter((item) => item.farmId === farmId) : items;
}

export function saveLocalWork(work: WorkRecord) {
  const current = readArray<WorkRecord>(WORK_KEY);
  const next = [work, ...current.filter((item) => item.id !== work.id)];
  writeArray(WORK_KEY, next);
  return work;
}

export function removeLocalWork(id: string) {
  const current = readArray<WorkRecord>(WORK_KEY);
  writeArray(WORK_KEY, current.filter((item) => item.id !== id));
}

export function getLocalParties() {
  return readArray<PartyRecord>(PARTY_KEY);
}

export function saveLocalParty(party: PartyRecord) {
  const current = readArray<PartyRecord>(PARTY_KEY);
  writeArray(PARTY_KEY, [party, ...current.filter((item) => item.id !== party.id)]);
  return party;
}

export function removeLocalParty(id: string) {
  writeArray(PARTY_KEY, readArray<PartyRecord>(PARTY_KEY).filter((item) => item.id !== id));
}

export function getLocalCrews() {
  return readArray<CrewRecord>(CREW_KEY);
}

export function saveLocalCrew(crew: CrewRecord) {
  const current = readArray<CrewRecord>(CREW_KEY);
  writeArray(CREW_KEY, [crew, ...current.filter((item) => item.id !== crew.id)]);
  return crew;
}

export function removeLocalCrew(id: string) {
  writeArray(CREW_KEY, readArray<CrewRecord>(CREW_KEY).filter((item) => item.id !== id));
}

export function getLocalMachinery() {
  return readArray<MachineryRecord>(MACHINERY_KEY);
}

export function saveLocalMachinery(machine: MachineryRecord) {
  const current = readArray<MachineryRecord>(MACHINERY_KEY);
  writeArray(MACHINERY_KEY, [machine, ...current.filter((item) => item.id !== machine.id)]);
  return machine;
}

export function removeLocalMachinery(id: string) {
  writeArray(MACHINERY_KEY, readArray<MachineryRecord>(MACHINERY_KEY).filter((item) => item.id !== id));
}

export function getLocalMaterials() {
  return readArray<MaterialRecord>(MATERIAL_KEY);
}

export function saveLocalMaterial(material: MaterialRecord) {
  const current = readArray<MaterialRecord>(MATERIAL_KEY);
  writeArray(MATERIAL_KEY, [material, ...current.filter((item) => item.id !== material.id)]);
  return material;
}

export function removeLocalMaterial(id: string) {
  writeArray(MATERIAL_KEY, readArray<MaterialRecord>(MATERIAL_KEY).filter((item) => item.id !== id));
}

export function getLocalCampaigns() {
  return readArray<CampaignRecord>(CAMPAIGN_KEY);
}

export function saveLocalCampaign(campaign: CampaignRecord) {
  const current = readArray<CampaignRecord>(CAMPAIGN_KEY);
  writeArray(CAMPAIGN_KEY, [campaign, ...current.filter((item) => item.id !== campaign.id)]);
  return campaign;
}

export function getLocalHarvestDeliveries(campaignId?: string, farmId?: string) {
  const items = readArray<HarvestDeliveryRecord>(HARVEST_DELIVERY_KEY);
  return items.filter((item) => {
    if (campaignId && item.campaignId !== campaignId) return false;
    if (farmId && !item.allocations.some((allocation) => allocation.farmId === farmId)) return false;
    return true;
  });
}

export function saveLocalHarvestDelivery(delivery: HarvestDeliveryRecord) {
  const current = readArray<HarvestDeliveryRecord>(HARVEST_DELIVERY_KEY);
  writeArray(HARVEST_DELIVERY_KEY, [delivery, ...current.filter((item) => item.id !== delivery.id)]);
  return delivery;
}

export function getLocalHarvestResults(deliveryId?: string) {
  const items = readArray<HarvestResultRecord>(HARVEST_RESULT_KEY);
  return deliveryId ? items.filter((item) => item.deliveryId === deliveryId) : items;
}

export function saveLocalHarvestResult(result: HarvestResultRecord) {
  const current = readArray<HarvestResultRecord>(HARVEST_RESULT_KEY);
  writeArray(HARVEST_RESULT_KEY, [result, ...current.filter((item) => item.id !== result.id)]);
  return result;
}

export function clearPrototypeData() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ACTIVITY_KEY);
  window.localStorage.removeItem(FARM_KEY);
  window.localStorage.removeItem(PARCEL_KEY);
  window.localStorage.removeItem(WORK_KEY);
  window.localStorage.removeItem(PARTY_KEY);
  window.localStorage.removeItem(CREW_KEY);
  window.localStorage.removeItem(MACHINERY_KEY);
  window.localStorage.removeItem(MATERIAL_KEY);
  window.localStorage.removeItem(CAMPAIGN_KEY);
  window.localStorage.removeItem(HARVEST_DELIVERY_KEY);
  window.localStorage.removeItem(HARVEST_RESULT_KEY);
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key: 'all' } }));
}

export const prototypeStoreKeys = {
  activities: ACTIVITY_KEY,
  farms: FARM_KEY,
  fields: FARM_KEY,
  parcels: PARCEL_KEY,
  works: WORK_KEY,
  parties: PARTY_KEY,
  crews: CREW_KEY,
  machinery: MACHINERY_KEY,
  materials: MATERIAL_KEY,
  campaigns: CAMPAIGN_KEY,
  harvestDeliveries: HARVEST_DELIVERY_KEY,
  harvestResults: HARVEST_RESULT_KEY,
} as const;
