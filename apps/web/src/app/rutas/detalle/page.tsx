import type { Metadata } from 'next';
import 'maplibre-gl/dist/maplibre-gl.css';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { RouteDetailClient } from './route-detail-client';

export const metadata: Metadata = {
  title: 'Detalle de ruta · Mágina Olivo',
  description: 'Ficha de ruta con track validado, mapa interactivo y perfil de elevación.',
};

export default function RouteDetailPage() {
  return <main className="app-shell">
    <Topbar />
    <div className="page"><RouteDetailClient /></div>
    <BottomNav active="/explorar" />
  </main>;
}
