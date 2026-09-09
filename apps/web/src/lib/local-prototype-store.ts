import type { ActivityRecord, FieldRecord } from '@/lib/domain';

const ACTIVITY_KEY = 'magina:v20:activities';
const FIELD_KEY = 'magina:v20:fields';

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

export function getLocalFields() {
  return readArray<FieldRecord>(FIELD_KEY);
}

export function saveLocalField(field: FieldRecord) {
  const current = readArray<FieldRecord>(FIELD_KEY);
  const next = [field, ...current.filter((item) => item.id !== field.id)];
  writeArray(FIELD_KEY, next);
  return field;
}

export function removeLocalField(id: string) {
  const current = readArray<FieldRecord>(FIELD_KEY);
  writeArray(FIELD_KEY, current.filter((item) => item.id !== id));
}

export function clearPrototypeData() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ACTIVITY_KEY);
  window.localStorage.removeItem(FIELD_KEY);
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key: 'all' } }));
}

export const prototypeStoreKeys = {
  activities: ACTIVITY_KEY,
  fields: FIELD_KEY,
} as const;
