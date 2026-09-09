import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

export default function ProfilePage(){
  return <main className="app-shell"><Topbar/><div className="page">
    <section className="profile-hero"><div className="avatar">👨‍🌾</div><h1>Isi</h1><p>📍 Bedmar · Agricultor · Profesional</p></section>
    <section className="section card field-summary"><div className="field-summary-top"><div><h2>Perfil 80 %</h2><p>Completa tu perfil para aprovechar toda la experiencia.</p></div><b>80%</b></div></section>
    <div className="profile-grid">
      <section className="card profile-card"><h3>Mi cuenta</h3><div className="profile-line"><span>Google</span><span>Conectado ✓</span></div><div className="profile-line"><span>Email</span><span>isi@ejemplo.com</span></div><div className="profile-line"><span>Sesiones</span><span>2 activas</span></div></section>
      <section className="card profile-card"><h3>Preferencias</h3><div className="profile-line"><span>Tema</span><span>Automático</span></div><div className="profile-line"><span>Unidades</span><span>ha · kg</span></div><div className="profile-line"><span>Municipio</span><span>Bedmar</span></div></section>
      <section className="card profile-card"><h3>Privacidad y permisos</h3><div className="profile-line"><span>Ubicación</span><span>Permitida</span></div><div className="profile-line"><span>Notificaciones</span><span>Permitidas</span></div><div className="profile-line"><span>Cámara</span><span>Contextual</span></div></section>
      <section className="card profile-card"><h3>Perfil profesional</h3><div className="profile-line"><span>Servicios</span><span>Recolección</span></div><div className="profile-line"><span>Directorio</span><span>Visible</span></div></section>
    </div>
  </div><BottomNav active="/perfil"/></main>;
}
