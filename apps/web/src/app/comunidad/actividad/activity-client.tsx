'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiRequestError } from '@/lib/api-client';
import {
  loadCommunityActivity,
  markCommunityActivityRead,
  type CommunityActivityItem,
} from '@/lib/community-source';
import styles from './activity.module.css';

function formatDate(value: string) {
  return new Intl.DateTimeFormat('es-ES', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value));
}

function eventCopy(item: CommunityActivityItem) {
  if (item.type === 'like') return 'ha marcado que le gusta tu publicación';
  if (item.type === 'reply') return 'ha respondido a uno de tus comentarios';
  return 'ha comentado en tu publicación';
}

function eventIcon(item: CommunityActivityItem) {
  if (item.type === 'like') return '♥';
  if (item.type === 'reply') return '↪';
  return '💬';
}

export function CommunityActivityClient() {
  const [items, setItems] = useState<CommunityActivityItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const [needsLogin, setNeedsLogin] = useState(false);
  const [error, setError] = useState(false);
  const [marking, setMarking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    setNeedsLogin(false);
    try {
      const result = await loadCommunityActivity(50);
      setItems(result.items);
      setUnread(result.unread_count);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        setNeedsLogin(true);
        setItems([]);
        setUnread(0);
      } else {
        console.warn('Unable to load community activity', cause);
        setError(true);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function markRead() {
    if (!unread || marking) return;
    setMarking(true);
    try {
      await markCommunityActivityRead();
      setUnread(0);
      setItems((current) => current.map((item) => ({ ...item, unread: false })));
    } catch (cause) {
      console.warn('Unable to mark community activity read', cause);
    } finally {
      setMarking(false);
    }
  }

  if (loading) return <div className={styles.state}>Cargando tu actividad comunitaria…</div>;
  if (needsLogin) return <div className={styles.state}><strong>Inicia sesión para ver tu actividad.</strong><span>Likes y respuestas se guardan en tu cuenta, no en un workspace agrícola.</span></div>;
  if (error) return <div className={styles.state}><strong>No podemos cargar la actividad ahora mismo.</strong><button type="button" onClick={() => void load()}>Reintentar</button></div>;

  return <section className={styles.panel}>
    <div className={styles.head}>
      <div>
        <span className="eyebrow dark">MI ACTIVIDAD</span>
        <h1>Lo que está pasando alrededor de tus conversaciones</h1>
        <p>{unread > 0 ? `${unread} novedades sin leer` : 'Estás al día'}</p>
      </div>
      <button type="button" onClick={() => void markRead()} disabled={!unread || marking}>
        {marking ? 'Marcando…' : 'Marcar todo como leído'}
      </button>
    </div>

    {items.length === 0 ? <div className={styles.state}>
      <strong>Todavía no hay actividad.</strong>
      <span>Cuando alguien reaccione, comente o responda a tus conversaciones aparecerá aquí.</span>
    </div> : <div className={styles.list}>
      {items.map((item) => <Link
        key={item.event_id}
        href={`/comunidad#post-${item.post_id}`}
        className={`${styles.item} ${item.unread ? styles.unread : ''}`}
      >
        <span className={styles.icon} aria-hidden="true">{eventIcon(item)}</span>
        <span className={styles.body}>
          <span><strong>{item.actor_name}</strong> {eventCopy(item)}.</span>
          <span className={styles.excerpt}>“{item.post_excerpt}”</span>
          <small>{item.municipality_name ? `${item.municipality_name} · ` : ''}{formatDate(item.created_at)}</small>
        </span>
        {item.unread ? <span className={styles.dot} aria-label="Sin leer" /> : null}
      </Link>)}
    </div>}
  </section>;
}
