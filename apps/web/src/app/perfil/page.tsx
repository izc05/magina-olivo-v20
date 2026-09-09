import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, SproutIcon } from '@/components/icons';
import { InstallAppCard } from '@/components/install-app-card';
import { PermissionCenter } from '@/components/permission-center';
import { demoUser } from '@/lib/demo-data';

const cards = [
  {title:'Mi cuenta', rows:[['Google','Conectado ✓'],['Email','isi@ejemplo.com'],['Sesiones','2 activas']]},
  {title:'Preferencias', rows:[['Tema','Automático'],['Unidades','Métricas · ha · kg'],['Municipio preferido',demoUser.municipality]]},
] as const;

export default function ProfilePage(){
  return <main className="app-shell"><Topbar/><div className="page profile-page">
    <section className="profile-hero premium-profile-hero"><div className="avatar premium-avatar">{demoUser.displayName.slice(0,1)}</div><div className="profile-name-row"><div><h1>{demoUser.displayName}</h1><p><MapPinIcon/> {demoUser.municipality}</p></div><button className="profile-edit">Editar perfil</button></div><div className="role-row"><span><SproutIcon/> {demoUser.roles[0]}</span><span>▣ {demoUser.roles[1]}</span></div></section>

    <section className="section card profile-progress"><div className="progress-ring"><span>{demoUser.profileCompletion}%</span></div><div><h2>Perfil {demoUser.profileCompletion} % completado</h2><p>Completa los datos que te ayudan a personalizar Mágina.</p></div><ArrowIcon/></section>

    <section className="section"><InstallAppCard/></section>

    <div className="profile-grid">
      {cards.map((card)=><section className="card profile-card premium-profile-card" key={card.title}><div className="profile-card-head"><h3>{card.title}</h3><span>Gestionar</span></div>{card.rows.map(([a,b])=><div className="profile-line" key={a}><span>{a}</span><span>{b} ›</span></div>)}</section>)}
      <section className="card profile-card premium-profile-card olive-card"><div className="profile-card-head"><h3>Mi Olivo</h3><span>Nivel {demoUser.level}</span></div><div className="olivo-level"><span className="olivo-mark"><SproutIcon/></span><div><strong>Cuidador del territorio</strong><small>{demoUser.points.toLocaleString('es-ES')} puntos</small><div className="level-bar"><i/></div></div></div></section>
      <section className="card profile-card premium-profile-card professional-card"><div className="profile-card-head"><h3>Perfil profesional</h3><span>Editar</span></div><p>Trabaja para terceros sin mezclar tu información personal.</p><div className="profile-line"><span>Servicios</span><span>Recolección · poda ›</span></div><div className="profile-line"><span>Directorio</span><span>Visible ›</span></div></section>
    </div>

    <section className="section"><PermissionCenter/></section>

    <section className="profile-footer-actions"><button className="secondary-action">Exportar mis datos</button><button className="danger-action">Cerrar sesión</button></section>
  </div><BottomNav active="/perfil"/></main>;
}
