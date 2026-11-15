import { test } from 'node:test';
import assert from 'node:assert/strict';
import { detectMotion, estimateArrival, shouldNotify } from './engine.ts';
import type { RadarGrid, FarmWatch } from './engine.ts';

const prevTime = '2026-11-15T12:00:00Z';
const currTime = '2026-11-15T12:10:00Z';
const now = '2026-11-15T12:12:00Z';

function frame(time: string, offsetX: number, offsetY = 0): RadarGrid {
  const width = 20, height = 20;
  const a = Array<number | null>(width * height).fill(0);
  // An asymmetrical echo to avoid fake precision from repeated shapes.
  for (const [x, y] of [[3, 5], [3, 6], [4, 6], [5, 6], [4, 7]]) {
    a[(y + offsetY) * width + x + offsetX] = 3.0;
  }
  return { observedAt: time, width, height,
    bounds: { west: -4, east: -3.8, south: 37.6, north: 37.8 },
    mmPerHour: a, provider: 'TEST_SYNTHETIC' };
}
const farm = (col: number, row: number): FarmWatch => ({
  farmId: 'farm-one', longitude: -4 + (col + .5) * .01,
  latitude: 37.8 - (row + .5) * .01,
  rainThresholdMmHour: 1, leadMinutes: 30, enabled: true,
});

test('detects movement east with no invented precision', () => {
  const motion = detectMotion(frame(prevTime,0), frame(currTime,1), now);
  assert.ok(motion);
  assert.equal(motion.deltaX, 1);
  assert.equal(motion.deltaY, 0);
  assert.equal(motion.direction, 'E');
  assert.equal(motion.confidence, 1);
});

test('estimates arrival within 30 minutes and respects lead-time choices', () => {
  const current = frame(currTime,1);
  const motion = detectMotion(frame(prevTime,0), current, now)!;
  const watch = farm(9, 6);
  const arrival = estimateArrival(current, motion, watch);
  assert.ok(arrival);
  assert.equal(arrival.estimatedMinutes, 30);
  assert.equal(shouldNotify(arrival, watch, null, now), true);
  assert.equal(shouldNotify(arrival, { ...watch, leadMinutes: 15 }, null, now), false);
});

test('never alerts for stale observations or uncovered pixels', () => {
  const curr = frame(currTime,1);
  assert.equal(detectMotion(frame(prevTime,0), curr, '2026-11-15T13:00:00Z'), null);
  const motion = detectMotion(frame(prevTime,0), curr, now)!;
  const outside = farm(60, 6);
  assert.equal(estimateArrival(curr, motion, outside), null);
  curr.mmPerHour[6 * 20 + 9] = null;
  assert.equal(estimateArrival(curr, motion, farm(9,6)), null);
});

test('rejects ambiguous, stationary repetitive, dry, impossible and missing radar data', () => {
  const identicalDry = frame(currTime,0);
  identicalDry.mmPerHour.fill(0);
  assert.equal(detectMotion(frame(prevTime,0), identicalDry, now), null);
  assert.equal(detectMotion(frame(prevTime,0), frame(prevTime,1), now), null);
  const bad = frame(currTime,1); bad.mmPerHour[8] = -3;
  assert.equal(detectMotion(frame(prevTime,0), bad, now), null);
});

test('deduplicates alerts per farm with a 90 min cooldown and escalation', () => {
  const current = frame(currTime,1);
  const motion = detectMotion(frame(prevTime,0), current, now)!;
  const watch = farm(9,6);
  const arrival = estimateArrival(current, motion, watch)!;
  const last = { farmId: watch.farmId, deliveredAt:'2026-11-15T11:55:00Z', intensityMmHour: 3 };
  assert.equal(shouldNotify(arrival,watch,last,now),false);
  assert.equal(shouldNotify(arrival,watch,{...last, deliveredAt:'2026-11-15T10:30:00Z'},now),true);
  assert.equal(shouldNotify(arrival,watch,{...last, intensityMmHour:1, deliveredAt:'2026-11-15T11:40:00Z'},now),true);
});

test('prevents duplicated warnings across different or disabled user choices', () => {
  const current = frame(currTime,1);
  const motion = detectMotion(frame(prevTime,0), current, now)!;
  const watch = farm(9,6);
  const arrival = estimateArrival(current, motion, watch)!;
  assert.equal(shouldNotify(arrival,{...watch,enabled:false},null,now),false);
  assert.equal(shouldNotify(arrival,{...watch,farmId:'wrong'},null,now),false);
});
