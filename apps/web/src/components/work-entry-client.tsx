'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms } from '@/lib/farm-data-source';
import { saveLocalWork } from '@/lib/local-prototype-store';
import { createCustomer, createCustomerSite, createWork, loadWorkDirectory, type CustomerSiteOption, type WorkPartyOption } from '@/lib/work-api-source';

const workTypes = [
  ['pruning', 'Poda'], ['shredding', 'Trituración'], ['harvest', 'Recolección'], ['treatment', 'Tratamiento'],
  ['fertilization', 'Abonado'], ['irrigation', 'Riego'], ['mowing', 'Desbroce'], ['tillage', 'Laboreo'],
  ['transport', 'Transporte'], ['manual-work', 'Trabajo manual'], ['machinery-work', 'Maquinaria'], ['other', 'Otro'],
] as const;

function num(value: FormDataEntryValue | null) {
  if (typeof value !== 'string' || !value.trim()) return undefined;
  const parsed = Number(value.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function WorkEntryClient() {
  const params = useSearchParams();
  const fieldId = params.get('fieldId');
  const source = params.get('source');
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const apiMode = source === 'api' && apiConfigured && status === 'authenticated' && Boolean(selectedWorkspaceId);
  const previewFarm = useMemo(() => fieldId ? getPreviewFarms().find((farm) => farm.id === fieldId) : undefined, [fieldId]);
  const [mode, setMode] = useState<'self' | 'third-party'>(fieldId ? 'self' : 'third-party');
  const [parties, setParties] = useState<WorkPartyOption[]>([]);
  const [sites, setSites] = useState<CustomerSiteOption[]>([]);
  const [customerId, setCustomerId] = useState('');
  const [siteId, setSiteId] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!apiMode || !selectedWorkspaceId) return;
    void loadWorkDirectory(selectedWorkspaceId).then(({ parties: nextParties, sites: nextSites }) => {
      setParties(nextParties);
      setSites(nextSites);
    }).catch(() => setError('No se ha podido cargar la agenda de trabajo.'));
  }, [apiMode, selectedWorkspaceId]);

  const customerSites = sites.filter((site) => !customerId || site.customer_party_id === customerId);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const fd = new FormData(event.currentTarget);
    const date = String(fd.get('date') || new Date().toISOString().slice(0, 10));
    const title = String(fd.get('title') || 'Trabajo');
    const type = String(fd.get('type') || 'other');
    const notes = String(fd.get('notes') || '') || undefined;
    const workerName = String(fd.get('workerName') || '').trim();
    const quantity = num(fd.get('quantity'));
    const unit = String(fd.get('unit') || 'hours');
    const laborCost = num(fd.get('laborCost'));
    const machineName = String(fd.get('machineName') || '').trim();
    const machineHours = num(fd.get('machineHours'));
    const machineCost = num(fd.get('machineCost'));
    const charge = num(fd.get('charge'));
    const collected = num(fd.get('collected'));

    const participants = workerName ? [{ display_name: workerName, quantity, unit, cost_eur: laborCost }] : [];
    const resources = machineName ? [{ kind: 'machinery', name: machineName, quantity: machineHours, unit: machineHours ? 'h' : undefined, cost_eur: machineCost }] : [];

    try {
      if (apiMode && selectedWorkspaceId) {
        if (mode === 'self') {
          if (!fieldId) throw new Error('missing_field');
          await createWork(selectedWorkspaceId, { field_id: fieldId, type, occurred_on: date, title, notes, performed_for: 'self', participants, resources });
        } else {
          let effectiveCustomerId = customerId;
          if (!effectiveCustomerId) {
            const newCustomerName = String(fd.get('newCustomer') || '').trim();
            if (!newCustomerName) throw new Error('missing_customer');
            effectiveCustomerId = (await createCustomer(selectedWorkspaceId, newCustomerName)).id;
          }

          let effectiveSiteId = siteId;
          if (!effectiveSiteId) {
            const newSiteName = String(fd.get('newSite') || '').trim();
            if (!newSiteName) throw new Error('missing_site');
            effectiveSiteId = (await createCustomerSite(selectedWorkspaceId, {
              customerPartyId: effectiveCustomerId,
              name: newSiteName,
              municipality: String(fd.get('siteMunicipality') || '').trim() || undefined,
            })).id;
          }

          await createWork(selectedWorkspaceId, {
            customer_site_id: effectiveSiteId,
            type, occurred_on: date, title, notes,
            performed_for: 'third-party', customer_party_id: effectiveCustomerId,
            charge_eur: charge, collected_eur: collected,
            payment_status: charge !== undefined ? (collected && collected >= charge ? 'paid' : collected ? 'partial' : 'pending') : 'pending',
            participants, resources,
          });
        }
      } else {
        if (mode === 'third-party') throw new Error('third_party_requires_api');
        if (!fieldId || !previewFarm) throw new Error('missing_field');
        saveLocalWork({
          id: crypto.randomUUID(), farmId: fieldId, scope: 'whole-farm', campaign: '2026/27',
          type: type as any, occurredOn: date, title, notes, performedFor: 'self',
          participants: participants.map((item) => ({ id: crypto.randomUUID(), displayName: item.display_name, quantity: item.quantity, unit: item.unit as any, costEur: item.cost_eur })),
          resources: resources.map((item) => ({ id: crypto.randomUUID(), kind: 'machinery', name: item.name, quantity: item.quantity, unit: item.unit, costEur: item.cost_eur })),
          directCostEur: (laborCost ?? 0) + (machineCost ?? 0), createdAt: new Date().toISOString(),
        });
      }
      setSaved(true);
    } catch (cause) {
      const code = cause instanceof Error ? cause.message : '';
      setError(code === 'third_party_requires_api'
        ? 'En la preview no creamos fincas falsas de clientes. Este flujo se guarda en el servidor real.'
        : 'No se ha podido guardar el trabajo. Revisa destino y datos.');
    } finally {
      setSaving(false);
    }
  }

  if (saved) return <section className="card record-success"><div className="success-mark">✓</div><h1>Trabajo registrado</h1><p>Mano de obra, maquinaria, coste y contexto comercial han quedado unidos al mismo trabajo.</p><Link className="primary action-link" href="/mi-campo">Volver a Mi Campo</Link></section>;

  return <form className="quick-record-form" onSubmit={submit}>
    <header className="page-title compact-record-title"><span className="eyebrow dark">MI CAMPO · TRABAJO</span><h1>Registrar trabajo</h1><p>Una sola entrada para labor, personas, maquinaria, coste y cliente.</p></header>

    <section className="card record-panel"><h2>¿Dónde trabajaste?</h2><div className="record-fields">
      <label className="record-field"><span>Tipo</span><select className="record-control" value={mode} onChange={(e) => setMode(e.target.value as 'self' | 'third-party')}><option value="self">En mi finca</option><option value="third-party">Para un cliente</option></select></label>
      {mode === 'self' ? <div className="record-field"><span>Finca</span><strong>{previewFarm?.name ?? (fieldId ? 'Finca seleccionada' : 'Selecciona una finca desde Mi Campo')}</strong></div> : null}
      {mode === 'third-party' ? <>
        <label className="record-field"><span>Cliente existente</span><select className="record-control" value={customerId} onChange={(e) => { setCustomerId(e.target.value); setSiteId(''); }}><option value="">Nuevo cliente</option>{parties.filter((p) => p.roles?.includes('customer')).map((p) => <option key={p.id} value={p.id}>{p.display_name}</option>)}</select></label>
        {!customerId ? <label className="record-field"><span>Nombre del cliente</span><input className="record-control" name="newCustomer" /></label> : null}
        <label className="record-field"><span>Finca/ubicación del cliente</span><select className="record-control" value={siteId} onChange={(e) => setSiteId(e.target.value)}><option value="">Nueva ubicación</option>{customerSites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select></label>
        {!siteId ? <><label className="record-field"><span>Nombre de la finca</span><input className="record-control" name="newSite" /></label><label className="record-field"><span>Municipio</span><input className="record-control" name="siteMunicipality" /></label></> : null}
      </> : null}
    </div></section>

    <section className="card record-panel"><h2>Trabajo</h2><div className="record-fields">
      <label className="record-field"><span>Fecha</span><input className="record-control" name="date" type="date" required /></label>
      <label className="record-field"><span>Tipo de labor</span><select className="record-control" name="type">{workTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="record-field wide"><span>Qué se hizo</span><input className="record-control" name="title" required placeholder="Ej. Desbroce completo" /></label>
      <label className="record-field wide"><span>Notas</span><textarea className="record-control" name="notes" rows={3} /></label>
    </div></section>

    <details className="card record-details" open><summary>Personas y maquinaria <span>Opcional</span></summary><div className="record-fields detail-fields">
      <label className="record-field"><span>Trabajador/cuadrilla</span><input className="record-control" name="workerName" /></label>
      <label className="record-field"><span>Cantidad</span><input className="record-control" name="quantity" type="number" step="any" /></label>
      <label className="record-field"><span>Unidad</span><select className="record-control" name="unit"><option value="hours">Horas</option><option value="jornales">Jornales</option><option value="days">Días</option><option value="fixed">Importe fijo</option></select></label>
      <label className="record-field"><span>Coste mano de obra</span><input className="record-control" name="laborCost" type="number" step="any" /></label>
      <label className="record-field"><span>Maquinaria</span><input className="record-control" name="machineName" placeholder="Tractor, vibrador…" /></label>
      <label className="record-field"><span>Horas máquina</span><input className="record-control" name="machineHours" type="number" step="any" /></label>
      <label className="record-field"><span>Coste maquinaria</span><input className="record-control" name="machineCost" type="number" step="any" /></label>
    </div></details>

    {mode === 'third-party' ? <section className="card record-panel"><h2>¿Qué vas a cobrar?</h2><div className="record-fields"><label className="record-field"><span>Importe a cobrar</span><input className="record-control" name="charge" type="number" step="any" /></label><label className="record-field"><span>Ya cobrado</span><input className="record-control" name="collected" type="number" step="any" /></label></div></section> : null}

    {mode === 'third-party' && !apiMode ? <p className="form-error">La preview muestra el flujo, pero no guardará trabajos de terceros para no crear fincas falsas.</p> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    <section className="record-save-bar"><small>{apiMode ? 'Destino, costes y cobro se guardarán en el servidor.' : 'Modo preview estructural.'}</small><button className="primary" type="submit" disabled={saving || (mode === 'self' && !fieldId)}>{saving ? 'Guardando…' : 'Guardar trabajo →'}</button></section>
  </form>;
}
