'use client';

import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { Topbar } from '@/components/topbar';
import { SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';

export default function MiCampoLayout({ children }: { children: React.ReactNode }) {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();

  // GitHub Pages / visual prototype has no private API. Keep the existing
  // navigable demo available for design review only.
  if (!apiConfigured) return children;

  if (status === 'authenticated' && selectedWorkspaceId) return children;

  return (
    <main className="app-shell">
      <Topbar />
      <div className="page profile-page">
        <section className="profile-hero premium-profile-hero">
          <div className="avatar premium-avatar"><SproutIcon /></div>
          <div className="profile-name-row"><div><h1>Mi Campo es privado</h1><p>Tus fincas, kilos, costes y documentos solo se cargan después de identificarte.</p></div></div>
        </section>

        <section className="section card profile-progress">
          <div>
            <h2>{status === 'loading' ? 'Comprobando tu sesión…' : 'Entra para abrir Mi Campo'}</h2>
            <p>La guía, noticias, cooperativas, rutas, recetas y negocios de Mágina siguen disponibles sin cuenta.</p>
            {status === 'anonymous' ? <GoogleSignInButton /> : null}
          </div>
        </section>

        <section className="profile-footer-actions">
          <Link className="secondary-action" href="/explorar">Seguir explorando Mágina</Link>
          <Link className="secondary-action" href="/perfil">Ver mi perfil</Link>
        </section>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
