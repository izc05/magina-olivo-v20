import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

export default function HomePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page">
        <section className="hero">
          <div className="hero-kicker">📍 Huelma · ubicación de demostración</div>
          <h1>Mágina,<br/>de un vistazo</h1>
          <p>Aquí la tierra tiene sentido.</p>
          <div className="weather-row">
            <div className="weather-temp">24°</div>
            <div className="weather-meta">Soleado<br/>Viento 9 km/h · Humedad 48 %</div>
          </div>
        </section>

        <section className="section">
          <div className="section-head"><h2>Avisos importantes</h2><Link href="/radar">Ver todos ›</Link></div>
          <div className="alert-grid">
            <Link href="/radar" className="card alert blue"><span className="icon">🌧</span><strong>Lluvia mañana</strong><small>70 % · 5–12 mm</small></Link>
            <Link href="/mi-campo/fincas/las-cenillas" className="card alert green"><span className="icon">💧</span><strong>Próximo riego</strong><small>En 2 días</small></Link>
            <div className="card alert rose"><span className="icon">🪰</span><strong>Riesgo de mosca</strong><small>Nivel alto</small></div>
          </div>
        </section>

        <section className="section card field-summary">
          <div className="field-summary-top"><div><h2>Mi Campo</h2><p>Tus fincas, siempre contigo</p></div><span>🌳</span></div>
          <div className="stats"><div className="stat"><b>6</b><span>fincas</span></div><div className="stat"><b>248</b><span>olivas</span></div><div className="stat"><b>1</b><span>aviso activo</span></div></div>
          <Link href="/mi-campo"><button className="primary">Entrar en Mi Campo →</button></Link>
        </section>

        <section className="section">
          <div className="section-head"><h2>Actualidad</h2><a>Ver todo ›</a></div>
          <div className="card feed">
            <div className="feed-row"><div className="dot">🫒</div><div><strong>La campaña del olivar</strong><small>Noticias · Sierra Mágina</small></div></div>
            <div className="feed-row"><div className="dot">📅</div><div><strong>Feria del Olivo de Huelma</strong><small>Próximo evento</small></div></div>
            <div className="feed-row"><div className="dot">⭐</div><div><strong>Empresa destacada cerca de ti</strong><small>Contenido patrocinado</small></div></div>
          </div>
        </section>
      </div>
      <BottomNav active="/" />
    </main>
  );
}
