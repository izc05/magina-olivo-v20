import type { Metadata } from 'next';
import { BusinessExperiencesAdmin } from '@/components/business-experiences-admin';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Experiencias y reservas · Empresas · Administración',
  description: 'Gestión de experiencias, sesiones, aforo y solicitudes de reserva.',
  robots: { index: false, follow: false },
};

export default function BusinessExperiencesAdminPage(){return <BusinessExperiencesAdmin/>;}
