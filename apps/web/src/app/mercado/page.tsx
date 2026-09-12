import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { MarketDashboard } from './market-dashboard';

export const metadata: Metadata = {
  title: 'Aceite y mercado · Mágina Olivo',
  description: 'Precios de referencia del aceite de oliva, evolución semanal y una estimación sencilla para tu cosecha.',
};

export default function MarketPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <MarketDashboard />
      <BottomNav active="/explorar" />
    </main>
  );
}
