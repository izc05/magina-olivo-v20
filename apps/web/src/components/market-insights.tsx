import { buildMarketInsights } from '@/lib/market-insights';
import type { OliveMarketSnapshot } from '@/lib/market-data';
import styles from './market-insights.module.css';

export function MarketInsights({ snapshot }: { snapshot: OliveMarketSnapshot }) {
  const insights = buildMarketInsights(snapshot);
  if (insights.length === 0) return null;

  return (
    <section className={styles.section} aria-labelledby="market-insights-title">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>LECTURA RÁPIDA</span>
          <h2 id="market-insights-title">Qué dicen los datos</h2>
        </div>
        <p>Resumen descriptivo del último corte oficial. No es una previsión de precios.</p>
      </div>

      <div className={styles.grid}>
        {insights.map((insight) => (
          <article className={styles.card} data-tone={insight.tone} key={insight.id}>
            <span>{insight.label}</span>
            <strong>{insight.value}</strong>
            <p>{insight.detail}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
