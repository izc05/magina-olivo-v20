import type { WeatherDay } from './aemet.js';

export const AGRONOMY_RULE_VERSION = 'weather-task-v1';
export type AgronomyTask = 'treatment' | 'irrigation' | 'pruning' | 'harvest' | 'work';

export function normalizeAgronomyTask(value?: string): AgronomyTask {
  if (value === 'treatment' || value === 'irrigation' || value === 'pruning' || value === 'harvest' || value === 'work') return value;
  if (value === 'harvest_delivery') return 'harvest';
  return 'work';
}

export function evaluateWeatherDayForTask(day: WeatherDay, task: AgronomyTask) {
  const rain = day.precipitationProbabilityPercent;
  const wind = day.windMaxKmh;
  const reasons: string[] = [];
  let suitability: 'good' | 'caution' | 'avoid' | 'unknown' = 'good';
  let risk: 'none' | 'low' | 'medium' | 'high' | 'unknown' = 'low';

  if (task === 'treatment') {
    if (rain !== null && rain >= 70) { suitability = 'avoid'; risk = 'high'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
    else if (rain !== null && rain >= 40) { suitability = 'caution'; risk = 'medium'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
    if (wind !== null && wind >= 30) { suitability = 'avoid'; risk = 'high'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
    else if (wind !== null && wind >= 20 && suitability !== 'avoid') { suitability = 'caution'; risk = 'medium'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
  } else if (task === 'harvest' || task === 'pruning' || task === 'work') {
    if (rain !== null && rain >= 70) { suitability = 'caution'; risk = 'medium'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
    if (wind !== null && wind >= 40) { suitability = 'caution'; risk = 'medium'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
  } else if (task === 'irrigation') {
    if (rain !== null && rain >= 70) { suitability = 'caution'; risk = 'medium'; reasons.push(`Lluvia probable (${rain}%): conviene revisar la necesidad de riego`); }
  }

  if (!reasons.length) reasons.push('No se detectan señales meteorológicas destacadas con las reglas actuales');
  return {
    suitability,
    riskLevel: risk,
    title: suitability === 'avoid' ? 'Condiciones poco favorables' : suitability === 'caution' ? 'Conviene revisar las condiciones' : 'Sin alertas meteorológicas destacadas',
    summary: reasons.join(' · '),
    reasons,
  };
}
