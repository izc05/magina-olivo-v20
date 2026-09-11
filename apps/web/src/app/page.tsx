import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { HomeDailyCenter } from '@/components/home-daily-center';
import { HomePriorityCard } from '@/components/home-priority-card';
import { ArrowIcon } from '@/components/icons';
import { demoContext } from '@/lib/demo-data';

export default function HomePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page home-page">
        <HomeDailyCenter />

        <HomePriorityCard />

        <section className="section">
          <div className="section-head"><h2>Actualidad y vida local</h2><Link href="/explorar">Ver todo <ArrowIcon /></Link></div>
          <div className="story-grid">
            <article className="card story-card"><div className="story-image story-olive"/><span className="story-tag">NOTICIAS</span><h3>La campaña del olivar en Sierra Mágina</h3><p>Actualidad agrícola y territorio.</p></article>
            <article className="card story-card"><div className="story-image story-town"/><span className="story-tag">EVENTOS</span><h3>Agenda local de {demoContext.municipality}</h3><p>Ferias, jornadas y encuentros.</p></article>
            <article className="card story-card sponsored"><div className="story-image story-oil"/><span className="story-tag gold">PATROCINADO</span><h3>Empresas de nuestra tierra</h3><p>Promoción local integrada y clara.</p></article>
          </div>
        </section>

        <section className="territory-banner">
          <div><span className="eyebrow">MÁGINA OLIVO</span><h2>Personas que cuidan de un territorio único</h2></div>
          <Link href="/explorar">Descubrir Mágina <ArrowIcon /></Link>
        </section>
      </div>
      <BottomNav active="/" />
    </main>
  );
}
