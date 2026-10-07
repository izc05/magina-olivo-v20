const DAY_MS = 86_400_000;
const J1970 = 2440588;
const J2000 = 2451545;
const J0 = 0.0009;
const RAD = Math.PI / 180;
const OBLIQUITY = 23.4397 * RAD;
const SUNRISE_ANGLE = -0.833 * RAD;

function toJulian(date: Date): number {
  return date.getTime() / DAY_MS - 0.5 + J1970;
}

function fromJulian(julian: number): Date {
  return new Date((julian + 0.5 - J1970) * DAY_MS);
}

function solarMeanAnomaly(days: number): number {
  return RAD * (357.5291 + 0.98560028 * days);
}

function eclipticLongitude(meanAnomaly: number): number {
  const correction = RAD * (
    1.9148 * Math.sin(meanAnomaly) +
    0.02 * Math.sin(2 * meanAnomaly) +
    0.0003 * Math.sin(3 * meanAnomaly)
  );
  return meanAnomaly + correction + RAD * 102.9372 + Math.PI;
}

function declination(longitude: number): number {
  return Math.asin(Math.sin(longitude) * Math.sin(OBLIQUITY));
}

function solarTransitJ(approxTransit: number, meanAnomaly: number, longitude: number): number {
  return J2000 + approxTransit + 0.0053 * Math.sin(meanAnomaly) - 0.0069 * Math.sin(2 * longitude);
}

function localDateKey(date: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

/** Civil sunrise/sunset for the place's local day, returned as UTC instants. */
export function solarTimesFor(
  date: Date,
  latitude: number | null,
  longitude: number | null,
  timeZone = "Europe/Madrid",
): {
  date: string;
  timeZone: string;
  sunriseAt: string | null;
  sunsetAt: string | null;
} | null {
  if (latitude == null || longitude == null || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;

  const dateKey = localDateKey(date, timeZone);
  const noonUtc = new Date(`${dateKey}T12:00:00.000Z`);
  const days = toJulian(noonUtc) - J2000;
  const lw = -longitude * RAD;
  const phi = latitude * RAD;
  const cycle = Math.round(days - J0 - lw / (2 * Math.PI));
  const approxNoon = J0 + lw / (2 * Math.PI) + cycle;
  const meanAnomaly = solarMeanAnomaly(approxNoon);
  const ecliptic = eclipticLongitude(meanAnomaly);
  const dec = declination(ecliptic);
  const solarNoon = solarTransitJ(approxNoon, meanAnomaly, ecliptic);

  const hourCos = (Math.sin(SUNRISE_ANGLE) - Math.sin(phi) * Math.sin(dec)) /
    (Math.cos(phi) * Math.cos(dec));
  if (hourCos < -1 || hourCos > 1) {
    return { date: dateKey, timeZone, sunriseAt: null, sunsetAt: null };
  }

  const hourAngle = Math.acos(hourCos);
  const approxSet = J0 + (hourAngle + lw) / (2 * Math.PI) + cycle;
  const sunsetJulian = solarTransitJ(approxSet, meanAnomaly, ecliptic);
  const sunriseJulian = solarNoon - (sunsetJulian - solarNoon);

  return {
    date: dateKey,
    timeZone,
    sunriseAt: fromJulian(sunriseJulian).toISOString(),
    sunsetAt: fromJulian(sunsetJulian).toISOString(),
  };
}
