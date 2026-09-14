'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiFetch, apiBaseUrl } from '../lib/api-client';

type QueueItem = Record<string, unknown> & { id: string; route_name?: string; display_name?: string; moderation_status?: string };
type Queue = { reviews: QueueItem[]; media: QueueItem[]; conditions: QueueItem[]; reports: QueueItem[] };

const kindLabel = { review: 'Reseña', review_media: 'Foto', condition_report: 'Estado' } as const;

function value(item: QueueItem, key: string) {
  const candidate = item[key];
  return candidate == null ? '' : String(candidate);
}

export function RoutesCommunityAdmin() {
  const [queue, setQueue] = useState<Queue | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setQueue(await apiFetch<Queue>('/api/v1/admin/routes/community/moderation'));
      setMessage(null);
    } catch { setMessage('No se ha podido cargar la cola de moderación.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  async function moderate(kind: 'review' | 'review_media' | 'condition_report', id: string, status: 'approved' | 'rejected' | 'hidden') {
    setMessage(null);
    try {
      await apiFetch(`/api/v1/admin/routes/community/${kind}/${id}/moderate`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await refresh();
    } catch { setMessage('No se ha podido aplicar la moderación.'); }
  }

  async function resolveReport(id: string, status: 'resolved' | 'dismissed') {
    try {
      await apiFetch(`/api/v1/admin/routes/community/reports/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await refresh();
    } catch { setMessage('No se ha podido resolver la denuncia.'); }
  }

  const total = (queue?.reviews.length ?? 0) + (queue?.media.length ?? 0) + (queue?.conditions.length ?? 0) + (queue?.reports.length ?? 0);

  return <main className="admin-shell">
    <header className="admin-page-header">
      <div><p className="admin-eyebrow">Rutas · Comunidad</p><h1>Moderación</h1><p>Comentarios, fotos, estado del sendero y denuncias antes de que lleguen a la ficha pública.</p></div>
      <div className="admin-header-actions"><a className="admin-secondary-button" href="/admin/rutas">Volver a Rutas</a><button className="admin-primary-button" onClick={() => void refresh()}>Actualizar</button></div>
    </header>

    <section className="admin-summary-grid">
      <article><span>Pendientes</span><strong>{loading ? '…' : total}</strong></article>
      <article><span>Reseñas</span><strong>{queue?.reviews.length ?? 0}</strong></article>
      <article><span>Fotos</span><strong>{queue?.media.length ?? 0}</strong></article>
      <article><span>Estados / denuncias</span><strong>{(queue?.conditions.length ?? 0) + (queue?.reports.length ?? 0)}</strong></article>
    </section>

    {message ? <p className="admin-notice">{message}</p> : null}

    <section className="admin-card">
      <div className="admin-section-heading"><div><p className="admin-eyebrow">Contenido</p><h2>Reseñas pendientes</h2></div></div>
      <div className="admin-stack">
        {queue?.reviews.length ? queue.reviews.map((item) => <article className="admin-list-card" key={item.id}>
          <div><strong>{value(item, 'route_name') || 'Ruta'}</strong><span>{value(item, 'display_name') || 'Usuario'} · {value(item, 'rating')}/5</span><p>{value(item, 'title')}</p><p>{value(item, 'body')}</p></div>
          <div className="admin-inline-actions"><button onClick={() => void moderate('review', item.id, 'approved')}>Aprobar</button><button onClick={() => void moderate('review', item.id, 'rejected')}>Rechazar</button><button onClick={() => void moderate('review', item.id, 'hidden')}>Ocultar</button></div>
        </article>) : <p>No hay reseñas pendientes.</p>}
      </div>
    </section>

    <section className="admin-card">
      <div className="admin-section-heading"><div><p className="admin-eyebrow">Galería</p><h2>Fotos pendientes</h2></div></div>
      <div className="admin-stack">
        {queue?.media.length ? queue.media.map((item) => <article className="admin-list-card" key={item.id}>
          <div className="admin-media-review"><img src={`${apiBaseUrl}${value(item, 'url')}`} alt={value(item, 'caption') || 'Foto de usuario pendiente'} /><div><strong>{value(item, 'route_name')}</strong><span>{value(item, 'display_name')}</span><p>{value(item, 'caption')}</p></div></div>
          <div className="admin-inline-actions"><button onClick={() => void moderate('review_media', item.id, 'approved')}>Aprobar</button><button onClick={() => void moderate('review_media', item.id, 'rejected')}>Rechazar</button><button onClick={() => void moderate('review_media', item.id, 'hidden')}>Ocultar</button></div>
        </article>) : <p>No hay fotografías pendientes.</p>}
      </div>
    </section>

    <section className="admin-card">
      <div className="admin-section-heading"><div><p className="admin-eyebrow">Condiciones</p><h2>Avisos de usuarios</h2></div></div>
      <div className="admin-stack">
        {queue?.conditions.length ? queue.conditions.map((item) => <article className="admin-list-card" key={item.id}>
          <div><strong>{value(item, 'route_name')}</strong><span>{value(item, 'display_name')} · {value(item, 'condition_kind')} · {value(item, 'severity')}</span><p>{value(item, 'note')}</p><small>Observado: {value(item, 'observed_at')}</small></div>
          <div className="admin-inline-actions"><button onClick={() => void moderate('condition_report', item.id, 'approved')}>Aprobar</button><button onClick={() => void moderate('condition_report', item.id, 'rejected')}>Rechazar</button><button onClick={() => void moderate('condition_report', item.id, 'hidden')}>Ocultar</button></div>
        </article>) : <p>No hay avisos pendientes.</p>}
      </div>
    </section>

    <section className="admin-card">
      <div className="admin-section-heading"><div><p className="admin-eyebrow">Seguridad</p><h2>Contenido denunciado</h2></div></div>
      <div className="admin-stack">
        {queue?.reports.length ? queue.reports.map((item) => <article className="admin-list-card" key={item.id}>
          <div><strong>{value(item, 'reason')}</strong><span>{value(item, 'target_type')} · {value(item, 'target_id')}</span><p>{value(item, 'details')}</p></div>
          <div className="admin-inline-actions"><button onClick={() => void resolveReport(item.id, 'resolved')}>Resolver</button><button onClick={() => void resolveReport(item.id, 'dismissed')}>Descartar</button></div>
        </article>) : <p>No hay denuncias abiertas.</p>}
      </div>
    </section>

    <footer className="admin-card"><strong>Regla editorial</strong><p>La comunidad aporta contexto, pero no puede convertir una observación en cierre oficial ni alterar distancia, track, desnivel o datos técnicos de la ruta.</p></footer>
  </main>;
}