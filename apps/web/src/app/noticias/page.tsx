import type { Metadata } from 'next';
import { Suspense } from 'react';
import { PublicEditorialPage } from '../../components/public-editorial-page';
import { TerritoryReturnLink } from '../../components/territory-return-link';

export const metadata: Metadata = {
  title: 'Noticias · Mágina Olivo',
  description: 'Actualidad del campo, pueblos y cooperativas de Sierra Mágina.',
};

export default function NewsPage() {
  return <Suspense fallback={null}><TerritoryReturnLink /><PublicEditorialPage type="news" /></Suspense>;
}
