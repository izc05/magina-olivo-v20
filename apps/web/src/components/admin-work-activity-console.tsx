'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-work-activity-console.module.css';

type WorkType = 'pruning'|'shredding'|'harvest'|'treatment'|'fertilization'|'irrigation'|'mowing'|'tillage'|'transport'|'manual-work'|'machinery-work'|'other';
type Work = {
  id:string; workspace_id:string; workspace_name:string; field_id:string|null; field_name:string|null;
  customer_site_id:string|null; customer_site_name:string|null; campaign_id:string|null; campaign_name:string|null;
  type:WorkType; occurred_on:string; title:string; notes:string|null; performed_for:'self'|'third-party';
  customer_party_id:string|null; customer_name:string|null; quoted_amount_eur:number|null; charge_eur:number|null;
  collected_eur:number|null; payment_status:string; invoice_reference:string|null; created_by:string; created_by_name:string|null;
  participants_count:number; resources_count:number; participant_cost_eur:number; resource_cost_eur:number;
  collection_total_eur:number; created_at:string; updated_at:string;
};
type Counts = { total_30d:number; self_30d:number; third_party_30d:number; pending_receivables:number; pending_eur:number; cost_30d:number };
type Participant = { id:string; display_name:string; role:string|null; quantity:number|null; unit:string|null; rate_eur:number|null; cost_eur:number|null };
type Resource = { id:string; kind:string; name:string; quantity:number|null; unit:string|null; unit_cost_eur:number|null; cost_eur:number|null };
type Collection = { id:string; collected_on:string; amount_eur:number; method:string|null; reference:string|null; notes:string|null };
type Detail = { work:Work; participants:Participant[]; resources:Resource[]; collections:Collection[] };
type Campaign = { id:string; workspace_id:string; name:string; start_date:string; end_date:string|null; status:string };
type Draft = { title:string; type:WorkType; occurred_on:string; notes:string; campaign_id:string };

const typeLabels: Record<WorkType,string> = {
  pruning:'Poda', shredding:'Trituración', harvest:'Recolección', treatment:'Tratamiento', fertilization:'Abonado', irrigation:'Riego',
  mowing:'Desbroce', tillage:'Laboreo', transport:'Transporte', 'manual-work':'Trabajo manual', 'machinery-work':'Maquinaria', other:'Otro',
};

function canEdit(session: AdminSession|null) { return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin'; }
function money(value:number|null|undefined) { return new Intl.NumberFormat('es-ES',{style:'currency',currency:'EUR'}).format(value ?? 0); }
function dateLabel(value:string) { const date = new Date(`${value}T12:00:00`); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-ES',{dateStyle:'medium'}).format(date); }
function draftFrom(work:Work): Draft { return { title:work.title, type:work.type, occurred_on:work.occurred_on, notes:work.notes ?? '', campaign_id:work.campaign_id ?? '' }; }
function destination(work:Work) { return work.field_name ?? work.customer_site_name ?? 'Sin destino'; }

export function AdminWorkActivityConsole() {
  const auth = useAuth();
  const [session,setSession] = useState<AdminSession|null>(null);
  const [denied,setDenied] = useState(false);
  const [loading,setLoading] = useState(true);
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState<string|null>(null);
  const [message,setMessage] = useState<string|null>(null);
  const [works,setWorks] = useState<Work[]>([]);
  const [counts,setCounts] = useState<Counts>({total_30d:0,self_30d:0,third_party_30d:0,pending_receivables:0,pending_eur:0,cost_30d:0});
  const [query,setQuery] = useState('');
  const [performed,setPerformed] = useState<'all'|'self'|'third-party'>('all');
  const [typeFilter,setTypeFilter] = useState<'all'|WorkType>('all');
  const [payment,setPayment] = useState<'all'|'not-applicable'|'pending'|'partial'|'paid'>('all');
  const [selectedId,setSelectedId] = useState<string|null>(null);
  const [detail,setDetail] = useState<Detail|null>(null);
  const [campaigns,setCampaigns] = useState<Campaign[]>([]);
  const [draft,setDraft] = useState<Draft|null>(null);

  const editable = canEdit(session);
  const selected = useMemo(() => works.find((work) => work.id === selectedId) ?? detail?.work ?? null,[works,selectedId,detail]);

  const load = useCallback(async () => {
    const params = new URLSearchParams({ limit:'300', performed_for:performed, payment_status:payment });
    if (query.trim()) params.set('q',query.trim());
    if (typeFilter !== 'all') params.set('type',typeFilter);
    const payload = await apiFetch<{works:Work[];counts:Counts}>(`/api/v1/admin/works?${params}`);
    setWorks(payload.works); setCounts(payload.counts);
    setSelectedId((current) => current && payload.works.some((item) => item.id === current) ? current : payload.works[0]?.id ?? null);
  },[query,performed,typeFilter,payment]);

  const hydrate = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true); setDenied(false); setError(null);
    try { const current=await adminApi.session(); setSession(current); await load(); }
    catch (caught) { if (caught instanceof ApiRequestError && caught.status===403) { setDenied(true); setSession(null); } else { console.error(caught); setError('No se ha podido cargar la actividad agrícola.'); } }
    finally { setLoading(false); }
  },[auth.status,load]);

  useEffect(() => { if (auth.status==='authenticated') void hydrate(); if (auth.status==='anonymous') setLoading(false); },[auth.status,hydrate]);

  useEffect(() => {
    if (!selectedId) { setDetail(null); setDraft(null); setCampaigns([]); return; }
    let cancelled=false;
    void (async()=>{
      try {
        const payload=await apiFetch<Detail>(`/api/v1/admin/works/${selectedId}`);
        if (cancelled) return;
        setDetail(payload); setDraft(draftFrom(payload.work));
        const campaignPayload=await apiFetch<{campaigns:Campaign[]}>(`/api/v1/admin/campaigns?workspace_id=${payload.work.workspace_id}`);
        if (!cancelled) setCampaigns(campaignPayload.campaigns);
      } catch (caught) { console.error(caught); if (!cancelled) setError('No se ha podido cargar el detalle del trabajo.'); }
    })();
    return()=>{cancelled=true;};
  },[selectedId]);

  async function save() {
    if (!editable || !detail || !draft) return;
    setBusy(true); setError(null); setMessage(null);
    try {
      const payload=await apiFetch<{work:Work}>(`/api/v1/admin/works/${detail.work.id}`,{method:'PATCH',body:JSON.stringify({
        title:draft.title.trim(), type:draft.type, occurred_on:draft.occurred_on, notes:draft.notes.trim()||null, campaign_id:draft.campaign_id||null,
      })});
      setDetail((current)=>current?{...current,work:payload.work}:current); setDraft(draftFrom(payload.work)); setMessage('Trabajo corregido y auditado.'); await load();
    } catch(caught) {
      console.error(caught);
      if (caught instanceof ApiRequestError && caught.status===409) setError('La campaña elegida no corresponde al workspace o la fecha del trabajo queda fuera de su período.');
      else setError('No se ha podido guardar la corrección administrativa.');
    } finally { setBusy(false); }
  }

  if (auth.status==='loading'||loading) return <main className={styles.gate}><div className={styles.gateCard}><strong>Cargando trabajos globales…</strong></div></main>;
  if (auth.status==='anonymous') return <main className={styles.gate}><div className={styles.gateCard}><span>Administración V20</span><h1>Actividad protegida</h1><GoogleSignInButton/></div></main>;
  if (denied) return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><Link href="/">Volver</Link></div></main>;
  if (!session) return <main className={styles.gate}><div className={styles.gateCard}><h1>No disponible</h1><p>{error}</p></div></main>;

  return <main className={styles.shell}>
    <header className={styles.hero}><div><span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span><h1>Trabajos y actividad agrícola</h1><p>Consulta transversal y correcciones controladas sin romper la trazabilidad de finca, personal, recursos o cobros.</p></div><div className={styles.nav}><span>{session.platform_access.role.replace('_',' ')}</span><Link href="/admin/agenda">Agenda</Link><Link href="/admin/gestion">Gestión</Link><Link href="/admin/operaciones">Operaciones</Link></div></header>
    {message?<div className={styles.success}>{message}</div>:null}{error?<div className={styles.error}>{error}</div>:null}

    <section className={styles.metrics}>
      <article><span>Trabajos · 30 días</span><strong>{counts.total_30d}</strong></article><article><span>Propios</span><strong>{counts.self_30d}</strong></article><article><span>Para terceros</span><strong>{counts.third_party_30d}</strong></article><article><span>Coste · 30 días</span><strong>{money(counts.cost_30d)}</strong></article><article><span>Cobros pendientes</span><strong>{counts.pending_receivables}</strong></article><article><span>Pendiente €</span><strong>{money(counts.pending_eur)}</strong></article>
    </section>

    <section className={styles.layout}>
      <aside className={styles.listPanel}>
        <div className={styles.heading}><div><span className={styles.eyebrow}>Registro global</span><h2>Trabajos</h2></div><button disabled={busy} onClick={()=>void load()}>Actualizar</button></div>
        <div className={styles.filters}><input value={query} onChange={(event)=>setQuery(event.target.value)} onKeyDown={(event)=>{if(event.key==='Enter')void load();}} placeholder="Buscar trabajo, finca, cliente…"/><select value={performed} onChange={(event)=>setPerformed(event.target.value as typeof performed)}><option value="all">Todos</option><option value="self">Propios</option><option value="third-party">Para terceros</option></select><select value={typeFilter} onChange={(event)=>setTypeFilter(event.target.value as typeof typeFilter)}><option value="all">Todos los tipos</option>{Object.entries(typeLabels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select><select value={payment} onChange={(event)=>setPayment(event.target.value as typeof payment)}><option value="all">Cualquier cobro</option><option value="pending">Pendiente</option><option value="partial">Parcial</option><option value="paid">Pagado</option><option value="not-applicable">No aplica</option></select><button onClick={()=>void load()}>Aplicar filtros</button></div>
        <div className={styles.list}>{works.map((work)=><button key={work.id} className={selectedId===work.id?styles.selected:''} onClick={()=>setSelectedId(work.id)}><strong>{work.title}</strong><span>{work.workspace_name} · {destination(work)}</span><small>{typeLabels[work.type]} · {dateLabel(work.occurred_on)} · {work.performed_for==='third-party'?'tercero':'propio'}</small></button>)}{!works.length?<p>Sin trabajos para estos filtros.</p>:null}</div>
      </aside>

      <section className={styles.detailPanel}>{detail&&draft?<>
        <div className={styles.heading}><div><span className={styles.eyebrow}>Detalle administrativo</span><h2>{detail.work.title}</h2><p>{detail.work.workspace_name} · {destination(detail.work)}</p></div><span className={styles.badge}>{typeLabels[detail.work.type]}</span></div>
        <div className={styles.notice}>Vínculos protegidos: workspace, finca/destino, cliente, creador, participantes, recursos y movimientos de cobro no se reescriben desde esta pantalla.</div>
        <div className={styles.formGrid}><label>Título<input disabled={!editable} value={draft.title} onChange={(event)=>setDraft({...draft,title:event.target.value})}/></label><label>Tipo<select disabled={!editable} value={draft.type} onChange={(event)=>setDraft({...draft,type:event.target.value as WorkType})}>{Object.entries(typeLabels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><label>Fecha<input disabled={!editable} type="date" value={draft.occurred_on} onChange={(event)=>setDraft({...draft,occurred_on:event.target.value})}/></label><label>Campaña<select disabled={!editable} value={draft.campaign_id} onChange={(event)=>setDraft({...draft,campaign_id:event.target.value})}><option value="">Sin campaña</option>{campaigns.map((campaign)=><option key={campaign.id} value={campaign.id}>{campaign.name} · {campaign.status}</option>)}</select></label><label className={styles.full}>Notas<textarea disabled={!editable} rows={4} value={draft.notes} onChange={(event)=>setDraft({...draft,notes:event.target.value})}/></label></div>
        {editable?<button className={styles.primary} disabled={busy||!draft.title.trim()||!draft.occurred_on} onClick={()=>void save()}>Guardar corrección</button>:null}

        <div className={styles.facts}><span>Modalidad <strong>{detail.work.performed_for==='third-party'?'Trabajo para tercero':'Trabajo propio'}</strong></span><span>Cliente <strong>{detail.work.customer_name??'—'}</strong></span><span>Creado por <strong>{detail.work.created_by_name??detail.work.created_by}</strong></span><span>Campaña <strong>{detail.work.campaign_name??'Sin campaña'}</strong></span></div>
        <section className={styles.moneyGrid}><article><span>Coste personal</span><strong>{money(detail.work.participant_cost_eur)}</strong></article><article><span>Coste recursos</span><strong>{money(detail.work.resource_cost_eur)}</strong></article><article><span>Presupuestado</span><strong>{money(detail.work.quoted_amount_eur)}</strong></article><article><span>Cargo</span><strong>{money(detail.work.charge_eur)}</strong></article><article><span>Cobrado</span><strong>{money(detail.work.collection_total_eur)}</strong></article><article><span>Estado</span><strong>{detail.work.payment_status}</strong></article></section>

        <h3>Participantes</h3><div className={styles.tableWrap}><table><thead><tr><th>Persona</th><th>Rol</th><th>Cantidad</th><th>Tarifa</th><th>Coste</th></tr></thead><tbody>{detail.participants.map((row)=><tr key={row.id}><td>{row.display_name}</td><td>{row.role??'—'}</td><td>{row.quantity??'—'} {row.unit??''}</td><td>{row.rate_eur==null?'—':money(row.rate_eur)}</td><td>{row.cost_eur==null?'—':money(row.cost_eur)}</td></tr>)}{!detail.participants.length?<tr><td colSpan={5}>Sin participantes registrados.</td></tr>:null}</tbody></table></div>
        <h3>Recursos y maquinaria</h3><div className={styles.tableWrap}><table><thead><tr><th>Recurso</th><th>Tipo</th><th>Cantidad</th><th>Coste unidad</th><th>Coste</th></tr></thead><tbody>{detail.resources.map((row)=><tr key={row.id}><td>{row.name}</td><td>{row.kind}</td><td>{row.quantity??'—'} {row.unit??''}</td><td>{row.unit_cost_eur==null?'—':money(row.unit_cost_eur)}</td><td>{row.cost_eur==null?'—':money(row.cost_eur)}</td></tr>)}{!detail.resources.length?<tr><td colSpan={5}>Sin recursos registrados.</td></tr>:null}</tbody></table></div>
        {detail.work.performed_for==='third-party'?<><h3>Movimientos de cobro</h3><div className={styles.tableWrap}><table><thead><tr><th>Fecha</th><th>Importe</th><th>Método</th><th>Referencia</th></tr></thead><tbody>{detail.collections.map((row)=><tr key={row.id}><td>{dateLabel(row.collected_on)}</td><td>{money(row.amount_eur)}</td><td>{row.method??'—'}</td><td>{row.reference??'—'}</td></tr>)}{!detail.collections.length?<tr><td colSpan={4}>Sin cobros registrados.</td></tr>:null}</tbody></table></div></>:null}
      </>:<p>Selecciona un trabajo.</p>}</section>
    </section>
  </main>;
}
