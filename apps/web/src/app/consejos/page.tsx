import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { FieldAdviceExplorer } from '@/components/field-advice-explorer';
import { Topbar } from '@/components/topbar';

export const metadata: Metadata = {
  title: 'Consejos del campo | Mágina Olivo',
  description: 'Guías prácticas para observar, registrar y tomar decisiones con más contexto en el olivar.',
};

export default function FieldAdvicePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <FieldAdviceExplorer />
      <BottomNav active="/explorar" />
    </main>
  );
}
