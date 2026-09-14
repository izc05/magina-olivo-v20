'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { apiFetch } from '@/lib/api-client';
import { loadPublicMunicipalities, type PublicMunicipalityDirectory } from '@/lib/public-territory-source';
import styles from './discovery.module.css';

type SortMode = 'recent' | 'most_commented' | 'most_liked';
type DiscoveryPost = {
  id: string;
  category: string;
  body: string;
  created_at: string;
  author_id: string | null;
  author_name: string;
  municipality_name: string | null;
  reaction_count: number;
  comment_count: number;
};

type DiscoveryPayload = {
  items: DiscoveryPost[];
  query: { q: string | null; category: string | null; municipality: string | null; sort: SortMode };
};

const categories = [
  ['all', 'Todas'], ['campo', 'Campo'], ['preguntas', 'Preguntas'], ['plagas', 'Plagas'],
  ['maquinaria', 'Maquinaria'], ['cosecha', 'Cosecha'], ['pueblos', 'Pueblos'],
  ['gastronomia', 'Gastronomía'], ['rutas', 'Rutas'],
] as const;

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium' }).format(date);
}

export function CommunityDiscoveryClient() {
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');
  const [municipality, setMunicipality] = useState('all');
  const [sort, setSort] = useState<SortMode>('recent');
  const [municipalities, setMunicipalities] = useState<PublicMunicipalityDirectory[]>([]);
  const [items, setItems] = useState<DiscoveryPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicMunicipalities()
      .then((value) => { if (!cancelled) setMunicipalities(value); })
      .catch(() => { if (!cancelled) setMunicipalities([]); });
    return () => { cancelled = true; };
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const params = new URLSearchParams({ sort, limit: '30' });
      if (query.trim().length >= 2) params.set('q', query.trim());
      if (category !== 'all') params.set('category', category);
      if (municipality !== 'all') params.set('municipality', municipality);
      const payload = await apiFetch<DiscoveryPayload>(`/api/v1/public/community/discover?${params}`);
      setItems(payload.items);
    } catch (cause) {
      console.warn('Unable to discover community content', cause);
      setItems([]);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [category, municipality, query, sort]);

  useEffect(() => { void load(); }, [load]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setQuery(draft.trim());
  }

  return <section className={styles.shell}>
    <header className={styles.hero}>
      <div><span className="eyebrow">COMUNIDAD MÁGINA</span><h1>Descubre conversaciones</h1><p>Busca en el texto público de las publicaciones y ordena la conversación sin consultar nombres privados ni información de Mi Campo.</p></div>
      <Link href="/comunidad" className={styles.back}>Volver a Comunidad</Link>
    </header>

    <form className={styles.controls} onSubmit={submit}>
      <label className={styles.search}>Buscar en publicaciones
        <div><input type="search" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Ej. poda, aceituna, Bedmar…" maxLength={120}/><button type="submit">Buscar</button></div>
      </label>
      <label>Orden
        <select value={sort} onChange={(event) => setSort(event.target.value as SortMode)}>
          <option value="recent">Más recientes</option>
          <option value="most_commented">Más comentadas</option>
          <option value="most_liked">Más valoradas</option>
        </select>
      </label>
      <label>Temática
        <select value={category} onChange={(event) => setCategory(event.target.value)}>{categories.map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select>
      </label>
      <label>Municipio
        <select value={municipality} onChange={(event) => setMunicipality(event.target.value)}><option value="all">Toda Sierra Mágina</option>{municipalities.map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}</select>
      </label>
    </form>

    {query ? <div className={styles.queryLine}>Resultados para <strong>“{query}”</strong> · <button type="button" onClick={() => { setDraft(''); setQuery(''); }}>Limpiar</button></div> : null}
    {loading ? <div className={styles.state}>Buscando conversaciones públicas…</div> : null}
    {!loading && error ? <div className={styles.state}>No se ha podido cargar el descubridor ahora mismo.</div> : null}
    {!loading && !error && !items.length ? <div className={styles.state}>No hay publicaciones que coincidan con estos filtros.</div> : null}

    <div className={styles.grid}>
      {items.map((post) => <article className={styles.card} key={post.id}>
        <div className={styles.meta}><span>{post.category}</span><span>{post.municipality_name ?? 'Sierra Mágina'} · {formatDate(post.created_at)}</span></div>
        <p>{post.body}</p>
        <footer>
          <span>♥ {post.reaction_count} · 💬 {post.comment_count}</span>
          {post.author_id ? <Link href={`/comunidad/persona?id=${encodeURIComponent(post.author_id)}`}>{post.author_name}</Link> : <span>{post.author_name}</span>}
        </footer>
      </article>)}
    </div>
  </section>;
}
