'use client';

import { useMemo, useState } from 'react';
import styles from './farm-tools.module.css';

type SurfaceUnit = 'm2' | 'ha';

function parsePositiveDecimal(raw: string) {
  const normalized = raw.trim().replace(',', '.');
  if (!normalized) return null;
  const value = Number(normalized);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function formatNumber(value: number, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('es-ES', {
    maximumFractionDigits,
    minimumFractionDigits: 0,
  }).format(value);
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export function FarmToolsClient() {
  const [surfaceValue, setSurfaceValue] = useState('');
  const [surfaceUnit, setSurfaceUnit] = useState<SurfaceUnit>('m2');
  const [rowSpacing, setRowSpacing] = useState('');
  const [treeSpacing, setTreeSpacing] = useState('');
  const [plantingArea, setPlantingArea] = useState('');
  const [totalCost, setTotalCost] = useState('');
  const [productionKg, setProductionKg] = useState('');
  const [costArea, setCostArea] = useState('');

  const surface = useMemo(() => {
    const value = parsePositiveDecimal(surfaceValue);
    if (!value) return null;
    const squareMeters = surfaceUnit === 'm2' ? value : value * 10_000;
    return { squareMeters, hectares: squareMeters / 10_000 };
  }, [surfaceUnit, surfaceValue]);

  const density = useMemo(() => {
    const row = parsePositiveDecimal(rowSpacing);
    const tree = parsePositiveDecimal(treeSpacing);
    if (!row || !tree) return null;
    const treesPerHectare = 10_000 / (row * tree);
    const area = parsePositiveDecimal(plantingArea);
    return {
      treesPerHectare,
      estimatedTrees: area ? treesPerHectare * area : null,
    };
  }, [plantingArea, rowSpacing, treeSpacing]);

  const costs = useMemo(() => {
    const total = parsePositiveDecimal(totalCost);
    const kg = parsePositiveDecimal(productionKg);
    if (!total || !kg) return null;
    const area = parsePositiveDecimal(costArea);
    return {
      costPerKg: total / kg,
      costPerHectare: area ? total / area : null,
    };
  }, [costArea, productionKg, totalCost]);

  return (
    <div className={styles.surface}>
      <section className={styles.hero} aria-labelledby="tools-title">
        <span className={styles.eyebrow}>HERRAMIENTAS RÁPIDAS</span>
        <h1 id="tools-title">Calcula sin guardar nada</h1>
        <p>
          Introduce tus propios datos para convertir superficies, estimar una densidad geométrica o repartir un coste.
          Los cálculos se hacen en este navegador y no se envían ni se guardan.
        </p>
        <div className={styles.boundary} role="note">
          <strong>Solo apoyo de cálculo.</strong>
          <span>No calcula dosis, mezclas, tratamientos, fertilización ni recomienda productos o labores.</span>
        </div>
      </section>

      <section className={styles.grid} aria-label="Calculadoras disponibles">
        <article className={styles.card}>
          <div className={styles.cardHead}>
            <span className={styles.step}>01</span>
            <div>
              <h2>Superficie</h2>
              <p>Convierte entre metros cuadrados y hectáreas.</p>
            </div>
          </div>

          <div className={styles.fields}>
            <label>
              <span>Superficie</span>
              <input
                value={surfaceValue}
                onChange={(event) => setSurfaceValue(event.target.value)}
                inputMode="decimal"
                placeholder="Ej. 12500"
                aria-describedby="surface-help"
              />
            </label>
            <label>
              <span>Unidad</span>
              <select value={surfaceUnit} onChange={(event) => setSurfaceUnit(event.target.value as SurfaceUnit)}>
                <option value="m2">m²</option>
                <option value="ha">ha</option>
              </select>
            </label>
          </div>
          <small id="surface-help" className={styles.help}>Admite coma o punto decimal.</small>

          <div className={styles.result} data-testid="surface-result" aria-live="polite">
            {surface ? (
              <>
                <strong>{formatNumber(surface.hectares, 4)} ha</strong>
                <span>{formatNumber(surface.squareMeters, 2)} m²</span>
              </>
            ) : (
              <span>Introduce una superficie mayor que cero.</span>
            )}
          </div>
        </article>

        <article className={styles.card}>
          <div className={styles.cardHead}>
            <span className={styles.step}>02</span>
            <div>
              <h2>Marco de plantación</h2>
              <p>Estima la densidad teórica de un marco rectangular regular.</p>
            </div>
          </div>

          <div className={styles.fields}>
            <label>
              <span>Distancia entre filas (m)</span>
              <input value={rowSpacing} onChange={(event) => setRowSpacing(event.target.value)} inputMode="decimal" placeholder="Ej. 7" />
            </label>
            <label>
              <span>Distancia entre árboles (m)</span>
              <input value={treeSpacing} onChange={(event) => setTreeSpacing(event.target.value)} inputMode="decimal" placeholder="Ej. 7" />
            </label>
            <label className={styles.fullField}>
              <span>Superficie de referencia (ha) · opcional</span>
              <input value={plantingArea} onChange={(event) => setPlantingArea(event.target.value)} inputMode="decimal" placeholder="Ej. 2" />
            </label>
          </div>

          <div className={styles.result} data-testid="density-result" aria-live="polite">
            {density ? (
              <>
                <strong>{formatNumber(Math.round(density.treesPerHectare), 0)} olivos/ha</strong>
                {density.estimatedTrees !== null && <span>≈ {formatNumber(Math.round(density.estimatedTrees), 0)} olivos en la superficie indicada</span>}
              </>
            ) : (
              <span>Introduce las dos distancias mayores que cero.</span>
            )}
          </div>
          <p className={styles.caution}>Es una estimación geométrica. Lindes, pendientes, calles, marras y marcos irregulares cambian el número real.</p>
        </article>

        <article className={styles.card}>
          <div className={styles.cardHead}>
            <span className={styles.step}>03</span>
            <div>
              <h2>Coste unitario</h2>
              <p>Reparte un coste total entre kilos producidos y, si quieres, entre hectáreas.</p>
            </div>
          </div>

          <div className={styles.fields}>
            <label>
              <span>Coste total (€)</span>
              <input value={totalCost} onChange={(event) => setTotalCost(event.target.value)} inputMode="decimal" placeholder="Ej. 1200" />
            </label>
            <label>
              <span>Producción (kg)</span>
              <input value={productionKg} onChange={(event) => setProductionKg(event.target.value)} inputMode="decimal" placeholder="Ej. 4000" />
            </label>
            <label className={styles.fullField}>
              <span>Superficie (ha) · opcional</span>
              <input value={costArea} onChange={(event) => setCostArea(event.target.value)} inputMode="decimal" placeholder="Ej. 2" />
            </label>
          </div>

          <div className={styles.result} data-testid="cost-result" aria-live="polite">
            {costs ? (
              <>
                <strong>{formatMoney(costs.costPerKg)}/kg</strong>
                {costs.costPerHectare !== null && <span>{formatMoney(costs.costPerHectare)}/ha</span>}
              </>
            ) : (
              <span>Introduce coste total y kilos, ambos mayores que cero.</span>
            )}
          </div>
          <p className={styles.caution}>El resultado solo reparte los datos introducidos. No es un cálculo de beneficio ni una previsión de liquidación.</p>
        </article>
      </section>
    </div>
  );
}
