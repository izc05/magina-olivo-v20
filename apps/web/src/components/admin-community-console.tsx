'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import styles from './admin-community-console.module.css';

type ReportStatus = 'open' | 'reviewed' | 'dismissed' | 'actioned';
type ReportItem = {
  id: string;
  target_type: 'post' | 'comment';
  target_id: string;
  reason: string;
  details: string | null;
  status: ReportStatus;
  created_at: string;
  reporter_name: string;
  target_excerpt: string;
  target_status: string;
};

const statusLabels: Record<ReportStatus, string> = {
  open: 'Abiertos',
  reviewed: 'Revisados',
  dismissed: 'Descartados',
  actioned: 'Con acción',
};

const reasonLabels: Record<string, string> = {
  spam: 'Spam',
  abuse: 'Falta de respeto',
  privacy: 'Privacidad',
  dangerous: 'Contenido peligroso',
  misinformation: 'Información dudosa',
  other: 'Otro motivo',
};

function canModerate(session: AdminSession | null) {
  const role = session?.platform_access.role;
  return role === 'editor' || role === 'admin' || role === 'super_admin';
}

function when(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-ES', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date);
}

export function AdminCommunityConsole() {
  const [session, setSession] = useState<AdminSession | null>(null);
  const [status, setStatus] = useState<ReportStatus>('open');
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [reason, setReason] = useState('Contenido revisado por moderación.');

  const selected = useMemo(() => reports.find((item) => item.id === selectedId) ?? null, [reports, selectedId]);
  const editable = canModerate(session);

  const load = useCallback(async (nextStatus: ReportStatus = status) => {
    setLoading(true);
    setError(null);
    try {
      const [current, payload] = await Promise.all([
        adminApi.session(),
        apiFetch<{ items: ReportItem[]; status: ReportStatus }>(`/api/v1/admin/community/reports?status=${nextStatus}&limit=100`),
      ]);
      setSession(current);
      setReports(payload.items);
      setSelectedId((currentId) => currentId && payload.items.some((item) => item.id === currentId)
        ? currentId
        : payload.items[0]?.id ?? null);
    } catch (caught) {
      console.error(caught);
      if (caught instanceof ApiRequestError && caught.status === 403) setError('Tu rol no tiene acceso a la moderación de Comunidad Mágina.');
      else setError('No se ha podido cargar la cola de moderación.');
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => { void load(); }, [load]);

  async function changeStatus(next: ReportStatus) {
    setStatus(next);
    await load(next);
  }

  async function review(next: Exclude<ReportStatus, 'open'>) {
    if (!selected || busy) return;
    setBusy(true); setError(null); setMessage(null);
    try {
      await apiFetch(`/api/v1/admin/community/reports/${selected.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: next }),
      });
      setMessage(next === 'dismissed' ? 'Reporte descartado y auditado.' : 'Reporte revisado y auditado.');
      await load(status);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido actualizar el reporte.');
    } finally {
      setBusy(false);
    }
  }

  async function moderate(action: 'hide' | 'restore' | 'delete') {
    if (!selected || !editable || busy || reason.trim().length < 3) return;
    setBusy(true); setError(null); setMessage(null);
    try {
      await apiFetch('/api/v1/admin/community/moderation', {
        method: 'POST',
        body: JSON.stringify({
          target_type: selected.target_type,
          target_id: selected.target_id,
          action,
          reason: reason.trim(),
          report_id: selected.id,
        }),
      });
      setMessage(action === 'restore' ? 'Contenido restaurado y acción auditada.' : action === 'delete' ? 'Contenido eliminado y acción auditada.' : 'Contenido ocultado y acción auditada.');
      await load(status);
    } catch (caught) {
      console.error(caught);
      if (caught instanceof ApiRequestError && caught.status === 403) setError('Tu rol puede revisar reportes, pero no moderar contenido.');
      else setError('No se ha podido ejecutar la acción de moderación.');
    } finally {
      setBusy(false);
    }
  }

  return <main className={styles.shell}>
    <header className={styles.hero}>
      <div>
        <span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span>
        <h1>Comunidad y moderación</h1>
        <p>Reportes de usuarios, revisión de contenido y acciones auditadas sin acceso a geometrías ni datos privados de Mi Campo.</p>
      </div>
      <div className={styles.heroLinks}><Link href="/admin/modulos">Todos los módulos</Link><Link href="/comunidad">Ver comunidad pública</Link></div>
    </header>

    {message ? <div className={styles.success} role="status">{message}</div> : null}
    {error ? <div className={styles.error} role="alert">{error}</div> : null}

    <section className={styles.statusBar} aria-label="Estado de reportes">
      {(Object.keys(statusLabels) as ReportStatus[]).map((value) => <button key={value} type="button" className={status === value ? styles.active : ''} onClick={() => void changeStatus(value)}>{statusLabels[value]}</button>)}
      <button type="button" className={styles.refresh} onClick={() => void load()} disabled={loading || busy}>Actualizar</button>
    </section>

    {loading ? <section className={styles.state}>Cargando cola de moderación…</section> : null}
    {!loading && !error && reports.length === 0 ? <section className={styles.state}><strong>La cola está limpia.</strong><span>No hay reportes con el estado “{statusLabels[status].toLowerCase()}”.</span></section> : null}

    {!loading && reports.length > 0 ? <section className={styles.layout}>
      <aside className={styles.list}>
        {reports.map((report) => <button type="button" key={report.id} className={selectedId === report.id ? styles.selected : ''} onClick={() => setSelectedId(report.id)}>
          <span className={styles.reason}>{reasonLabels[report.reason] ?? report.reason}</span>
          <strong>{report.target_type === 'post' ? 'Publicación' : 'Comentario'}</strong>
          <p>{report.target_excerpt || 'Contenido sin texto visible'}</p>
          <small>{report.reporter_name} · {when(report.created_at)}</small>
        </button>)}
      </aside>

      <article className={styles.detail}>
        {selected ? <>
          <div className={styles.detailHead}>
            <div><span className={styles.eyebrow}>REPORTE</span><h2>{reasonLabels[selected.reason] ?? selected.reason}</h2></div>
            <span className={styles.badge}>{selected.target_status}</span>
          </div>
          <dl>
            <div><dt>Tipo</dt><dd>{selected.target_type === 'post' ? 'Publicación' : 'Comentario'}</dd></div>
            <div><dt>Reportado por</dt><dd>{selected.reporter_name}</dd></div>
            <div><dt>Fecha</dt><dd>{when(selected.created_at)}</dd></div>
            {selected.details ? <div><dt>Detalle</dt><dd>{selected.details}</dd></div> : null}
          </dl>
          <div className={styles.excerpt}>{selected.target_excerpt}</div>

          {selected.status === 'open' ? <div className={styles.reviewActions}>
            <button type="button" onClick={() => void review('reviewed')} disabled={busy}>Marcar revisado</button>
            <button type="button" onClick={() => void review('dismissed')} disabled={busy}>Descartar reporte</button>
          </div> : null}

          {editable ? <div className={styles.moderation}>
            <h3>Acción sobre el contenido</h3>
            <label>Motivo de moderación<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value.slice(0, 500))}/></label>
            <div className={styles.moderationActions}>
              <button type="button" onClick={() => void moderate('hide')} disabled={busy || reason.trim().length < 3}>Ocultar</button>
              <button type="button" onClick={() => void moderate('restore')} disabled={busy || reason.trim().length < 3}>Restaurar</button>
              <button type="button" className={styles.danger} onClick={() => void moderate('delete')} disabled={busy || reason.trim().length < 3}>Eliminar</button>
            </div>
            <p>Todas estas acciones quedan registradas en la auditoría administrativa.</p>
          </div> : <div className={styles.readOnly}>Tu rol puede revisar la cola, pero las acciones sobre contenido requieren rol editor o superior.</div>}
        </> : null}
      </article>
    </section> : null}
  </main>;
}
