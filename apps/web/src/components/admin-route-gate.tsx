'use client';

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiRequestError } from '../lib/api-client';
import { adminApi } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

type GateState = 'checking' | 'authorized' | 'denied' | 'expired' | 'error';

export function AdminRouteGate({ children }: Readonly<{ children: ReactNode }>) {
  const auth = useAuth();
  const [state, setState] = useState<GateState>('checking');
  const validatingRef = useRef(false);

  const validate = useCallback(async () => {
    if (validatingRef.current) return;
    validatingRef.current = true;
    setState('checking');
    try {
      await adminApi.session();
      setState('authorized');
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 401) {
        await auth.refreshSession();
        setState('expired');
        return;
      }
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setState('denied');
        return;
      }
      setState('error');
    } finally {
      validatingRef.current = false;
    }
  }, [auth.refreshSession]);

  useEffect(() => {
    if (auth.status === 'authenticated') {
      void validate();
      return;
    }
    setState('checking');
  }, [auth.status, validate]);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;

    const revalidateOnFocus = () => void validate();
    const revalidateOnVisibility = () => {
      if (document.visibilityState === 'visible') void validate();
    };

    window.addEventListener('focus', revalidateOnFocus);
    document.addEventListener('visibilitychange', revalidateOnVisibility);
    return () => {
      window.removeEventListener('focus', revalidateOnFocus);
      document.removeEventListener('visibilitychange', revalidateOnVisibility);
    };
  }, [auth.status, validate]);

  if (auth.status === 'loading' || (auth.status === 'authenticated' && state === 'checking')) {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card"><strong>Comprobando acceso administrativo…</strong></div>
      </main>
    );
  }

  if (auth.status === 'anonymous') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Mágina Olivo · Administración</span>
          <h1>Acceso corporativo</h1>
          <p>Entra con una cuenta corporativa autorizada. El servidor valida los permisos antes de habilitar las operaciones administrativas.</p>
          <GoogleSignInButton />
          <small>Los datos y acciones sensibles siguen protegidos por autorización de servidor; este gate unifica también la experiencia de acceso.</small>
        </div>
      </main>
    );
  }

  if (state === 'expired') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Sesión administrativa</span>
          <h1>La sesión ha caducado</h1>
          <p>Vuelve a iniciar sesión para continuar. Ninguna superficie administrativa se habilita hasta que el servidor confirme de nuevo tu identidad y permisos.</p>
          <button className="admin-button secondary" onClick={() => void auth.logout()}>Volver a iniciar sesión</button>
        </div>
      </main>
    );
  }

  if (state === 'denied') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Acceso restringido</span>
          <h1>Esta cuenta no administra Mágina Olivo</h1>
          <p>{auth.user?.primary_email ?? 'Tu cuenta'} tiene sesión válida, pero no dispone de un permiso de plataforma activo.</p>
          <button className="admin-button secondary" onClick={() => void auth.logout()}>Salir y usar otra cuenta</button>
        </div>
      </main>
    );
  }

  if (state === 'error') {
    return (
      <main className="admin-gate">
        <div className="admin-gate-card">
          <span className="admin-eyebrow">Administración</span>
          <h1>No se ha podido validar el acceso</h1>
          <p>La sesión no se da por autorizada mientras el servidor no confirme los permisos.</p>
          <button className="admin-button secondary" onClick={() => void validate()}>Reintentar</button>
        </div>
      </main>
    );
  }

  if (state !== 'authorized') return null;
  return children;
}
