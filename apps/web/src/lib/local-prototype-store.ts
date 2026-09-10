import type { ActivityRecord, FarmRecord, FieldRecord, ParcelRecord, WorkRecord } from '@/lib/domain';

const ACTIVITY_KEY = 'magina:v20:activities';
const FARM_KEY = 'magina:v20:fields'; // legacy key kept to preserve existing preview data
const PARCEL_KEY = 'magina:v20:parcels';
const WORK_KEY = 'magina:v20:works';

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

export function clearPrototypeData() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ACTIVITY_KEY);
  window.localStorage.removeItem(FARM_KEY);
  window.localStorage.removeItem(PARCEL_KEY);
  window.localStorage.removeItem(WORK_KEY);
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key: 'all' } }));
}

export const prototypeStoreKeys = {
  activities: ACTIVITY_KEY,
  farms: FARM_KEY,
  fields: FARM_KEY,
  parcels: PARCEL_KEY,
  works: WORK_KEY,
} as const;
