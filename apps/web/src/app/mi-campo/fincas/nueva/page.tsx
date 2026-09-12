import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { NewFarmWizard } from '@/components/new-farm-wizard';

export default function NewFarmPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page new-farm-page">
        <header className="page-title compact-record-title">
          <span className="eyebrow dark">MI CAMPO · FINCAS</span>
          <h1>Añadir finca</h1>
          <p>Primero el nombre que conoces. Catastro y SIGPAC son una ayuda, no una obligación.</p>
        </header>
        <NewFarmWizard />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
