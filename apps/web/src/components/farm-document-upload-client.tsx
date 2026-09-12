'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadApiCampaigns, type CampaignListItem } from '@/lib/campaign-data-source';
import { assignDocumentCampaign } from '@/lib/document-data-source';
import { uploadDomainAttachment, type DocumentKind } from '@/lib/document-upload-source';
import {
  buildDocumentReviewHref,
  documentKindLabel,
  documentKinds,
  supportsDocumentOcr,
} from '@/lib/document-workflow';
import { useFieldContext } from '@/lib/use-field-context';
import styles from './document-workflow.module.css';

type UploadedDocument = {
  documentId: string;
  versionId: string;
  kind: DocumentKind;
  title: string;
};

export function FarmDocumentUploadClient() {
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const [kind, setKind] = useState<DocumentKind>('invoice');
  const [title, setTitle] = useState('');
  const [campaignId, setCampaignId] = useState('');
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [saving, setSaving] = useState(false);
  const [uploadedDocument, setUploadedDocument] = useState<UploadedDocument | null>(null);
  const [contextWarning, setContextWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedWorkspaceId || context.source !== 'api') { setCampaigns([]); return; }
    let cancelled = false;
    loadApiCampaigns(selectedWorkspaceId).then((items) => {
      if (cancelled) return;
      setCampaigns(items);
      const active = items.find((item) => item.status === 'active');
      if (active) setCampaignId((current) => current || active.id);
    }).catch((cause) => console.warn('Unable to load campaigns for document', cause));
    return () => { cancelled = true; };
  }, [context.source, selectedWorkspaceId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setContextWarning(null);
    if (!selectedWorkspaceId || context.source !== 'api') {
      setError('La subida documental real necesita una finca del servidor.');
      return;
    }

    const fd = new FormData(event.currentTarget);
    const file = fd.get('file');
    if (!(file instanceof File) || !file.size) {
      setError('Selecciona un archivo.');
      return;
    }

    const resolvedTitle = title.trim() || file.name;
    try {
      setSaving(true);
      const uploaded = await uploadDomainAttachment({
        workspaceId: selectedWorkspaceId,
        fieldId: context.id,
        file,
        kind,
        title: resolvedTitle,
        relation: 'attachment',
      });

      if (campaignId) {
        try {
          await assignDocumentCampaign(selectedWorkspaceId, uploaded.documentId, campaignId);
        } catch (campaignError) {
          console.warn('Document stored but campaign assignment failed', campaignError);
          setContextWarning('El documento está guardado y verificado, pero quedó sin campaña asignada. Podrás clasificarlo después.');
        }
      }

      setUploadedDocument({ ...uploaded, kind, title: resolvedTitle });
    } catch (cause) {
      console.error('Unable to upload farm document', cause);
      setError('No se ha podido subir y verificar el documento. El archivo no se dará por válido hasta completar la verificación.');
    } finally {
      setSaving(false);
    }
  }

  function resetForm() {
    setUploadedDocument(null);
    setContextWarning(null);
    setError(null);
    setTitle('');
  }

  if (!ready) return <section className="card"><p>Cargando finca…</p></section>;
  if (!found) return <section className="card"><h1>Finca no encontrada</h1><Link href="/mi-campo">Volver a Mi Campo</Link></section>;

  if (uploadedDocument) {
    const canAnalyze = supportsDocumentOcr(uploadedDocument.kind);
    const reviewHref = buildDocumentReviewHref(uploadedDocument.documentId, context.id, context.source);

    return <section className={`card record-success ${styles.flowCard}`}>
      <div className={styles.flowHeader}>
        <div><div className="success-mark">✓</div><h1>Documento disponible</h1><p><strong>{uploadedDocument.title}</strong> ya está guardado en {context.name} con el archivo original verificado.</p></div>
        <span className={styles.statusPill}>Archivo guardado ✓</span>
      </div>

      {canAnalyze ? <>
        <div className={styles.steps} aria-label="Progreso del documento">
          <div className={`${styles.step} ${styles.stepDone}`}><strong>1 · Archivo</strong><span>Guardado y verificado</span></div>
          <div className={`${styles.step} ${styles.stepCurrent}`}><strong>2 · Lectura</strong><span>Iniciar OCR cuando quieras</span></div>
          <div className={styles.step}><strong>3 · Revisión</strong><span>Confirmar o corregir</span></div>
          <div className={styles.step}><strong>4 · Registro</strong><span>Crear y vincular el dato</span></div>
        </div>
        <div className={styles.notice}><strong>El OCR no guardará kilos, importes ni fechas como datos definitivos.</strong> Primero te enseñará una propuesta para que la revises.</div>
      </> : <div className={styles.notice}><strong>{documentKindLabel(uploadedDocument.kind)} guardado.</strong> Este tipo no necesita lectura OCR. El original seguirá disponible en Documentos y podrás relacionarlo con la actividad que corresponda.</div>}

      {contextWarning ? <p className="form-error" role="status">{contextWarning}</p> : null}
      <div className={styles.actions}>
        {canAnalyze ? <Link className="primary action-link" href={reviewHref}>Analizar y revisar →</Link> : null}
        <button className="secondary-action" type="button" onClick={resetForm}>Añadir otro</button>
        <Link className="secondary-action action-link" href={context.returnHref}>Volver a la finca</Link>
      </div>
    </section>;
  }

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · DOCUMENTOS</span><h1>Añadir documento</h1><p>{context.name} · guarda el original una sola vez y relaciónalo después con campaña o actividad.</p></div></header>
    <form className="section card" onSubmit={submit}>
      <label className="form-field"><span>Tipo</span><select value={kind} onChange={(event) => setKind(event.target.value as DocumentKind)}>{documentKinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select><small>{supportsDocumentOcr(kind) ? 'Este tipo admite lectura OCR opcional después de guardarlo.' : 'Este tipo se conserva como evidencia y no necesita OCR.'}</small></label>
      <label className="form-field"><span>Campaña</span><select value={campaignId} onChange={(event) => setCampaignId(event.target.value)}><option value="">Sin asignar</option>{campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name}{campaign.status === 'active' ? ' · activa' : ''}</option>)}</select><small>No es obligatorio. Si no estás seguro, déjalo sin asignar.</small></label>
      <label className="form-field"><span>Título</span><input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Ej. Liquidación cooperativa enero" maxLength={240} /></label>
      <label className="form-field"><span>Archivo</span><input name="file" type="file" accept="image/*,.pdf" required /><small>Máximo 100 MB. El original se verifica antes de quedar disponible.</small></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="form-actions"><button className="primary" type="submit" disabled={saving}>{saving ? 'Subiendo y verificando…' : 'Guardar documento'}</button><Link className="secondary-action action-link" href={context.returnHref}>Cancelar</Link></div>
    </form>
  </>;
}
