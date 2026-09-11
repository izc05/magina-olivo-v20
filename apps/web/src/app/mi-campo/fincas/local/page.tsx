import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { LocalFieldPage } from '@/components/local-field-page';
import { previewModeEnabled } from '@/lib/api-client';

export default function LocalFarmPage() {
  if (!previewModeEnabled) {
    return <main className="app-shell"><Topbar/><div className="page local-field-page"><section className="card"><h1>Ruta de preview no disponible</h1><p>Esta finca local pertenece únicamente al prototipo visual. En Beta, Mi Campo usa la API y datos persistidos del workspace.</p><Link href="/mi-campo" className="primary action-link">Volver a Mi Campo</Link></section></div><BottomNav active="/mi-campo"/></main>;
  }
  return <main className="app-shell"><Topbar/><div className="page local-field-page"><LocalFieldPage/></div><BottomNav active="/mi-campo"/></main>;
}
