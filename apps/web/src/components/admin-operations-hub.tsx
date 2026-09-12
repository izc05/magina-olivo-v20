'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '../lib/api-client';
import {
  adminApi,
  type AdminDatasetDescriptor,
  type AdminDatasetId,
  type AdminDatasetResponse,
  type AdminExternalAppConfig,
  type AdminOperationsSnapshot,
  type AdminSession,
  type AdminSourcesSnapshot,
} from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-operations-hub.module.css';

function number(value: number, maximumFractionDigits = 0) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits }).format(value);
}

function euro(value: number) {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 }).format(value);
}

function bytes(value: number) {
  if (value < 1024) return `${number(value)} B`;
  if (value < 1024 ** 2) return `${number(value / 1024, 1)} KB`;
  if (value < 1024 ** 3) return `${number(value / 1024 ** 2, 1)} MB`;
  return `${number(value / 1024 ** 3, 1)} GB`;
}

function cell(value: unknown) {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Sí' : 'No';
  if (typeof value === 'number') return number(value, 2);
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function canConfigure(session: AdminSession | null) {
  return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <article className={styles.metricCard}>
      <span>{label}</span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </article>
  );
}

function DatasetExplorer({ datasets }: { datasets: AdminDatasetDescriptor[] }) {
  const [selected, setSelected] = useState<AdminDatasetId>('fields');
  const [result, setResult] = useState<AdminDatasetResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (dataset = selected) => {
    setSelected(dataset);
    setLoading(true);
    setError(null);
    try {
      setResult(await adminApi.dataset(dataset, 50));
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido abrir este conjunto de datos.');
    } finally {
      setLoading(false);
    }
  }, [selected]);

  const columns = useMemo(() => {
    const rows = result?.rows ?? [];
    const keys = new Set<string>();
    rows.slice(0, 10).forEach((row) => Object.keys(row).forEach((key) => keys.add(key)));
    return Array.from(keys);
  }, [result]);

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.eyebrow}>Explorador seguro</span>
          <h2>Datos de la plataforma</h2>
          <p>Lectura operativa de datos clave. Se excluyen secretos, tokens, binarios y texto OCR bruto.</p>
        </div>
        <button className={styles.secondaryButton} type="button" disabled={loading} onClick={() => void load()}>
          {loading ? 'Cargando…' : 'Actualizar'}
        </button>
      </div>

      <div className={styles.datasetTabs} role="list" aria-label="Conjuntos de datos">
        {datasets.map((dataset) => (
          <button
            key={dataset.id}
            type="button"
            className={selected === dataset.id ? styles.datasetActive : styles.datasetButton}
            onClick={() => void load(dataset.id)}
          >
            <span>{dataset.label}</span>
            <small>{dataset.count === null ? 'ver' : number(dataset.count)}</small>
          </button>
        ))}
      </div>

      {error ? <p className={styles.error}>{error}</p> : null}
      {!result && !loading ? <div className={styles.empty}>Selecciona un conjunto para inspeccionar sus 50 registros más recientes.</div> : null}
      {result ? (
        <div className={styles.tableWrap}>
          <table className={styles.dataTable}>
            <thead><tr>{columns.map((column) => <th key={column}>{column.replaceAll('_', ' ')}</th>)}</tr></thead>
            <tbody>
              {result.rows.map((row, rowIndex) => (
                <tr key={`${result.dataset}-${rowIndex}`}>
                  {columns.map((column) => <td key={column} title={cell(row[column])}>{cell(row[column])}</td>)}
                </tr>
              ))}
              {!result.rows.length ? <tr><td colSpan={Math.max(columns.length, 1)}>Sin registros.</td></tr> : null}
            </tbody>
          </table>
        </div>
      ) : null}
      {result ? <small className={styles.caption}>Vista de solo lectura · {result.rows.length} registros · {new Date(result.generated_at).toLocaleString('es-ES')}</small> : null}
    </section>
  );
}

function ExternalAppPanel({ session, initialConfig, onSaved }: {
  session: AdminSession;
  initialConfig: AdminExternalAppConfig;
  onSaved: (config: AdminExternalAppConfig) => void;
}) {
  const [config, setConfig] = useState(initialConfig);
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const editable = canConfigure(session);

  useEffect(() => setConfig(initialConfig), [initialConfig]);

  async function save() {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const result = await adminApi.saveExternalApp(config);
      setConfig(result.config);
      onSaved(result.config);
      setMessage('Configuración guardada y auditada.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar. Revisa la URL y tus permisos.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.eyebrow}>App Gateway</span>
          <h2>Aplicación integrada / externa</h2>
          <p>Da acceso a otra app sin mezclar su código con el Admin. Puede abrirse aparte o mostrarse dentro del panel si el destino permite iframe.</p>
        </div>
        <span className={config.enabled ? styles.statusOk : styles.statusMuted}>{config.enabled ? 'Activa' : 'Desactivada'}</span>
      </div>

      <div className={styles.formGrid}>
        <label>
          Nombre
          <input disabled={!editable} value={config.name} onChange={(event) => setConfig({ ...config, name: event.target.value })} />
        </label>
        <label>
          URL o ruta
          <input disabled={!editable} value={config.url} onChange={(event) => setConfig({ ...config, url: event.target.value })} placeholder="https://app.ejemplo.es o /mi-campo" />
        </label>
        <label className={styles.fullField}>
          Descripción
          <textarea disabled={!editable} value={config.description ?? ''} onChange={(event) => setConfig({ ...config, description: event.target.value || null })} rows={2} />
        </label>
        <label>
          Modo de acceso
          <select disabled={!editable} value={config.mode} onChange={(event) => setConfig({ ...config, mode: event.target.value as AdminExternalAppConfig['mode'] })}>
            <option value="new_tab">Abrir en pestaña nueva</option>
            <option value="embedded">Integrar en el Admin</option>
          </select>
        </label>
        <label>
          Health URL opcional
          <input disabled={!editable} value={config.health_url ?? ''} onChange={(event) => setConfig({ ...config, health_url: event.target.value || null })} placeholder="https://app.ejemplo.es/health" />
        </label>
        <label className={styles.toggleField}>
          <input type="checkbox" disabled={!editable} checked={config.enabled} onChange={(event) => setConfig({ ...config, enabled: event.target.checked })} />
          Habilitar acceso desde Administración
        </label>
      </div>

      <div className={styles.actions}>
        {editable ? <button className={styles.primaryButton} type="button" disabled={saving || !config.name.trim() || !config.url.trim()} onClick={() => void save()}>{saving ? 'Guardando…' : 'Guardar configuración'}</button> : null}
        {config.enabled && config.url ? <a className={styles.secondaryButton} href={config.url} target="_blank" rel="noreferrer">Abrir aplicación</a> : null}
        {config.enabled && config.mode === 'embedded' ? <button className={styles.secondaryButton} type="button" onClick={() => setPreviewOpen((current) => !current)}>{previewOpen ? 'Cerrar vista integrada' : 'Probar vista integrada'}</button> : null}
      </div>
      {message ? <p className={styles.success}>{message}</p> : null}
      {error ? <p className={styles.error}>{error}</p> : null}
      {!editable ? <p className={styles.note}>Solo Administrador y Superadministrador pueden cambiar este acceso.</p> : null}
      {config.mode === 'embedded' ? <p className={styles.note}>Si la app externa bloquea iframes con CSP/X-Frame-Options, seguirá funcionando mediante “Abrir aplicación”. Para una integración completa podremos publicarla bajo el mismo dominio mediante reverse proxy.</p> : null}

      {previewOpen && config.enabled && config.mode === 'embedded' ? (
        <div className={styles.iframeShell}>
          <div className={styles.iframeBar}><strong>{config.name}</strong><span>{config.url}</span></div>
          <iframe title={`Vista integrada de ${config.name}`} src={config.url} sandbox="allow-forms allow-modals allow-popups allow-same-origin allow-scripts" referrerPolicy="strict-origin-when-cross-origin" />
        </div>
      ) : null}
    </section>
  );
}

export function AdminOperationsHub() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [snapshot, setSnapshot] = useState<AdminOperationsSnapshot | null>(null);
  const [sources, setSources] = useState<AdminSourcesSnapshot | null>(null);
  const [denied, setDenied] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    setDenied(false);
    try {
      const [adminSession, operations, sourceSnapshot] = await Promise.all([
        adminApi.session(),
        adminApi.operations(),
        adminApi.sources(),
      ]);
      setSession(adminSession);
      setSnapshot(operations);
      setSources(sourceSnapshot);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
        return;
      }
      console.error(caught);
      setError('No se ha podido cargar el centro operativo.');
    } finally {
      setLoading(false);
    }
  }, [auth.status]);

  useEffect(() => {
    if (auth.status === 'authenticated') void load();
    if (auth.status === 'anonymous') setLoading(false);
  }, [auth.status, load]);

  if (auth.status === 'loading' || loading) {
    return <main className={styles.gate}><div className={styles.gateCard}><strong>Cargando centro operativo…</strong></div></main>;
  }

  if (auth.status === 'anonymous') {
    return (
      <main className={styles.gate}>
        <div className={styles.gateCard}>
          <span className={styles.eyebrow}>Administración V20</span>
          <h1>Centro operativo protegido</h1>
          <p>Inicia sesión con una cuenta corporativa autorizada.</p>
          <GoogleSignInButton />
        </div>
      </main>
    );
  }

  if (denied) {
    return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><p>Tu cuenta tiene sesión válida, pero no permiso de plataforma.</p><Link href="/">Volver a la web</Link></div></main>;
  }

  if (!session || !snapshot) {
    return <main className={styles.gate}><div className={styles.gateCard}><h1>No se ha podido abrir Administración</h1><p>{error ?? 'Faltan datos de sesión.'}</p><button className={styles.secondaryButton} onClick={() => void load()}>Reintentar</button></div></main>;
  }

  const metrics = snapshot.metrics;
  const sourceProblems = sources?.sources.filter((source) => source.state === 'error' || source.state === 'attention').length ?? 0;

  return (
    <main className={styles.shell}>
      <header className={styles.hero}>
        <div>
          <span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span>
          <h1>Centro operativo</h1>
          <p>Datos, métricas, contenido, fuentes, usuarios, configuración y acceso a aplicaciones desde un único lugar.</p>
        </div>
        <div className={styles.heroActions}>
          <span className={styles.role}>{session.platform_access.role.replace('_', ' ')}</span>
          <button className={styles.secondaryButton} type="button" onClick={() => void load()}>Actualizar todo</button>
          <Link className={styles.secondaryButton} href="/admin">Admin clásico</Link>
        </div>
      </header>

      <nav className={styles.quickNav} aria-label="Áreas de administración">
        <Link href="/admin/web"><strong>Web y contenido</strong><span>Portada, noticias, eventos, SEO y publicidad</span></Link>
        <Link href="/admin/media"><strong>Multimedia</strong><span>Imágenes y recursos corporativos</span></Link>
        <Link href="/admin/territorio"><strong>Territorio</strong><span>Pueblos, municipios y directorio</span></Link>
        <Link href="/admin/fuentes"><strong>Fuentes y datos</strong><span>AEMET, radar, OCR, Catastro y SIGPAC</span></Link>
      </nav>

      <section className={styles.metricGrid} aria-label="Métricas de plataforma">
        <MetricCard label="Usuarios activos" value={number(metrics.users_active)} detail={`${number(metrics.users_new_30d)} nuevos en 30 días`} />
        <MetricCard label="Fincas activas" value={number(metrics.fields_active)} detail={`${number(metrics.field_area_ha, 2)} ha registradas`} />
        <MetricCard label="Campañas activas" value={number(metrics.campaigns_active)} detail={`${number(metrics.scheduled_open)} tareas abiertas`} />
        <MetricCard label="Cosecha registrada" value={`${number(metrics.harvest_kg, 1)} kg`} />
        <MetricCard label="Documentos" value={number(metrics.documents_active)} detail={bytes(metrics.document_bytes)} />
        <MetricCard label="OCR pendiente" value={number(metrics.ocr_pending)} detail={`${number(metrics.ocr_failed)} fallidos`} />
        <MetricCard label="Facturas emitidas" value={number(metrics.invoices_issued)} detail={euro(metrics.invoiced_eur)} />
        <MetricCard label="Presupuestos" value={number(metrics.quotes_open)} detail={`${number(metrics.quotes_accepted)} aceptados`} />
        <MetricCard label="Contenido publicado" value={number(metrics.content_published)} detail={`${number(metrics.content_draft)} borradores`} />
        <MetricCard label="Mercado validado" value={number(metrics.market_observations)} detail={metrics.market_latest_period ? `hasta ${metrics.market_latest_period}` : 'sin período'} />
        <MetricCard label="Fuentes con atención" value={number(sourceProblems)} detail={`${number(metrics.weather_stale)} cachés de clima caducadas`} />
        <MetricCard label="Acciones Admin 24 h" value={number(metrics.admin_actions_24h)} detail={`${number(metrics.users_suspended)} usuarios suspendidos`} />
      </section>

      {sources ? (
        <section className={styles.panel}>
          <div className={styles.panelHeading}>
            <div><span className={styles.eyebrow}>Telemetría</span><h2>Estado de fuentes</h2><p>Visión compacta del estado real de integraciones y pipelines.</p></div>
            <Link className={styles.secondaryButton} href="/admin/fuentes">Ver detalle</Link>
          </div>
          <div className={styles.sourceGrid}>
            {sources.sources.map((source) => (
              <article key={source.id}>
                <div><strong>{source.name}</strong><small>{source.provider}</small></div>
                <span className={source.state === 'ok' ? styles.statusOk : source.state === 'error' ? styles.statusError : styles.statusWarn}>{source.state}</span>
                <p>{source.state_reason}</p>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <DatasetExplorer datasets={snapshot.datasets} />
      <ExternalAppPanel session={session} initialConfig={snapshot.external_app} onSaved={(external_app) => setSnapshot({ ...snapshot, external_app })} />

      <section className={styles.panel}>
        <div className={styles.panelHeading}><div><span className={styles.eyebrow}>Gobierno</span><h2>Qué queda bajo control del Admin</h2></div></div>
        <div className={styles.controlGrid}>
          <div><strong>Experiencia pública</strong><span>CMS, Inicio, SEO, promociones, avisos, medios y territorio.</span></div>
          <div><strong>Plataforma</strong><span>Usuarios, roles, workspaces, fincas, campañas, documentos y actividad.</span></div>
          <div><strong>Negocio</strong><span>Presupuestos, facturas, mercado y métricas agregadas.</span></div>
          <div><strong>Operación</strong><span>Fuentes, OCR, clima, auditoría, configuración y aplicación externa.</span></div>
        </div>
        <p className={styles.note}>El centro permite consultar transversalmente la plataforma, pero no muestra claves, tokens, credenciales ni contenido binario. Las modificaciones sensibles siguen APIs tipadas y quedan auditadas.</p>
      </section>
    </main>
  );
}
