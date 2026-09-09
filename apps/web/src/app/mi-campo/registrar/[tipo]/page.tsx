import { notFound } from 'next/navigation';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { QuickRecordForm } from '@/components/quick-record-form';
import { getRecordType, recordTypes } from '@/lib/record-types';

export const dynamicParams = false;

export function generateStaticParams() {
  return recordTypes.map((type) => ({ tipo: type.slug }));
}

export default async function QuickRecordPage({ params }: { params: Promise<{ tipo: string }> }) {
  const { tipo } = await params;
  const type = getRecordType(tipo);
  if (!type) notFound();

  return (
    <main className="app-shell">
      <Topbar />
      <div className="page quick-record-page">
        <header className="page-title compact-record-title">
          <span className="eyebrow dark">MI CAMPO · REGISTRAR</span>
          <h1>{type.label}</h1>
          <p>{type.description}</p>
        </header>
        <QuickRecordForm type={type} />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
