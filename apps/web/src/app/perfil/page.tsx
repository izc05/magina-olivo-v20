'use client';

import { BottomNav } from '@/components/bottom-nav';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, SproutIcon } from '@/components/icons';
import { InstallAppCard } from '@/components/install-app-card';
import { PermissionCenter } from '@/components/permission-center';
import { useAuth } from '@/components/auth-provider';
import { demoUser } from '@/lib/demo-data';

export default function ProfilePage(){
  const { status, user, workspaces, selectedWorkspaceId, selectWorkspace, logout } = useAuth();
  const authenticated = status === 'authenticated' && user;
  const selectedWorkspace = workspaces.find((workspace) => workspace.workspace_id === selectedWorkspaceId) ?? workspaces[0] ?? null;
  const displayName = authenticated ? user.display_name : 'Tu perfil en Mágina';
  const initial = displayName.trim().slice(0, 1).toUpperCase() || 'M';
  const locationLabel = authenticated ? 'Municipio por configurar' : 'Accede para personalizarlo';
  const accountRows = authenticated
    ? [
        ['Google', 'Conectado ✓'],
        ['Email', user.primary_email ?? 'Sin email principal'],
        ['Espacios', `${workspaces.length} activo${workspaces.length === 1 ? '' : 's'}`],
      ]
    : [
        ['Cuenta', 'Sin iniciar sesión'],
        ['Mi Campo', 'Requiere acceso'],
        ['Comunidad', 'Acceso para publicar'],
      ];

  return <main className="app-shell"><Topbar/><div className="page profile-page">
    <section className="profile-hero premium-profile-hero">
      <div className="avatar premium-avatar">{initial}</div>
      <div className="profile-name-row"><div><h1>{displayName}</h1><p><MapPinIcon/> {locationLabel}</p></div>{authenticated ? <button className="profile-edit">Editar perfil</button> : null}</div>
      <div className="role-row">
        {authenticated && selectedWorkspace ? <span><SproutIcon/> {selectedWorkspace.workspace_name}</span> : <span><SproutIcon/> Mágina Olivo</span>}
        {authenticated && selectedWorkspace ? <span>▣ {selectedWorkspace.role}</span> : <span>▣ Guía pública</span>}
      </div>
    </section>

    {status === 'loading' ? <section className="section card profile-progress"><div><h2>Comprobando tu sesión…</h2><p>La guía pública sigue disponible mientras cargamos tu cuenta.</p></div></section> : null}

    {status === 'anonymous' ? <section className="section card profile-progress"><div><h2>Entra para activar tu espacio personal</h2><p>Mi Campo, tus documentos, alertas, favoritos y publicaciones quedan asociados a tu cuenta. La guía de Mágina sigue siendo pública sin iniciar sesión.</p><GoogleSignInButton/></div><ArrowIcon/></section> : null}

    {authenticated ? <section className="section card profile-progress"><div className="progress-ring"><span>{demoUser.profileCompletion}%</span></div><div><h2>Completa tu perfil</h2><p>Añadiremos municipio, presentación, actividad y preferencias sin exponer datos privados de tus fincas.</p></div><ArrowIcon/></section> : null}

    <section className="section"><InstallAppCard/></section>

    <div className="profile-grid">
      <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Mi cuenta</h3><span>{authenticated ? 'Gestionar' : 'Acceder'}</span></div>{accountRows.map(([a,b])=><div className="profile-line" key={a}><span>{a}</span><span>{b} ›</span></div>)}</section>

      <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Preferencias</h3><span>Gestionar</span></div><div className="profile-line"><span>Tema</span><span>Automático ›</span></div><div className="profile-line"><span>Unidades</span><span>Métricas · ha · kg ›</span></div><div className="profile-line"><span>Municipio preferido</span><span>{authenticated ? 'Por configurar' : demoUser.municipality} ›</span></div></section>

      {authenticated && workspaces.length > 1 ? <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Espacio activo</h3><span>Cambiar</span></div>{workspaces.map((workspace)=><button type="button" className="profile-line" key={workspace.workspace_id} onClick={()=>selectWorkspace(workspace.workspace_id)}><span>{workspace.workspace_name}</span><span>{workspace.workspace_id === selectedWorkspaceId ? 'Activo ✓' : `${workspace.role} ›`}</span></button>)}</section> : null}

      <section className="card profile-card premium-profile-card olive-card"><div className="profile-card-head"><h3>Mi Olivo</h3><span>{authenticated ? `Nivel ${demoUser.level}` : 'Con cuenta'}</span></div><div className="olivo-level"><span className="olivo-mark"><SproutIcon/></span><div><strong>{authenticated ? 'Cuidador del territorio' : 'Tu olivo personal'}</strong><small>{authenticated ? `${demoUser.points.toLocaleString('es-ES')} puntos` : 'Entra para guardar puntos y recompensas'}</small><div className="level-bar"><i/></div></div></div></section>
      <section className="card profile-card premium-profile-card professional-card"><div className="profile-card-head"><h3>Perfil profesional</h3><span>{authenticated ? 'Configurar' : 'Con cuenta'}</span></div><p>Trabaja para terceros sin mezclar tu información personal ni tus fincas privadas.</p><div className="profile-line"><span>Servicios</span><span>{authenticated ? 'Por configurar ›' : 'Accede ›'}</span></div><div className="profile-line"><span>Directorio</span><span>{authenticated ? 'No publicado ›' : 'Accede ›'}</span></div></section>
    </div>

    <section className="section"><PermissionCenter/></section>

    {authenticated ? <section className="profile-footer-actions"><button className="secondary-action">Exportar mis datos</button><button className="danger-action" onClick={()=>void logout()}>Cerrar sesión</button></section> : null}
  </div><BottomNav active="/perfil"/></main>;
}
