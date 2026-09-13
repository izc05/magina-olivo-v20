'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry, type CmsEntryStatus } from '@/lib/admin-data-source';
import '../../admin.css';
import styles from './editorial.module.css';

type MunicipalityRole = 'profile' | 'heritage' | 'nature' | 'tourism' | '';

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function textField(entry: CmsEntry, key: string) {
  const value = asObject(entry.content_json)[key];
  return typeof value === 'string' ? value : '';
}

function roleOf(entry: CmsEntry): MunicipalityRole {
  const role = textField(entry, 'municipality_role');
  return role === 'profile' || role === 'heritage' || role === 'nature' || role === 'tourism' ? role : '';
}

function roleLabel(role: MunicipalityRole) {
  if (role === 'profile') return 'Perfil principal';
  if (role === 'heritage') return 'Patrimonio';
  if (role === 'nature') return 'Naturaleza';
  if (role === 'tourism') return 'Turismo';
  return 'Sin función';
}

function updatePayload(entry: CmsEntry, patch: Partial<Pick<CmsEntry, 'summary' | 'status' | 'featured' | 'media_url' | 'sort_order'>> = {}, contentJson = entry.content_json) {
  return {
    type: entry.type,
    title: entry.title,
    slug: entry.slug,
    summary: patch.summary !== undefined ? patch.summary : entry.summary,
    content_json: contentJson,
    status: patch.status ?? entry.status,
    featured: patch.featured ?? entry.featured,
    starts_at: entry.starts_at,
    ends_at: entry.ends_at,
    media_url: patch.media_url !== undefined ? patch.media_url : entry.media_url,
    external_url: entry.external_url,
    sort_order: patch.sort_order ?? entry.sort_order,
  };
}

export default function MunicipalityEditorialAdminPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [municipalityId, setMunicipalityId] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [content, territory] = await Promise.all([adminApi.content(), adminApi.territoryCatalog()]);
    const active = territory.municipalities.filter((item) => item.active);
    setMunicipalities(active);
    setEntries(content.entries.filter((entry) => entry.type === 'place'));
    setMunicipalityId((current) => current || active[0]?.id || '');
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void load().catch(() => setError('No se ha podido cargar la configuración editorial municipal.'));
  }, [auth.status, load]);

  const selectedMunicipality = municipalities.find((item) => item.id === municipalityId) ?? null;
  const municipalEntries = useMemo(() => entries.filter((entry) => textField(entry, 'municipality_id') === municipalityId), [entries, municipalityId]);
  const profile = useMemo(() => municipalEntries.find((entry) => roleOf(entry) === 'profile') ?? null, [municipalEntries]);
  const discoveries = useMemo(() => municipalEntries
    .filter((entry) => ['heritage', 'nature', 'tourism'].includes(roleOf(entry)))
    .sort((a, b) => Number(b.featured) - Number(a.featured) || b.sort_order - a.sort_order || a.title.localeCompare(b.title, 'es')),
  [municipalEntries]);
  const featuredCount = discoveries.filter((entry) => entry.featured).length;

  async function saveEntry(entry: CmsEntry, patch: Partial<Pick<CmsEntry, 'summary' | 'status' | 'featured' | 'media_url' | 'sort_order'>>, success: string) {
    setBusyId(entry.id); setMessage(null); setError(null);
    try {
      await adminApi.updateContent(entry.id, updatePayload(entry, patch));
      await load();
      setMessage(success);
    } catch {
      setError('No se ha podido guardar la decisión editorial.');
    } finally {
      setBusyId(null);
    }
  }

  async function setProfile(next: CmsEntry) {
    if (!selectedMunicipality) return;
    setBusyId(`profile:${next.id}`); setMessage(null); setError(null);
    try {
      if (profile && profile.id !== next.id) {
        const previous = { ...asObject(profile.content_json) };
        delete previous.municipality_role;
        await adminApi.updateContent(profile.id, updatePayload(profile, {}, previous));
      }
      const nextContent = { ...asObject(next.content_json), municipality_role: 'profile' };
      await adminApi.updateContent(next.id, updatePayload(next, { featured: false }, nextContent));
      await load();
      setMessage(`${next.title} es ahora el perfil principal de ${selectedMunicipality.name}.`);
    } catch {
      setError('No se ha podido cambiar el perfil principal.');
    } finally {
      setBusyId(null);
    }
  }

  async function toggleFeatured(entry: CmsEntry, nextFeatured: boolean) {
    if (nextFeatured && !entry.featured && featuredCount >= 3) {
      setError('Solo puede haber tres imprescindibles destacados. Desmarca uno antes de añadir otro.');
      return;
    }
    await saveEntry(entry, { featured: nextFeatured }, nextFeatured ? `${entry.title} añadido a Lo imprescindible.` : `${entry.title} retirado de Lo imprescindible.`);
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Portada editorial municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar">
      <div><a href="/admin/ayuntamientos">← Centro de control municipal</a><h1>Portada editorial municipal</h1><p>Controla perfil, imagen hero y “Lo imprescindible” con contenido CMS real.</p></div>
      {selectedMunicipality ? <a href={`/ayuntamientos/${selectedMunicipality.slug}`} target="_blank" rel="noreferrer">Ver resultado público ↗</a> : null}
    </header>

    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}

    <section className={`admin-card ${styles.selectorCard}`}>
      <label><span>Municipio</span><select value={municipalityId} onChange={(event) => { setMunicipalityId(event.target.value); setMessage(null); setError(null); }}>{municipalities.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <div><strong>{municipalEntries.length}</strong><span>lugares CMS vinculados</span></div>
      <div><strong>{featuredCount}/3</strong><span>imprescindibles elegidos</span></div>
    </section>

    {selectedMunicipality ? <>
      <section className={`admin-card ${styles.profileCard}`}>
        <div className={styles.sectionHeading}><div><span className={styles.kicker}>HERO Y PERFIL</span><h2>Perfil principal de {selectedMunicipality.name}</h2><p>Su imagen alimenta el hero público y su resumen presenta el municipio.</p></div><a href="/admin/ayuntamientos/patrimonio">Clasificación avanzada →</a></div>
        {profile ? <div className={styles.profileEditor}>
          <div className={styles.preview} style={profile.media_url ? { backgroundImage: `linear-gradient(rgba(20,50,31,.55),rgba(20,50,31,.55)),url("${profile.media_url.replace(/"/g, '%22')}")` } : undefined}><span>{profile.media_url ? 'Hero actual' : 'Sin imagen hero'}</span><strong>{profile.title}</strong></div>
          <div className={styles.formGrid}>
            <label>Resumen de portada<textarea defaultValue={profile.summary ?? ''} onBlur={(event) => { const value = event.currentTarget.value.trim() || null; if (value !== profile.summary) void saveEntry(profile, { summary: value }, 'Resumen de portada actualizado.'); }} /></label>
            <label>Imagen hero / media URL<input defaultValue={profile.media_url ?? ''} placeholder="/media/... o URL de un recurso real" onBlur={(event) => { const value = event.currentTarget.value.trim() || null; if (value !== profile.media_url) void saveEntry(profile, { media_url: value }, 'Imagen hero actualizada.'); }} /></label>
          </div>
        </div> : <div className={styles.empty}><strong>Este municipio todavía no tiene perfil principal.</strong><p>Selecciona uno de sus lugares vinculados para convertirlo en perfil. Al hacerlo deja de ser descubrimiento y pasa a representar al municipio.</p></div>}

        <div className={styles.profileCandidates}>
          {municipalEntries.map((entry) => <button key={entry.id} type="button" disabled={busyId === `profile:${entry.id}` || profile?.id === entry.id} onClick={() => void setProfile(entry)}><span>{roleLabel(roleOf(entry))}</span><strong>{entry.title}</strong><small>{profile?.id === entry.id ? 'Perfil actual' : 'Usar como perfil principal'}</small></button>)}
          {!municipalEntries.length ? <p>No hay lugares CMS vinculados a este municipio.</p> : null}
        </div>
      </section>

      <section className="admin-card">
        <div className={styles.sectionHeading}><div><span className={styles.kicker}>LO IMPRESCINDIBLE</span><h2>Destacados de portada</h2><p>Máximo tres. La prioridad más alta aparece primero. La web conserva después diversidad por categorías cuando faltan destacados.</p></div><strong>{featuredCount}/3 seleccionados</strong></div>
        <div className={styles.discoveryList}>
          {discoveries.map((entry) => <article key={entry.id} className={entry.featured ? styles.discoveryFeatured : styles.discoveryRow}>
            <div className={styles.discoveryIdentity}>{entry.media_url ? <div className={styles.thumb} style={{ backgroundImage: `url("${entry.media_url.replace(/"/g, '%22')}")` }} /> : <div className={styles.thumbFallback}>—</div>}<div><span>{roleLabel(roleOf(entry))} · {entry.status}</span><h3>{entry.title}</h3>{entry.summary ? <p>{entry.summary}</p> : null}</div></div>
            <div className={styles.discoveryControls}>
              <label className={styles.featureToggle}><input type="checkbox" checked={entry.featured} disabled={busyId === entry.id} onChange={(event) => void toggleFeatured(entry, event.target.checked)} /><span>Imprescindible</span></label>
              <label>Prioridad<input type="number" min="0" max="9999" defaultValue={entry.sort_order} disabled={busyId === entry.id} onBlur={(event) => { const next = Math.max(0, Math.min(9999, Number.parseInt(event.currentTarget.value || '0', 10) || 0)); if (next !== entry.sort_order) void saveEntry(entry, { sort_order: next }, `${entry.title}: prioridad actualizada.`); }} /></label>
              <label>Estado<select value={entry.status} disabled={busyId === entry.id} onChange={(event) => void saveEntry(entry, { status: event.target.value as CmsEntryStatus }, `${entry.title}: estado actualizado.`)}><option value="draft">Borrador</option><option value="published">Publicado</option><option value="archived">Archivado</option></select></label>
              <label>Imagen<input defaultValue={entry.media_url ?? ''} disabled={busyId === entry.id} placeholder="Media URL real" onBlur={(event) => { const value = event.currentTarget.value.trim() || null; if (value !== entry.media_url) void saveEntry(entry, { media_url: value }, `${entry.title}: imagen actualizada.`); }} /></label>
            </div>
          </article>)}
          {!discoveries.length ? <div className={styles.empty}><strong>Sin descubrimientos clasificados.</strong><p>Usa Patrimonio y turismo para vincular contenido como patrimonio, naturaleza o turismo.</p></div> : null}
        </div>
      </section>
    </> : null}
  </main>;
}
