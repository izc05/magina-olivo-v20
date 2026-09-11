import type { Metadata } from 'next';
import { PublicEditorialPage } from '../../components/public-editorial-page';

export const metadata: Metadata = {
  title: 'Noticias · Mágina Olivo',
  description: 'Actualidad del campo, pueblos y cooperativas de Sierra Mágina.',
};

export default function NewsPage() {
  return <PublicEditorialPage type="news" />;
}
