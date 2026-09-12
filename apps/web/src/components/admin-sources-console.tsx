'use client';

import { useCallback, useEffect, useState } from 'react';
import { ApiRequestError } from '../lib/api-client';
import { adminApi, type AdminSession, type AdminSourceState, type AdminSourcesSnapshot } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

const stateLabels: Record<AdminSourceState, string> = {
  ok: 'Correcto',
  attention: 'Revisar',
  error: 'Error',
  unknown: 'Sin datos',
  unmonitored: 'Sin telemetría',
};

const telemetryLabels = {
  cache_health: 'Caché y errores persistidos',
  pipeline_status: 'Estado del pipeline',
  usage_only: 'Solo uso y verificaciones',
} as const;

const metricLabels: Record<string, string> = {
  enabled_municipalities: 'Municipios habilitados',
  cache_entries: 'Previsiones en caché',
  expired_entries: 'Cachés caducadas',
  snapshots: 'Snapshots',
  failed_snapshots: 'Snapshots fallidos',
  analysis_ready_snapshots: 'Listos para análisis',
  latest_analysis_ready: 'Último listo para análisis',
  runs: 'Ejecuciones totales',
  runs_7d: 'Ejecuciones · 7 días',
  succeeded_7d: 'Correctas · 7 días',
  failed_7d: 'Fallidas · 7 días',
  queued_7d: 'En cola · 7 días',
  processing_7d: 'Procesando · 7 días',
  references: 'Referencias',
  verified_references: 'Verificadas',
  linked_references: 'Vinculadas',
};

const timestampLabels: Record<string, string> = {
  last_success_at: 'Última descarga correcta',
  last_error_at: 'Último error',
  last_observed_at: 'Última observación',
  last_fetched_at: 'Última captura',
  last_completed_at: 'Última finalización',
  last_checked_at: 'Última comprobación usada',
};

function formatDate(value: string | null) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function AdminSourcesConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [snapshot, setSnapshot] = useState<AdminSourcesSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    setDenied(false);
    try {
      const [adminSession, sources] = await Promise.all([adminApi.session(), adminApi.sources()]);
      setSession(adminSession);
      setSnapshot(sources);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
        setSnapshot(null);
      } else {
        console.error(caught);
        setError('No se ha podido cargar la telemetría administrativa.');
      }
    } finally {
      setLoading(false);
    }
  }, [auth.status]);

  useEffect(() => {
    if (auth.status === 'authenticated') void load();
    if (auth.status === 'anonymous') {
      setSession(null);
      setSnapshot(null);
      setDenied(false);
    }
  }, [auth.status, load]);

  if (auth.status === 'loading') {
    return <main className="admin-gate"><div className="admin-gate-card"><strong>Comprobando sesión…</strong></div></main>;
  }

  if (auth.status === 'anonymous') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Mágina Olivo · Fuentes y datos</span>
          <h1>Acceso corporativo</h1>
          <p>Esta superficie usa los mismos permisos de plataforma que el resto de Administración.</p>
          <GoogleSignInButton />
        </div>
      </main>
    );
  }

  if (denied) {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Acceso restringido</span>
          <h1>Sin acceso a Fuentes y datos</h1>
          <p>Tu sesión es válida, pero esta cuenta no tiene un rol corporativo activo.</p>
          <a className="admin-button secondary" href="/">Volver a la web</a>
        </div>
      </main>
    );
  }

  return (
    <main className="sources-shell">
      <header className="sources-header">
        <div>
          <span className="admin-eyebrow">Mágina Olivo V20 · Administración</span>
          <h1>Fuentes y datos</h1>
          <p>Estado observado desde datos persistidos. No se muestran proveedores como “operativos” si Mágina no guarda telemetría suficiente para afirmarlo.</p>
        </div>
        <div className="sources-actions">
          <a className="admin-button secondary" href="/admin">Centro de control</a>
          <button className="admin-button" type="button" disabled={loading} onClick={() => void load()}>{loading ? 'Actualizando…' : 'Actualizar'}</button>
        </div>
      </header>

      {session ? <div className="sources-session">{session.user.primary_email} · {session.platform_access.role}</div> : null}
      {error ? <div className="admin-notice error">{error}</div> : null}

      <section className="sources-legend" aria-label="Cómo interpretar la telemetría">
        <article><strong>Salud de caché</strong><span>Éxitos, errores y caducidad que sí quedan registrados.</span></article>
        <article><strong>Pipeline</strong><span>Estado de la última ejecución interna; no equivale a disponibilidad continua del proveedor.</span></article>
        <article><strong>Solo uso</strong><span>Tenemos referencias/comprobaciones, pero no monitorización del proveedor.</span></article>
      </section>

      {snapshot ? (
        <>
          <div className="sources-generated">Lectura generada: {formatDate(snapshot.generated_at)}</div>
          <section className="sources-grid">
            {snapshot.sources.map((source) => (
              <article className={`source-card state-${source.state}`} key={source.id}>
                <div className="source-card-head">
                  <div><span className="source-provider">{source.provider}</span><h2>{source.name}</h2></div>
                  <span className={`source-state state-${source.state}`}>{stateLabels[source.state]}</span>
                </div>
                <p className="source-reason">{source.state_reason}</p>
                <div className="source-mode">{telemetryLabels[source.telemetry]}</div>

                <dl className="source-metrics">
                  {Object.entries(source.metrics).map(([key, value]) => (
                    <div key={key}><dt>{metricLabels[key] ?? key}</dt><dd>{key === 'latest_analysis_ready' ? (value ? 'Sí' : 'No') : value}</dd></div>
                  ))}
                </dl>

                <dl className="source-timestamps">
                  {Object.entries(source.timestamps).map(([key, value]) => (
                    <div key={key}><dt>{timestampLabels[key] ?? key}</dt><dd>{formatDate(value)}</dd></div>
                  ))}
                </dl>
                {source.last_error_code ? <div className="source-error-code"><span>Último código de error</span><code>{source.last_error_code}</code></div> : null}
              </article>
            ))}
          </section>
        </>
      ) : loading ? <div className="sources-loading">Cargando fuentes…</div> : null}
    </main>
  );
}
