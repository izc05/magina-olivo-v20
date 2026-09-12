'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import {
  confirmDocumentExtraction,
  getDocumentReadUrl,
  loadDocumentAnalysis,
  requestDocumentOcr,
  type DocumentAnalysis,
} from '@/lib/document-data-source';
import {
  documentKindLabel,
  documentWorkflowState,
  supportsDocumentOcr,
  workflowStateLabels,
  type DocumentWorkflowState,
} from '@/lib/document-workflow';
import styles from './document-workflow.module.css';

const labels: Record<string, string> = {
  date: 'Fecha', total_eur: 'Importe total (€)', document_number: 'Nº documento', supplier_tax_id: 'NIF/CIF proveedor',
  total_kg: 'Kilos totales', ticket_number: 'Nº albarán / ticket', yield_percent: 'Rendimiento (%)', moisture_percent: 'Humedad (%)', acidity_percent: 'Acidez (%)',
  gross_eur: 'Importe bruto (€)', deductions_eur: 'Deducciones (€)', net_eur: 'Importe neto (€)', settlement_number: 'Nº liquidación', counterparty_name: 'Cooperativa / almazara',
  amount_eur: 'Importe cobrado (€)', reference: 'Referencia',
};
const numericKeys = new Set(['total_eur', 'total_kg', 'yield_percent', 'moisture_percent', 'acidity_percent', 'gross_eur', 'deductions_eur', 'net_eur', 'amount_eur']);
const stageOrder: DocumentWorkflowState[] = ['available', 'processing', 'needs_review', 'confirmed'];

function fieldLabel(key: string) { return labels[key] ?? key.replaceAll('_', ' '); }
function coerceValue(key: string, raw: string) {
  if (!numericKeys.has(key)) return raw.trim();
  const value = Number(raw.replace(',', '.'));
  return Number.isFinite(value) ? value : raw.trim();
}
function stringValue(fields: Record<string, unknown>, key: string) { const value = fields[key]; return value == null ? '' : String(value); }
function buildHref(path: string, values: Record<string, string>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value) query.set(key, value);
  return `${path}?${query.toString()}`;
}
function reviewedFields(analysis: DocumentAnalysis | null): Record<string, unknown> | null {
  if (!analysis?.extraction) return null;
  if (!analysis.review) return analysis.extraction.data_json;
  return {
    ...analysis.extraction.data_json,
    ...analysis.review.confirmed_fields,
    ...analysis.review.corrections,
  };
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
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const backHref = fieldId ? `/mi-campo/fincas/ver?id=${encodeURIComponent(fieldId)}&source=${encodeURIComponent(source)}` : '/mi-campo';
  const refresh = useCallback(async () => {
    if (!selectedWorkspaceId || !documentId) {
      setLoading(false);
      return;
    }
    try {
      setError(null);
      setAnalysis(await loadDocumentAnalysis(selectedWorkspaceId, documentId));
    } catch (cause) {
      console.error('Unable to load document analysis', cause);
      setError('No se ha podido cargar el análisis del documento.');
    } finally {
      setLoading(false);
    }
  }, [documentId, selectedWorkspaceId]);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    const data = reviewedFields(analysis);
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

  async function openOriginal() {
    if (!selectedWorkspaceId) return;
    const popup = window.open('', '_blank');
    try {
      setOpening(true);
      setError(null);
      const access = await getDocumentReadUrl(selectedWorkspaceId, documentId);
      if (popup) popup.location.href = access.url;
      else window.location.href = access.url;
    } catch (cause) {
      console.error('Unable to open original document', cause);
      popup?.close();
      setError('No se ha podido abrir el archivo original en este momento.');
    } finally {
      setOpening(false);
    }
  }

  async function startOcr() {
    if (!selectedWorkspaceId || !analysis?.version) return;
    try {
      setRunning(true);
      setError(null);
      await requestDocumentOcr(selectedWorkspaceId, documentId, analysis.version.id);
      await refresh();
    } catch (cause) {
      console.error('Unable to start OCR', cause);
      setError('No se ha podido iniciar el análisis OCR. El archivo original sigue guardado.');
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
      setError('No se ha podido guardar la revisión. Ningún registro agrícola se ha creado.');
    } finally {
      setSaving(false);
    }
  }

  if (!documentId) return <section className="card"><h1>Documento no indicado</h1><Link href={backHref}>Volver</Link></section>;
  if (!selectedWorkspaceId) return <section className="card"><h1>Sesión necesaria</h1><p>Entra en tu espacio para revisar documentos privados.</p><Link href="/perfil">Ir a mi cuenta</Link></section>;
  if (loading) return <section className="card"><p>Cargando documento y estado de lectura…</p></section>;
  if (!analysis) return <section className="card"><h1>Análisis no disponible</h1><p>{error ?? 'No se ha encontrado un análisis accesible para este documento.'}</p><Link href={backHref}>Volver</Link></section>;

  const ocrBusy = analysis.ocr && ['queued', 'processing'].includes(analysis.ocr.status);
  const reviewed = analysis.review ? reviewedFields(analysis) : null;
  const workflowState = documentWorkflowState(analysis);
  const workflowIndex = workflowState === 'failed' ? 1 : stageOrder.indexOf(workflowState);
  const canAnalyze = supportsDocumentOcr(analysis.document.kind);
  const baseParams = { fieldId, source, sourceDocumentId: documentId };
  const expenseHref = reviewed && ['invoice', 'purchase_receipt'].includes(analysis.document.kind) ? buildHref('/mi-campo/registrar/gasto', {
    ...baseParams, prefillDate: stringValue(reviewed, 'date'), prefillAmount: stringValue(reviewed, 'total_eur'), prefillConcept: analysis.document.title,
    prefillCategory: 'Otro', prefillNotes: stringValue(reviewed, 'document_number') ? `Documento ${stringValue(reviewed, 'document_number')}` : '',
  }) : null;
  const deliveryHref = reviewed && analysis.document.kind === 'delivery_ticket' ? buildHref('/mi-campo/registrar/cosecha', {
    ...baseParams, prefillDate: stringValue(reviewed, 'date'), prefillKg: stringValue(reviewed, 'total_kg'), prefillTicket: stringValue(reviewed, 'ticket_number'),
  }) : null;
  const resultHref = reviewed && analysis.document.kind === 'yield_result' ? buildHref('/mi-campo/registrar/rendimiento', {
    ...baseParams, prefillDate: stringValue(reviewed, 'date'), prefillYield: stringValue(reviewed, 'yield_percent'), prefillMoisture: stringValue(reviewed, 'moisture_percent'), prefillAcidity: stringValue(reviewed, 'acidity_percent'), prefillTicket: stringValue(reviewed, 'ticket_number'),
  }) : null;
  const settlementHref = reviewed && analysis.document.kind === 'settlement_statement' ? buildHref('/mi-campo/registrar/liquidacion', {
    ...baseParams, prefillDate: stringValue(reviewed, 'date'), prefillGross: stringValue(reviewed, 'gross_eur'), prefillDeductions: stringValue(reviewed, 'deductions_eur'), prefillNet: stringValue(reviewed, 'net_eur'), prefillNumber: stringValue(reviewed, 'settlement_number'), prefillCounterparty: stringValue(reviewed, 'counterparty_name'),
  }) : null;
  const collectionHref = reviewed && analysis.document.kind === 'collection_receipt' ? buildHref('/mi-campo/registrar/cobro', {
    ...baseParams, prefillDate: stringValue(reviewed, 'date'), prefillAmount: stringValue(reviewed, 'amount_eur'), prefillReference: stringValue(reviewed, 'reference'),
  }) : null;

  const stepClass = (index: number) => `${styles.step} ${index < workflowIndex ? styles.stepDone : index === workflowIndex ? styles.stepCurrent : ''}`.trim();

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · DOCUMENTOS</span><h1>Revisar lectura</h1><p>{analysis.document.title}</p></div></header>

    <section className={`section card ${styles.flowCard}`}>
      <div className={styles.flowHeader}>
        <div><h2>{documentKindLabel(analysis.document.kind)}</h2><p>El archivo original se conserva siempre. La lectura OCR solo prepara una propuesta para tu revisión.</p></div>
        <span className={styles.statusPill}>{workflowStateLabels[workflowState]}</span>
      </div>
      <div className={styles.steps} aria-label="Progreso del documento">
        <div className={stepClass(0)}><strong>1 · Archivo</strong><span>Original disponible</span></div>
        <div className={stepClass(1)}><strong>2 · Lectura</strong><span>OCR opcional</span></div>
        <div className={stepClass(2)}><strong>3 · Revisión</strong><span>Confirmar o corregir</span></div>
        <div className={stepClass(3)}><strong>4 · Registro</strong><span>Crear y vincular</span></div>
      </div>
      <div className={styles.originalMeta}>
        <div><span>Archivo</span><strong>{analysis.version?.original_filename ?? 'Sin versión disponible'}</strong></div>
        <div><span>Integridad</span><strong>{analysis.version?.integrity_status === 'verified' ? 'Verificada ✓' : analysis.version?.integrity_status ?? 'Pendiente'}</strong></div>
        <div><span>Formato</span><strong>{analysis.version?.mime_type ?? 'No indicado'}</strong></div>
        <div><span>Lectura OCR</span><strong>{analysis.ocr ? workflowStateLabels[workflowState] : canAnalyze ? 'Sin iniciar' : 'No necesaria'}</strong></div>
      </div>
      <div className={styles.actions}><button className="secondary-action" type="button" onClick={() => void openOriginal()} disabled={opening}>{opening ? 'Abriendo original…' : 'Abrir archivo original'}</button></div>
    </section>

    <section className="section card"><div className="section-head"><h2>Lectura del documento</h2><span>{workflowStateLabels[workflowState]}</span></div><p>El OCR solo propone datos. Nada se convierte en gasto, entrega, rendimiento, liquidación o cobro hasta que lo revises y confirmes expresamente.</p>
      <small className="subtle">Estado técnico OCR: {analysis.ocr?.status ?? 'sin analizar'}</small>
      {!analysis.ocr && canAnalyze ? <div className={styles.actions}><button className="primary" type="button" onClick={() => void startOcr()} disabled={running || analysis.version?.upload_status !== 'uploaded'}>{running ? 'Iniciando lectura…' : 'Analizar documento'}</button></div> : null}
      {!canAnalyze ? <div className={styles.notice}>Este tipo de documento se conserva como evidencia y no tiene un extractor OCR estructurado. Puedes abrir el original y volver a la finca.</div> : null}
      {ocrBusy ? <div className={styles.notice} aria-live="polite"><strong>Procesando documento…</strong> Esta pantalla se actualizará automáticamente. Puedes salir: el archivo original ya está guardado.</div> : null}
      {analysis.ocr?.status === 'failed' ? <><p className="form-error">El OCR ha fallado{analysis.ocr.error_message ? `: ${analysis.ocr.error_message}` : '.'} El original no se ha perdido.</p><button className="secondary-action" type="button" onClick={() => void startOcr()} disabled={running}>{running ? 'Reintentando…' : 'Reintentar lectura'}</button></> : null}
      {analysis.ocr?.status === 'succeeded' && !analysis.extraction ? <div className={styles.notice}>El texto se ha leído, pero no se han identificado campos estructurados con suficiente patrón. El documento sigue disponible como evidencia.</div> : null}
    </section>

    {analysis.extraction ? <section className="section card"><div className="section-head"><h2>Propuesta para revisar</h2><span>{analysis.review ? 'Revisada ✓' : 'Pendiente'}</span></div><p>Compara estos valores con el original. Puedes corregir cualquier campo antes de confirmarlo.</p>
      {editableKeys.map((key) => <label className="form-field" key={key}><span>{fieldLabel(key)}</span><input value={values[key] ?? ''} onChange={(event) => setValues((current) => ({ ...current, [key]: event.target.value }))} disabled={Boolean(analysis.review)} inputMode={numericKeys.has(key) ? 'decimal' : undefined} /><small>Confianza de lectura: {Math.round((analysis.extraction?.confidence_json[key] ?? 0) * 100)} %</small></label>)}
      {analysis.review ? <p className="success-note">✓ Revisión humana guardada. Los campos confirmados/corregidos se aplican sobre la extracción original; aún no se ha creado ningún registro agrícola automáticamente.</p> : <button className="primary" type="button" onClick={() => void confirmReview()} disabled={saving}>{saving ? 'Guardando revisión…' : 'Confirmar datos revisados'}</button>}
    </section> : null}

    {analysis.review && (expenseHref || deliveryHref || resultHref || settlementHref || collectionHref) ? <section className="section card"><div className="section-head"><h2>Crear registro con los datos revisados</h2><span>Paso manual</span></div><p>El siguiente formulario se abrirá prellenado. Puedes cambiar cualquier dato y nada se guardará hasta que pulses Guardar. Al guardar, el documento quedará relacionado con ese registro.</p><div className="record-actions">
      {expenseHref ? <Link className="primary action-link" href={expenseHref}>Crear gasto con estos datos →</Link> : null}
      {deliveryHref ? <Link className="primary action-link" href={deliveryHref}>Crear entrega con estos datos →</Link> : null}
      {resultHref ? <Link className="primary action-link" href={resultHref}>Asociar rendimiento a una entrega →</Link> : null}
      {settlementHref ? <Link className="primary action-link" href={settlementHref}>Crear liquidación y elegir entregas →</Link> : null}
      {collectionHref ? <Link className="primary action-link" href={collectionHref}>Registrar cobro y elegir liquidación →</Link> : null}
    </div></section> : null}

    {error ? <p className="form-error" role="alert">{error}</p> : null}<div className="record-actions"><Link className="secondary-action action-link" href={backHref}>Volver a la finca</Link></div>
  </>;
}
