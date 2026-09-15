'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

export function FarmEditShortcut() {
  const params = useSearchParams();
  const fieldId = params.get('id');
  const source = params.get('source');
  if (!fieldId || source === 'local' || source === 'demo') return null;
  return <div className="section-head"><span className="subtle">Datos y límites de esta finca</span><Link className="detail-link" href={`/mi-campo/fincas/editar?fieldId=${encodeURIComponent(fieldId)}`}>Editar finca</Link></div>;
}
