import { createDatabase } from '../db/client.js';
import { fetchJuntaOliveOilMarketSnapshot } from './junta-observatorio-adapter.js';
import { refreshOliveOilMarketFromJunta } from './refresh.js';

function summary(snapshot: Awaited<ReturnType<typeof fetchJuntaOliveOilMarketSnapshot>>) {
  return {
    revision: snapshot.revision,
    week: snapshot.period.week,
    periodStart: snapshot.period.start,
    periodEnd: snapshot.period.end,
    publishedOn: snapshot.source.publishedOn,
    prices: Object.fromEntries(
      snapshot.series.map((series) => [series.id, series.latest.priceEurKg]),
    ),
  };
}

async function main() {
  const apply = process.argv.includes('--apply');
  const databaseUrl = process.env.DATABASE_URL?.trim();

  if (!databaseUrl) {
    if (apply) throw new Error('DATABASE_URL is required with --apply');
    const snapshot = await fetchJuntaOliveOilMarketSnapshot();
    console.log(JSON.stringify({ mode: 'source-check', valid: true, source: summary(snapshot) }, null, 2));
    return;
  }

  const db = createDatabase(databaseUrl);
  try {
    const result = await refreshOliveOilMarketFromJunta(db, { apply });
    console.log(
      JSON.stringify(
        {
          mode: apply ? 'apply' : 'dry-run',
          kind: result.kind,
          applied: result.applied,
          currentRevision: result.currentRevision,
          candidateRevision: result.candidateRevision,
          currentThrough: result.currentThrough,
          candidateThrough: result.candidateThrough,
          source: summary(result.snapshot),
        },
        null,
        2,
      ),
    );
  } finally {
    await db.destroy();
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
