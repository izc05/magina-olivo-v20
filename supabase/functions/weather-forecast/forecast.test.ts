// Fixture tests (no live AEMET / MET Norway). Run: node --experimental-strip-types --test
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { handleForecast, resetMasterCache } from "./handler.ts";
import { resolveMunicipality } from "./municipalities.ts";
import { aemetCondition, parseAemetHourly } from "./aemet.ts";
import { metnoCondition, parseMetNo } from "./metno.ts";
import { madridLocalToIso } from "./time.ts";

const fixture = (name: string) => JSON.parse(readFileSync(new URL(`./fixtures/${name}`, import.meta.url), "utf8"));
const MASTER = fixture("aemet-municipios.json");
const META = fixture("aemet-meta.json");
const HOURLY = fixture("aemet-horaria-bedmar.json");
const METNO = fixture("metno-compact.json");
// 08:30 in Madrid (CEST, UTC+2).
const NOW = new Date("2026-09-25T06:30:00Z");

type Route = { match: (url: string) => boolean; reply: () => Promise<Response> };
function fakeFetch(routes: Route[]) {
  const calls: string[] = [];
  const fn = async (input: string) => {
    calls.push(input);
    const route = routes.find((r) => r.match(input));
    if (!route) throw new Error(`unexpected ${input}`);
    return route.reply();
  };
  return { fn, calls };
}
const json = (body: unknown, status = 200) => async () =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8" } });
const fail = (reason: string) => async (): Promise<Response> => {
  throw new Error(reason);
};
const master: Route = { match: (u) => u.endsWith("/maestro/municipios"), reply: json(MASTER) };
const metno = (reply = json(METNO)): Route => ({ match: (u) => u.startsWith("https://api.met.no/"), reply });
const aemetOk: Route[] = [
  { match: (u) => u.includes("/prediccion/especifica/municipio/horaria/23019"), reply: json(META) },
  { match: (u) => u.endsWith("/datos-bedmar"), reply: json(HOURLY) },
];
const deps = (fn: (u: string) => Promise<Response>, key: string | null = "test-key") => ({
  fetch: fn,
  aemetApiKey: key ?? undefined,
  now: () => NOW,
});

test("any Spanish municipality resolves, not only the old four", () => {
  const find = (municipality: string, province?: string) => resolveMunicipality(MASTER, { municipality, province });
  assert.deepEqual(find("Bedmar", "Jaén"), { kind: "found", place: { code: "23019", name: "Bedmar y Garcíez", province: "Jaén", latitude: 37.8216, longitude: -3.4101 } });
  assert.equal((find("jodar", "jaen") as { place: { code: string } }).place.code, "23050");
  assert.equal((find("La Carolina", "Jaén") as { place: { code: string } }).place.code, "23024");
  assert.equal((find("Albanchez", "Jaén") as { place: { code: string } }).place.code, "23002");
  assert.equal((find("Écija", "Sevilla") as { place: { code: string } }).place.code, "41039");
  assert.equal((find("Jimena", "Jaén") as { place: { code: string } }).place.code, "23049");
  assert.equal(find("Villanueva", "Jaén").kind, "ambiguous");
  assert.equal(find("Mancha Real", "Jaén").kind, "not_found");
  assert.equal(resolveMunicipality(MASTER, { municipalityCode: "23019" }).kind, "found");
});

test("AEMET hourly: the current hour, its rain range and wind; codes map without guessing", () => {
  const { current, updatedAt } = parseAemetHourly(HOURLY, NOW);
  assert.deepEqual(current, {
    validAt: "2026-09-25T06:00:00.000Z",
    temperatureC: 16,
    condition: "PARTLY_CLOUDY",
    rainProbabilityPercent: 15,
    windKmh: 11,
  });
  assert.equal(updatedAt, "2026-09-25T05:40:00.000Z");
  assert.equal(aemetCondition("11n"), "CLEAR");
  assert.equal(aemetCondition("54"), "STORM");
  assert.equal(aemetCondition("99"), null);
  assert.throws(() => parseAemetHourly([{ prediccion: { dia: [] } }], NOW));
  assert.equal(madridLocalToIso("2026-01-10T08:00:00"), "2026-01-10T07:00:00.000Z");
});

test("MET Norway compact: the step covering now; no rain probability is invented", () => {
  const { current, updatedAt } = parseMetNo(METNO, NOW);
  assert.deepEqual(current, {
    validAt: "2026-09-25T06:00:00.000Z",
    temperatureC: 16,
    condition: "PARTLY_CLOUDY",
    rainProbabilityPercent: null,
    windKmh: 12,
  });
  assert.equal(updatedAt, "2026-09-25T05:12:44.000Z");
  assert.equal(metnoCondition("lightrainshowers_day"), "RAIN");
  assert.equal(metnoCondition("heavysnowandthunder"), "STORM");
});

test("AEMET answers: MET Norway is never asked", async () => {
  resetMasterCache();
  const { fn, calls } = fakeFetch([master, ...aemetOk, metno()]);
  const result = await handleForecast({ municipality: "Bedmar", province: "Jaén" }, deps(fn));
  assert.equal(result.status, 200);
  const body = result.body as { provider: string; providerName: string; attribution: string; updatedAt: string; fetchedAt: string; location: { code: string } };
  assert.equal(body.provider, "AEMET");
  assert.equal(body.providerName, "AEMET");
  assert.match(body.attribution, /AEMET/);
  assert.equal(body.updatedAt, "2026-09-25T05:40:00.000Z");
  assert.equal(body.fetchedAt, NOW.toISOString());
  assert.equal(body.location.code, "23019");
  assert.equal(calls.filter((u) => u.includes("api.met.no")).length, 0);
});

for (const [label, reply] of [
  ["an error", json({}, 500)],
  ["a rate limit", json({}, 429)],
  ["a timeout", fail("TimeoutError")],
  ["an invalid document", json({ descripcion: "exito", estado: 404 })],
] as const) {
  test(`AEMET fails with ${label}: MET Norway answers and says so`, async () => {
    resetMasterCache();
    const { fn, calls } = fakeFetch([
      master,
      { match: (u) => u.includes("/prediccion/especifica/municipio/horaria/"), reply },
      metno(),
    ]);
    const result = await handleForecast({ municipality: "Bedmar", province: "Jaén" }, deps(fn));
    assert.equal(result.status, 200);
    const body = result.body as { provider: string; attribution: string; current: { temperatureC: number } };
    assert.equal(body.provider, "MET_NORWAY");
    assert.match(body.attribution, /CC BY 4\.0/);
    assert.equal(body.current.temperatureC, 16);
    assert.ok(calls.some((u) => u.includes("lat=37.8216&lon=-3.4101")));
  });
}

test("both providers fail: 502, no value; the app keeps its cache", async () => {
  resetMasterCache();
  const { fn } = fakeFetch([
    master,
    { match: (u) => u.includes("/prediccion/especifica/municipio/horaria/"), reply: json({}, 503) },
    metno(json({}, 500)),
  ]);
  const result = await handleForecast({ municipality: "Bedmar", province: "Jaén" }, deps(fn));
  assert.deepEqual(result, { status: 502, body: { error: "providers_unavailable" } });
});

test("without the AEMET list, coordinates from the app still reach MET Norway", async () => {
  resetMasterCache();
  const { fn, calls } = fakeFetch([metno()]);
  const result = await handleForecast(
    { municipality: "Bedmar", province: "Jaén", latitude: 37.82, longitude: -3.41 },
    deps(fn, null),
  );
  assert.equal(result.status, 200);
  assert.equal((result.body as { provider: string }).provider, "MET_NORWAY");
  assert.deepEqual(calls, ["https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=37.8200&lon=-3.4100"]);
});

test("bad requests and unknown places are refused, not guessed", async () => {
  resetMasterCache();
  const { fn } = fakeFetch([master]);
  assert.equal((await handleForecast({}, deps(fn))).status, 400);
  assert.equal((await handleForecast({ municipalityCode: "23-19" }, deps(fn))).status, 400);
  assert.equal((await handleForecast({ municipality: "Mancha Real", province: "Jaén" }, deps(fn))).status, 404);
  assert.equal((await handleForecast({ municipality: "Villanueva", province: "Jaén" }, deps(fn))).status, 409);
});
