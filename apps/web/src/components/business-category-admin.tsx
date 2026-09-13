'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { businessAdminApi, type AdminBusinessCategory } from '@/lib/business-admin-source';
import styles from './business-admin.module.css';

type FormState = {
  id: string | null;
  name: string;
  slug: string;
  description: string;
  parentId: string;
  sortOrder: string;
  active: boolean;
};

const emptyForm: FormState = {
  id: null,
  name: '',
  slug: '',
  description: '',
  parentId: '',
  sortOrder: '0',
  active: true,
};

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function formFromCategory(category: AdminBusinessCategory): FormState {
  return {
    id: category.id,
    name: category.name,
    slug: category.slug,
    description: category.description ?? '',
    parentId: category.parent_id ?? '',
    sortOrder: String(category.sort_order),
    active: category.active,
  };
}

export function BusinessCategoryAdmin() {
  const auth = useAuth();
  const [categories, setCategories] = useState<AdminBusinessCategory[]>([]);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    const catalog = await businessAdminApi.catalog();
    setCategories(catalog.categories);
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) {
        setDenied(true);
        return;
      }
      setError('No se han podido cargar las categorías.');
    });
  }, [auth.status, load]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (!needle) return categories;
    return categories.filter((category) => `${category.name} ${category.slug} ${category.description ?? ''}`.toLocaleLowerCase('es').includes(needle));
  }, [categories, query]);

  async function save() {
    const name = form.name.trim();
    const slug = slugify(form.slug || form.name);
    if (!name || !slug) {
      setError('Nombre y slug son obligatorios.');
      return;
    }
    if (form.parentId && form.parentId === form.id) {
      setError('Una categoría no puede depender de sí misma.');
      return;
    }

    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const payload = {
        name,
        slug,
        description: form.description.trim() || null,
        parentId: form.parentId || null,
        sortOrder: Math.max(0, Number(form.sortOrder) || 0),
        active: form.active,
      };
      if (form.id) await businessAdminApi.updateCategory(form.id, payload);
      else await businessAdminApi.createCategory(payload);
      await load();
      setForm(emptyForm);
      setMessage(form.id ? 'Categoría actualizada.' : 'Categoría creada.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar la categoría. Comprueba que el slug no esté repetido.');
    } finally {
      setBusy(false);
    }
  }

  async function toggle(category: AdminBusinessCategory) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await businessAdminApi.updateCategory(category.id, { active: !category.active });
      await load();
      if (form.id === category.id) setForm((current) => ({ ...current, active: !category.active }));
      setMessage(category.active ? 'Categoría desactivada.' : 'Categoría activada.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido cambiar el estado de la categoría.');
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === 'loading') return <main className={styles.login}><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.login}><div><p className={styles.eyebrow}>Mágina Olivo · Categorías</p><h1>Acceso corporativo</h1><p>Inicia sesión para administrar la taxonomía de empresas.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.login}><div><p className={styles.eyebrow}>Acceso restringido</p><h1>Sin permisos de plataforma</h1><p>Esta cuenta no puede administrar categorías.</p></div></main>;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><a href="/admin/empresas">← Empresas</a><p className={styles.eyebrow}>TAXONOMÍA DEL DIRECTORIO</p><h1>Categorías de empresas</h1><p>Crea, ordena y desactiva categorías sin eliminar relaciones históricas.</p></div>
      <div className={styles.actions}><a className={styles.secondaryButton} href="/explorar/empresas" target="_blank">Ver directorio ↗</a><button className={styles.secondaryButton} disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error} role="alert">{error}</div> : null}

    <section className={styles.layout}>
      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Categorías</h2><p>{categories.length} definidas · {categories.filter((category) => category.active).length} activas.</p></div><button className={styles.button} type="button" onClick={() => setForm(emptyForm)}>+ Nueva</button></div>
        <input className={styles.search} type="search" placeholder="Buscar categoría…" value={query} onChange={(event) => setQuery(event.target.value)} />
        <div className={styles.businessList}>{filtered.map((category) => <button key={category.id} type="button" className={`${styles.businessRow} ${form.id === category.id ? styles.selectedRow : ''}`} onClick={() => setForm(formFromCategory(category))}>
          <span><strong>{category.name}</strong><small>/{category.slug} · {category.business_count} empresas</small></span>
          <span className={styles.rowBadges}><i>{category.active ? 'activa' : 'inactiva'}</i></span>
        </button>)}</div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>{form.id ? 'Editar categoría' : 'Nueva categoría'}</h2><p>Los slugs existentes deben cambiarse solo cuando sea necesario, porque pueden estar enlazados desde filtros públicos.</p></div></div>
        <div className={styles.formGrid}>
          <label>Nombre<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value, slug: form.id ? form.slug : slugify(event.target.value) })} /></label>
          <label>Slug<input value={form.slug} onChange={(event) => setForm({ ...form, slug: slugify(event.target.value) })} /></label>
          <label className={styles.wide}>Descripción<textarea rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
          <label>Categoría superior<select value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}><option value="">Sin categoría superior</option>{categories.filter((category) => category.id !== form.id).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
          <label>Orden<input type="number" min="0" value={form.sortOrder} onChange={(event) => setForm({ ...form, sortOrder: event.target.value })} /></label>
          <label className={styles.checkbox}><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} />Visible / activa</label>
        </div>
        <div className={styles.actions}><button className={styles.button} type="button" disabled={busy} onClick={() => void save()}>{busy ? 'Guardando…' : 'Guardar categoría'}</button>{form.id ? <button className={styles.secondaryButton} type="button" disabled={busy} onClick={() => { const category = categories.find((item) => item.id === form.id); if (category) void toggle(category); }}>{form.active ? 'Desactivar' : 'Activar'}</button> : null}</div>
      </article>
    </section>
  </main>;
}
