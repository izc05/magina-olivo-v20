import type { AgronomyTask } from './agronomy-advisory.js';

export const RADAR_AGRONOMY_RULE_VERSION = 'radar-task-v1';
export const RADAR_FRESHNESS_MINUTES = 20;

export type RadarAgronomyObservation = {
  observationId: string;
  observedAt: Date | string;
  coverageStatus: 'covered' | 'partial' | 'outside' | 'unavailable';
  precipitationDetected: boolean | null;
  nearestEchoDistanceKm: number | null;
  reflectivityDbzMax: number | null;
  qualityFlags: string[];
};

function observedAtMs(value: Date | string) {
  const time = value instanceof Date ? value.getTime() : new Date(value).getTime();
  return Number.isFinite(time) ? time : Number.NaN;
}

export function radarObservationAgeMinutes(observation: RadarAgronomyObservation, now = new Date()) {
  const observed = observedAtMs(observation.observedAt);
  if (!Number.isFinite(observed)) return null;
  return Math.max(0, Math.round((now.getTime() - observed) / 60_000));
}

export function radarObservationIsFresh(observation: RadarAgronomyObservation, now = new Date()) {
  const age = radarObservationAgeMinutes(observation, now);
  return age !== null && age <= RADAR_FRESHNESS_MINUTES;
}

export function evaluateRadarObservationForTask(observation: RadarAgronomyObservation | null, task: AgronomyTask, now = new Date()) {
  if (!observation) return { actionable: false, suitability: 'unknown' as const, riskLevel: 'unknown' as const, summary: 'Sin observación radar disponible para la finca.', fresh: false, ageMinutes: null };
  const ageMinutes = radarObservationAgeMinutes(observation, now);
  const fresh = radarObservationIsFresh(observation, now);
  if (!fresh) return { actionable: false, suitability: 'unknown' as const, riskLevel: 'unknown' as const, summary: ageMinutes === null ? 'La fecha de la observación radar no es válida.' : `Observación radar antigua (${ageMinutes} min); no modifica la recomendación.`, fresh: false, ageMinutes };
  if (observation.qualityFlags.length > 0 || observation.coverageStatus === 'outside' || observation.coverageStatus === 'unavailable') return { actionable: false, suitability: 'unknown' as const, riskLevel: 'unknown' as const, summary: 'Radar sin calidad o cobertura suficiente para modificar la recomendación.', fresh, ageMinutes };
  if (observation.precipitationDetected !== true) return { actionable: false, suitability: 'good' as const, riskLevel: 'low' as const, summary: 'Sin eco de precipitación detectado cerca de la finca en la observación radar reciente.', fresh, ageMinutes };

  const distance = observation.nearestEchoDistanceKm;
  const overOrVeryNear = distance === null || distance <= 3;
  const near = distance !== null && distance <= 10;
  const detail = distance === null ? 'precipitación detectada cerca de la finca' : `precipitación detectada a ${Math.round(distance * 10) / 10} km`;

  if (task === 'treatment') {
    if (overOrVeryNear) return { actionable: true, suitability: 'avoid' as const, riskLevel: 'high' as const, summary: `Radar reciente: ${detail}.`, fresh, ageMinutes };
    if (near) return { actionable: true, suitability: 'caution' as const, riskLevel: 'medium' as const, summary: `Radar reciente: ${detail}.`, fresh, ageMinutes };
  }
  if ((task === 'harvest' || task === 'pruning' || task === 'work') && (near || overOrVeryNear)) return { actionable: true, suitability: 'caution' as const, riskLevel: 'medium' as const, summary: `Radar reciente: ${detail}.`, fresh, ageMinutes };
  if (task === 'irrigation' && (near || overOrVeryNear)) return { actionable: true, suitability: 'caution' as const, riskLevel: 'medium' as const, summary: `Radar reciente: ${detail}; conviene revisar la necesidad de riego.`, fresh, ageMinutes };
  return { actionable: false, suitability: 'good' as const, riskLevel: 'low' as const, summary: `Radar reciente: ${detail}, fuera del umbral operativo actual.`, fresh, ageMinutes };
}

const suitabilityRank = { unknown: 0, good: 1, caution: 2, avoid: 3 } as const;
const riskRank = { unknown: 0, none: 1, low: 2, medium: 3, high: 4 } as const;

export function combineAgronomySignals(
  forecast: { suitability: 'good' | 'caution' | 'avoid' | 'unknown'; riskLevel: 'none' | 'low' | 'medium' | 'high' | 'unknown'; summary: string },
  radar: ReturnType<typeof evaluateRadarObservationForTask>,
) {
  if (!radar.actionable) return { suitability: forecast.suitability, riskLevel: forecast.riskLevel, summary: forecast.summary, radarElevated: false };
  const suitability = suitabilityRank[radar.suitability] > suitabilityRank[forecast.suitability] ? radar.suitability : forecast.suitability;
  const riskLevel = riskRank[radar.riskLevel] > riskRank[forecast.riskLevel] ? radar.riskLevel : forecast.riskLevel;
  return { suitability, riskLevel, summary: `${forecast.summary} · ${radar.summary}`, radarElevated: suitability !== forecast.suitability || riskLevel !== forecast.riskLevel };
}
