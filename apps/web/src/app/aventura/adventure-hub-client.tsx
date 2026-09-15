'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  loadExplorerProfile,
  loadPublicAdventures,
  publicRouteMediaUrl,
  type AdventureCollectionCategory,
  type AdventureRarity,
  type ExplorerProfile,
  type PublicAdventureSummary,
} from '../../lib/public-routes-source';
import styles from './adventure.module.css';

function difficultyLabel(value: PublicAdventureSummary['difficulty']) {
  if (value === 'easy') return 'Fácil';
  if (value === 'moderate') return 'Moderada';
  if (value === 'hard') return 'Difícil';
  if (value === 'very_hard') return 'Muy difícil';
  return 'Sin clasificar';
}

function difficultyTone(value: PublicAdventureSummary['difficulty']) {
  if (value === 'easy') return styles.easy;
  if (value === 'moderate') return styles.moderate;
  if (value === 'hard' || value === 'very_hard') return styles.hard;
  return styles.neutral;
}

function km(value: number | null) {
  return value == null ? '—' : `${(Number(value) / 1000).toFixed(1)} km`;
}

function duration(value: number | null) {
  if (value == null) return '—';
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  return hours > 0 ? `${hours} h${minutes ? ` ${minutes} min` : ''}` : `${minutes} min`;
}

function albumCategoryLabel(category: AdventureCollectionCategory) {
  return ({
    flora: 'Flora', fauna: 'Fauna', heritage: 'Patrimonio', olive_culture: 'Olivar',
    tradition: 'Tradiciones', landscape: 'Paisaje',
  } satisfies Record<AdventureCollectionCategory, string>)[category];
}

function albumCategoryIcon(category: AdventureCollectionCategory) {
  return ({
    flora: '✿', fauna: '◒', heritage: '⌂', olive_culture: '❧', tradition: '✦', landscape: '△',
  } satisfies Record<AdventureCollectionCategory, string>)[category];
}

function rarityLabel(rarity: AdventureRarity) {
  return ({ common: 'Común', uncommon: 'Poco común', rare: 'Raro', legendary: 'Legendario' } satisfies Record<AdventureRarity, string>)[rarity];
}

function badgeLabel(code: string) {
  if (code === 'primer_descubrimiento') return 'Primer descubrimiento';
  if (code === 'aventurero_magina') return 'Aventurero de Mágina';
  if (code === 'caminante_de_la_sierra') return 'Caminante de la Sierra';
  if (code === 'mil_puntos') return '1.000 puntos';
  if (code === 'coleccionista_de_magina') return 'Coleccionista de Mágina';
  if (code === 'hallazgo_legendario') return 'Hallazgo legendario';
  return code.replaceAll('_', ' ');
}

function runStatus(value: ExplorerProfile['recent_runs'][number]['status']) {
  if (value === 'completed') return 'Completada';
  if (value === 'active') return 'En curso';
  return 'Abandonada';
}

export function AdventureHubClient() {
  const [adventures, setAdventures] = useState<PublicAdventureSummary[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [profile, setProfile] = useState<ExplorerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicAdventures()
      .then((value) => {
        if (cancelled) return;
        setAdventures(value.adventures);
        setNotice(value.notice);
        setError(false);
      })
      .catch((cause) => {
        console.warn('Unable to load Mágina Aventura hub', cause);
        if (!cancelled) setError(true);
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    loadExplorerProfile()
      .then((value) => { if (!cancelled) setProfile(value); })
      .catch(() => { if (!cancelled) setProfile(null); });

    return () => { cancelled = true; };
  }, []);

  const totals = useMemo(() => adventures.reduce((current, adventure) => ({
    checkpoints: current.checkpoints + Number(adventure.checkpoint_count || 0),
    points: current.points + Number(adventure.total_points || 0),
  }), { checkpoints: 0, points: 0 }), [adventures]);

  const albumCategories = useMemo(() => {
    if (!profile) return [];
    const categories = new Map<AdventureCollectionCategory, { available: number; unlocked: number; rarities: string[] }>();
    for (const item of profile.album) {
      const current = categories.get(item.category) ?? { available: 0, unlocked: 0, rarities: [] };
      current.available += Number(item.available);
      current.unlocked += Number(item.unlocked);
      if (item.unlocked > 0) current.rarities.push(`${rarityLabel(item.rarity)} ${item.unlocked}/${item.available}`);
      categories.set(item.category, current);
    }
    return [...categories.entries()].map(([category, stats]) => ({ category, ...stats }));
  }, [profile]);

  const featured = adventures[0] ?? null;
  const heroImage = featured?.hero_url ? publicRouteMediaUrl(featured.hero_url) : null;

  return <main className={styles.shell}>
    <section className={styles.hero} aria-labelledby="magina-aventura-title">
      <div className={styles.heroMedia} aria-hidden="true">
        {heroImage ? <img src={heroImage} alt="" /> : <div className={styles.heroFallback}>△</div>}
      </div>
      <div className={styles.heroShade} />
      <div className={styles.heroContent}>
        <span className={styles.eyebrow}>MÁGINA OLIVO V20 · SIERRA MÁGINA</span>
        <h1 id="magina-aventura-title">Mágina<br/><em>Aventura</em></h1>
        <p>Explora rutas reales, descubre el territorio y convierte cada salida en una colección de lugares, naturaleza, patrimonio y olivar.</p>
        <div className={styles.heroActions}>
          <a href="#aventuras" className={styles.primaryAction}>Comenzar aventura <span>→</span></a>
          <Link href="/rutas" className={styles.secondaryAction}>Ver todas las rutas</Link>
        </div>
      </div>
      <div className={styles.heroGlass} aria-label="Resumen de Mágina Aventura">
        <span className={styles.livePill}>● Territorio vivo</span>
        <div className={styles.heroMetrics}>
          <article><strong>{adventures.length}</strong><span>Rutas aventura</span></article>
          <article><strong>{totals.checkpoints}</strong><span>Descubrimientos</span></article>
          <article><strong>{totals.points}</strong><span>XP disponibles</span></article>
        </div>
        {featured ? <div className={styles.featuredMini}>
          <span>Ruta destacada</span>
          <strong>{featured.title}</strong>
          <small>{featured.municipality_name ?? 'Sierra Mágina'} · {km(featured.distance_m)} · {difficultyLabel(featured.difficulty)}</small>
        </div> : null}
      </div>
    </section>

    <nav className={styles.quickNav} aria-label="Mágina Aventura">
      <a href="#aventuras"><span>⌁</span><strong>Rutas</strong><small>Sal a explorar</small></a>
      <Link href="/aventura/en-curso"><span>◉</span><strong>Aventura en curso</strong><small>Mapa, GPS y retos</small></Link>
      <a href="#mi-aventura"><span>△</span><strong>Mi aventura</strong><small>XP y progreso</small></a>
      <a href="#album"><span>✦</span><strong>Colecciones</strong><small>Completa el álbum</small></a>
      <a href="#comunidad"><span>◎</span><strong>Comunidad</strong><small>Experiencias y avisos</small></a>
    </nav>

    {profile ? <section id="mi-aventura" className={styles.profilePanel} aria-labelledby="explorer-profile-title">
      <div className={styles.profileTop}>
        <div className={styles.profileIdentity}>
          <div className={styles.avatarRing}><span>△</span></div>
          <div><span className={styles.eyebrow}>MI AVENTURA</span><h2 id="explorer-profile-title">Explorador de Mágina</h2><p>Tu progreso real por la sierra, sin convertir la experiencia en una competición.</p></div>
        </div>
        <Link className={styles.textAction} href="/perfil">Ver pasaporte <span>→</span></Link>
      </div>

      <div className={styles.profileStats}>
        <article><span className={styles.metricIcon}>◉</span><strong>{profile.summary.total_score}</strong><span>XP acumulado</span></article>
        <article><span className={styles.metricIcon}>✓</span><strong>{profile.summary.adventures_completed}</strong><span>Aventuras completadas</span></article>
        <article><span className={styles.metricIcon}>✦</span><strong>{profile.summary.discoveries}</strong><span>Descubrimientos</span></article>
        <article><span className={styles.metricIcon}>↗</span><strong>{profile.summary.adventures_started}</strong><span>Expediciones iniciadas</span></article>
      </div>

      {profile.badges.length > 0 ? <div className={styles.badgeShelf} aria-label="Insignias globales">
        <div className={styles.subheading}><div><span className={styles.eyebrow}>INSIGNIAS</span><h3>Logros que ya son tuyos</h3></div></div>
        <div className={styles.badges}>{profile.badges.map((badge) => <span key={badge}><b>✦</b>{badgeLabel(badge)}</span>)}</div>
      </div> : null}

      {profile.recent_runs.length > 0 ? <div className={styles.recent}>
        <div className={styles.subheading}><div><span className={styles.eyebrow}>RECIENTE</span><h3>Últimas expediciones</h3></div></div>
        <div className={styles.runList}>{profile.recent_runs.slice(0, 3).map((run) => <Link href={`/rutas/detalle?slug=${encodeURIComponent(run.slug)}`} key={run.id}>
          <span className={styles.runIcon}>{run.status === 'completed' ? '✓' : run.status === 'active' ? '↗' : '·'}</span>
          <span className={styles.runTitle}><strong>{run.adventure_title}</strong><small>{run.route_name}</small></span>
          <span className={styles.runStatus}><strong>{run.unlocked_checkpoints}/{run.total_checkpoints}</strong><small>{runStatus(run.status)}</small></span>
          <span className={styles.chevron}>›</span>
        </Link>)}</div>
      </div> : null}
      <small className={styles.privacy}>{profile.privacy}</small>
    </section> : null}

    <section id="aventuras" className={styles.adventures} aria-labelledby="aventuras-title">
      <div className={styles.sectionHeading}>
        <div><span className={styles.eyebrow}>RUTAS · AVENTURAS</span><h2 id="aventuras-title">Elige tu próxima aventura</h2></div>
        <Link href="/rutas" className={styles.textAction}>Ver rutas <span>→</span></Link>
      </div>

      {loading ? <div className={styles.state}><div className={styles.stateIcon}>⌁</div><h3>Buscando aventuras…</h3><p>Consultando los recorridos disponibles.</p></div> : null}
      {!loading && error ? <div className={styles.state}><div className={styles.stateIcon}>!</div><h3>Mágina Aventura no está disponible ahora</h3><p>No mostramos recorridos ficticios como sustitución. Puedes seguir consultando las rutas públicas.</p><Link href="/rutas">Abrir rutas</Link></div> : null}
      {!loading && !error && adventures.length === 0 ? <div className={styles.state}><div className={styles.stateIcon}>△</div><h3>Las primeras aventuras están en preparación</h3><p>El sistema ya está listo, pero no publicaremos una aventura hasta que sus puntos reales estén revisados desde Administración.</p><Link href="/rutas">Explorar rutas disponibles</Link></div> : null}

      {!loading && !error && adventures.length > 0 ? <div className={styles.routeRail}>{adventures.map((adventure, index) => <article className={`${styles.routeCard} ${index === 0 ? styles.routeCardFeatured : ''}`} key={adventure.route_id}>
        <div className={styles.routeImage}>
          {adventure.hero_url ? <img src={publicRouteMediaUrl(adventure.hero_url)} alt="" /> : <span className={styles.routeFallback}>△</span>}
          <div className={styles.routeOverlay} />
          <div className={styles.routeTopline}>
            {index === 0 ? <span className={styles.featuredBadge}>Destacada</span> : <span />}
            <span className={`${styles.difficultyBadge} ${difficultyTone(adventure.difficulty)}`}>{difficultyLabel(adventure.difficulty)}</span>
          </div>
          <div className={styles.routeImageCopy}>
            <span>{adventure.municipality_name ?? 'Sierra Mágina'}</span>
            <h3>{adventure.title}</h3>
            <p>{adventure.place_name ?? adventure.route_name}</p>
          </div>
        </div>
        <div className={styles.routeBody}>
          <div className={styles.routeStats}>
            <span><b>⌁</b><strong>{km(adventure.distance_m)}</strong><small>Distancia</small></span>
            <span><b>◷</b><strong>{duration(adventure.duration_minutes)}</strong><small>Duración</small></span>
            <span><b>✦</b><strong>{adventure.checkpoint_count}</strong><small>Hallazgos</small></span>
            <span><b>◎</b><strong>{adventure.total_points}</strong><small>XP</small></span>
          </div>
          <p>{adventure.intro ?? adventure.short_description ?? `Aventura sobre la ruta ${adventure.route_name}.`}</p>
          <Link className={styles.routeAction} href={`/rutas/detalle?slug=${encodeURIComponent(adventure.slug)}`}>Entrar en la aventura <span>→</span></Link>
        </div>
      </article>)}</div> : null}
    </section>

    {profile && albumCategories.length > 0 ? <section id="album" className={styles.albumSection} aria-labelledby="album-title">
      <div className={styles.sectionHeading}>
        <div><span className={styles.eyebrow}>ÁLBUM DE SIERRA MÁGINA</span><h2 id="album-title">Cada descubrimiento cuenta una historia</h2><p>Flora, fauna, patrimonio, olivar, tradiciones y paisaje se desbloquean únicamente desde contenido editorial revisado.</p></div>
      </div>
      <div className={styles.albumGrid}>{albumCategories.map((entry) => {
        const percent = entry.available > 0 ? Math.round((entry.unlocked / entry.available) * 100) : 0;
        return <article key={entry.category}>
          <div className={styles.albumIcon}>{albumCategoryIcon(entry.category)}</div>
          <div className={styles.albumCopy}><div><strong>{albumCategoryLabel(entry.category)}</strong><span>{entry.unlocked}/{entry.available}</span></div><progress max={Math.max(entry.available, 1)} value={entry.unlocked} aria-label={`${albumCategoryLabel(entry.category)} ${percent}%`} /><small>{percent}% descubierto{entry.rarities.length ? ` · ${entry.rarities.join(' · ')}` : ''}</small></div>
        </article>;
      })}</div>
    </section> : null}

    <section className={styles.how} aria-labelledby="como-funciona">
      <div className={styles.sectionHeading}><div><span className={styles.eyebrow}>EXPLORA · DESCUBRE · PROTEGE</span><h2 id="como-funciona">La montaña es el juego. El territorio es lo importante.</h2></div></div>
      <div className={styles.howGrid}>
        <article><span>01</span><div className={styles.howIcon}>⌁</div><h3>Elige una ruta real</h3><p>Solo usamos rutas públicas con track validado. Antes de salir, revisa siempre sus datos técnicos y avisos.</p></article>
        <article><span>02</span><div className={styles.howIcon}>✦</div><h3>Descubre por el camino</h3><p>Encuentra lugares, retos y coleccionables vinculados a la naturaleza, patrimonio, cultura y olivar de Sierra Mágina.</p></article>
        <article><span>03</span><div className={styles.howIcon}>◎</div><h3>Construye tu pasaporte</h3><p>Suma XP, insignias y territorio explorado. Tu posición exacta no se convierte en una clasificación de velocidad.</p></article>
      </div>
    </section>

    {notice ? <p className={styles.notice}>{notice}</p> : null}

    <nav className={styles.mobileDock} aria-label="Navegación móvil de Mágina Aventura">
      <a href="#magina-aventura-title"><span>⌂</span><small>Inicio</small></a>
      <a href="#aventuras"><span>⌁</span><small>Rutas</small></a>
      <a href="#album"><span>✦</span><small>Explorar</small></a>
      <a href="#mi-aventura"><span>△</span><small>Logros</small></a>
      <Link href="/perfil"><span>◎</span><small>Perfil</small></Link>
    </nav>
  </main>;
}
