import type { Metadata } from 'next';
import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { MarketValueCalculator } from '@/components/market-value-calculator';
import { Topbar } from '@/components/topbar';
import {
  latestMarketPrice,
  marketDelta,
  oliveMarketSnapshot,
  type MarketSeries,
} from '@/lib/market-data';
import styles from './market.module.css';

export const metadata: Metadata = {
  title: 'Aceite y mercado · Mágina Olivo',
  description: 'Precios de referencia del aceite de oliva, evolución semanal y una estimación sencilla para tu cosecha.',
};

function formatPrice(value: number): string {
  return new Intl.NumberFormat('es-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDelta(series: MarketSeries): { label: string; className: string } {
  const delta = marketDelta(series);
  const sign = delta.absolute > 0 ? '+' : '';
  const label = `${sign}${formatPrice(delta.absolute)} €/kg · ${sign}${delta.percent.toFixed(1).replace('.', ',')}% vs. semana anterior`;

  if (delta.absolute > 0.005) return { label, className: styles.deltaUp };
  if (delta.absolute < -0.005) return { label, className: styles.deltaDown };
  return { label, className: styles.deltaFlat };
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
      aria-label={`Evolución de ${series.shortName} durante las últimas ocho semanas`}
      preserveAspectRatio="none"
    >
      <line x1="0" y1={height - 8} x2={width} y2={height - 8} />
      <polyline points={polyline} />
      {lastPoint ? <circle cx={lastPoint.x} cy={lastPoint.y} r="4" /> : null}
    </svg>
  );
}

export default function MarketPage() {
  const aove = oliveMarketSnapshot.series.find((series) => series.id === 'virgen-extra');
  const defaultPrice = aove ? latestMarketPrice(aove) : 0;
  const firstWeek = oliveMarketSnapshot.series[0]?.points[0];
  const lastWeek = oliveMarketSnapshot.series[0]?.points.at(-1);

  return (
    <main className="app-shell">
      <Topbar />
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
            <span>{oliveMarketSnapshot.marketLevel}</span>
            <span>{oliveMarketSnapshot.periodLabel}</span>
            <span>Fuente oficial y fechada</span>
          </div>
        </section>

        <section aria-labelledby="market-prices-title">
          <div className={styles.sectionHeading}>
            <div>
              <span className={styles.eyebrow}>REFERENCIA EN ORIGEN</span>
              <h2 id="market-prices-title">Último dato validado</h2>
            </div>
            <span className={styles.statusChip}>€/kg aceite</span>
          </div>

          <div className={styles.priceGrid}>
            {oliveMarketSnapshot.series.map((series) => {
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

        <section className={styles.trendCard} aria-labelledby="market-trend-title">
          <div className={styles.sectionHeading}>
            <div>
              <span className={styles.eyebrow}>8 SEMANAS</span>
              <h2 id="market-trend-title">Cómo se está moviendo</h2>
            </div>
          </div>

          <div className={styles.trendList}>
            {oliveMarketSnapshot.series.map((series) => (
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
            <span>agosto</span>
            <span>agosto</span>
            <span>{lastWeek?.label ?? ''}</span>
          </div>
        </section>

        <MarketValueCalculator defaultPrice={defaultPrice} />

        <section className={styles.sourceCard} aria-labelledby="market-source-title">
          <div>
            <span className={styles.eyebrow}>FUENTE Y CONTEXTO</span>
            <h2 id="market-source-title">{oliveMarketSnapshot.sourceName}</h2>
            <p>{oliveMarketSnapshot.note}</p>
          </div>
          <a
            className={styles.sourceLink}
            href={oliveMarketSnapshot.sourceUrl}
            target="_blank"
            rel="noreferrer"
          >
            Ver fuente oficial
          </a>
        </section>
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
