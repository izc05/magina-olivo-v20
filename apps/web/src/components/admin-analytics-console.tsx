'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import { adminAnalyticsApi, type AdminAnalyticsPoint, type AdminAnalyticsSnapshot } from '../lib/admin-analytics';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-analytics-console.module.css';

type MetricKey = Exclude<keyof AdminAnalyticsPoint, 'day'>;

const metrics: Array<{ key: MetricKey; label: string; kind: 'number' | 'kg' | 'eur' }> = [
  { key: 'users_new', label: 'Usuarios nuevos', kind: 'number' },
  { key: 'workspaces_new', label: 'Workspaces nuevos', kind: 'number' },
  { key: 'works', label: 'Trabajos', kind: 'number' },
  { key: 'harvest_kg', label: 'Cosecha', kind: 'kg' },
  { key: 'expenses_eur', label: 'Gastos', kind: 'eur' },
  { key: 'invoiced_eur', label: 'Facturado', kind: 'eur' },
  { key: 'admin_actions', label: 'Acciones Admin', kind: 'number' },
];

function formatNumber(value: number, maximumFractionDigits = 0) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits }).format(value);
}

function formatMetric(value: number, kind: 'number' | 'kg' | 'eur') {
  if (kind === 'eur') return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 }).format(value);
  if (kind === 'kg') return `${formatNumber(value, 1)} kg`;
  return formatNumber(value);
}

function canExport(session: AdminSession | null) {
  return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';
}

export function AdminAnalyticsConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [days, setDays] = useState<30 | 90>(30);
  const [metric, setMetric] = useState<MetricKey>('works');
  const [snapshot, setSnapshot] = useState<AdminAnalyticsSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (windowDays: 30 | 90 = days) => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    setDenied(false);
    try {
      const [adminSession, analytics] = await Promise.all([
        adminApi.session(),
        adminAnalyticsApi.timeseries(windowDays),
      ]);
      setSession(adminSession);
      setSnapshot(analytics);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
        setSnapshot(null);
      } else {
        console.error(caught);
        setError('No se ha podido cargar la analítica administrativa.');
      }
    } finally {
      setLoading(false);
    }
  }, [auth.status, days]);

  useEffect(() => {
    if (auth.status === 'authenticated') void load(days);
    if (auth.status === 'anonymous') {
      setSession(null);
      setSnapshot(null);
      setDenied(false);
    }
  }, [auth.status, days, load]);

  const selectedMetric = metrics.find((item) => item.key === metric) ?? metrics[0];
  const maxValue = useMemo(() => Math.max(1, ...(snapshot?.points.map((point) => Number(point[metric])) ?? [1])), [snapshot, metric]);

  async function exportCsv() {
    setExporting(true);
    setError(null);
    try {
      const blob = await adminAnalyticsApi.exportCsv(days);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `magina-admin-analytics-${days}d.csv`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (caught) {
      console.error(caught);
      setError(caught instanceof ApiRequestError && caught.status === 403
        ? 'La exportación requiere rol Admin o Super Admin.'
        : 'No se ha podido generar el CSV.');
    } finally {
      setExporting(false);
    }
  }

  if (auth.status === 'loading') return <main className={styles.gate}><div className={styles.gateCard}><strong>Comprobando sesión…</strong></div></main>;
  if (auth.status === 'anonymous') {
    return <main className={styles.gate}><div className={styles.gateCard}><span>Administración V20</span><h1>Analítica protegida</h1><p>Inicia sesión con una cuenta corporativa autorizada.</p><GoogleSignInButton /></div></main>;
  }
  if (denied) return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><p>Tu cuenta no tiene acceso de plataforma.</p><a href="/admin">Volver a Administración</a></div></main>;

  return (
    <main className={styles.shell}>
      <header className={styles.hero}>
        <div>
          <span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span>
          <h1>Analítica de plataforma</h1>
          <p>Evolución operativa agregada. Sin nombres, emails, texto OCR ni datos personales en las series o exportaciones.</p>
        </div>
        <div className={styles.actions}>
          <a className={styles.secondaryButton} href="/admin/operaciones">Centro operativo</a>
          <button className={styles.secondaryButton} type="button" disabled={loading} onClick={() => void load(days)}>{loading ? 'Actualizando…' : 'Actualizar'}</button>
          {canExport(session) ? <button className={styles.primaryButton} type="button" disabled={exporting || !snapshot} onClick={() => void exportCsv()}>{exporting ? 'Generando…' : 'Exportar CSV'}</button> : null}
        </div>
      </header>

      <section className={styles.toolbar} aria-label="Período y métrica">
        <div className={styles.periodButtons}>
          {[30, 90].map((value) => <button key={value} type="button" className={days === value ? styles.activeButton : styles.periodButton} onClick={() => setDays(value as 30 | 90)}>{value} días</button>)}
        </div>
        <label>Métrica
          <select value={metric} onChange={(event) => setMetric(event.target.value as MetricKey)}>
            {metrics.map((item) => <option key={item.key} value={item.key}>{item.label}</option>)}
          </select>
        </label>
        {session && !canExport(session) ? <span className={styles.readonly}>Lectura disponible · exportación reservada a Admin+</span> : null}
      </section>

      {error ? <div className={styles.error}>{error}</div> : null}

      {snapshot ? (
        <>
          <section className={styles.summaryGrid} aria-label={`Resumen últimos ${days} días`}>
            <article><span>Usuarios nuevos</span><strong>{formatNumber(snapshot.summary.users_new)}</strong></article>
            <article><span>Workspaces nuevos</span><strong>{formatNumber(snapshot.summary.workspaces_new)}</strong></article>
            <article><span>Trabajos</span><strong>{formatNumber(snapshot.summary.works)}</strong></article>
            <article><span>Cosecha</span><strong>{formatMetric(snapshot.summary.harvest_kg, 'kg')}</strong></article>
            <article><span>Gastos</span><strong>{formatMetric(snapshot.summary.expenses_eur, 'eur')}</strong></article>
            <article><span>Facturado</span><strong>{formatMetric(snapshot.summary.invoiced_eur, 'eur')}</strong></article>
            <article><span>Acciones Admin</span><strong>{formatNumber(snapshot.summary.admin_actions)}</strong></article>
          </section>

          <section className={styles.chartPanel}>
            <div className={styles.chartHeading}>
              <div><span className={styles.eyebrow}>Serie diaria</span><h2>{selectedMetric.label}</h2></div>
              <strong>{formatMetric(snapshot.summary[metric], selectedMetric.kind)}</strong>
            </div>
            <div className={styles.chartScroll}>
              <div className={styles.chart} style={{ minWidth: `${Math.max(620, snapshot.points.length * 18)}px` }}>
                {snapshot.points.map((point) => {
                  const value = Number(point[metric]);
                  const height = value <= 0 ? 2 : Math.max(6, (value / maxValue) * 100);
                  return (
                    <div className={styles.barSlot} key={point.day} title={`${new Date(`${point.day}T00:00:00`).toLocaleDateString('es-ES')}: ${formatMetric(value, selectedMetric.kind)}`}>
                      <div className={styles.bar} style={{ height: `${height}%` }} />
                    </div>
                  );
                })}
              </div>
            </div>
            <div className={styles.axis}><span>{snapshot.points[0]?.day ? new Date(`${snapshot.points[0].day}T00:00:00`).toLocaleDateString('es-ES') : '—'}</span><span>{snapshot.points.at(-1)?.day ? new Date(`${snapshot.points.at(-1)!.day}T00:00:00`).toLocaleDateString('es-ES') : '—'}</span></div>
            <p className={styles.note}>El CSV contiene únicamente fecha y métricas agregadas. Cada exportación queda registrada en la auditoría Admin.</p>
          </section>
        </>
      ) : loading ? <div className={styles.loading}>Cargando serie histórica…</div> : null}
    </main>
  );
}
