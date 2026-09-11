'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import {
  confirmDocumentExtraction,
  loadDocumentAnalysis,
  requestDocumentOcr,
  type DocumentAnalysis,
} from '@/lib/document-data-source';

const labels: Record<string, string> = {
  date: 'Fecha',
  total_eur: 'Importe total (€)',
  document_number: 'Nº documento',
  supplier_tax_id: 'NIF/CIF proveedor',
  total_kg: 'Kilos totales',
  ticket_number: 'Nº albarán / ticket',
  yield_percent: 'Rendimiento (%)',
  moisture_percent: 'Humedad (%)',
  acidity_percent: 'Acidez (%)',
};
const numericKeys = new Set(['total_eur', 'total_kg', 'yield_percent', 'moisture_percent', 'acidity_percent']);

function fieldLabel(key: string) {
  return labels[key] ?? key.replaceAll('_', ' ');
}

function coerceValue(key: string, raw: string) {
  if (!numericKeys.has(key)) return raw.trim();
  const value = Number(raw.replace(',', '.'));
  return Number.isFinite(value) ? value : raw.trim();
}

function stringValue(fields: Record<string, unknown>, key: string) {
  const value = fields[key];
  return value == null ? '' : String(value);
}

function buildHref(path: string, values: Record<string, string>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value) query.set(key, value);
  return `${path}?${query.toString()}`;
}

export function DocumentReviewClient() {
  const params = useSearchParams();
  const documentId = params.get('documentId') ?? '';
  const fieldId = params.get('fieldId') ?? '';
  const source = params.get('source') ?? 'api';
  const { selectedWorkspaceId } = useAuth();
  const [analysis, setAnalysis] = useState<DocumentAnalysis | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const backHref = fieldId ? `/mi-campo/fincas/ver?id=${encodeURIComponent(fieldId)}&source=${encodeURIComponent(source)}` : '/mi-campo';

  const refresh = useCallback(async () => {
    if (!selectedWorkspaceId || !documentId) return;
    try {
      setError(null);
      const next = await loadDocumentAnalysis(selectedWorkspaceId, documentId);
      setAnalysis(next);
    } catch (cause) {
      console.error('Unable to load document analysis', cause);
      setError('No se ha podido cargar el análisis del documento.');
    } finally {
      setLoading(false);
    }
  }, [documentId, selectedWorkspaceId]);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    const data = analysis?.review?.confirmed_fields ?? analysis?.extraction?.data_json;
    if (!data) return;
    const next: Record<string, string> = {};
    for (const [key, value] of Object.entries(data)) next[key] = value == null ? '' : String(value);
    setValues(next);
  }, [analysis?.extraction?.id, analysis?.review?.id]);

  useEffect(() => {
    if (!analysis?.ocr || !['queued', 'processing'].includes(analysis.ocr.status)) return;
    const timer = window.setTimeout(() => void refresh(), 2200);
    return () => window.clearTimeout(timer);
  }, [analysis?.ocr?.status, refresh]);

  const editableKeys = useMemo(() => Object.keys(values), [values]);

  async function startOcr() {
    if (!selectedWorkspaceId || !analysis?.version) return;
    try {
      setRunning(true);
      setError(null);
      await requestDocumentOcr(selectedWorkspaceId, documentId, analysis.version.id);
      await refresh();
    } catch (cause) {
      console.error('Unable to start OCR', cause);
      setError('No se ha podido iniciar el análisis OCR.');
    } finally {
      setRunning(false);
    }
  }

  async function confirmReview() {
    if (!selectedWorkspaceId || !analysis?.extraction) return;
    const confirmed: Record<string, unknown> = {};
    const corrections: Record<string, unknown> = {};
    for (const [key, raw] of Object.entries(values)) {
      const value = coerceValue(key, raw);
      confirmed[key] = value;
      if (JSON.stringify(value) !== JSON.stringify(analysis.extraction.data_json[key])) corrections[key] = value;
    }
    try {
      setSaving(true);
      setError(null);
      await confirmDocumentExtraction(selectedWorkspaceId, analysis.extraction.id, confirmed, corrections);
      await refresh();
    } catch (cause) {
      console.error('Unable to confirm OCR review', cause);
      setError('No se ha podido guardar la revisión.');
    } finally {
      setSaving(false);
    }
  }

  if (!documentId) return <section className="card"><h1>Documento no indicado</h1><Link href={backHref}>Volver</Link></section>;
  if (loading) return <section className="card"><p>Cargando análisis…</p></section>;
  if (!analysis) return <section className="card"><h1>Análisis no disponible</h1><Link href={backHref}>Volver</Link></section>;

  const ocrBusy = analysis.ocr && ['queued', 'processing'].includes(analysis.ocr.status);
  const reviewed = analysis.review?.confirmed_fields ?? null;
  const baseParams = { fieldId, source, sourceDocumentId: documentId };
  const expenseHref = reviewed && ['invoice', 'purchase_receipt'].includes(analysis.document.kind)
    ? buildHref('/mi-campo/registrar/gasto', {
        ...baseParams,
        prefillDate: stringValue(reviewed, 'date'),
        prefillAmount: stringValue(reviewed, 'total_eur'),
        prefillConcept: analysis.document.title,
        prefillCategory: 'Otro',
        prefillNotes: stringValue(reviewed, 'document_number') ? `Documento ${stringValue(reviewed, 'document_number')}` : '',
      })
    : null;
  const deliveryHref = reviewed && analysis.document.kind === 'delivery_ticket'
    ? buildHref('/mi-campo/registrar/cosecha', {
        ...baseParams,
        prefillDate: stringValue(reviewed, 'date'),
        prefillKg: stringValue(reviewed, 'total_kg'),
        prefillTicket: stringValue(reviewed, 'ticket_number'),
      })
    : null;
  const resultHref = reviewed && analysis.document.kind === 'yield_result'
    ? buildHref('/mi-campo/registrar/rendimiento', {
        ...baseParams,
        prefillDate: stringValue(reviewed, 'date'),
        prefillYield: stringValue(reviewed, 'yield_percent'),
        prefillMoisture: stringValue(reviewed, 'moisture_percent'),
        prefillAcidity: stringValue(reviewed, 'acidity_percent'),
        prefillTicket: stringValue(reviewed, 'ticket_number'),
      })
    : null;

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · DOCUMENTOS</span><h1>Revisar lectura</h1><p>{analysis.document.title}</p></div></header>
    <section className="section card">
      <div className="section-head"><h2>Estado OCR</h2><span>{analysis.ocr?.status ?? 'sin analizar'}</span></div>
      <p>El OCR solo propone datos. Nada se convierte en gasto, entrega o rendimiento hasta que lo revises y confirmes expresamente.</p>
      {!analysis.ocr ? <button className="primary" type="button" onClick={() => void startOcr()} disabled={running || analysis.version?.upload_status !== 'uploaded'}>{running ? 'Iniciando…' : 'Analizar documento'}</button> : null}
      {ocrBusy ? <p>Procesando el documento… esta pantalla se actualizará automáticamente.</p> : null}
      {analysis.ocr?.status === 'failed' ? <><p className="form-error">El OCR ha fallado{analysis.ocr.error_message ? `: ${analysis.ocr.error_message}` : '.'}</p><button className="secondary-action" type="button" onClick={() => void startOcr()} disabled={running}>{running ? 'Reintentando…' : 'Reintentar'}</button></> : null}
      {analysis.ocr?.status === 'succeeded' && !analysis.extraction ? <p>El texto se ha leído, pero no se han identificado campos estructurados con suficiente patrón. El documento sigue siendo válido y puede revisarse manualmente más adelante.</p> : null}
    </section>

    {analysis.extraction ? <section className="section card">
      <div className="section-head"><h2>Propuesta para revisar</h2><span>{analysis.review ? 'confirmada' : 'pendiente'}</span></div>
      {editableKeys.map((key) => <label className="form-field" key={key}><span>{fieldLabel(key)}</span><input value={values[key] ?? ''} onChange={(event) => setValues((current) => ({ ...current, [key]: event.target.value }))} disabled={Boolean(analysis.review)} /><small>Confianza OCR: {Math.round((analysis.extraction?.confidence_json[key] ?? 0) * 100)} %</small></label>)}
      {analysis.review ? <p className="success-note">✓ Revisión humana guardada. Estos son los datos confirmados; aún no se ha creado ningún registro agrícola automáticamente.</p> : <button className="primary" type="button" onClick={() => void confirmReview()} disabled={saving}>{saving ? 'Guardando revisión…' : 'Confirmar datos revisados'}</button>}
    </section> : null}

    {analysis.review && (expenseHref || deliveryHref || resultHref) ? <section className="section card">
      <div className="section-head"><h2>Usar datos confirmados</h2><span>paso manual</span></div>
      <p>El siguiente formulario se abrirá prellenado. Puedes cambiar cualquier dato y nada se guardará hasta que pulses Guardar.</p>
      <div className="record-actions">
        {expenseHref ? <Link className="primary action-link" href={expenseHref}>Crear gasto con estos datos →</Link> : null}
        {deliveryHref ? <Link className="primary action-link" href={deliveryHref}>Crear entrega con estos datos →</Link> : null}
        {resultHref ? <Link className="primary action-link" href={resultHref}>Asociar rendimiento a una entrega →</Link> : null}
      </div>
    </section> : null}

    {error ? <p className="form-error" role="alert">{error}</p> : null}
    <div className="record-actions"><Link className="secondary-action action-link" href={backHref}>Volver a la finca</Link></div>
  </>;
}
