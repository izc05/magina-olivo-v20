'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '@/lib/api-client';
import {
  communityCategories,
  createCommunityComment,
  createCommunityPost,
  loadCommunityBookmarks,
  loadCommunityComments,
  loadCommunityFeed,
  loadCommunityHighlights,
  reportCommunityTarget,
  setCommunityBookmark,
  setCommunityLike,
  type CommunityCategory,
  type CommunityComment,
  type CommunityHighlight,
  type CommunityPost,
} from '@/lib/community-source';
import { loadPublicMunicipalities, type PublicMunicipalityDirectory } from '@/lib/public-territory-source';
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
type FeedMode = 'recent' | 'saved';
type ReplyTarget = { id: string; name: string };

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

function orderedComments(items: CommunityComment[]) {
  const roots = items.filter((item) => !item.parent_comment_id);
  const replies = items.filter((item) => item.parent_comment_id);
  const ordered: CommunityComment[] = [];
  for (const root of roots) {
    ordered.push(root);
    ordered.push(...replies.filter((reply) => reply.parent_comment_id === root.id));
  }
  ordered.push(...replies.filter((reply) => !roots.some((root) => root.id === reply.parent_comment_id)));
  return ordered;
}

export function CommunityClient() {
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [mode, setMode] = useState<FeedMode>('recent');
  const [category, setCategory] = useState<CommunityCategory | 'all'>('all');
  const [municipality, setMunicipality] = useState<string>('all');
  const [municipalities, setMunicipalities] = useState<PublicMunicipalityDirectory[]>([]);
  const [highlights, setHighlights] = useState<CommunityHighlight[]>([]);
  const [highlightsLoading, setHighlightsLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);
  const [savedNeedsLogin, setSavedNeedsLogin] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [composerBody, setComposerBody] = useState('');
  const [composerCategory, setComposerCategory] = useState<CommunityCategory>('campo');
  const [composerMunicipality, setComposerMunicipality] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [comments, setComments] = useState<Record<string, CommunityComment[]>>({});
  const [commentsOpen, setCommentsOpen] = useState<Record<string, boolean>>({});
  const [commentDrafts, setCommentDrafts] = useState<Record<string, string>>({});
  const [replyTargets, setReplyTargets] = useState<Record<string, ReplyTarget | null>>({});
  const [reportReason, setReportReason] = useState<Record<string, ReportReason>>({});

  const activeCategory = category === 'all' ? null : category;
  const activeMunicipality = municipality === 'all' ? null : municipality;
  const activeMunicipalityName = municipalities.find((item) => item.slug === activeMunicipality)?.name ?? null;

  useEffect(() => {
    let cancelled = false;
    loadPublicMunicipalities()
      .then((items) => { if (!cancelled) setMunicipalities(items); })
      .catch(() => { if (!cancelled) setMunicipalities([]); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setHighlightsLoading(true);
    loadCommunityHighlights({ municipality: activeMunicipality, limit: 3 })
      .then((response) => { if (!cancelled) setHighlights(response.items); })
      .catch((cause) => {
        console.warn('Unable to load community highlights', cause);
        if (!cancelled) setHighlights([]);
      })
      .finally(() => { if (!cancelled) setHighlightsLoading(false); });
    return () => { cancelled = true; };
  }, [activeMunicipality]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(false);
    setSavedNeedsLogin(false);
    try {
      if (mode === 'saved') {
        const saved = await loadCommunityBookmarks({ category: activeCategory, municipality: activeMunicipality, limit: 100 });
        setPosts(saved.items);
        setNextCursor(null);
      } else {
        const feed = await loadCommunityFeed({ category: activeCategory, municipality: activeMunicipality, limit: 20 });
        setPosts(feed.items);
        setNextCursor(feed.next_cursor);
      }
    } catch (cause) {
      if (mode === 'saved' && cause instanceof ApiRequestError && cause.status === 401) {
        setSavedNeedsLogin(true);
        setPosts([]);
        setNextCursor(null);
      } else {
        console.warn('Unable to load community feed', cause);
        setError(true);
        setPosts([]);
        setNextCursor(null);
      }
    } finally {
      setLoading(false);
    }
  }, [activeCategory, activeMunicipality, mode]);

  useEffect(() => { void refresh(); }, [refresh]);

  const postCountLabel = useMemo(() => {
    if (loading) return mode === 'saved' ? 'Cargando tus guardados…' : 'Cargando conversación…';
    if (savedNeedsLogin) return 'Tus guardados están vinculados a tu cuenta';
    if (error) return 'Comunidad temporalmente no disponible';
    if (posts.length === 0) return mode === 'saved' ? 'No tienes publicaciones guardadas con estos filtros' : 'Todavía no hay publicaciones con estos filtros';
    if (mode === 'saved') return `${posts.length} publicaciones guardadas`;
    return `${posts.length}${nextCursor ? '+' : ''} publicaciones recientes`;
  }, [error, loading, mode, nextCursor, posts.length, savedNeedsLogin]);

  async function publish() {
    const body = composerBody.trim();
    if (!body || submitting) return;
    setSubmitting(true);
    setNotice(null);
    try {
      await createCommunityPost({
        category: composerCategory,
        body,
        municipality_slug: composerMunicipality || null,
      });
      setComposerBody('');
      setMode('recent');
      setCategory(composerCategory);
      setMunicipality(composerMunicipality || 'all');
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
      if (mode === 'saved' && !next) {
        setPosts((items) => items.filter((item) => item.id !== post.id));
      }
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
    const replyTarget = replyTargets[post.id] ?? null;
    try {
      await createCommunityComment(post.id, body, replyTarget?.id ?? null);
      const response = await loadCommunityComments(post.id);
      setComments((state) => ({ ...state, [post.id]: response.items }));
      setCommentDrafts((state) => ({ ...state, [post.id]: '' }));
      setReplyTargets((state) => ({ ...state, [post.id]: null }));
      setPosts((items) => items.map((item) => item.id === post.id ? { ...item, comment_count: response.items.length } : item));
    } catch (cause) {
      setNotice(authMessage(cause));
    }
  }

  function startReply(postId: string, comment: CommunityComment) {
    if (comment.parent_comment_id) return;
    setReplyTargets((state) => ({ ...state, [postId]: { id: comment.id, name: comment.author_name } }));
  }

  async function reportTarget(targetType: 'post' | 'comment', targetId: string) {
    const key = `${targetType}:${targetId}`;
    const reason = reportReason[key] ?? 'other';
    try {
      await reportCommunityTarget({ target_type: targetType, target_id: targetId, reason });
      setNotice('Gracias. El aviso ha entrado en la cola de moderación.');
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 409) {
        setNotice('Ya tienes un aviso abierto sobre este contenido.');
      } else {
        setNotice(authMessage(cause));
      }
    }
  }

  async function sharePost(post: CommunityPost) {
    const url = `${window.location.origin}${window.location.pathname}#post-${post.id}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: `Comunidad Mágina · ${categoryLabel(post.category)}`, text: post.body.slice(0, 180), url });
      } else {
        await navigator.clipboard.writeText(url);
        setNotice('Enlace de la publicación copiado.');
      }
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setNotice('No se ha podido compartir la publicación desde este dispositivo.');
    }
  }

  async function loadMore() {
    if (mode !== 'recent' || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const feed = await loadCommunityFeed({
        category: activeCategory,
        municipality: activeMunicipality,
        before: nextCursor,
        limit: 20,
      });
      setPosts((items) => [...items, ...feed.items.filter((post) => !items.some((item) => item.id === post.id))]);
      setNextCursor(feed.next_cursor);
    } catch (cause) {
      console.warn('Unable to load more community posts', cause);
      setNotice('No se han podido cargar más publicaciones.');
    } finally {
      setLoadingMore(false);
    }
  }

  function focusHighlight(highlight: CommunityHighlight) {
    setMode('recent');
    setCategory('all');
    setMunicipality(highlight.municipality_slug ?? 'all');
    window.setTimeout(() => document.getElementById(`post-${highlight.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 350);
  }

  return <>
    <section className={styles.hero}>
      <span className="eyebrow">COMUNIDAD MÁGINA</span>
      <div className={styles.heroGrid}>
        <div>
          <h1>El campo y los pueblos,<br/>contados por su gente</h1>
          <p>Comparte una duda, una experiencia del olivar o un rincón de Sierra Mágina. La comunidad nunca publica por defecto la ubicación exacta de una finca.</p>
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
        <div className={styles.composerSelectors}>
          <label>Temática
            <select value={composerCategory} onChange={(event) => setComposerCategory(event.target.value as CommunityCategory)}>
              {communityCategories.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
          </label>
          <label>Municipio público
            <select value={composerMunicipality} onChange={(event) => setComposerMunicipality(event.target.value)}>
              <option value="">Sin municipio</option>
              {municipalities.map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}
            </select>
          </label>
        </div>
        <button type="button" className={styles.primaryButton} onClick={() => void publish()} disabled={!composerBody.trim() || submitting}>
          {submitting ? 'Publicando…' : 'Publicar'}
        </button>
      </div>
      {notice ? <p className={styles.notice} role="status">{notice}</p> : null}
    </section>

    <section className={styles.highlights} aria-labelledby="community-highlights-title">
      <div className={styles.highlightsHead}>
        <div><span className="eyebrow dark">DESTACADO</span><h2 id="community-highlights-title">{activeMunicipalityName ? `Ahora en ${activeMunicipalityName}` : 'Lo que mueve Sierra Mágina'}</h2></div>
        <span>Últimos 30 días · conversación + reacciones</span>
      </div>
      {highlightsLoading ? <div className={styles.highlightState}>Calculando conversaciones destacadas…</div> : null}
      {!highlightsLoading && highlights.length === 0 ? <div className={styles.highlightState}>Todavía no hay suficiente actividad para destacar contenido aquí.</div> : null}
      <div className={styles.highlightGrid}>
        {highlights.map((highlight, index) => <button type="button" key={highlight.id} className={styles.highlightCard} onClick={() => focusHighlight(highlight)}>
          <span className={styles.highlightRank}>#{index + 1}</span>
          <strong>{categoryLabel(highlight.category)}{highlight.municipality_name ? ` · ${highlight.municipality_name}` : ''}</strong>
          <p>{highlight.body}</p>
          <span>♥ {highlight.reaction_count} · 💬 {highlight.comment_count}</span>
        </button>)}
      </div>
    </section>

    <section className={styles.feedSection}>
      <div className={styles.feedHead}>
        <div><span className="eyebrow dark">AHORA EN MÁGINA</span><h2>Conversaciones</h2><p>{postCountLabel}</p></div>
        <div className={styles.territoryFilter}>
          <label>Municipio
            <select value={municipality} onChange={(event) => setMunicipality(event.target.value)}>
              <option value="all">Toda Sierra Mágina</option>
              {municipalities.map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}
            </select>
          </label>
        </div>
      </div>

      <div className={styles.viewTabs} aria-label="Vista de comunidad">
        <button type="button" className={mode === 'recent' ? styles.viewTabActive : ''} onClick={() => setMode('recent')}>Recientes</button>
        <button type="button" className={mode === 'saved' ? styles.viewTabActive : ''} onClick={() => setMode('saved')}>★ Guardados</button>
      </div>

      <div className={styles.filters} aria-label="Filtrar comunidad por temática">
        <button type="button" className={category === 'all' ? styles.filterActive : ''} onClick={() => setCategory('all')}>Todo</button>
        {communityCategories.map((item) => <button type="button" key={item.value} className={category === item.value ? styles.filterActive : ''} onClick={() => setCategory(item.value)}>{item.label}</button>)}
      </div>

      {loading ? <div className={styles.stateCard}>{mode === 'saved' ? 'Cargando tus publicaciones guardadas…' : 'Cargando publicaciones reales…'}</div> : null}
      {!loading && savedNeedsLogin ? <div className={styles.stateCard}><strong>Inicia sesión para ver tus guardados.</strong><span>La colección guardada es privada y está vinculada a tu cuenta.</span></div> : null}
      {!loading && error ? <div className={styles.stateCard}><strong>La comunidad no está disponible ahora mismo.</strong><span>No mostramos contenido ficticio como sustitución.</span></div> : null}
      {!loading && !error && !savedNeedsLogin && posts.length === 0 ? <div className={styles.stateCard}><strong>{mode === 'saved' ? 'No tienes publicaciones guardadas aquí.' : 'Aún no hay publicaciones aquí.'}</strong><span>{mode === 'saved' ? 'Pulsa Guardar en cualquier publicación para añadirla a esta colección.' : 'Puedes ser la primera persona en abrir esta conversación.'}</span></div> : null}

      <div className={styles.feed}>
        {posts.map((post) => <article className={styles.post} id={`post-${post.id}`} key={post.id}>
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
            <button type="button" onClick={() => void sharePost(post)}>↗ Compartir</button>
          </div>

          {commentsOpen[post.id] ? <div className={styles.comments}>
            <div className={styles.commentList}>
              {!comments[post.id] ? <span>Cargando comentarios…</span> : null}
              {comments[post.id]?.length === 0 ? <span>Todavía no hay respuestas.</span> : null}
              {orderedComments(comments[post.id] ?? []).map((comment) => {
                const reportKey = `comment:${comment.id}`;
                return <div className={`${styles.comment} ${comment.parent_comment_id ? styles.commentReply : ''}`} key={comment.id}>
                  {comment.reply_to_author_name ? <span className={styles.replyContext}>↪ Respuesta a {comment.reply_to_author_name}</span> : null}
                  <strong>{comment.author_name}</strong><p>{comment.body}</p><small>{formatDate(comment.created_at)}</small>
                  <div className={styles.commentActions}>
                    {!comment.parent_comment_id ? <button type="button" onClick={() => startReply(post.id, comment)}>Responder</button> : null}
                    <details className={styles.commentReport}>
                      <summary>Reportar</summary>
                      <div>
                        <select value={reportReason[reportKey] ?? 'other'} onChange={(event) => setReportReason((state) => ({ ...state, [reportKey]: event.target.value as ReportReason }))}>
                          {reportReasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                        <button type="button" onClick={() => void reportTarget('comment', comment.id)}>Enviar aviso</button>
                      </div>
                    </details>
                  </div>
                </div>;
              })}
            </div>
            {replyTargets[post.id] ? <div className={styles.replyingTo}>
              <span>Respondiendo a <strong>{replyTargets[post.id]!.name}</strong></span>
              <button type="button" onClick={() => setReplyTargets((state) => ({ ...state, [post.id]: null }))}>Cancelar</button>
            </div> : null}
            <div className={styles.commentComposer}>
              <input value={commentDrafts[post.id] ?? ''} onChange={(event) => setCommentDrafts((state) => ({ ...state, [post.id]: event.target.value.slice(0, 1000) }))} placeholder={replyTargets[post.id] ? `Responder a ${replyTargets[post.id]!.name}…` : 'Escribe un comentario…'} />
              <button type="button" onClick={() => void submitComment(post)} disabled={!(commentDrafts[post.id] ?? '').trim()}>{replyTargets[post.id] ? 'Responder' : 'Comentar'}</button>
            </div>
          </div> : null}

          <details className={styles.reportBox}>
            <summary>Reportar publicación</summary>
            <div>
              <select value={reportReason[`post:${post.id}`] ?? 'other'} onChange={(event) => setReportReason((state) => ({ ...state, [`post:${post.id}`]: event.target.value as ReportReason }))}>
                {reportReasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
              <button type="button" onClick={() => void reportTarget('post', post.id)}>Enviar aviso</button>
            </div>
          </details>
        </article>)}
      </div>

      {mode === 'recent' && nextCursor && !loading ? <div className={styles.moreWrap}><button type="button" className={styles.moreButton} onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? 'Cargando…' : 'Ver más publicaciones'}</button></div> : null}
    </section>
  </>;
}
