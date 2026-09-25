// Spain's forecasts are in local (Europe/Madrid) time; the contract speaks ISO instants.

const MADRID = "Europe/Madrid";

/** Offset of Europe/Madrid at `instant`, as "+02:00". */
export function madridOffset(instant: Date): string {
  const part = new Intl.DateTimeFormat("en-US", { timeZone: MADRID, timeZoneName: "longOffset" })
    .formatToParts(instant)
    .find((p) => p.type === "timeZoneName")?.value ?? "GMT";
  const match = /GMT([+-]\d{2}):?(\d{2})?/.exec(part);
  return match ? `${match[1]}:${match[2] ?? "00"}` : "+00:00";
}

/** Local date ("2026-09-25") and hour (0-23) in Madrid for `instant`. */
export function madridDateHour(instant: Date): { date: string; hour: number } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: MADRID,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(instant);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "00";
  return { date: `${get("year")}-${get("month")}-${get("day")}`, hour: Number(get("hour")) };
}

/** "2026-09-25T08:40:00" in Madrid local time -> ISO instant. */
export function madridLocalToIso(local: string): string {
  const base = local.length === 16 ? `${local}:00` : local.slice(0, 19);
  // The offset is taken at the naive UTC reading, then corrected once around a DST change.
  let guess = new Date(`${base}Z`);
  for (let i = 0; i < 2; i++) {
    guess = new Date(`${base}${madridOffset(guess)}`);
  }
  return guess.toISOString();
}
