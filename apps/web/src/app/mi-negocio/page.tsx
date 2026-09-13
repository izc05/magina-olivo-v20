import type { Metadata } from 'next';
import Link from 'next/link';
import { BusinessOwnerPortal } from '@/components/business-owner-portal';

export const metadata: Metadata = {
  title: 'Mi negocio · Mágina Olivo',
  description: 'Gestiona tu ficha, ofertas, solicitudes, experiencias y rendimiento en Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function MyBusinessPage() {
  return <>
    <nav style={{maxWidth:1180,margin:'18px auto 0',padding:'0 20px',display:'flex',gap:10,flexWrap:'wrap'}} aria-label="Herramientas de mi negocio">
      <Link href="/mi-negocio/experiencias">Experiencias y reservas →</Link>
    </nav>
    <BusinessOwnerPortal />
  </>;
}
