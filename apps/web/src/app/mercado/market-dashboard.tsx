'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { MarketInsights } from '@/components/market-insights';
import { MarketValueCalculator } from '@/components/market-value-calculator';
import {
  latestMarketPrice,
  marketDelta,
  oliveMarketSnapshot,
  type MarketSeries,
  type OliveMarketSnapshot,
} from '@/lib/market-data';
import {
  fetchOliveMarketHistory,
  fetchOliveMarketSnapshot,
  type MarketSnapshotOrigin,
} from '@/lib/market-data-source';
import historyStyles from './market-history-controls.module.css';
import styles from './market.module.css';

type HistoryWindow = 4 | 8;

function formatPrice(value: number): string {
  return new Intl.NumberFormat('es-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatPublishedOn(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) return value;
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(Date.UTC(year, month - 1, day)));
}

function freshnessLabel(value: string): string {
  const publishedAt = Date.parse(`${value}T00:00:00Z`);
  if (!Number.isFinite(publishedAt)) return 'Publicación semanal';
  const ageDays = Math.max(0, Math.floor((Date.now() - publishedAt) / 86_400_000));
  if (ageDays <= 1) return 'Actualizado recientemente';
  if (ageDays <= 9) return `Actualizado hace ${ageDays} días`;
  return `Referencia de hace ${ageDays} días`;
}

function formatDelta(series: MarketSeries): { label: string; className: string } {
  const delta = marketDelta(series);
  const sign = delta.absolute > 0 ? '+' : '';
  const label = `${sign}${formatPrice(delta.absolute)} €/kg · ${sign}${delta.percent.toFixed(1).replace('.', ',')}% vs. semana anterior`;

  if (delta.absolute > 0.005) return { label, className: styles.deltaUp };
  if (delta.absolute < -0.005) return { label, className: styles.deltaDown };
  return { label, className: styles.deltaFlat };
}

function sliceSeriesForWindow(series: MarketSeries[], weeks: number): MarketSeries[] {
  return series.map((entry) => ({
    ...entry,
    points: entry.points.slice(-weeks),
  }));
}

function Sparkline({ series }: { series: MarketSeries }) {
  const width = 240;
  const height = 64;
  const padding = 6;
  const values = series.points.map((point) => point.priceEurKg);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = Math.max(max - min, 0.01);
  const points = values.map((value, index) => {
    const x = padding + (index / Math.max(values.length - 1, 1)) * (width - padding * 2);
    const y = height - padding - ((value - min) / spread) * (height - padding * 2);
    return { x, y };
  });
  const polyline = points.map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const lastPoint = points.at(-1);

  return (
    <svg
      className={styles.sparkline}
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={`Evolución de ${series.shortName} durante las últimas ${series.points.length} semanas`}
      preserveAspectRatio="none"
    >
      <line x1="0" y1={height - 8} x2={width} y2={height - 8} />
      <polyline points={polyline} />
      {lastPoint ? <circle cx={lastPoint.x} cy={lastPoint.y} r="4" /> : null}
    </svg>
  );
}

export function MarketDashboard() {
  const [snapshot, setSnapshot] = useState<OliveMarketSnapshot>(oliveMarketSnapshot);
  const [origin, setOrigin] = useState<MarketSnapshotOrigin>('fallback');
  const [trendWeeks, setTrendWeeks] = useState<HistoryWindow>(8);
  const [trendSeriesOverride, setTrendSeriesOverride] = useState<MarketSeries[] | null>(null);
  const [trendLoading, setTrendLoading] = useState(false);

  useEffect(() => {
    let active = true;

    fetchOliveMarketSnapshot()
      .then((nextSnapshot) => {
        if (!active) return;
        setSnapshot(nextSnapshot);
        setOrigin('api');
      })
      .catch(() => {
        // El snapshot local permite seguir consultando la última publicación
        // conocida cuando la API o la red no están disponibles.
      });

    return () => {
      active = false;
    };
  }, []);

  const trendSeries = trendSeriesOverride ?? snapshot.series;
  const visibleTrendWeeks = trendSeries[0]?.points.length ?? trendWeeks;
  const aove = snapshot.series.find((series) => series.id === 'virgen-extra');
  const defaultPrice = aove ? latestMarketPrice(aove) : 0;
  const priceOptions = snapshot.series.map((series) => ({
    id: series.id,
    label: series.shortName,
    priceEurKg: latestMarketPrice(series),
  }));
  const firstWeek = trendSeries[0]?.points[0];
  const lastWeek = trendSeries[0]?.points.at(-1);

  async function selectTrendWindow(weeks: HistoryWindow) {
    if (weeks === trendWeeks) return;

    setTrendWeeks(weeks);
    setTrendLoading(true);

    try {
      const history = await fetchOliveMarketHistory(weeks);
      setTrendSeriesOverride(history.series);
    } catch {
      // Si falla el histórico dedicado seguimos ofreciendo una ventana útil
      // sobre el snapshot que ya está visible, sin vaciar la gráfica.
      setTrendSeriesOverride(sliceSeriesForWindow(snapshot.series, weeks));
    } finally {
      setTrendLoading(false);
    }
  }

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <Link href="/explorar" className={styles.backLink}>
          ← Volver a Explorar
        </Link>
        <span className={styles.eyebrow}>ACEITE Y MERCADO</span>
        <h1>El precio del aceite, explicado sin ruido.</h1>
        <p className={styles.heroText}>
          Consulta la referencia oficial en origen, mira cómo se ha movido durante las últimas semanas y úsala como punto de partida para entender mejor tu cosecha.
        </p>
        <div className={styles.heroMeta}>
          <span>{snapshot.marketLevel}</span>
          <span>{snapshot.periodLabel}</span>
          <span>Publicado {formatPublishedOn(snapshot.sourcePublishedOn)}</span>
          <span>{freshnessLabel(snapshot.sourcePublishedOn)}</span>
        </div>
      </section>

      <section aria-labelledby="market-prices-title">
        <div className={styles.sectionHeading}>
          <div>
            <span className={styles.eyebrow}>REFERENCIA EN ORIGEN</span>
            <h2 id="market-prices-title">Último dato validado</h2>
          </div>
          <span className={styles.statusChip} data-market-source={origin}>
            {origin === 'api' ? 'Dato API validado' : 'Último dato guardado'}
          </span>
        </div>

        <div className={styles.priceGrid}>
          {snapshot.series.map((series) => {
            const delta = formatDelta(series);
            return (
              <article className={styles.priceCard} key={series.id}>
                <div className={styles.priceCardHeader}>
                  <h3>{series.shortName}</h3>
                </div>
                <div className={styles.priceValue}>
                  <strong>{formatPrice(latestMarketPrice(series))}</strong>
                  <span>€/kg</span>
                </div>
                <div className={`${styles.delta} ${delta.className}`}>{delta.label}</div>
                <p className={styles.cardCaption}>{series.description}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section
        className={styles.trendCard}
        aria-labelledby="market-trend-title"
        data-market-history-weeks={visibleTrendWeeks}
      >
        <div className={styles.sectionHeading}>
          <div>
            <span className={styles.eyebrow}>{visibleTrendWeeks} SEMANAS</span>
            <h2 id="market-trend-title">Cómo se está moviendo</h2>
          </div>
          <div>
            <div className={historyStyles.controls} role="group" aria-label="Periodo del histórico">
              {([4, 8] as const).map((weeks) => (
                <button
                  className={historyStyles.button}
                  type="button"
                  key={weeks}
                  aria-pressed={trendWeeks === weeks}
                  disabled={trendLoading}
                  onClick={() => void selectTrendWindow(weeks)}
                >
                  {weeks} sem
                </button>
              ))}
            </div>
            <p className={historyStyles.status} aria-live="polite">
              {trendLoading ? 'Actualizando histórico…' : `Ventana de ${visibleTrendWeeks} semanas`}
            </p>
          </div>
        </div>

        <div className={styles.trendList}>
          {trendSeries.map((series) => (
            <div className={styles.trendRow} key={series.id}>
              <div className={styles.trendName}>
                <strong>{series.shortName}</strong>
                <small>{series.points.length} semanas</small>
              </div>
              <Sparkline series={series} />
              <div className={styles.trendPrice}>{formatPrice(latestMarketPrice(series))}</div>
            </div>
          ))}
        </div>

        <div className={styles.weeksLegend} aria-hidden="true">
          <span>{firstWeek?.label ?? ''}</span>
          <span>histórico</span>
          <span>{visibleTrendWeeks} semanas</span>
          <span>{lastWeek?.label ?? ''}</span>
        </div>
      </section>

      <MarketInsights snapshot={snapshot} />

      <MarketValueCalculator
        key={snapshot.revision}
        defaultPrice={defaultPrice}
        priceOptions={priceOptions}
      />

      <section className={styles.sourceCard} aria-labelledby="market-source-title">
        <div>
          <span className={styles.eyebrow}>FUENTE Y CONTEXTO</span>
          <h2 id="market-source-title">{snapshot.sourceName}</h2>
          <p>{snapshot.note}</p>
        </div>
        <a
          className={styles.sourceLink}
          href={snapshot.sourceUrl}
          target="_blank"
          rel="noreferrer"
        >
          Ver fuente oficial
        </a>
      </section>
    </div>
  );
}
