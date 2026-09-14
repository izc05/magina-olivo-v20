'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '@/lib/api-client';
import {
  communityCategories,
  createCommunityComment,
  createCommunityPost,
  loadCommunityComments,
  loadCommunityFeed,
  reportCommunityTarget,
  setCommunityBookmark,
  setCommunityLike,
  type CommunityCategory,
  type CommunityComment,
  type CommunityPost,
} from '@/lib/community-source';
import styles from './community.module.css';

const reportReasons = [
  ['spam', 'Spam'],
  ['abuse', 'Falta de respeto'],
  ['privacy', 'Privacidad'],
  ['dangerous', 'Contenido peligroso'],
  ['misinformation', 'Información dudosa'],
  ['other', 'Otro motivo'],
] as const;

type ReportReason = (typeof reportReasons)[number][0];

function categoryLabel(value: string) {
  return communityCategories.find((category) => category.value === value)?.label ?? value;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('es-ES', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value));
}

function authMessage(error: unknown) {
  return error instanceof ApiRequestError && error.status === 401
    ? 'Inicia sesión para participar en la comunidad.'
    : 'No hemos podido completar la acción. Inténtalo de nuevo.';
}

export function CommunityClient() {
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [category, setCategory] = useState<CommunityCategory | 'all'>('all');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [composerBody, setComposerBody] = useState('');
  const [composerCategory, setComposerCategory] = useState<CommunityCategory>('campo');
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [comments, setComments] = useState<Record<string, CommunityComment[]>>({});
  const [commentsOpen, setCommentsOpen] = useState<Record<string, boolean>>({});
  const [commentDrafts, setCommentDrafts] = useState<Record<string, string>>({});
  const [reportReason, setReportReason] = useState<Record<string, ReportReason>>({});

  const activeCategory = category === 'all' ? null : category;

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const feed = await loadCommunityFeed({ category: activeCategory, limit: 20 });
      setPosts(feed.items);
      setNextCursor(feed.next_cursor);
    } catch (cause) {
      console.warn('Unable to load community feed', cause);
      setError(true);
      setPosts([]);
      setNextCursor(null);
    } finally {
      setLoading(false);
    }
  }, [activeCategory]);

  useEffect(() => { void refresh(); }, [refresh]);

  const postCountLabel = useMemo(() => {
    if (loading) return 'Cargando conversación…';
    if (error) return 'Comunidad temporalmente no disponible';
    if (posts.length === 0) return 'Todavía no hay publicaciones en esta categoría';
    return `${posts.length}${nextCursor ? '+' : ''} publicaciones recientes`;
  }, [error, loading, nextCursor, posts.length]);

  async function publish() {
    const body = composerBody.trim();
    if (!body || submitting) return;
    setSubmitting(true);
    setNotice(null);
    try {
      await createCommunityPost({ category: composerCategory, body });
      setComposerBody('');
      setCategory(composerCategory);
      await refresh();
      setNotice('Publicación compartida con la comunidad.');
    } catch (cause) {
      setNotice(authMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleLike(post: CommunityPost) {
    const next = !post.viewer_liked;
    setPosts((items) => items.map((item) => item.id === post.id ? {
      ...item,
      viewer_liked: next,
      reaction_count: Math.max(0, item.reaction_count + (next ? 1 : -1)),
    } : item));
    try {
      await setCommunityLike(post.id, next);
    } catch (cause) {
      setPosts((items) => items.map((item) => item.id === post.id ? post : item));
      setNotice(authMessage(cause));
    }
  }

  async function toggleBookmark(post: CommunityPost) {
    const next = !post.viewer_bookmarked;
    setPosts((items) => items.map((item) => item.id === post.id ? { ...item, viewer_bookmarked: next } : item));
    try {
      await setCommunityBookmark(post.id, next);
    } catch (cause) {
      setPosts((items) => items.map((item) => item.id === post.id ? post : item));
      setNotice(authMessage(cause));
    }
  }

  async function toggleComments(post: CommunityPost) {
    const open = !commentsOpen[post.id];
    setCommentsOpen((state) => ({ ...state, [post.id]: open }));
    if (!open || comments[post.id]) return;
    try {
      const response = await loadCommunityComments(post.id);
      setComments((state) => ({ ...state, [post.id]: response.items }));
    } catch (cause) {
      console.warn('Unable to load comments', cause);
      setNotice('No se han podido cargar los comentarios.');
    }
  }

  async function submitComment(post: CommunityPost) {
    const body = (commentDrafts[post.id] ?? '').trim();
    if (!body) return;
    try {
      await createCommunityComment(post.id, body);
      const response = await loadCommunityComments(post.id);
      setComments((state) => ({ ...state, [post.id]: response.items }));
      setCommentDrafts((state) => ({ ...state, [post.id]: '' }));
      setPosts((items) => items.map((item) => item.id === post.id ? { ...item, comment_count: response.items.length } : item));
    } catch (cause) {
      setNotice(authMessage(cause));
    }
  }

  async function reportPost(post: CommunityPost) {
    const reason = reportReason[post.id] ?? 'other';
    try {
      await reportCommunityTarget({ target_type: 'post', target_id: post.id, reason });
      setNotice('Gracias. El aviso ha entrado en la cola de moderación.');
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 409) {
        setNotice('Ya tienes un aviso abierto sobre esta publicación.');
      } else {
        setNotice(authMessage(cause));
      }
    }
  }

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const feed = await loadCommunityFeed({ category: activeCategory, before: nextCursor, limit: 20 });
      setPosts((items) => [...items, ...feed.items.filter((post) => !items.some((item) => item.id === post.id))]);
      setNextCursor(feed.next_cursor);
    } catch (cause) {
      console.warn('Unable to load more community posts', cause);
      setNotice('No se han podido cargar más publicaciones.');
    } finally {
      setLoadingMore(false);
    }
  }

  return <>
    <section className={styles.hero}>
      <span className="eyebrow">COMUNIDAD MÁGINA</span>
      <div className={styles.heroGrid}>
        <div>
          <h1>El campo y los pueblos,<br/>contados por su gente</h1>
          <p>Comparte una duda, una fotografía, una experiencia del olivar o un rincón de Sierra Mágina. La comunidad nunca publica por defecto la ubicación exacta de una finca.</p>
        </div>
        <div className={styles.principles} aria-label="Principios de la comunidad">
          <strong>Una comunidad útil y cercana</strong>
          <span>✓ Cuenta necesaria para participar</span>
          <span>✓ Reportes y moderación desde el primer día</span>
          <span>✓ Sin geometrías privadas de Mi Campo</span>
        </div>
      </div>
    </section>

    <section className={styles.composer} aria-labelledby="community-composer-title">
      <div className={styles.composerHeader}>
        <div><span className="eyebrow dark">COMPARTE</span><h2 id="community-composer-title">¿Qué está pasando por Mágina?</h2></div>
        <span className={styles.counter}>{composerBody.length}/2000</span>
      </div>
      <textarea
        value={composerBody}
        onChange={(event) => setComposerBody(event.target.value.slice(0, 2000))}
        placeholder="Pregunta, comparte una experiencia del campo, una recomendación o algo que hayas descubierto…"
        rows={4}
      />
      <div className={styles.composerActions}>
        <label>Temática
          <select value={composerCategory} onChange={(event) => setComposerCategory(event.target.value as CommunityCategory)}>
            {communityCategories.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </label>
        <button type="button" className={styles.primaryButton} onClick={() => void publish()} disabled={!composerBody.trim() || submitting}>
          {submitting ? 'Publicando…' : 'Publicar'}
        </button>
      </div>
      {notice ? <p className={styles.notice} role="status">{notice}</p> : null}
    </section>

    <section className={styles.feedSection}>
      <div className={styles.feedHead}>
        <div><span className="eyebrow dark">AHORA EN MÁGINA</span><h2>Conversaciones</h2><p>{postCountLabel}</p></div>
        <div className={styles.filters} aria-label="Filtrar comunidad por temática">
          <button type="button" className={category === 'all' ? styles.filterActive : ''} onClick={() => setCategory('all')}>Todo</button>
          {communityCategories.map((item) => <button type="button" key={item.value} className={category === item.value ? styles.filterActive : ''} onClick={() => setCategory(item.value)}>{item.label}</button>)}
        </div>
      </div>

      {loading ? <div className={styles.stateCard}>Cargando publicaciones reales…</div> : null}
      {!loading && error ? <div className={styles.stateCard}><strong>La comunidad no está disponible ahora mismo.</strong><span>No mostramos contenido ficticio como sustitución.</span></div> : null}
      {!loading && !error && posts.length === 0 ? <div className={styles.stateCard}><strong>Aún no hay publicaciones aquí.</strong><span>Puedes ser la primera persona en abrir esta conversación.</span></div> : null}

      <div className={styles.feed}>
        {posts.map((post) => <article className={styles.post} key={post.id}>
          <header className={styles.postHeader}>
            <div className={styles.avatar} aria-hidden="true">{post.author_name.slice(0, 1).toLocaleUpperCase('es')}</div>
            <div><strong>{post.author_name}</strong><span>{post.municipality_name ? `${post.municipality_name} · ` : ''}{formatDate(post.created_at)}</span></div>
            <span className={styles.category}>{categoryLabel(post.category)}</span>
          </header>
          <p className={styles.postBody}>{post.body}</p>
          {post.media_url ? <img className={styles.postImage} src={post.media_url} alt="Imagen compartida en Comunidad Mágina" loading="lazy"/> : null}
          <div className={styles.postActions}>
            <button type="button" className={post.viewer_liked ? styles.actionActive : ''} onClick={() => void toggleLike(post)}>♥ {post.reaction_count}</button>
            <button type="button" onClick={() => void toggleComments(post)}>💬 {post.comment_count}</button>
            <button type="button" className={post.viewer_bookmarked ? styles.actionActive : ''} onClick={() => void toggleBookmark(post)}>{post.viewer_bookmarked ? '★ Guardado' : '☆ Guardar'}</button>
          </div>

          {commentsOpen[post.id] ? <div className={styles.comments}>
            <div className={styles.commentList}>
              {!comments[post.id] ? <span>Cargando comentarios…</span> : null}
              {comments[post.id]?.length === 0 ? <span>Todavía no hay respuestas.</span> : null}
              {comments[post.id]?.map((comment) => <div className={styles.comment} key={comment.id}><strong>{comment.author_name}</strong><p>{comment.body}</p><small>{formatDate(comment.created_at)}</small></div>)}
            </div>
            <div className={styles.commentComposer}>
              <input value={commentDrafts[post.id] ?? ''} onChange={(event) => setCommentDrafts((state) => ({ ...state, [post.id]: event.target.value.slice(0, 1000) }))} placeholder="Escribe una respuesta…" />
              <button type="button" onClick={() => void submitComment(post)} disabled={!(commentDrafts[post.id] ?? '').trim()}>Responder</button>
            </div>
          </div> : null}

          <details className={styles.reportBox}>
            <summary>Reportar contenido</summary>
            <div>
              <select value={reportReason[post.id] ?? 'other'} onChange={(event) => setReportReason((state) => ({ ...state, [post.id]: event.target.value as ReportReason }))}>
                {reportReasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
              <button type="button" onClick={() => void reportPost(post)}>Enviar aviso</button>
            </div>
          </details>
        </article>)}
      </div>

      {nextCursor && !loading ? <div className={styles.moreWrap}><button type="button" className={styles.moreButton} onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? 'Cargando…' : 'Ver más publicaciones'}</button></div> : null}
    </section>
  </>;
}
