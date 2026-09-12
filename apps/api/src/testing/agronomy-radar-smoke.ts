import {
  combineAgronomySignals,
  evaluateRadarObservationForTask,
  type RadarAgronomyObservation,
} from '../domain/agronomy-radar.js';

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

const now = new Date('2026-09-10T20:00:00.000Z');

function observation(overrides: Partial<RadarAgronomyObservation> = {}): RadarAgronomyObservation {
  return {
    observationId: '11111111-1111-4111-8111-111111111111',
    observedAt: '2026-09-10T19:50:00.000Z',
    coverageStatus: 'covered',
    precipitationDetected: true,
    nearestEchoDistanceKm: 2.5,
    reflectivityDbzMax: 28,
    qualityFlags: [],
    ...overrides,
  };
}

const treatmentNear = evaluateRadarObservationForTask(observation(), 'treatment', now);
assert(treatmentNear.actionable, 'Fresh nearby radar should be actionable for treatment');
assert(treatmentNear.suitability === 'avoid', 'Very near precipitation should advise avoid for treatment');

const treatmentTenKm = evaluateRadarObservationForTask(observation({ nearestEchoDistanceKm: 8 }), 'treatment', now);
assert(treatmentTenKm.suitability === 'caution', 'Nearby precipitation should advise caution for treatment');

const stale = evaluateRadarObservationForTask(observation({ observedAt: '2026-09-10T19:20:00.000Z' }), 'treatment', now);
assert(!stale.actionable, 'Stale radar must not change the advisory');
assert(!stale.fresh, 'Stale radar must be marked not fresh');

const poorQuality = evaluateRadarObservationForTask(observation({ qualityFlags: ['edge_of_coverage'] }), 'treatment', now);
assert(!poorQuality.actionable, 'Quality-flagged radar must not change the advisory');

const irrigation = evaluateRadarObservationForTask(observation({ nearestEchoDistanceKm: 6 }), 'irrigation', now);
assert(irrigation.suitability === 'caution', 'Nearby precipitation should prompt irrigation review');

const combined = combineAgronomySignals(
  { suitability: 'good', riskLevel: 'low', summary: 'Previsión sin señales destacadas' },
  treatmentNear,
);
assert(combined.suitability === 'avoid', 'Fresh radar should be able to elevate forecast suitability');
assert(combined.radarElevated, 'Escalation must be explicit');

const staleCombined = combineAgronomySignals(
  { suitability: 'caution', riskLevel: 'medium', summary: 'Previsión con precaución' },
  stale,
);
assert(staleCombined.suitability === 'caution', 'Stale radar must preserve forecast severity');
assert(!staleCombined.radarElevated, 'Stale radar must never report escalation');

console.log('agronomy radar smoke: ok');
