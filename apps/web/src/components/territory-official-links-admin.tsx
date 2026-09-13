'use client';

import { useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import {
  createAdminMunicipalityOfficialLink,
  deleteAdminMunicipalityOfficialLink,
  loadAdminMunicipalityOfficialLinks,
  updateAdminMunicipalityOfficialLink,
  type AdminMunicipalityOfficialLink,
  type AdminMunicipalitySummary,
} from '../lib/territory-official-links-admin-source';

type FormState = {
  municipalityId: string;
  kind: AdminMunicipalityOfficialLink['kind'];
  label: string;
  url: string;
  sourceUrl: string;
  verified: boolean;
  active: boolean;
  sortOrder: string;
};

const emptyForm: FormState = {
  municipalityId: '',
  kind: 'town_hall',
  label: '',
  url: '',
  sourceUrl: '',
  verified: false,
  active: true,
  sortOrder: '0',
};

const kindLabels: Record<AdminMunicipalityOfficialLink['kind'], string> = {
  town_hall: 'Web municipal',
  electronic_office: 'Sede electrónica',
  transparency: 'Portal de transparencia',
  tourism: 'Turismo oficial',
  other_official: 'Otro servicio oficial',
};

export function TerritoryOfficialLinksAdmin() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminMunicipalitySummary[]>([]);
  const [links, setLinks] = useState<AdminMunicipalityOfficialLink[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    const payload = await loadAdminMunicipalityOfficialLinks();
    setMunicipalities(payload.municipalities);
    setLinks(payload.links);
    setForm((current) => ({ ...current, municipalityId: current.municipalityId || payload.municipalities[0]?.id || '' }));
  }

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void reload().catch(() => setError('No se han podido cargar los enlaces oficiales municipales.'));
  }, [auth.status]);

  const grouped = useMemo(() => municipalities.map((municipality) => ({
    municipality,
    links: links.filter((link) => link.municipality_id === municipality.id),
  })), [municipalities, links]);

  function edit(link: AdminMunicipalityOfficialLink) {
    setEditingId(link.id);
    setForm({
      municipalityId: link.municipality_id,
      kind: link.kind,
      label: link.label,
      url: link.url,
      sourceUrl: link.source_url ?? '',
      verified: Boolean(link.verified_at),
      active: link.active,
      sortOrder: String(link.sort_order),
    });
    setMessage(null);
    setError(null);
  }

  function reset() {
    setEditingId(null);
    setForm({ ...emptyForm, municipalityId: municipalities[0]?.id ?? '' });
  }

  async function save() {
    if (!form.municipalityId || !form.label.trim() || !form.url.trim()) {
      setError('Municipio, etiqueta y URL son obligatorios.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const common = {
        kind: form.kind,
        label: form.label.trim(),
        url: form.url.trim(),
        source_url: form.sourceUrl.trim() || null,
        verified: form.verified,
        active: form.active,
        sort_order: Number(form.sortOrder) || 0,
      };
      if (editingId) await updateAdminMunicipalityOfficialLink(editingId, common);
      else await createAdminMunicipalityOfficialLink({ municipality_id: form.municipalityId, ...common });
      await reload();
      reset();
      setMessage(editingId ? 'Enlace oficial actualizado.' : 'Enlace oficial creado.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar. Las altas, verificaciones y bajas de enlaces oficiales requieren rol administrador.');
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!window.confirm('¿Eliminar este enlace oficial? Esta acción queda auditada.')) return;
    setBusy(true);
    setError(null);
    try {
      await deleteAdminMunicipalityOfficialLink(id);
      await reload();
      if (editingId === id) reset();
      setMessage('Enlace oficial eliminado.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido eliminar el enlace. Se requiere rol administrador.');
    } finally {
      setBusy(false);
    }
  }

  if (auth.status !== 'authenticated') return null;

  return <section className="territory-admin-shell" aria-labelledby="official-links-admin-title">
    <div className="territory-admin-card territory-admin-wide">
      <div className="territory-admin-title">
        <div>
          <span className="territory-admin-eyebrow">AYUNTAMIENTOS · FUENTES VERIFICADAS</span>
          <h2 id="official-links-admin-title">Enlaces oficiales municipales</h2>
          <p>Gestiona webs municipales, sedes electrónicas, transparencia y turismo. Un enlace solo aparece públicamente cuando está activo y marcado como verificado.</p>
        </div>
      </div>

      {message ? <div className="territory-admin-notice success">{message}</div> : null}
      {error ? <div className="territory-admin-notice error">{error}</div> : null}

      <div className="territory-admin-form-grid">
        <label>Municipio<select value={form.municipalityId} disabled={Boolean(editingId) || busy} onChange={(event) => setForm((current) => ({ ...current, municipalityId: event.target.value }))}>{municipalities.map((municipality) => <option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label>
        <label>Tipo<select value={form.kind} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, kind: event.target.value as FormState['kind'] }))}>{Object.entries(kindLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Etiqueta<input value={form.label} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, label: event.target.value }))} placeholder="Ayuntamiento de…" /></label>
        <label>URL oficial<input type="url" value={form.url} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, url: event.target.value }))} placeholder="https://…" /></label>
        <label>Fuente de verificación<input type="url" value={form.sourceUrl} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, sourceUrl: event.target.value }))} placeholder="https://…/aviso-legal/" /></label>
        <label>Orden<input type="number" value={form.sortOrder} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, sortOrder: event.target.value }))} /></label>
        <label><input type="checkbox" checked={form.verified} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, verified: event.target.checked }))} /> Verificado para publicación</label>
        <label><input type="checkbox" checked={form.active} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} /> Activo</label>
      </div>
      <div className="territory-admin-actions">
        <button className="territory-admin-btn" type="button" disabled={busy} onClick={() => void save()}>{editingId ? 'Guardar cambios' : 'Añadir enlace'}</button>
        {editingId ? <button className="territory-admin-btn secondary" type="button" disabled={busy} onClick={reset}>Cancelar</button> : null}
      </div>
    </div>

    <div className="territory-admin-grid">
      {grouped.map(({ municipality, links: municipalLinks }) => <article className="territory-admin-card" key={municipality.id}>
        <div className="territory-admin-title"><div><h3>{municipality.name}</h3><p>INE {municipality.ine_code} · {municipalLinks.length} enlaces</p></div></div>
        {municipalLinks.length ? <div className="territory-admin-list">{municipalLinks.map((link) => <div className="territory-admin-row" key={link.id}>
          <div><strong>{link.label}</strong><small>{kindLabels[link.kind]} · {link.active ? 'Activo' : 'Inactivo'} · {link.verified_at ? 'Verificado' : 'Sin verificar'}</small><a href={link.url} target="_blank" rel="noopener noreferrer">{link.url}</a></div>
          <div className="territory-admin-actions"><button className="territory-admin-btn secondary" type="button" disabled={busy} onClick={() => edit(link)}>Editar</button><button className="territory-admin-btn secondary" type="button" disabled={busy} onClick={() => void remove(link.id)}>Eliminar</button></div>
        </div>)}</div> : <p>Sin enlaces oficiales cargados.</p>}
      </article>)}
    </div>
  </section>;
}
