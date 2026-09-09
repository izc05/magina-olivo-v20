import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, RainIcon, SproutIcon } from '@/components/icons';
import { demoContext, demoFarmSummary, lasCenillas } from '@/lib/demo-data';

export default function HomePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page home-page">
        <section className="hero home-hero">
          <div className="hero-content">
            <div className="location-chip"><MapPinIcon /> {demoContext.municipality} <span>· ubicación de demostración</span></div>
            <div className="hero-spacer" />
            <div className="hero-weather">
              <div><div className="weather-temp">{demoContext.temperatureC}°</div><strong>{demoContext.condition}</strong></div>
              <div className="weather-meta">Viento {demoContext.windKmh} km/h<br/>Humedad {demoContext.humidityPercent} %</div>
            </div>
            <div className="hero-rule" />
            <p className="hero-message">Buen día para revisar el campo</p>
          </div>
        </section>

        <section className="section section-overlap">
          <div className="section-head"><h2>Avisos importantes</h2><Link href="/radar">Ver todos <ArrowIcon /></Link></div>
          <div className="alert-grid">
            <Link href="/radar" className="card alert blue"><span className="alert-icon"><RainIcon /></span><strong>Lluvia mañana</strong><small>Probabilidad 70 % · 5–12 mm</small></Link>
            <Link href="/mi-campo/fincas/las-cenillas" className="card alert green"><span className="alert-icon water-drop">●</span><strong>Próximo riego</strong><small>{lasCenillas.name} · en 2 días</small></Link>
            <div className="card alert rose"><span className="alert-icon">◉</span><strong>Riesgo de mosca</strong><small>Nivel alto · extrema vigilancia</small></div>
          </div>
        </section>

        <section className="section card field-summary premium-summary">
          <div className="field-summary-top">
            <div className="summary-brand"><span className="summary-mark"><SproutIcon /></span><div><h2>Mi Campo</h2><p>Tus fincas, siempre contigo</p></div></div>
            <Link href="/mi-campo" className="detail-link">Ver detalle <ArrowIcon /></Link>
          </div>
          <div className="stats"><div className="stat"><b>{demoFarmSummary.farms}</b><span>fincas</span></div><div className="stat"><b>{demoFarmSummary.oliveTrees}</b><span>olivas</span></div><div className="stat"><b>{demoFarmSummary.activeAlerts}</b><span>aviso activo</span></div></div>
          <Link href="/mi-campo" className="primary action-link">Entrar en Mi Campo <ArrowIcon /></Link>
        </section>

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
