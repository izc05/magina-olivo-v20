import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { handleRadar } from "./handler.ts";

const MAPS = JSON.parse(readFileSync(new URL("./fixtures/rainviewer-maps.json", import.meta.url), "utf8"));
const NOW = new Date("2026-09-25T06:32:00Z");
const deps = (reply: () => Promise<Response>) => ({ fetch: async () => reply(), now: () => NOW });

test("frames come back as tile templates with their time and source", async () => {
  const result = await handleRadar({ operation: "frames" }, deps(async () => new Response(JSON.stringify(MAPS))));
  assert.equal(result.status, 200);
  const body = result.body as { provider: string; updatedAt: string; frames: { time: string; tileUrlTemplate: string }[] };
  assert.equal(body.provider, "RAINVIEWER");
  assert.equal(body.updatedAt, new Date(1790316600 * 1000).toISOString());
  assert.deepEqual(body.frames.map((f) => f.time), [new Date(1790315400 * 1000).toISOString(), new Date(1790316000 * 1000).toISOString()]);
  assert.equal(body.frames[1].tileUrlTemplate, "https://tilecache.rainviewer.com/v2/radar/1790316000/256/{z}/{x}/{y}/2/1_1.png");
});

test("unknown operations and provider failures are refused, never faked", async () => {
  assert.equal((await handleRadar({ operation: "now" }, deps(async () => new Response("{}")))).status, 400);
  assert.equal((await handleRadar({ operation: "frames" }, deps(async () => new Response("{}", { status: 500 })))).status, 502);
  assert.equal((await handleRadar({ operation: "frames" }, deps(async () => { throw new Error("offline"); }))).status, 502);
  assert.equal((await handleRadar({ operation: "frames" }, deps(async () => new Response(JSON.stringify({ host: "https://x", radar: { past: [] } }))))).status, 502);
});
