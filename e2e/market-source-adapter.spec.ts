import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { expect, test } from '@playwright/test';

const pricesHtml = readFileSync(join(process.cwd(), 'e2e/fixtures/junta-market-prices-week36.html'), 'utf8');
const publicationsHtml = readFileSync(
  join(process.cwd(), 'e2e/fixtures/junta-market-publications-week36.html'),
  'utf8',
);

async function loadDistModule(name: string): Promise<any> {
  return import(pathToFileURL(join(process.cwd(), 'apps/api/dist/market', name)).href);
}

test.describe('Adaptador oficial de Aceite y Mercado', () => {
  test('convierte el HTML oficial esperado en un snapshot estricto', async () => {
    const adapter = await loadDistModule('junta-observatorio-adapter.js');
    const snapshot = adapter.buildJuntaOliveOilMarketSnapshot(pricesHtml, publicationsHtml);

    expect(snapshot).toMatchObject({
      schemaVersion: 1,
      revision: 'junta-andalucia-olive-oil-2026-w36-v1',
      source: {
        publishedOn: '2026-09-09',
        validatedThrough: '2026-09-06',
      },
      period: {
        week: 36,
        start: '2026-08-31',
        end: '2026-09-06',
      },
    });
    expect(snapshot.series.map((series: any) => [series.id, series.points.length, series.latest.priceEurKg])).toEqual([
      ['virgen-extra', 8, 3.42],
      ['virgen', 8, 3.25],
      ['lampante', 8, 3.17],
    ]);
  });

  test('falla cerrado si falta una categoría obligatoria', async () => {
    const adapter = await loadDistModule('junta-observatorio-adapter.js');
    const malformed = pricesHtml.replace('LAMPANTE (1 g)', 'OTRA CATEGORÍA');

    expect(() => adapter.buildJuntaOliveOilMarketSnapshot(malformed, publicationsHtml)).toThrow(
      /market_source_category_row_invalid:lampante/,
    );
  });

  test('distingue sin cambios de una corrección oficial y exige aprobación para aplicarla', async () => {
    const adapter = await loadDistModule('junta-observatorio-adapter.js');
    const refresh = await loadDistModule('refresh.js');
    const snapshotModule = await loadDistModule('snapshot.js');
    const now = new Date('2026-09-11T12:00:00Z');

    const candidate = adapter.buildJuntaOliveOilMarketSnapshot(pricesHtml, publicationsHtml);
    const unchanged = refresh.planOliveOilMarketRefresh(snapshotModule.oliveOilMarketSnapshot, candidate, now);
    expect(unchanged.kind).toBe('unchanged');
    expect(unchanged.historicalCorrections).toBe(0);
    expect(unchanged.candidateRevision).toBe('junta-andalucia-olive-oil-2026-w36-v1');

    const correctedHtml = pricesHtml.replace('<td>3,42</td>', '<td>3,43</td>');
    const correctedCandidate = adapter.buildJuntaOliveOilMarketSnapshot(correctedHtml, publicationsHtml);
    const correction = refresh.planOliveOilMarketRefresh(
      snapshotModule.oliveOilMarketSnapshot,
      correctedCandidate,
      now,
    );

    expect(correction.kind).toBe('correction');
    expect(correction.historicalCorrections).toBeGreaterThan(0);
    expect(correction.candidateRevision).toMatch(/^junta-andalucia-olive-oil-2026-w36-v1-corr-[a-f0-9]{10}$/);
    expect(correction.snapshot.series.find((series: any) => series.id === 'virgen-extra')?.latest.priceEurKg).toBe(3.43);
    expect(() => refresh.assertMarketRefreshApplyAllowed(correction)).toThrow(
      /market_refresh_correction_requires_approval:correction:/,
    );
    expect(() => refresh.assertMarketRefreshApplyAllowed(correction, true)).not.toThrow();
  });

  test('cubre alta inicial, nuevo periodo, corrección histórica y regresión temporal', async () => {
    const adapter = await loadDistModule('junta-observatorio-adapter.js');
    const refresh = await loadDistModule('refresh.js');
    const snapshotModule = await loadDistModule('snapshot.js');
    const now = new Date('2026-09-18T12:00:00Z');
    const candidate = adapter.buildJuntaOliveOilMarketSnapshot(pricesHtml, publicationsHtml);

    const initial = refresh.planOliveOilMarketRefresh(null, candidate, now);
    expect(initial.kind).toBe('initial');
    expect(initial.currentRevision).toBeNull();
    expect(initial.historicalCorrections).toBe(0);

    const previous = {
      ...snapshotModule.oliveOilMarketSnapshot,
      revision: 'junta-andalucia-olive-oil-2026-w35-v1',
      period: { week: 35, start: '2026-08-24', end: '2026-08-30' },
      source: {
        ...snapshotModule.oliveOilMarketSnapshot.source,
        publishedOn: '2026-09-02',
        validatedThrough: '2026-08-30',
      },
      series: snapshotModule.oliveOilMarketSnapshot.series.map((series: any) => ({
        ...series,
        points: series.points.slice(0, -1),
      })),
    };
    const newPeriod = refresh.planOliveOilMarketRefresh(previous, candidate, now);
    expect(newPeriod.kind).toBe('new-period');
    expect(newPeriod.currentThrough).toBe('2026-08-30');
    expect(newPeriod.candidateThrough).toBe('2026-09-06');
    expect(newPeriod.historicalCorrections).toBe(0);
    expect(() => refresh.assertMarketRefreshApplyAllowed(newPeriod)).not.toThrow();

    const candidateWithHistoricalCorrection = {
      ...candidate,
      series: candidate.series.map((series: any) => ({
        ...series,
        points: series.points.map((point: any) =>
          series.id === 'virgen-extra' && point.week === 35
            ? { ...point, priceEurKg: Number((point.priceEurKg + 0.01).toFixed(2)) }
            : point,
        ),
      })),
    };
    const correctedNewPeriod = refresh.planOliveOilMarketRefresh(previous, candidateWithHistoricalCorrection, now);
    expect(correctedNewPeriod.kind).toBe('new-period');
    expect(correctedNewPeriod.historicalCorrections).toBe(1);
    expect(() => refresh.assertMarketRefreshApplyAllowed(correctedNewPeriod)).toThrow(
      /market_refresh_correction_requires_approval:new-period:1/,
    );
    expect(() => refresh.assertMarketRefreshApplyAllowed(correctedNewPeriod, true)).not.toThrow();

    expect(() => refresh.planOliveOilMarketRefresh(candidate, previous, now)).toThrow(
      /market_source_regression:2026-08-30:2026-09-06/,
    );
  });

  test('los modos de operación son explícitos y fallan cerrado ante flags ambiguos', async () => {
    const modes = await loadDistModule('refresh-mode.js');

    expect(modes.parseMarketRefreshOperation([])).toEqual({ mode: 'source-check', allowCorrections: false });
    expect(modes.parseMarketRefreshOperation(['--source-check'])).toEqual({ mode: 'source-check', allowCorrections: false });
    expect(modes.parseMarketRefreshOperation(['--dry-run'])).toEqual({ mode: 'dry-run', allowCorrections: false });
    expect(modes.parseMarketRefreshOperation(['--apply'])).toEqual({ mode: 'apply', allowCorrections: false });
    expect(modes.parseMarketRefreshOperation(['--apply', '--allow-corrections'])).toEqual({
      mode: 'apply',
      allowCorrections: true,
    });
    expect(modes.marketRefreshModeNeedsDatabase('source-check')).toBe(false);
    expect(modes.marketRefreshModeNeedsDatabase('dry-run')).toBe(true);
    expect(modes.marketRefreshModeNeedsDatabase('apply')).toBe(true);

    expect(() => modes.parseMarketRefreshOperation(['--dry-run', '--apply'])).toThrow(/market_refresh_mode_conflict/);
    expect(() => modes.parseMarketRefreshOperation(['--dry-run', '--allow-corrections'])).toThrow(
      /market_refresh_correction_approval_requires_apply/,
    );
    expect(() => modes.parseMarketRefreshOperation(['--force'])).toThrow(/market_refresh_mode_unknown:--force/);
  });
});
