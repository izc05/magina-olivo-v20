import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { LocalFieldModulePage } from '@/components/local-field-module-page';
import { previewModeEnabled } from '@/lib/api-client';

export default function LocalFarmModulePage() {
  if (!previewModeEnabled) {
    return <main className="app-shell"><Topbar/><div className="page farm-module-page"><section className="card"><h1>Ruta de preview no disponible</h1><p>Los módulos locales pertenecen únicamente al prototipo visual. En Beta, la finca y sus registros se consultan desde la API.</p><Link href="/mi-campo" className="primary action-link">Volver a Mi Campo</Link></section></div><BottomNav active="/mi-campo"/></main>;
  }
  return <main className="app-shell"><Topbar/><div className="page farm-module-page"><LocalFieldModulePage/></div><BottomNav active="/mi-campo"/></main>;
}
