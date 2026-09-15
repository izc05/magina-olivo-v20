'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-campaign-plan-console.module.css';

type CampaignStatus = 'planned' | 'active' | 'closed';
type PlanCode = 'free' | 'pro' | 'professional';
type PlanStatus = 'active' | 'trialing' | 'paused' | 'cancelled';
type InterestStatus = 'pending' | 'contacted' | 'converted' | 'cancelled';

type Campaign = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  name: string;
  start_date: string;
  end_date: string | null;
  status: CampaignStatus;
  created_at: string;
};

type Subscription = {
  workspace_id: string;
  workspace_name: string;
  workspace_type: string;
  plan_code: PlanCode | null;
  status: PlanStatus | null;
  source: string | null;
  started_at: string | null;
  current_period_end: string | null;
  updated_at: string | null;
};

type Interest = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  target_plan: 'pro' | 'professional';
  status: InterestStatus;
  requested_by: string | null;
  requested_by_name: string | null;
  created_at: string;
  updated_at: string;
};

const campaignStatusLabels: Record<CampaignStatus, string> = { planned: 'Planificada', active: 'Activa', closed: 'Cerrada' };
const planLabels: Record<PlanCode, string> = { free: 'Campo', pro: 'Pro', professional: 'Profesional' };
const planStatusLabels: Record<PlanStatus, string> = { active: 'Activo', trialing: 'Prueba', paused: 'Pausado', cancelled: 'Cancelado' };
const interestLabels: Record<InterestStatus, string> = { pending: 'Pendiente', contacted: 'Contactado', converted: 'Convertido', cancelled: 'Cancelado' };

function canEdit(session: AdminSession | null) {
  return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';
}
function isSuper(session: AdminSession | null) {
  return session?.platform_access.role === 'super_admin';
}
function day(value: string | null) {
  return value ? value.slice(0, 10) : '';
}

export function AdminCampaignPlanConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [interests, setInterests] = useState<Interest[]>([]);
  const [selectedCampaignId, setSelectedCampaignId] = useState<string | null>(null);
  const [campaignName, setCampaignName] = useState('');
  const [campaignStart, setCampaignStart] = useState('');
  const [campaignEnd, setCampaignEnd] = useState('');
  const [campaignStatus, setCampaignStatus] = useState<CampaignStatus>('active');
  const [campaignSearch, setCampaignSearch] = useState('');
  const [planSearch, setPlanSearch] = useState('');

  const selectedCampaign = useMemo(() => campaigns.find((item) => item.id === selectedCampaignId) ?? null, [campaigns, selectedCampaignId]);
  const editable = canEdit(session);
  const superAdmin = isSuper(session);

  const load = useCallback(async () => {
    const [campaignPayload, planPayload] = await Promise.all([
      apiFetch<{ campaigns: Campaign[] }>('/api/v1/admin/campaigns'),
      apiFetch<{ subscriptions: Subscription[]; interests: Interest[]; billing_enabled: boolean }>('/api/v1/admin/plans'),
    ]);
    setCampaigns(campaignPayload.campaigns);
    setSubscriptions(planPayload.subscriptions);
    setInterests(planPayload.interests);
    setSelectedCampaignId((current) => current && campaignPayload.campaigns.some((item) => item.id === current) ? current : campaignPayload.campaigns[0]?.id ?? null);
  }, []);

  const hydrate = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    setDenied(false);
    try {
      const current = await adminApi.session();
      setSession(current);
      await load();
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
      } else {
        console.error(caught);
        setError('No se ha podido cargar Campañas y Planes.');
      }
    } finally {
      setLoading(false);
    }
  }, [auth.status, load]);

  useEffect(() => {
    if (auth.status === 'authenticated') void hydrate();
    if (auth.status === 'anonymous') setLoading(false);
  }, [auth.status, hydrate]);

  useEffect(() => {
    if (!selectedCampaign) return;
    setCampaignName(selectedCampaign.name);
    setCampaignStart(day(selectedCampaign.start_date));
    setCampaignEnd(day(selectedCampaign.end_date));
    setCampaignStatus(selectedCampaign.status);
  }, [selectedCampaign]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await task();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido completar la operación.');
    } finally {
      setBusy(false);
    }
  }

  async function saveCampaign() {
    if (!selectedCampaign || !editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/campaigns/${selectedCampaign.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ name: campaignName.trim(), start_date: campaignStart, end_date: campaignEnd || null, status: campaignStatus }),
      });
      await load();
    }, 'Campaña actualizada y auditada.');
  }

  async function savePlan(subscription: Subscription, planCode: PlanCode, status: PlanStatus, periodEnd: string) {
    if (!superAdmin) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/workspaces/${subscription.workspace_id}/plan`, {
        method: 'PUT',
        body: JSON.stringify({ plan_code: planCode, status, current_period_end: periodEnd ? new Date(`${periodEnd}T23:59:59Z`).toISOString() : null }),
      });
      await load();
    }, 'Plan manual actualizado y auditado.');
  }

  async function updateInterest(interest: Interest, status: InterestStatus) {
    if (!editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/plan-interests/${interest.id}`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await load();
    }, 'Interés comercial actualizado.');
  }

  const filteredCampaigns = useMemo(() => {
    const q = campaignSearch.trim().toLowerCase();
    return q ? campaigns.filter((item) => `${item.name} ${item.workspace_name}`.toLowerCase().includes(q)) : campaigns;
  }, [campaignSearch, campaigns]);

  const filteredSubscriptions = useMemo(() => {
    const q = planSearch.trim().toLowerCase();
    return q ? subscriptions.filter((item) => item.workspace_name.toLowerCase().includes(q)) : subscriptions;
  }, [planSearch, subscriptions]);

  if (auth.status === 'loading' || loading) return <main className={styles.gate}><div className={styles.gateCard}><strong>Cargando campañas y planes…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.gate}><div className={styles.gateCard}><span>Administración V20</span><h1>Acceso protegido</h1><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><Link href="/">Volver</Link></div></main>;
  if (!session) return <main className={styles.gate}><div className={styles.gateCard}><h1>No disponible</h1><p>{error}</p></div></main>;

  return <main className={styles.shell}>
    <header className={styles.hero}>
      <div><span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span><h1>Campañas y planes</h1><p>Control global de campañas agrícolas, suscripciones internas e intereses comerciales. Billing continúa desactivado.</p></div>
      <div className={styles.nav}><span>{session.platform_access.role.replace('_', ' ')}</span><Link href="/admin/gestion">Gestión</Link><Link href="/admin/operaciones">Operaciones</Link></div>
    </header>
    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}
    <div className={styles.notice}>Los planes se administran como estado interno de plataforma. No se activa checkout ni se genera ningún cobro desde esta pantalla.</div>

    <section className={styles.grid}>
      <div className={styles.panel}>
        <div className={styles.heading}><div><span className={styles.eyebrow}>Agricultura</span><h2>Campañas</h2></div><span>{filteredCampaigns.length}</span></div>
        <input value={campaignSearch} onChange={(event) => setCampaignSearch(event.target.value)} placeholder="Buscar campaña o workspace…" />
        <div className={styles.list}>{filteredCampaigns.map((campaign) => <button key={campaign.id} className={selectedCampaignId === campaign.id ? styles.selected : ''} onClick={() => setSelectedCampaignId(campaign.id)}><strong>{campaign.name}</strong><span>{campaign.workspace_name}</span><small>{campaignStatusLabels[campaign.status]} · {day(campaign.start_date)}{campaign.end_date ? ` → ${day(campaign.end_date)}` : ''}</small></button>)}</div>
      </div>

      <div className={styles.panel}>
        {selectedCampaign ? <><div className={styles.heading}><div><span className={styles.eyebrow}>Editar campaña</span><h2>{selectedCampaign.name}</h2><p>{selectedCampaign.workspace_name}</p></div></div><div className={styles.formGrid}>
          <label>Nombre<input disabled={!editable} value={campaignName} onChange={(event) => setCampaignName(event.target.value)} /></label>
          <label>Estado<select disabled={!editable} value={campaignStatus} onChange={(event) => setCampaignStatus(event.target.value as CampaignStatus)}><option value="planned">Planificada</option><option value="active">Activa</option><option value="closed">Cerrada</option></select></label>
          <label>Inicio<input disabled={!editable} type="date" value={campaignStart} onChange={(event) => setCampaignStart(event.target.value)} /></label>
          <label>Fin<input disabled={!editable} type="date" value={campaignEnd} onChange={(event) => setCampaignEnd(event.target.value)} /></label>
        </div>{editable ? <button className={styles.primary} disabled={busy || !campaignName.trim() || !campaignStart} onClick={() => void saveCampaign()}>Guardar campaña</button> : null}</> : <p>Selecciona una campaña.</p>}
      </div>
    </section>

    <section className={styles.panelWide}>
      <div className={styles.heading}><div><span className={styles.eyebrow}>Planes</span><h2>Suscripciones internas</h2><p>Solo Superadministrador puede modificar el plan efectivo.</p></div><input value={planSearch} onChange={(event) => setPlanSearch(event.target.value)} placeholder="Buscar workspace…" /></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Workspace</th><th>Plan</th><th>Estado</th><th>Origen</th><th>Fin período</th><th></th></tr></thead><tbody>{filteredSubscriptions.map((subscription) => <PlanRow key={subscription.workspace_id} subscription={subscription} editable={superAdmin} busy={busy} onSave={savePlan} />)}</tbody></table></div>
    </section>

    <section className={styles.panelWide}>
      <div className={styles.heading}><div><span className={styles.eyebrow}>Comercial</span><h2>Intereses de plan</h2><p>Seguimiento manual de solicitudes, sin generar pagos.</p></div><span>{interests.length}</span></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Workspace</th><th>Plan</th><th>Solicitante</th><th>Estado</th><th>Actualizado</th></tr></thead><tbody>{interests.map((interest) => <tr key={interest.id}><td>{interest.workspace_name}</td><td>{planLabels[interest.target_plan]}</td><td>{interest.requested_by_name ?? '—'}</td><td><select disabled={!editable || busy} value={interest.status} onChange={(event) => void updateInterest(interest, event.target.value as InterestStatus)}>{Object.entries(interestLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></td><td>{day(interest.updated_at)}</td></tr>)}{!interests.length ? <tr><td colSpan={5}>Sin intereses registrados.</td></tr> : null}</tbody></table></div>
    </section>
  </main>;
}

function PlanRow({ subscription, editable, busy, onSave }: { subscription: Subscription; editable: boolean; busy: boolean; onSave: (subscription: Subscription, plan: PlanCode, status: PlanStatus, periodEnd: string) => Promise<void> }) {
  const [plan, setPlan] = useState<PlanCode>(subscription.plan_code ?? 'free');
  const [status, setStatus] = useState<PlanStatus>(subscription.status ?? 'active');
  const [periodEnd, setPeriodEnd] = useState(day(subscription.current_period_end));
  useEffect(() => { setPlan(subscription.plan_code ?? 'free'); setStatus(subscription.status ?? 'active'); setPeriodEnd(day(subscription.current_period_end)); }, [subscription]);
  return <tr><td><strong>{subscription.workspace_name}</strong><small>{subscription.workspace_type}</small></td><td><select disabled={!editable || busy} value={plan} onChange={(event) => setPlan(event.target.value as PlanCode)}>{Object.entries(planLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></td><td><select disabled={!editable || busy} value={status} onChange={(event) => setStatus(event.target.value as PlanStatus)}>{Object.entries(planStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></td><td>{subscription.source ?? 'default'}</td><td><input disabled={!editable || busy} type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} /></td><td>{editable ? <button disabled={busy} onClick={() => void onSave(subscription, plan, status, periodEnd)}>Guardar</button> : 'Solo lectura'}</td></tr>;
}
