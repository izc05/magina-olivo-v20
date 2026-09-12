import { createDatabase } from '../db/client.js';
import { fetchJuntaOliveOilMarketSnapshot } from './junta-observatorio-adapter.js';
import { marketRefreshModeNeedsDatabase, parseMarketRefreshOperation } from './refresh-mode.js';
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
  const operation = parseMarketRefreshOperation(process.argv.slice(2));
  const { mode, allowCorrections } = operation;
  const databaseUrl = process.env.DATABASE_URL?.trim();

  if (mode === 'source-check') {
    const snapshot = await fetchJuntaOliveOilMarketSnapshot();
    console.log(JSON.stringify({ mode, valid: true, source: summary(snapshot) }, null, 2));
    return;
  }

  if (marketRefreshModeNeedsDatabase(mode) && !databaseUrl) {
    throw new Error(`DATABASE_URL is required with --${mode}`);
  }

  const db = createDatabase(databaseUrl!);
  try {
    const apply = mode === 'apply';
    const result = await refreshOliveOilMarketFromJunta(db, { apply, allowCorrections });
    console.log(
      JSON.stringify(
        {
          mode,
          allowCorrections,
          kind: result.kind,
          applied: result.applied,
          historicalCorrections: result.historicalCorrections,
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
