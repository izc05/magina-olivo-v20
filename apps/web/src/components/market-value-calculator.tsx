'use client';

import { useMemo, useState } from 'react';
import styles from '@/app/mercado/market.module.css';

type MarketValueCalculatorProps = {
  defaultPrice: number;
};

function parsePositive(value: string): number {
  const normalized = value.replace(',', '.');
  const parsed = Number.parseFloat(normalized);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}

function formatNumber(value: number, maximumFractionDigits = 2): string {
  return new Intl.NumberFormat('es-ES', {
    maximumFractionDigits,
  }).format(value);
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 2,
  }).format(value);
}

export function MarketValueCalculator({ defaultPrice }: MarketValueCalculatorProps) {
  const [oliveKg, setOliveKg] = useState('5000');
  const [yieldPct, setYieldPct] = useState('20');
  const [oilPrice, setOilPrice] = useState(defaultPrice.toFixed(2));

  const result = useMemo(() => {
    const olives = parsePositive(oliveKg);
    const yieldValue = Math.min(parsePositive(yieldPct), 100);
    const price = parsePositive(oilPrice);
    const estimatedOilKg = olives * (yieldValue / 100);
    const theoreticalValue = estimatedOilKg * price;
    const theoreticalValuePerOliveKg = olives > 0 ? theoreticalValue / olives : 0;

    return {
      estimatedOilKg,
      theoreticalValue,
      theoreticalValuePerOliveKg,
    };
  }, [oliveKg, oilPrice, yieldPct]);

  return (
    <section className={styles.calculator} aria-labelledby="market-calculator-title">
      <div className={styles.sectionHeading}>
        <div>
          <span className={styles.eyebrow}>MI COSECHA</span>
          <h2 id="market-calculator-title">Calcula una referencia rápida</h2>
        </div>
        <span className={styles.statusChip}>Estimación</span>
      </div>

      <p className={styles.calculatorIntro}>
        Introduce tus kilos de aceituna, rendimiento industrial y un precio del aceite para estimar el valor teórico del aceite obtenido.
      </p>

      <div className={styles.inputGrid}>
        <label>
          <span>Kilos de aceituna</span>
          <div className={styles.inputWithUnit}>
            <input
              inputMode="decimal"
              min="0"
              type="number"
              value={oliveKg}
              onChange={(event) => setOliveKg(event.target.value)}
              aria-label="Kilos de aceituna"
            />
            <small>kg</small>
          </div>
        </label>

        <label>
          <span>Rendimiento industrial</span>
          <div className={styles.inputWithUnit}>
            <input
              inputMode="decimal"
              min="0"
              max="100"
              step="0.1"
              type="number"
              value={yieldPct}
              onChange={(event) => setYieldPct(event.target.value)}
              aria-label="Rendimiento industrial"
            />
            <small>%</small>
          </div>
        </label>

        <label>
          <span>Precio de referencia</span>
          <div className={styles.inputWithUnit}>
            <input
              inputMode="decimal"
              min="0"
              step="0.01"
              type="number"
              value={oilPrice}
              onChange={(event) => setOilPrice(event.target.value)}
              aria-label="Precio del aceite por kilogramo"
            />
            <small>€/kg</small>
          </div>
        </label>
      </div>

      <div className={styles.resultGrid} aria-live="polite">
        <article>
          <span>Aceite estimado</span>
          <strong>{formatNumber(result.estimatedOilKg, 1)} kg</strong>
        </article>
        <article>
          <span>Valor teórico</span>
          <strong>{formatCurrency(result.theoreticalValue)}</strong>
        </article>
        <article>
          <span>Equivalente por kg de aceituna</span>
          <strong>{formatCurrency(result.theoreticalValuePerOliveKg)}</strong>
        </article>
      </div>

      <p className={styles.disclaimer}>
        No es una liquidación ni una oferta de compra. No descuenta molturación, costes, calidad, bonificaciones, penalizaciones ni condiciones de cada cooperativa o almazara.
      </p>
    </section>
  );
}
