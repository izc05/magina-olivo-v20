'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession, type AdminUser } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-management-console.module.css';

type WorkspaceType = 'family' | 'professional' | 'organization';
type MemberRole = 'owner' | 'admin' | 'manager' | 'member' | 'worker' | 'viewer';
type MemberStatus = 'invited' | 'active' | 'suspended' | 'revoked';
type FieldStatus = 'active' | 'archived';
type WaterRegime = 'secano' | 'regadio' | 'mixto';

type Workspace = {
  id: string;
  name: string;
  type: WorkspaceType;
  created_at: string;
  updated_at: string;
  active_members: number;
  active_owners: number;
  active_fields: number;
  fields_total: number;
  plan: { workspace_id: string; plan_code: string; status: string } | null;
};

type Member = {
  id: string;
  workspace_id: string;
  user_id: string;
  role: MemberRole;
  status: MemberStatus;
  display_name: string;
  primary_email: string | null;
  user_status: string;
};

type Field = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  name: string;
  description: string | null;
  municipality: string | null;
  province: string | null;
  calculated_area_ha: number | null;
  geometry_source: string | null;
  geometry_status: string;
  tree_count: number | null;
  crop: string;
  variety: string | null;
  water_regime: WaterRegime | null;
  planting_year: number | null;
  tenure_type: string | null;
  status: FieldStatus;
  updated_at: string;
};

type FieldDraft = {
  name: string;
  description: string;
  tree_count: string;
  crop: string;
  variety: string;
  water_regime: WaterRegime | '';
  planting_year: string;
  tenure_type: string;
  status: FieldStatus;
};

const workspaceTypeLabels: Record<WorkspaceType, string> = {
  family: 'Familiar', professional: 'Profesional', organization: 'Organización',
};
const roleLabels: Record<MemberRole, string> = {
  owner: 'Propietario', admin: 'Administrador', manager: 'Gestor', member: 'Miembro', worker: 'Trabajador', viewer: 'Consulta',
};
const statusLabels: Record<MemberStatus, string> = {
  invited: 'Invitado', active: 'Activo', suspended: 'Suspendido', revoked: 'Revocado',
};

function canManage(session: AdminSession | null) {
  return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';
}

function fieldToDraft(field: Field): FieldDraft {
  return {
    name: field.name,
    description: field.description ?? '',
    tree_count: field.tree_count ? String(field.tree_count) : '',
    crop: field.crop,
    variety: field.variety ?? '',
    water_regime: field.water_regime ?? '',
    planting_year: field.planting_year ? String(field.planting_year) : '',
    tenure_type: field.tenure_type ?? '',
    status: field.status,
  };
}

function formatNumber(value: number | null, digits = 0) {
  if (value === null) return '—';
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: digits }).format(value);
}

export function AdminManagementConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [fields, setFields] = useState<Field[]>([]);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [workspaceSearch, setWorkspaceSearch] = useState('');
  const [fieldSearch, setFieldSearch] = useState('');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);
  const [workspaceName, setWorkspaceName] = useState('');
  const [workspaceType, setWorkspaceType] = useState<WorkspaceType>('family');
  const [members, setMembers] = useState<Member[]>([]);
  const [memberUserId, setMemberUserId] = useState('');
  const [memberRole, setMemberRole] = useState<MemberRole>('member');
  const [memberStatus, setMemberStatus] = useState<MemberStatus>('active');
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null);
  const [fieldDraft, setFieldDraft] = useState<FieldDraft | null>(null);

  const selectedWorkspace = useMemo(() => workspaces.find((item) => item.id === selectedWorkspaceId) ?? null, [workspaces, selectedWorkspaceId]);
  const selectedField = useMemo(() => fields.find((item) => item.id === selectedFieldId) ?? null, [fields, selectedFieldId]);
  const editable = canManage(session);

  const loadMembers = useCallback(async (workspaceId: string) => {
    const payload = await apiFetch<{ members: Member[] }>(`/api/v1/admin/workspaces/${workspaceId}/members`);
    setMembers(payload.members);
  }, []);

  const loadCore = useCallback(async () => {
    const [workspacePayload, fieldPayload, userPayload] = await Promise.all([
      apiFetch<{ workspaces: Workspace[] }>('/api/v1/admin/workspaces'),
      apiFetch<{ fields: Field[] }>('/api/v1/admin/fields'),
      adminApi.users(),
    ]);
    setWorkspaces(workspacePayload.workspaces);
    setFields(fieldPayload.fields);
    setUsers(userPayload.users);
    setSelectedWorkspaceId((current) => current && workspacePayload.workspaces.some((item) => item.id === current) ? current : workspacePayload.workspaces[0]?.id ?? null);
    setSelectedFieldId((current) => current && fieldPayload.fields.some((item) => item.id === current) ? current : fieldPayload.fields[0]?.id ?? null);
  }, []);

  const hydrate = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    setDenied(false);
    try {
      const currentSession = await adminApi.session();
      setSession(currentSession);
      await loadCore();
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
      } else {
        console.error(caught);
        setError('No se ha podido cargar la gestión administrativa.');
      }
    } finally {
      setLoading(false);
    }
  }, [auth.status, loadCore]);

  useEffect(() => {
    if (auth.status === 'authenticated') void hydrate();
    if (auth.status === 'anonymous') setLoading(false);
  }, [auth.status, hydrate]);

  useEffect(() => {
    if (!selectedWorkspace) {
      setMembers([]);
      return;
    }
    setWorkspaceName(selectedWorkspace.name);
    setWorkspaceType(selectedWorkspace.type);
    void loadMembers(selectedWorkspace.id).catch((caught) => {
      console.error(caught);
      setError('No se han podido cargar los miembros del espacio.');
    });
  }, [selectedWorkspace, loadMembers]);

  useEffect(() => {
    setFieldDraft(selectedField ? fieldToDraft(selectedField) : null);
  }, [selectedField]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await task();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      const suffix = caught instanceof ApiRequestError && caught.status === 409 ? ' La operación entraría en conflicto con las reglas de seguridad.' : '';
      setError(`No se ha podido completar la operación.${suffix}`);
    } finally {
      setBusy(false);
    }
  }

  async function saveWorkspace() {
    if (!selectedWorkspace || !editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/workspaces/${selectedWorkspace.id}`, {
        method: 'PATCH', body: JSON.stringify({ name: workspaceName.trim(), type: workspaceType }),
      });
      await loadCore();
    }, 'Espacio de trabajo actualizado y auditado.');
  }

  async function updateMember(member: Member, role: MemberRole, status: MemberStatus) {
    if (!selectedWorkspace || !editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/workspaces/${selectedWorkspace.id}/members/${member.user_id}`, {
        method: 'PUT', body: JSON.stringify({ role, status }),
      });
      await Promise.all([loadMembers(selectedWorkspace.id), loadCore()]);
    }, 'Permiso del miembro actualizado y auditado.');
  }

  async function addMember() {
    if (!selectedWorkspace || !memberUserId || !editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/workspaces/${selectedWorkspace.id}/members/${memberUserId}`, {
        method: 'PUT', body: JSON.stringify({ role: memberRole, status: memberStatus }),
      });
      setMemberUserId('');
      await Promise.all([loadMembers(selectedWorkspace.id), loadCore()]);
    }, 'Miembro añadido o actualizado.');
  }

  async function saveField() {
    if (!selectedField || !fieldDraft || !editable) return;
    const treeCount = fieldDraft.tree_count.trim() ? Number(fieldDraft.tree_count) : null;
    const plantingYear = fieldDraft.planting_year.trim() ? Number(fieldDraft.planting_year) : null;
    if ((treeCount !== null && (!Number.isInteger(treeCount) || treeCount < 1)) || (plantingYear !== null && (!Number.isInteger(plantingYear) || plantingYear < 1800))) {
      setError('Revisa el número de olivos y el año de plantación.');
      return;
    }
    await run(async () => {
      await apiFetch(`/api/v1/admin/fields/${selectedField.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          name: fieldDraft.name.trim(),
          description: fieldDraft.description.trim() || null,
          tree_count: treeCount,
          crop: fieldDraft.crop.trim(),
          variety: fieldDraft.variety.trim() || null,
          water_regime: fieldDraft.water_regime || null,
          planting_year: plantingYear,
          tenure_type: fieldDraft.tenure_type.trim() || null,
          status: fieldDraft.status,
        }),
      });
      await loadCore();
    }, 'Finca actualizada y auditada.');
  }

  const filteredWorkspaces = useMemo(() => {
    const q = workspaceSearch.trim().toLowerCase();
    return q ? workspaces.filter((item) => item.name.toLowerCase().includes(q)) : workspaces;
  }, [workspaceSearch, workspaces]);

  const filteredFields = useMemo(() => {
    const q = fieldSearch.trim().toLowerCase();
    return fields.filter((item) => {
      if (selectedWorkspaceId && item.workspace_id !== selectedWorkspaceId) return false;
      return !q || `${item.name} ${item.workspace_name} ${item.municipality ?? ''}`.toLowerCase().includes(q);
    });
  }, [fieldSearch, fields, selectedWorkspaceId]);

  if (auth.status === 'loading' || loading) return <main className={styles.gate}><div className={styles.gateCard}><strong>Cargando gestión…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.gate}><div className={styles.gateCard}><span>Administración V20</span><h1>Gestión protegida</h1><p>Inicia sesión con una cuenta autorizada.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><p>Tu cuenta no tiene permisos de plataforma.</p><Link href="/">Volver a la web</Link></div></main>;
  if (!session) return <main className={styles.gate}><div className={styles.gateCard}><h1>No se puede abrir Gestión</h1><p>{error}</p></div></main>;

  return (
    <main className={styles.shell}>
      <header className={styles.hero}>
        <div><span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span><h1>Gestión de datos</h1><p>Edición controlada de espacios, miembros y fincas. Cada cambio sensible queda auditado.</p></div>
        <div className={styles.actions}><span className={styles.role}>{session.platform_access.role.replace('_', ' ')}</span><Link href="/admin/operaciones">Operaciones</Link><Link href="/admin">Admin clásico</Link></div>
      </header>

      {message ? <div className={styles.success}>{message}</div> : null}
      {error ? <div className={styles.error}>{error}</div> : null}
      {!editable ? <div className={styles.notice}>Tu rol es de consulta. Solo Administrador y Superadministrador pueden modificar datos.</div> : null}

      <section className={styles.layout}>
        <aside className={styles.listPanel}>
          <div className={styles.panelTitle}><div><span className={styles.eyebrow}>Organización</span><h2>Espacios de trabajo</h2></div><button disabled={busy} onClick={() => void loadCore()}>Actualizar</button></div>
          <input value={workspaceSearch} onChange={(event) => setWorkspaceSearch(event.target.value)} placeholder="Buscar espacio…" />
          <div className={styles.list}>
            {filteredWorkspaces.map((workspace) => <button key={workspace.id} className={selectedWorkspaceId === workspace.id ? styles.selected : ''} onClick={() => setSelectedWorkspaceId(workspace.id)}><strong>{workspace.name}</strong><span>{workspaceTypeLabels[workspace.type]} · {workspace.active_fields} fincas · {workspace.active_members} miembros</span><small>{workspace.plan ? `${workspace.plan.plan_code} · ${workspace.plan.status}` : 'Sin plan registrado'}</small></button>)}
            {!filteredWorkspaces.length ? <p>Sin espacios.</p> : null}
          </div>
        </aside>

        <div className={styles.detailStack}>
          {selectedWorkspace ? <section className={styles.panel}>
            <div className={styles.panelTitle}><div><span className={styles.eyebrow}>Workspace</span><h2>{selectedWorkspace.name}</h2></div><span>{selectedWorkspace.active_owners} propietario(s) activo(s)</span></div>
            <div className={styles.formGrid}>
              <label>Nombre<input disabled={!editable} value={workspaceName} onChange={(event) => setWorkspaceName(event.target.value)} /></label>
              <label>Tipo<select disabled={!editable} value={workspaceType} onChange={(event) => setWorkspaceType(event.target.value as WorkspaceType)}><option value="family">Familiar</option><option value="professional">Profesional</option><option value="organization">Organización</option></select></label>
            </div>
            {editable ? <button className={styles.primary} disabled={busy || !workspaceName.trim()} onClick={() => void saveWorkspace()}>Guardar workspace</button> : null}
          </section> : null}

          {selectedWorkspace ? <section className={styles.panel}>
            <div className={styles.panelTitle}><div><span className={styles.eyebrow}>Acceso</span><h2>Miembros</h2></div><span>{members.length} relaciones</span></div>
            <div className={styles.memberList}>
              {members.map((member) => <div className={styles.memberRow} key={member.id}><div><strong>{member.display_name}</strong><span>{member.primary_email ?? 'Sin correo'} · usuario {member.user_status}</span></div><select disabled={!editable || busy} value={member.role} onChange={(event) => void updateMember(member, event.target.value as MemberRole, member.status)}>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><select disabled={!editable || busy} value={member.status} onChange={(event) => void updateMember(member, member.role, event.target.value as MemberStatus)}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>)}
              {!members.length ? <p>Sin miembros.</p> : null}
            </div>
            {editable ? <div className={styles.addMember}><select value={memberUserId} onChange={(event) => setMemberUserId(event.target.value)}><option value="">Selecciona usuario…</option>{users.filter((user) => !members.some((member) => member.user_id === user.id)).map((user) => <option key={user.id} value={user.id}>{user.display_name} · {user.primary_email ?? 'sin correo'}</option>)}</select><select value={memberRole} onChange={(event) => setMemberRole(event.target.value as MemberRole)}>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><select value={memberStatus} onChange={(event) => setMemberStatus(event.target.value as MemberStatus)}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><button disabled={busy || !memberUserId} onClick={() => void addMember()}>Añadir</button></div> : null}
            <p className={styles.note}>El servidor impide suspender, revocar o degradar al último propietario activo de un espacio.</p>
          </section> : null}
        </div>
      </section>

      <section className={styles.fieldSection}>
        <div className={styles.fieldListPanel}>
          <div className={styles.panelTitle}><div><span className={styles.eyebrow}>Mi Campo</span><h2>Fincas</h2></div><span>{filteredFields.length} visibles</span></div>
          <input value={fieldSearch} onChange={(event) => setFieldSearch(event.target.value)} placeholder="Buscar finca…" />
          <div className={styles.list}>{filteredFields.map((field) => <button key={field.id} className={selectedFieldId === field.id ? styles.selected : ''} onClick={() => setSelectedFieldId(field.id)}><strong>{field.name}</strong><span>{field.municipality ?? 'Sin localidad'} · {formatNumber(field.calculated_area_ha, 2)} ha</span><small>{field.geometry_status} · {field.status}</small></button>)}</div>
        </div>

        {selectedField && fieldDraft ? <section className={styles.panel}>
          <div className={styles.panelTitle}><div><span className={styles.eyebrow}>Ficha administrativa</span><h2>{selectedField.name}</h2><p>{selectedField.workspace_name} · {selectedField.municipality ?? 'Sin municipio'} · geometría {selectedField.geometry_status}</p></div></div>
          <div className={styles.formGrid}>
            <label>Nombre<input disabled={!editable} value={fieldDraft.name} onChange={(event) => setFieldDraft({ ...fieldDraft, name: event.target.value })} /></label>
            <label>Estado<select disabled={!editable} value={fieldDraft.status} onChange={(event) => setFieldDraft({ ...fieldDraft, status: event.target.value as FieldStatus })}><option value="active">Activa</option><option value="archived">Archivada</option></select></label>
            <label>Cultivo<input disabled={!editable} value={fieldDraft.crop} onChange={(event) => setFieldDraft({ ...fieldDraft, crop: event.target.value })} /></label>
            <label>Variedad<input disabled={!editable} value={fieldDraft.variety} onChange={(event) => setFieldDraft({ ...fieldDraft, variety: event.target.value })} /></label>
            <label>Olivos<input disabled={!editable} inputMode="numeric" value={fieldDraft.tree_count} onChange={(event) => setFieldDraft({ ...fieldDraft, tree_count: event.target.value })} /></label>
            <label>Régimen<select disabled={!editable} value={fieldDraft.water_regime} onChange={(event) => setFieldDraft({ ...fieldDraft, water_regime: event.target.value as WaterRegime | '' })}><option value="">Sin indicar</option><option value="secano">Secano</option><option value="regadio">Regadío</option><option value="mixto">Mixto</option></select></label>
            <label>Año plantación<input disabled={!editable} inputMode="numeric" value={fieldDraft.planting_year} onChange={(event) => setFieldDraft({ ...fieldDraft, planting_year: event.target.value })} /></label>
            <label>Tenencia<input disabled={!editable} value={fieldDraft.tenure_type} onChange={(event) => setFieldDraft({ ...fieldDraft, tenure_type: event.target.value })} /></label>
            <label className={styles.full}>Descripción<textarea disabled={!editable} rows={4} value={fieldDraft.description} onChange={(event) => setFieldDraft({ ...fieldDraft, description: event.target.value })} /></label>
          </div>
          <div className={styles.readOnlyFacts}><span>Superficie: <strong>{formatNumber(selectedField.calculated_area_ha, 2)} ha</strong></span><span>Geometría: <strong>{selectedField.geometry_source ?? 'sin fuente'} / {selectedField.geometry_status}</strong></span><span>Provincia: <strong>{selectedField.province ?? '—'}</strong></span></div>
          <p className={styles.note}>La geometría, Catastro/SIGPAC y la localidad canónica no se editan aquí: se mantienen bajo los flujos GIS para evitar incoherencias territoriales.</p>
          {editable ? <button className={styles.primary} disabled={busy || !fieldDraft.name.trim() || !fieldDraft.crop.trim()} onClick={() => void saveField()}>Guardar finca</button> : null}
        </section> : <section className={styles.panel}><p>Selecciona una finca.</p></section>}
      </section>
    </main>
  );
}
