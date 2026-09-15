import type { Metadata } from 'next';
import { AdminAgendaConsole } from '../../../components/admin-agenda-console';

export const metadata: Metadata = {
  title: 'Agenda global · Mágina Olivo',
  description: 'Administración global y auditada de tareas planificadas.',
  robots: { index: false, follow: false },
};

export default function AdminAgendaPage() {
  return <AdminAgendaConsole />;
}
