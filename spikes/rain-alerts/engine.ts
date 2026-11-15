/**
 * Mágina Olivo - radar movement and farm arrival MVP.
 * Pure, provider-independent. No network calls, credentials, FCM or personal data.
 * Inputs MUST be georeferenced, quality-controlled precipitation rates in mm/h,
 * not a rendered colour PNG or raw reflectivity in dBZ.
 */
export interface GeoBounds {
  west: number;
  east: number;
  south: number;
  north: number;
}

export interface RadarGrid {
  observedAt: string; // UTC ISO-8601
  width: number;
  height: number;
  bounds: GeoBounds;
  mmPerHour: (number | null)[]; // row-major, north->south, west->east
  provider: string;
}

export interface RainMotion {
  deltaX: number; // pixels, first frame -> second frame
  deltaY: number;
  frameIntervalMinutes: number;
  speedKmHour: number;
  direction: string;
  confidence: number; // 0..1; IoU of wet echoes
  updatedAt: string;
}

export interface FarmWatch {
  farmId: string;
  latitude: number;
  longitude: number;
  rainThresholdMmHour: number;
  leadMinutes: 15 | 30 | 60;
  enabled: boolean;
}

export type Arrival = {
  farmId: string;
  estimatedMinutes: number;
  estimatedIntensityMmHour: number;
  motion: RainMotion;
  observedAt: string;
};

export type LastNotification = {
  farmId: string;
  deliveredAt: string;
  intensityMmHour: number;
};

const WET_THRESHOLD_MM_H = 0.2;
const MAX_DATA_AGE_MINUTES = 20;
const MAX_INTERVAL_MINUTES = 20;
const MIN_RAIN_PIXELS = 3;
const MAX_VELOCITY_KM_H = 160;

function minutesBetween(first: string, second: string): number {
  const ms1 = Date.parse(first);
  const ms2 = Date.parse(second);
  if (!Number.isFinite(ms1) || !Number.isFinite(ms2)) return NaN;
  return (ms2 - ms1) / 60000;
}

function validGrid(grid: RadarGrid): boolean {
  const { width, height, bounds, mmPerHour } = grid;
  return Number.isInteger(width) && Number.isInteger(height) && width >= 4 && height >= 4 &&
    width * height === mmPerHour.length && width * height <= 2_000_000 &&
    Number.isFinite(bounds.west) && Number.isFinite(bounds.east) &&
    Number.isFinite(bounds.north) && Number.isFinite(bounds.south) &&
    bounds.west < bounds.east && bounds.south < bounds.north &&
    mmPerHour.every(n => n === null || (Number.isFinite(n) && n >= 0));
}

function sameGrid(a: RadarGrid, b: RadarGrid): boolean {
  return a.width === b.width && a.height === b.height &&
    (Object.keys(a.bounds) as (keyof GeoBounds)[]).every(k => a.bounds[k] === b.bounds[k]);
}

function rainPoints(grid: RadarGrid): number[] {
  const result: number[] = [];
  for (let i = 0; i < grid.mmPerHour.length; i++) {
    const value = grid.mmPerHour[i];
    if (value !== null && value >= WET_THRESHOLD_MM_H) result.push(i);
  }
  return result;
}

/** Estimates one global echo translation using binary overlap; refuses ambiguous data. */
export function detectMotion(previous: RadarGrid, current: RadarGrid, now: string, maxShift = 5): RainMotion | null {
  if (!validGrid(previous) || !validGrid(current) || !sameGrid(previous, current)) return null;
  const dt = minutesBetween(previous.observedAt, current.observedAt);
  const age = minutesBetween(current.observedAt, now);
  if (!(dt > 0 && dt <= MAX_INTERVAL_MINUTES) || !(age >= 0 && age <= MAX_DATA_AGE_MINUTES)) return null;
  if (!Number.isInteger(maxShift) || maxShift < 0 || maxShift > 30) return null;

  const oldWet = rainPoints(previous);
  const newWet = rainPoints(current);
  if (oldWet.length < MIN_RAIN_PIXELS || newWet.length < MIN_RAIN_PIXELS) return null;
  const newSet = new Set(newWet);
  const scores: { dx: number; dy: number; score: number }[] = [];
  for (let dy = -maxShift; dy <= maxShift; dy++) {
    for (let dx = -maxShift; dx <= maxShift; dx++) {
      let intersection = 0;
      for (const idx of oldWet) {
        const x = idx % previous.width + dx;
        const y = Math.floor(idx / previous.width) + dy;
        if (x >= 0 && x < previous.width && y >= 0 && y < previous.height && newSet.has(y * previous.width + x)) intersection++;
      }
      const union = oldWet.length + newWet.length - intersection;
      scores.push({ dx, dy, score: union > 0 ? intersection / union : 0 });
    }
  }
  scores.sort((a, b) => b.score - a.score);
  const best = scores[0];
  const next = scores[1]?.score ?? 0;
  // Refuse visually ambiguous movement instead of manufacturing a direction/ETA.
  if (best.score < 0.70 || (best.score - next < 0.06)) return null;

  const latKmPerPixel = 111.2 * (current.bounds.north - current.bounds.south) / current.height;
  const midLat = (current.bounds.north + current.bounds.south) / 2;
  const lonKmPerPixel = 111.2 * Math.cos(midLat * Math.PI / 180) * (current.bounds.east - current.bounds.west) / current.width;
  const movedKm = Math.hypot(best.dx * lonKmPerPixel, best.dy * latKmPerPixel);
  const speedKmHour = movedKm * 60 / dt;
  if (speedKmHour > MAX_VELOCITY_KM_H) return null;
  const ns = best.dy < 0 ? 'N' : best.dy > 0 ? 'S' : '';
  const ew = best.dx > 0 ? 'E' : best.dx < 0 ? 'O' : '';
  return {
    deltaX: best.dx, deltaY: best.dy, frameIntervalMinutes: dt,
    speedKmHour: Math.round(speedKmHour * 10) / 10,
    direction: ns + ew || 'Estacionaria', confidence: Math.round(best.score * 100) / 100,
    updatedAt: current.observedAt,
  };
}

function farmGridXY(grid: RadarGrid, latitude: number, longitude: number): { x: number; y: number } | null {
  const { bounds, width, height } = grid;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) ||
      latitude < bounds.south || latitude > bounds.north ||
      longitude < bounds.west || longitude > bounds.east) return null;
  return {
    x: (longitude - bounds.west) / (bounds.east - bounds.west) * width - 0.5,
    y: (bounds.north - latitude) / (bounds.north - bounds.south) * height - 0.5,
  };
}

function valueNearest(grid: RadarGrid, x: number, y: number): number | null {
  const ix = Math.round(x);
  const iy = Math.round(y);
  if (ix < 0 || iy < 0 || ix >= grid.width || iy >= grid.height) return null;
  return grid.mmPerHour[iy * grid.width + ix];
}

/** Constant-advection MVP, NOT a promise of rain; deliberately capped at 60 min. */
export function estimateArrival(grid: RadarGrid, motion: RainMotion, watch: FarmWatch): Arrival | null {
  if (!watch.enabled || !validGrid(grid) || motion.updatedAt !== grid.observedAt ||
      !Number.isFinite(watch.rainThresholdMmHour) || watch.rainThresholdMmHour < WET_THRESHOLD_MM_H) return null;
  const point = farmGridXY(grid, watch.latitude, watch.longitude);
  if (!point) return null;
  if (valueNearest(grid, point.x, point.y) === null) return null; // no data != no rain
  for (let minute = 0; minute <= 60; minute += 5) {
    const displacement = minute / motion.frameIntervalMinutes;
    // Sample the rain at the origin of the air parcel advected to the farm.
    const intensity = valueNearest(grid, point.x - motion.deltaX * displacement, point.y - motion.deltaY * displacement);
    if (intensity === null) return null; // forecast crosses uncovered radar region
    if (intensity >= watch.rainThresholdMmHour) {
      return {
        farmId: watch.farmId, estimatedMinutes: minute,
        estimatedIntensityMmHour: intensity, motion, observedAt: grid.observedAt,
      };
    }
  }
  return null;
}

/** Per-farm dedupe: don't disturb farmer repeatedly for one rain event. */
export function shouldNotify(
  arrival: Arrival | null, watch: FarmWatch,
  last: LastNotification | null, now: string,
): boolean {
  if (!arrival || !watch.enabled || arrival.farmId !== watch.farmId) return false;
  if (arrival.estimatedMinutes > watch.leadMinutes) return false;
  if (arrival.motion.confidence < 0.70) return false;
  const age = minutesBetween(arrival.observedAt, now);
  if (!(age >= 0 && age <= MAX_DATA_AGE_MINUTES)) return false;
  if (!last) return true;
  if (last.farmId !== watch.farmId) return true;
  const elapsed = minutesBetween(last.deliveredAt, now);
  if (!Number.isFinite(elapsed) || elapsed < 0) return false;
  // Within 90 min only a significant intensity increase can trigger one new warning.
  return elapsed >= 90 || (elapsed >= 20 && arrival.estimatedIntensityMmHour >= Math.max(2, last.intensityMmHour * 2));
}
