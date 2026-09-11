'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { getDocumentReadUrl, loadFieldDocuments, type FieldDocument } from '@/lib/document-data-source';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';

export function FarmDocumentsPanel() {
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const [documents, setDocuments] = useState<FieldDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openingId, setOpeningId] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) {
      setDocuments([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadFieldDocuments(selectedWorkspaceId, context.id)
      .then((items) => { if (!cancelled) setDocuments(items); })
      .catch((cause) => { console.warn('Unable to load farm documents', cause); if (!cancelled) setError('No se han podido cargar los documentos.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [context.id, context.source, found, ready, selectedWorkspaceId]);

  async function openDocument(documentId: string) {
    if (!selectedWorkspaceId) return;
    const popup = window.open('', '_blank');
    try {
      setOpeningId(documentId);
      const access = await getDocumentReadUrl(selectedWorkspaceId, documentId);
      if (popup) popup.location.href = access.url;
      else window.location.href = access.url;
    } catch (cause) {
      console.error('Unable to open document', cause);
      popup?.close();
      setError('El documento no está disponible para lectura en este momento.');
    } finally {
      setOpeningId(null);
    }
  }

  if (!ready || !found || context.source !== 'api') return null;

  const uploadHref = withFieldQuery('/mi-campo/documentos/nuevo', context.id, context.source);
  return <section className="section">
    <div className="section-head"><h2>Documentos recientes</h2><Link href={uploadHref} className="detail-link">＋ Añadir documento</Link></div>
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {loading ? <section className="card"><p>Cargando documentos…</p></section> : null}
    {!loading && documents.length === 0 ? <section className="card"><p>Aún no hay documentos vinculados a esta finca.</p></section> : null}
    {documents.length ? <div className="card feed today-list">{documents.slice(0, 5).map((document) => <div className="feed-row" key={document.id}><div className="feed-copy"><strong>{document.title}</strong><small>{document.kind} · {document.created_at.slice(0, 10)}{document.domain_type ? ` · ${document.domain_type}` : ''}</small></div><button type="button" className="secondary-action" onClick={() => void openDocument(document.id)} disabled={openingId === document.id}>{openingId === document.id ? 'Abriendo…' : 'Abrir'}</button></div>)}</div> : null}
  </section>;
}
