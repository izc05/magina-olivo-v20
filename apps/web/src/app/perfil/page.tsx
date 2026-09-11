'use client';

import { BottomNav } from '@/components/bottom-nav';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, SproutIcon } from '@/components/icons';
import { InstallAppCard } from '@/components/install-app-card';
import { PermissionCenter } from '@/components/permission-center';
import { AgronomyAlertSettings } from '@/components/agronomy-alert-settings';
import { HomePrioritySettings } from '@/components/home-priority-settings';
import { FinancialNotificationSettings } from '@/components/financial-notification-settings';
import { CommercialNotificationSettings } from '@/components/commercial-notification-settings';
import { ProfessionalBusinessProfileSettings } from '@/components/professional-business-profile-settings';
import { useAuth } from '@/components/auth-provider';

const roleLabels: Record<string, string> = {
  agricultor: 'Agricultor',
  propietario: 'Propietario',
  trabajador: 'Trabajador del campo',
  profesional_agricola: 'Profesional agrícola',
  tecnico: 'Técnico',
  empresa: 'Empresa',
  otro: 'Miembro de Mágina',
};

const themeLabels: Record<string, string> = { system: 'Automático', light: 'Claro', dark: 'Oscuro' };

export default function ProfilePage(){
  const { status, user, profile, preferences, workspaces, selectedWorkspaceId, selectWorkspace, logout } = useAuth();
  const authenticated = status === 'authenticated' && user !== null;
  const selectedWorkspace = workspaces.find((workspace) => workspace.workspace_id === selectedWorkspaceId) ?? workspaces[0] ?? null;
  const displayName = authenticated ? user.display_name : 'Tu perfil en Mágina';
  const initial = displayName.trim().slice(0, 1).toUpperCase() || 'M';
  const locationLabel = authenticated
    ? profile?.municipality ?? preferences?.preferred_municipality ?? 'Municipio por configurar'
    : 'Accede para personalizarlo';
  const publicRoleLabel = profile?.public_role ? roleLabels[profile.public_role] : null;
  const completedProfileFields = authenticated
    ? [displayName, profile?.municipality, profile?.bio, profile?.public_role].filter(Boolean).length
    : 0;
  const profileCompletion = authenticated ? Math.round((completedProfileFields / 4) * 100) : 0;
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
      <div className="profile-name-row"><div><h1>{displayName}</h1><p><MapPinIcon/> {locationLabel}</p></div>{authenticated ? <span className="subtle">Edición de perfil pendiente de esta Beta</span> : null}</div>
      <div className="role-row">
        {authenticated && selectedWorkspace ? <span><SproutIcon/> {selectedWorkspace.workspace_name}</span> : <span><SproutIcon/> Mágina Olivo</span>}
        {authenticated ? <span>▣ {publicRoleLabel ?? selectedWorkspace?.role ?? 'Perfil privado'}</span> : <span>▣ Guía pública</span>}
      </div>
    </section>

    {status === 'loading' ? <section className="section card profile-progress"><div><h2>Comprobando tu sesión…</h2><p>La guía pública sigue disponible mientras cargamos tu cuenta.</p></div></section> : null}

    {status === 'anonymous' ? <section className="section card profile-progress"><div><h2>Entra para activar tu espacio personal</h2><p>Mi Campo, tus documentos, alertas, favoritos y publicaciones quedan asociados a tu cuenta. La guía de Mágina sigue siendo pública sin iniciar sesión.</p><GoogleSignInButton/></div><ArrowIcon/></section> : null}

    {authenticated ? <section className="section card profile-progress"><div className="progress-ring"><span>{profileCompletion}%</span></div><div><h2>Perfil {profileCompletion} % completado</h2><p>Nombre, municipio, presentación y actividad pública se gestionan sin exponer datos privados de tus fincas.</p></div></section> : null}

    <section className="section"><InstallAppCard/></section>

    <div className="profile-grid">
      <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Mi cuenta</h3><span>{authenticated ? 'Estado' : 'Acceder'}</span></div>{accountRows.map(([a,b])=><div className="profile-line" key={a}><span>{a}</span><span>{b}</span></div>)}</section>

      <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Preferencias</h3><span>{authenticated ? 'Configuradas' : 'Con cuenta'}</span></div><div className="profile-line"><span>Tema</span><span>{authenticated ? themeLabels[preferences?.theme ?? 'system'] : 'Automático'}</span></div><div className="profile-line"><span>Unidades</span><span>Métricas · ha · kg</span></div><div className="profile-line"><span>Municipio preferido</span><span>{authenticated ? preferences?.preferred_municipality ?? 'Por configurar' : 'Disponible al entrar'}</span></div><div className="profile-line"><span>Avisos del tiempo</span><span>{authenticated ? preferences?.weather_alerts === false ? 'Desactivados' : 'Activados' : 'Disponible al entrar'}</span></div></section>

      <AgronomyAlertSettings />
      <HomePrioritySettings />
      <FinancialNotificationSettings />
      <CommercialNotificationSettings />
      <ProfessionalBusinessProfileSettings />

      {authenticated && workspaces.length > 1 ? <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Espacio activo</h3><span>Cambiar</span></div>{workspaces.map((workspace)=><button type="button" className="profile-line" key={workspace.workspace_id} onClick={()=>selectWorkspace(workspace.workspace_id)}><span>{workspace.workspace_name}</span><span>{workspace.workspace_id === selectedWorkspaceId ? 'Activo ✓' : `${workspace.role} ›`}</span></button>)}</section> : null}

      <section className="card profile-card premium-profile-card olive-card"><div className="profile-card-head"><h3>Mi Olivo</h3><span>Después de Beta</span></div><div className="olivo-level"><span className="olivo-mark"><SproutIcon/></span><div><strong>{authenticated ? 'Tu olivo personal' : 'Entra para crear tu olivo'}</strong><small>Los puntos y recompensas se activarán en una fase posterior; todavía no forman parte del núcleo Beta.</small><div className="level-bar"><i/></div></div></div></section>
      <section className="card profile-card premium-profile-card professional-card"><div className="profile-card-head"><h3>Perfil profesional</h3><span>{authenticated ? 'Datos fiscales arriba' : 'Con cuenta'}</span></div><p>Trabaja para terceros sin mezclar tu información personal ni tus fincas privadas.</p><div className="profile-line"><span>Servicios públicos/directorio</span><span>Después de Beta</span></div><div className="profile-line"><span>Visibilidad actual</span><span>{profile?.visibility === 'public' ? 'Pública' : authenticated ? 'Privada' : 'Accede'}</span></div></section>
    </div>

    <section className="section"><PermissionCenter/></section>

    {authenticated ? <section className="profile-footer-actions"><span className="subtle">Exportar mis datos: pendiente de implementar antes de lanzamiento público.</span><button className="danger-action" onClick={()=>void logout()}>Cerrar sesión</button></section> : null}
  </div><BottomNav active="/perfil"/></main>;
}
