'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

export function FarmPlanShortcut() {
  const params = useSearchParams();
  const id = params.get('id');
  const source = params.get('source');
  if (!id) return null;
  const query = new URLSearchParams({ fieldId: id });
  if (source) query.set('source', source);
  return <section className="section card register-principle"><div><strong>¿Algo pendiente para esta finca?</strong><small>Planifícalo ahora. Entrará en Hoy y recibirá contexto meteorológico cuando corresponda.</small></div><Link className="primary action-link" href={`/mi-campo/planificar?${query.toString()}`}>Planificar</Link></section>;
}
