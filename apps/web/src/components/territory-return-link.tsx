'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

export function TerritoryReturnLink() {
  const params = useSearchParams();
  const slug = params.get('fromPlace')?.trim() ?? '';
  const name = params.get('fromName')?.trim() ?? '';
  if (!slug) return null;
  return <div style={{ maxWidth: 1180, margin: '0 auto', padding: '0 1rem 1rem' }}>
    <Link href={`/pueblos/territorio?slug=${encodeURIComponent(slug)}`}>← Volver a {name || 'territorio'}</Link>
  </div>;
}
