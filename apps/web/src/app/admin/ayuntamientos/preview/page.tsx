'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';
import styles from './preview.module.css';

type Role = 'profile' | 'heritage' | 'nature' | 'tourism' | '';

function asObject(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function field(entry: CmsEntry, key: string) { const value = asObject(entry.content_json)[key]; return typeof value === 'string' ? value : ''; }
function roleOf(entry: CmsEntry): Role { const role = field(entry, 'municipality_role'); return role === 'profile' || role === 'heritage' || role === 'nature' || role === 'tourism' ? role : ''; }
function roleLabel(role: Role) { return role === 'heritage' ? 'Patrimonio' : role === 'nature' ? 'Naturaleza' : role === 'tourism' ? 'Turismo' : role === 'profile' ? 'Perfil' : 'Lugar'; }
function selectEssentials(discoveries: CmsEntry[]) {
  const picks: CmsEntry[] = []; const picked = new Set<string>();
  const add = (entry?: CmsEntry) => { if (!entry || picked.has(entry.id) || picks.length >= 3) return; picks.push(entry); picked.add(entry.id); };
  discoveries.filter((entry) => entry.featured).forEach(add);
  (['heritage', 'nature', 'tourism'] as Role[]).forEach((role) => add(discoveries.find((entry) => roleOf(entry) === role)));
  discoveries.forEach(add);
  return picks;
}

export default function MunicipalityEditorialPreviewPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [municipalityId, setMunicipalityId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [content, territory] = await Promise.all([adminApi.content(), adminApi.territoryCatalog()]);
    const active = territory.municipalities.filter((item) => item.active);
    setMunicipalities(active); setEntries(content.entries.filter((entry) => entry.type === 'place'));
    setMunicipalityId((current) => {
      const requested = readMunicipalitySlug();
      const next = active.find((item) => requested && item.slug === requested) ?? active.find((item) => item.id === current) ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido preparar la vista previa municipal.')); }, [auth.status, load]);

  const municipality = municipalities.find((item) => item.id === municipalityId) ?? null;
  const municipalEntries = useMemo(() => entries.filter((entry) => field(entry, 'municipality_id') === municipalityId), [entries, municipalityId]);
  const profile = useMemo(() => municipalEntries.find((entry) => roleOf(entry) === 'profile') ?? null, [municipalEntries]);
  const discoveries = useMemo(() => municipalEntries.filter((entry) => ['heritage', 'nature', 'tourism'].includes(roleOf(entry))).sort((a, b) => Number(b.featured) - Number(a.featured) || b.sort_order - a.sort_order || a.title.localeCompare(b.title, 'es')), [municipalEntries]);
  const essentials = useMemo(() => selectEssentials(discoveries), [discoveries]);
  const heroMedia = profile?.media_url || discoveries.find((entry) => entry.media_url)?.media_url || null;
  const checks = [
    { label: 'Perfil principal', ok: Boolean(profile) },
    { label: 'Resumen', ok: Boolean(profile?.summary?.trim()) },
    { label: 'Imagen hero', ok: Boolean(heroMedia) },
    { label: 'Fuente + verificación', ok: Boolean(profile && field(profile, 'source_url') && field(profile, 'verified_at')) },
    { label: 'Descubrimiento publicado', ok: discoveries.some((entry) => entry.status === 'published') },
  ];
  const ready = checks.filter((check) => check.ok).length;

  function changeMunicipality(id: string) { setMunicipalityId(id); const next = municipalities.find((item) => item.id === id); replaceMunicipalityContext(next?.slug); }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Preview municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href={municipalityAdminHref('/admin/ayuntamientos/editorial', municipality?.slug)}>← Portada editorial</a><h1>Preview editorial municipal</h1><p>Compara el borrador CMS con el resultado público real antes de publicar.</p></div>{municipality ? <a href={`/ayuntamientos/${municipality.slug}`} target="_blank" rel="noreferrer">Abrir público ↗</a> : null}</header>
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={municipality?.slug} name={municipality?.name} active="preview" />

    <section className={`admin-card ${styles.context}`}>
      <label>Municipio<select value={municipalityId} onChange={(event) => changeMunicipality(event.target.value)}>{municipalities.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <div><strong>{ready}/5</strong><span>preflight editorial</span></div>
      <div className={styles.checks}>{checks.map((check) => <span key={check.label} data-ok={check.ok ? 'true' : 'false'}>{check.ok ? '✓' : '·'} {check.label}</span>)}</div>
    </section>

    {municipality ? <section className={styles.compare}>
      <article className={`admin-card ${styles.previewPanel}`}>
        <div className={styles.panelHeading}><div><span>BORRADOR EDITORIAL</span><h2>Así se plantea la portada</h2><p>Incluye contenido CMS aún no publicado. Los estados se muestran para evitar confundirlo con la web pública.</p></div><a href={municipalityAdminHref('/admin/ayuntamientos/editorial', municipality.slug)}>Editar portada →</a></div>
        <section className={styles.hero} style={heroMedia ? { backgroundImage: `linear-gradient(110deg,rgba(24,58,37,.96),rgba(32,73,46,.55)),url("${heroMedia.replace(/"/g, '%22')}")` } : undefined}>
          <span>MUNICIPIO · SIERRA MÁGINA</span><h2>{municipality.name}</h2><p>{profile?.summary || 'Sin resumen editorial. Completa el perfil principal antes de publicar.'}</p>{profile ? <small data-status={profile.status}>Perfil · {profile.status}</small> : <small>Sin perfil principal</small>}
        </section>
        <div className={styles.essentialsHeading}><span>PRIMERA MIRADA</span><h3>Lo imprescindible de {municipality.name}</h3><p>Misma regla editorial: destacados primero, después variedad por patrimonio/naturaleza/turismo.</p></div>
        <div className={styles.cards}>{essentials.map((entry, index) => <article key={entry.id} className={styles.card}>
          {entry.media_url ? <div className={styles.cardMedia} style={{ backgroundImage: `url("${entry.media_url.replace(/"/g, '%22')}")` }} /> : <div className={styles.cardFallback}>{String(index + 1).padStart(2, '0')}</div>}
          <div><span>{roleLabel(roleOf(entry))} · {entry.status}{entry.featured ? ' · destacado' : ''}</span><h4>{entry.title}</h4>{entry.summary ? <p>{entry.summary}</p> : <p>Sin resumen.</p>}</div>
        </article>)}{!essentials.length ? <div className={styles.empty}>No hay descubrimientos clasificados para previsualizar.</div> : null}</div>
      </article>

      <article className={`admin-card ${styles.publicPanel}`}>
        <div className={styles.panelHeading}><div><span>RESULTADO PÚBLICO ACTUAL</span><h2>Lo que ve ahora el usuario</h2><p>Esta vista carga la ficha pública real: solo muestra lo que ya supera sus reglas de publicación.</p></div><a href={`/ayuntamientos/${municipality.slug}`} target="_blank" rel="noreferrer">Abrir aparte ↗</a></div>
        <iframe title={`Resultado público de ${municipality.name}`} src={`/ayuntamientos/${municipality.slug}`} className={styles.frame} />
      </article>
    </section> : null}
  </main>;
}
