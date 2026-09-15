'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '../lib/api-client';
import {
  adminApi,
  type AdminAuditEntry,
  type AdminOverview,
  type AdminSession,
  type AdminUser,
  type CmsEntry,
  type CmsEntryStatus,
  type CmsEntryType,
  type PlatformAdminRole,
  type SiteSetting,
} from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

const tabs = ['resumen', 'contenido', 'usuarios', 'ajustes', 'auditoria'] as const;
type Tab = (typeof tabs)[number];

const roleLabels: Record<PlatformAdminRole, string> = {
  super_admin: 'Superadministrador',
  admin: 'Administrador',
  editor: 'Editor',
  support: 'Soporte',
};

const contentLabels: Record<CmsEntryType, string> = {
  page: 'Página', news: 'Noticia', event: 'Evento', place: 'Pueblo / lugar', mill: 'Almazara / cooperativa', directory: 'Directorio', promotion: 'Promoción', alert: 'Aviso',
};

const statusLabels: Record<CmsEntryStatus, string> = { draft: 'Borrador', published: 'Publicado', archived: 'Archivado' };

type ContentDraft = {
  id: string | null;
  type: CmsEntryType;
  slug: string;
  title: string;
  summary: string;
  contentJson: string;
  status: CmsEntryStatus;
  featured: boolean;
  mediaUrl: string;
  externalUrl: string;
  sortOrder: string;
};

const emptyContent: ContentDraft = {
  id: null,
  type: 'news',
  slug: '',
  title: '',
  summary: '',
  contentJson: '{}',
  status: 'draft',
  featured: false,
  mediaUrl: '',
  externalUrl: '',
  sortOrder: '0',
};

function formatDate(value: string | Date | null | undefined) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function canEdit(role: PlatformAdminRole) {
  return role === 'super_admin' || role === 'admin' || role === 'editor';
}

function canManageUsers(role: PlatformAdminRole) {
  return role === 'super_admin' || role === 'admin';
}

function contentDraft(entry: CmsEntry): ContentDraft {
  return {
    id: entry.id,
    type: entry.type,
    slug: entry.slug,
    title: entry.title,
    summary: entry.summary ?? '',
    contentJson: JSON.stringify(entry.content_json ?? {}, null, 2),
    status: entry.status,
    featured: entry.featured,
    mediaUrl: entry.media_url ?? '',
    externalUrl: entry.external_url ?? '',
    sortOrder: String(entry.sort_order ?? 0),
  };
}

export function AdminControlCenter() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [tab, setTab] = useState<Tab>('resumen');
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [settings, setSettings] = useState<SiteSetting[]>([]);
  const [audit, setAudit] = useState<AdminAuditEntry[]>([]);
  const [content, setContent] = useState<ContentDraft>(emptyContent);
  const [settingKey, setSettingKey] = useState('');
  const [settingDescription, setSettingDescription] = useState('');
  const [settingValue, setSettingValue] = useState('{}');
  const [settingPublic, setSettingPublic] = useState(false);
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const role = session?.platform_access.role ?? null;

  const loadData = useCallback(async (adminSession: AdminSession) => {
    const [overviewPayload, contentPayload, usersPayload, settingsPayload] = await Promise.all([
      adminApi.overview(), adminApi.content(), adminApi.users(), adminApi.settings(),
    ]);
    setOverview(overviewPayload);
    setEntries(contentPayload.entries);
    setUsers(usersPayload.users);
    setSettings(settingsPayload.settings);
    if (adminSession.platform_access.role === 'admin' || adminSession.platform_access.role === 'super_admin') {
      const auditPayload = await adminApi.audit();
      setAudit(auditPayload.entries);
    } else {
      setAudit([]);
    }
  }, []);

  const hydrateAdmin = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setError(null);
    setDenied(false);
    try {
      const adminSession = await adminApi.session();
      setSession(adminSession);
      await loadData(adminSession);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
        return;
      }
      setError('No se ha podido cargar el panel de administración.');
    }
  }, [auth.status, loadData]);

  useEffect(() => {
    if (auth.status === 'authenticated') void hydrateAdmin();
    if (auth.status === 'anonymous') {
      setSession(null);
      setDenied(false);
    }
  }, [auth.status, hydrateAdmin]);

  const filteredUsers = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return users;
    return users.filter((user) => `${user.display_name} ${user.primary_email ?? ''}`.toLowerCase().includes(query));
  }, [search, users]);

  const run = useCallback(async (task: () => Promise<void>, success: string) => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await task();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido completar la operación. Revisa los datos y vuelve a intentarlo.');
    } finally {
      setBusy(false);
    }
  }, []);

  async function refresh() {
    if (!session) return;
    await loadData(session);
  }

  async function saveContent() {
    if (!session || !canEdit(session.platform_access.role)) return;
    let parsedJson: unknown;
    try {
      parsedJson = JSON.parse(content.contentJson || '{}');
    } catch {
      setError('El contenido JSON no es válido.');
      return;
    }
    const payload = {
      type: content.type,
      slug: content.slug.trim(),
      title: content.title.trim(),
      summary: content.summary.trim() || null,
      content_json: parsedJson,
      status: content.status,
      featured: content.featured,
      media_url: content.mediaUrl.trim() || null,
      external_url: content.externalUrl.trim() || null,
      sort_order: Number(content.sortOrder) || 0,
    };
    await run(async () => {
      if (content.id) await adminApi.updateContent(content.id, payload);
      else await adminApi.createContent(payload);
      setContent(emptyContent);
      await refresh();
    }, content.id ? 'Contenido actualizado.' : 'Contenido creado.');
  }

  async function archiveContent(id: string) {
    await run(async () => {
      await adminApi.archiveContent(id);
      if (content.id === id) setContent(emptyContent);
      await refresh();
    }, 'Contenido archivado.');
  }

  async function changeUserStatus(user: AdminUser) {
    const next = user.status === 'active' ? 'suspended' : 'active';
    await run(async () => {
      await adminApi.setUserStatus(user.id, next);
      await refresh();
    }, next === 'active' ? 'Usuario reactivado.' : 'Usuario suspendido.');
  }

  async function changePlatformRole(userId: string, nextRole: PlatformAdminRole) {
    await run(async () => {
      await adminApi.setPlatformAccess(userId, nextRole, 'active');
      await refresh();
    }, 'Permiso de plataforma actualizado.');
  }

  async function revokePlatformRole(user: AdminUser) {
    const currentRole = user.platform_access?.role ?? 'support';
    await run(async () => {
      await adminApi.setPlatformAccess(user.id, currentRole, 'revoked');
      await refresh();
    }, 'Acceso administrativo revocado.');
  }

  async function saveSetting() {
    let parsedValue: unknown;
    try {
      parsedValue = JSON.parse(settingValue || '{}');
    } catch {
      setError('El valor del ajuste debe ser JSON válido.');
      return;
    }
    await run(async () => {
      await adminApi.saveSetting(settingKey.trim(), parsedValue, settingDescription.trim() || null, settingPublic);
      setSettingKey('');
      setSettingDescription('');
      setSettingValue('{}');
      setSettingPublic(false);
      await refresh();
    }, 'Ajuste guardado.');
  }

  if (auth.status === 'loading') {
    return <main className="admin-gate"><div className="admin-gate-card"><strong>Comprobando sesión…</strong></div></main>;
  }

  if (auth.status === 'anonymous') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Mágina Olivo · Administración</span>
          <h1>Acceso corporativo</h1>
          <p>Entra con la cuenta Google corporativa autorizada. El servidor valida el correo y los permisos antes de abrir el panel.</p>
          <GoogleSignInButton />
          <small>Los usuarios normales de Mi Campo no obtienen acceso administrativo.</small>
        </div>
      </main>
    );
  }

  if (denied) {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Acceso restringido</span>
          <h1>Esta cuenta no administra Mágina Olivo</h1>
          <p>{auth.user?.primary_email ?? 'Tu cuenta'} tiene sesión válida, pero no tiene un permiso de plataforma activo.</p>
          <button className="admin-button secondary" onClick={() => void auth.logout()}>Salir y usar otra cuenta</button>
        </div>
      </main>
    );
  }

  if (!session || !role) {
    return <main className="admin-gate"><div className="admin-gate-card"><strong>Cargando centro de control…</strong>{error ? <p className="admin-error">{error}</p> : null}</div></main>;
  }

  return (
    <main className="admin-shell">
      <aside className="admin-sidebar">
        <div>
          <span className="admin-eyebrow">Mágina Olivo V20</span>
          <h1>Centro de control</h1>
          <p className="admin-muted">{session.user.primary_email}</p>
          <span className="admin-role">{roleLabels[role]}</span>
        </div>
        <nav className="admin-nav" aria-label="Administración">
          {tabs.map((item) => {
            if (item === 'auditoria' && !canManageUsers(role)) return null;
            return <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item}</button>;
          })}
        </nav>
        <div className="admin-sidebar-footer">
          <a href="/">Ver web</a>
          <button onClick={() => void auth.logout()}>Cerrar sesión</button>
        </div>
      </aside>

      <section className="admin-main">
        <header className="admin-main-header">
          <div><span className="admin-eyebrow">Administración de plataforma</span><h2>{tab[0].toUpperCase() + tab.slice(1)}</h2></div>
          <button className="admin-button secondary" disabled={busy} onClick={() => void refresh()}>Actualizar</button>
        </header>

        {message ? <div className="admin-notice success">{message}</div> : null}
        {error ? <div className="admin-notice error">{error}</div> : null}

        {tab === 'resumen' && overview ? (
          <div className="admin-stack">
            <div className="admin-metrics">
              <article><span>Usuarios</span><strong>{overview.metrics.users}</strong></article>
              <article><span>Espacios</span><strong>{overview.metrics.workspaces}</strong></article>
              <article><span>Fincas activas</span><strong>{overview.metrics.active_fields}</strong></article>
              <article><span>Contenido</span><strong>{overview.metrics.content_entries}</strong></article>
              <article><span>Publicado</span><strong>{overview.metrics.published_entries}</strong></article>
            </div>
            <div className="admin-card">
              <div className="admin-card-title"><div><h3>Qué puedes controlar aquí</h3><p>Operación real de la plataforma, separada de los permisos de cada finca.</p></div></div>
              <div className="admin-capabilities">
                <span>Usuarios y bloqueos</span><span>Noticias y eventos</span><span>Pueblos y lugares</span><span>Almazaras y cooperativas</span><span>Promociones y avisos</span><span>Ajustes públicos</span><span>Roles corporativos</span><span>Auditoría</span>
              </div>
            </div>
            <div className="admin-card">
              <div className="admin-card-title"><div><h3>Actividad reciente</h3><p>Últimos cambios administrativos registrados.</p></div></div>
              <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Acción</th><th>Objeto</th><th>Rol</th><th>Fecha</th></tr></thead><tbody>
                {overview.recent_audit.map((entry) => <tr key={entry.id}><td>{entry.action}</td><td>{entry.target_type}</td><td>{entry.actor_role}</td><td>{formatDate(entry.created_at)}</td></tr>)}
                {!overview.recent_audit.length ? <tr><td colSpan={4}>Aún no hay cambios registrados.</td></tr> : null}
              </tbody></table></div>
            </div>
          </div>
        ) : null}

        {tab === 'contenido' ? (
          <div className="admin-grid-two">
            <div className="admin-card">
              <div className="admin-card-title"><div><h3>Contenido de la web</h3><p>Publica y actualiza contenido sin tocar código.</p></div>{canEdit(role) ? <button className="admin-button secondary" onClick={() => setContent(emptyContent)}>Nuevo</button> : null}</div>
              <div className="admin-content-list">
                {entries.map((entry) => <button key={entry.id} className={`admin-content-row ${content.id === entry.id ? 'selected' : ''}`} onClick={() => setContent(contentDraft(entry))}>
                  <span><strong>{entry.title}</strong><small>{contentLabels[entry.type]} · /{entry.slug}</small></span><span className={`admin-status ${entry.status}`}>{statusLabels[entry.status]}</span>
                </button>)}
                {!entries.length ? <p className="admin-muted">Todavía no hay contenido gestionado.</p> : null}
              </div>
            </div>
            <div className="admin-card admin-form-card">
              <div className="admin-card-title"><div><h3>{content.id ? 'Editar contenido' : 'Nuevo contenido'}</h3><p>{canEdit(role) ? 'Los cambios publicados quedan disponibles para la web pública.' : 'Tu rol es de consulta.'}</p></div></div>
              <label>Tipo<select disabled={!canEdit(role)} value={content.type} onChange={(e) => setContent((current) => ({ ...current, type: e.target.value as CmsEntryType }))}>{Object.entries(contentLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <label>Título<input disabled={!canEdit(role)} value={content.title} onChange={(e) => setContent((current) => ({ ...current, title: e.target.value }))} /></label>
              <label>Slug<input disabled={!canEdit(role)} placeholder="fiesta-del-olivar" value={content.slug} onChange={(e) => setContent((current) => ({ ...current, slug: e.target.value.toLowerCase().replace(/\s+/g, '-') }))} /></label>
              <label>Resumen<textarea disabled={!canEdit(role)} rows={3} value={content.summary} onChange={(e) => setContent((current) => ({ ...current, summary: e.target.value }))} /></label>
              <label>Contenido estructurado (JSON)<textarea className="admin-code" disabled={!canEdit(role)} rows={8} value={content.contentJson} onChange={(e) => setContent((current) => ({ ...current, contentJson: e.target.value }))} /></label>
              <div className="admin-field-grid"><label>Estado<select disabled={!canEdit(role)} value={content.status} onChange={(e) => setContent((current) => ({ ...current, status: e.target.value as CmsEntryStatus }))}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Orden<input disabled={!canEdit(role)} type="number" value={content.sortOrder} onChange={(e) => setContent((current) => ({ ...current, sortOrder: e.target.value }))} /></label></div>
              <label>Imagen / media URL<input disabled={!canEdit(role)} value={content.mediaUrl} onChange={(e) => setContent((current) => ({ ...current, mediaUrl: e.target.value }))} /></label>
              <label>Enlace externo<input disabled={!canEdit(role)} value={content.externalUrl} onChange={(e) => setContent((current) => ({ ...current, externalUrl: e.target.value }))} /></label>
              <label className="admin-check"><input disabled={!canEdit(role)} type="checkbox" checked={content.featured} onChange={(e) => setContent((current) => ({ ...current, featured: e.target.checked }))} /> Destacar este contenido</label>
              {canEdit(role) ? <div className="admin-actions"><button className="admin-button" disabled={busy || !content.title || !content.slug} onClick={() => void saveContent()}>Guardar</button>{content.id ? <button className="admin-button danger" disabled={busy} onClick={() => void archiveContent(content.id!)}>Archivar</button> : null}</div> : null}
            </div>
          </div>
        ) : null}

        {tab === 'usuarios' ? (
          <div className="admin-card">
            <div className="admin-card-title"><div><h3>Usuarios y acceso</h3><p>Suspende cuentas y, como superadministrador, concede acceso corporativo.</p></div><input className="admin-search" placeholder="Buscar usuario…" value={search} onChange={(e) => setSearch(e.target.value)} /></div>
            <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Usuario</th><th>Fincas/espacios</th><th>Último acceso</th><th>Estado</th><th>Plataforma</th><th>Acción</th></tr></thead><tbody>
              {filteredUsers.map((user) => <tr key={user.id}>
                <td><strong>{user.display_name}</strong><small>{user.primary_email ?? 'Sin correo'}</small></td><td>{user.workspace_count}</td><td>{formatDate(user.last_login_at)}</td><td><span className={`admin-status ${user.status === 'active' ? 'published' : 'archived'}`}>{user.status === 'active' ? 'Activo' : 'Suspendido'}</span></td>
                <td>{role === 'super_admin' ? <div className="admin-inline"><select value={user.platform_access?.role ?? 'support'} onChange={(e) => void changePlatformRole(user.id, e.target.value as PlatformAdminRole)}>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{user.platform_access?.status === 'active' ? <button className="admin-link danger-text" onClick={() => void revokePlatformRole(user)}>Revocar</button> : null}</div> : (user.platform_access?.status === 'active' ? roleLabels[user.platform_access.role] : '—')}</td>
                <td>{canManageUsers(role) ? <button className="admin-link" disabled={busy || user.id === session.user.id} onClick={() => void changeUserStatus(user)}>{user.status === 'active' ? 'Suspender' : 'Reactivar'}</button> : 'Consulta'}</td>
              </tr>)}
            </tbody></table></div>
          </div>
        ) : null}

        {tab === 'ajustes' ? (
          <div className="admin-grid-two">
            <div className="admin-card"><div className="admin-card-title"><div><h3>Ajustes globales</h3><p>Variables editables de la web. Solo los marcados públicos salen por la API pública.</p></div></div><div className="admin-setting-list">{settings.map((setting) => <button key={setting.key} className="admin-setting-row" onClick={() => { setSettingKey(setting.key); setSettingDescription(setting.description ?? ''); setSettingValue(JSON.stringify(setting.value_json, null, 2)); setSettingPublic(setting.is_public); }}><span><strong>{setting.key}</strong><small>{setting.description ?? 'Sin descripción'}</small></span><span>{setting.is_public ? 'Público' : 'Privado'}</span></button>)}{!settings.length ? <p className="admin-muted">No hay ajustes definidos.</p> : null}</div></div>
            <div className="admin-card admin-form-card"><div className="admin-card-title"><div><h3>Editar ajuste</h3><p>Ejemplos: home.hero, contact.phone, alerts.banner.</p></div></div><label>Clave<input disabled={!canEdit(role)} value={settingKey} onChange={(e) => setSettingKey(e.target.value.toLowerCase().replace(/\s+/g, '-'))} /></label><label>Descripción<input disabled={!canEdit(role)} value={settingDescription} onChange={(e) => setSettingDescription(e.target.value)} /></label><label>Valor JSON<textarea className="admin-code" disabled={!canEdit(role)} rows={10} value={settingValue} onChange={(e) => setSettingValue(e.target.value)} /></label><label className="admin-check"><input disabled={!canEdit(role)} type="checkbox" checked={settingPublic} onChange={(e) => setSettingPublic(e.target.checked)} /> Disponible para la web pública</label>{canEdit(role) ? <button className="admin-button" disabled={busy || !settingKey.trim()} onClick={() => void saveSetting()}>Guardar ajuste</button> : null}</div>
          </div>
        ) : null}

        {tab === 'auditoria' && canManageUsers(role) ? (
          <div className="admin-card"><div className="admin-card-title"><div><h3>Auditoría administrativa</h3><p>Quién cambió qué y cuándo. Los eventos son de solo lectura.</p></div></div><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Fecha</th><th>Rol</th><th>Acción</th><th>Tipo</th><th>Identificador</th></tr></thead><tbody>{audit.map((entry) => <tr key={entry.id}><td>{formatDate(entry.created_at)}</td><td>{entry.actor_role}</td><td>{entry.action}</td><td>{entry.target_type}</td><td><code>{entry.target_id ?? '—'}</code></td></tr>)}{!audit.length ? <tr><td colSpan={5}>Aún no hay eventos.</td></tr> : null}</tbody></table></div></div>
        ) : null}
      </section>
    </main>
  );
}
