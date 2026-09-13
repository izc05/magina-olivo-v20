import type { Metadata } from 'next';
import { BusinessOwnerPortal } from '@/components/business-owner-portal';

export const metadata: Metadata = {
  title: 'Mi negocio · Mágina Olivo',
  description: 'Gestiona tu ficha, ofertas, solicitudes y rendimiento en Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function MyBusinessPage() {
  return <BusinessOwnerPortal />;
}
