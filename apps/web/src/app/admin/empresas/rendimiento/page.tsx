import type { Metadata } from 'next';
import { BusinessRevenueAdmin } from '@/components/business-revenue-admin';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Rendimiento de empresas · Administración · Mágina Olivo',
  description: 'Leads, conversión, ofertas y valor generado por el directorio de empresas.',
  robots: { index: false, follow: false },
};

export default function BusinessRevenuePage() {
  return <BusinessRevenueAdmin />;
}
