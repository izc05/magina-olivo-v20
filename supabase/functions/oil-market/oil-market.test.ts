import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { handleOilMarket } from "./handler.ts";
import { categoryOf, isoWeekMonday, parseJuntaPage } from "./junta.ts";

const PAGE = readFileSync(new URL("./fixtures/junta-ultimos-precios.synthetic.html", import.meta.url), "utf8");
const CAPTURE = JSON.parse(
  readFileSync(new URL("../../../docs/06-testing/evidence/phase20d/junta-live-table-2026-09-27.json", import.meta.url), "utf8"),
);
const NOW = new Date("2026-09-27T09:00:00Z");
const deps = (reply: () => Promise<Response>) => ({ fetch: async () => reply(), now: () => NOW });
const page = (html: string) => deps(async () => new Response(html, { headers: { "content-type": "text/html" } }));

type Body = { series: { category: string; sourceId: string; geography: { code: string }; marketStage: string; observations: { periodStart: string; periodEnd: string; originalValue: number; valueEurPerKg: number; originalUnit: string }[] }[] };

test("every captured week and category comes back exactly as the owner verified it", () => {
  const values = parseJuntaPage(PAGE, NOW);
  assert.equal(values.length, 8 * 3);
  for (const week of CAPTURE.weeks) {
    for (const category of ["AOVE", "AOV", "AOL"]) {
      const found = values.find((v) => v.category === category && v.isoWeek === week.week);
      assert.ok(found, `${category} week ${week.week}`);
      assert.equal(Number(found.value), week[category]);
      assert.equal(found.periodStart, week.periodStart);
      assert.equal(found.periodEnd, week.periodEnd);
    }
  }
});

test("series answers the contract: one series per category, source, geography, stage, €/kg", async () => {
  const result = await handleOilMarket({ operation: "series", geography: "andalucia", weeks: 2 }, page(PAGE));
  assert.equal(result.status, 200);
  const body = result.body as Body;
  assert.deepEqual(body.series.map((s) => s.category), ["AOVE", "AOV", "AOL"]);
  const aove = body.series[0];
  assert.equal(aove.sourceId, "junta-andalucia-observatorio");
  assert.equal(aove.geography.code, "ES-AN");
  assert.equal(aove.marketStage, "ALMAZARA_OR_BODEGA");
  assert.deepEqual(aove.observations, [
    { periodStart: "2026-09-07", periodEnd: "2026-09-13", originalValue: 3.67, originalUnit: "EUR_PER_KG", valueEurPerKg: 3.67 },
    { periodStart: "2026-09-14", periodEnd: "2026-09-20", originalValue: 3.46, originalUnit: "EUR_PER_KG", valueEurPerKg: 3.46 },
  ]);
  const latest = await handleOilMarket({ operation: "latest", geography: "andalucia" }, page(PAGE));
  assert.deepEqual((latest.body as Body).series.map((s) => s.observations.map((o) => o.valueEurPerKg)), [[3.46], [3.31], [3.15]]);
});

test("a '--' or empty cell stays missing — never zero, never filled in", async () => {
  const gap = PAGE.replace(/(<td>VIRGEN<\/td>(?:<td class="dato">[^<]*<\/td>){7})<td class="dato">3,31<\/td>/, "$1<td class=\"dato\">--</td>")
    .replace(/(<td>VIRGEN-EXTRA<\/td>)<td class="dato">3,68<\/td>/, "$1<td class=\"dato\">&nbsp;</td>");
  assert.notEqual(gap, PAGE);
  const body = (await handleOilMarket({ operation: "series", geography: "andalucia", weeks: 12 }, page(gap))).body as Body;
  const aov = body.series.find((s) => s.category === "AOV")!;
  assert.equal(aov.observations.length, 7);
  assert.equal(aov.observations.at(-1)!.periodStart, "2026-09-07");
  const aove = body.series.find((s) => s.category === "AOVE")!;
  assert.equal(aove.observations[0].periodStart, "2026-08-03");
});

test("an unknown category, an odd value or a changed page is refused, not guessed", async () => {
  const unknown = PAGE.replace("<td>VIRGEN</td>", "<td>ORUJO</td>");
  assert.throws(() => parseJuntaPage(unknown, NOW), /junta_unknown_category/);
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia" }, page(unknown))).status, 502);
  const per100 = PAGE.replace(">3,46<", ">346,00<");
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia" }, page(per100))).status, 502);
  const noTable = "<html><body><p>Servicio en mantenimiento</p></body></html>";
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia" }, page(noTable))).status, 502);
});

test("source failures and bad requests are refused, never faked", async () => {
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia" }, deps(async () => new Response("", { status: 503 })))).status, 502);
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia" }, deps(async () => { throw new Error("timeout"); }))).status, 502);
  assert.equal((await handleOilMarket({ operation: "today", geography: "andalucia" }, page(PAGE))).status, 400);
  assert.equal((await handleOilMarket({ operation: "series", geography: "jaen" }, page(PAGE))).status, 400);
  assert.equal((await handleOilMarket({ operation: "series", geography: "andalucia", weeks: 0 }, page(PAGE))).status, 400);
});

test("labels and weeks: strict category names, ISO weeks across the new year", () => {
  assert.equal(categoryOf("LAMPANTE (1 g)"), "AOL");
  assert.equal(categoryOf("Virgen Extra"), "AOVE");
  assert.equal(categoryOf("VIRGEN-EXTRA"), "AOVE");
  assert.equal(categoryOf("VIRGEN"), "AOV");
  assert.equal(categoryOf("REFINADO"), null);
  assert.equal(isoWeekMonday(2026, 38).toISOString().slice(0, 10), "2026-09-14");
  assert.equal(isoWeekMonday(2026, 1).toISOString().slice(0, 10), "2025-12-29");
  // Week 52 read on 2027-01-06 (ISO week 1 of 2027) belongs to 2026.
  const winter = PAGE.replace("<th>Semana 38</th>", "<th>Semana 1</th>").replace("<th>Semana 37</th>", "<th>Semana 52</th>");
  const values = parseJuntaPage(winter, new Date("2027-01-06T09:00:00Z"));
  assert.equal(values.find((v) => v.isoWeek === 52)!.periodStart, "2026-12-21");
  assert.equal(values.find((v) => v.isoWeek === 1)!.periodStart, "2027-01-04");
});
