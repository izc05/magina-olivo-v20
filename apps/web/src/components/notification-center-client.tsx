'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import {
  emptyNotificationHistory,
  loadNotificationHistory,
  type NotificationDeliveryStatus,
  type NotificationHistory,
  type NotificationHistoryItem,
} from '@/lib/notification-center-data-source';

type Category = 'all' | 'field' | 'financial' | 'documents' | 'commercial' | 'system';
type StatusFilter = 'all' | NotificationDeliveryStatus;

const categoryOptions: Array<{ value: Category; label: string }> = [
  { value: 'all', label: 'Todos' },
  { value: 'field', label: 'Campo y clima' },
  { value: 'financial', label: 'Economía' },
  { value: 'documents', label: 'Documentos' },
  { value: 'commercial', label: 'Profesional' },
  { value: 'system', label: 'Sistema' },
];

const statusOptions: Array<{ value: StatusFilter; label: string }> = [
  { value: 'all', label: 'Todos los estados' },
  { value: 'dispatched', label: 'Enviados' },
  { value: 'pending', label: 'Pendientes de envío' },
  { value: 'failed', label: 'Con fallo de envío' },
  { value: 'suppressed', label: 'No enviados' },
];

function categoryFor(item: NotificationHistoryItem): Exclude<Category, 'all'> {
  if (item.kind.startsWith('agronomy_') || item.kind.startsWith('radar_')) return 'field';
  if (item.kind.startsWith('financial_')) return 'financial';
  if (item.kind.startsWith('document_')) return 'documents';
  if (item.kind.startsWith('commercial_')) return 'commercial';
  return 'system';
}

function categoryLabel(item: NotificationHistoryItem) {
  const category = categoryFor(item);
  return categoryOptions.find((option) => option.value === category)?.label ?? 'Sistema';
}

function statusLabel(status: NotificationDeliveryStatus) {
  if (status === 'dispatched') return 'Enviado';
  if (status === 'pending') return 'Pendiente';
  if (status === 'failed') return 'Fallo de envío';
  return 'No enviado';
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function NotificationCenterClient() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
  const [history, setHistory] = useState<NotificationHistory>(emptyNotificationHistory());
  const [category, setCategory] = useState<Category>('all');
  const [deliveryStatus, setDeliveryStatus] = useState<StatusFilter>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const data = await loadNotificationHistory(selectedWorkspaceId);
          if (!cancelled) setHistory(data);
        } else if (previewEnabled) {
          if (!cancelled) setHistory(emptyNotificationHistory());
        } else if (apiConfigured && status === 'loading') {
          return;
        } else if (!cancelled) {
          setHistory(emptyNotificationHistory());
          setError(apiConfigured ? 'Inicia sesión para consultar tus avisos.' : 'El historial de avisos no está disponible en esta instalación.');
        }
      } catch (err) {
        console.error(err);
        if (!cancelled) setError('No se ha podido cargar el historial de avisos.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, previewEnabled, revision, selectedWorkspaceId, status]);

  const visibleItems = useMemo(() => history.items.filter((item) => {
    if (category !== 'all' && categoryFor(item) !== category) return false;
    if (deliveryStatus !== 'all' && item.status !== deliveryStatus) return false;
    return true;
  }), [category, deliveryStatus, history.items]);

  return <>
    <header className="page-title mi-campo-title">
      <div>
        <span className="eyebrow dark">MI CAMPO · AVISOS</span>
        <h1>Centro de avisos</h1>
        <p>Un solo sitio para revisar los avisos que Mágina ha generado sobre tu campo, documentos, cobros y actividad profesional.</p>
      </div>
    </header>

    <section className="card field-summary campaign-summary">
      <div className="stats">
        <div className="stat"><b>{history.counts.total}</b><span>avisos</span></div>
        <div className="stat"><b>{history.counts.dispatched}</b><span>enviados</span></div>
        <div className="stat"><b>{history.counts.pending + history.counts.failed}</b><span>por revisar</span></div>
      </div>
    </section>

    <section className="card">
      <strong>Historial de entrega</strong>
      <p>“Enviado” significa que Mágina entregó el aviso al canal configurado; no significa que lo hayas leído.</p>
      <div className="record-actions">
        <label>
          <span className="subtle">Tipo</span>
          <select aria-label="Filtrar avisos por tipo" value={category} onChange={(event) => setCategory(event.target.value as Category)}>
            {categoryOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label>
          <span className="subtle">Estado</span>
          <select aria-label="Filtrar avisos por estado" value={deliveryStatus} onChange={(event) => setDeliveryStatus(event.target.value as StatusFilter)}>
            {statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button className="secondary-action" type="button" onClick={() => setRevision((value) => value + 1)}>Actualizar</button>
      </div>
    </section>

    {previewEnabled && !apiConfigured ? <section className="card"><strong>Modo demostración</strong><p>La demo pública no carga avisos privados. Tu historial aparecerá aquí cuando uses Mágina con tu cuenta conectada.</p></section> : null}
    {loading ? <section className="card"><p>Cargando avisos…</p></section> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}

    {!loading && !error ? <section className="section">
      <div className="section-head"><h2>Avisos recientes</h2><span className="subtle">{visibleItems.length}</span></div>
      {visibleItems.length ? <div className="card feed">
        {visibleItems.map((item) => <article className="feed-row" key={item.id}>
          <div className="feed-copy">
            <strong>{item.title}</strong>
            <small>{categoryLabel(item)} · {item.fieldName ?? 'General'} · {formatDate(item.createdAt)}</small>
            <p>{item.body}</p>
            {item.actionPath ? <div className="record-actions"><Link className="primary action-link" href={item.actionPath}>Abrir información →</Link></div> : null}
          </div>
          <span className="pending-pill">{statusLabel(item.status)}</span>
        </article>)}
      </div> : <section className="card">
        <strong>No hay avisos con estos filtros</strong>
        <p>{history.items.length ? 'Prueba con otro tipo o estado.' : 'Cuando Mágina genere un aviso para tu cuenta, aparecerá aquí con su fecha y contexto.'}</p>
      </section>}
    </section> : null}

    <section className="territory-banner compact-banner">
      <div><span className="eyebrow">AVISOS CON CONTEXTO</span><h2>Consulta el motivo y vuelve a la tarea, finca o documento relacionado.</h2></div>
      <Link href="/perfil">Configurar avisos</Link>
    </section>
  </>;
}
