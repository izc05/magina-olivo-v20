'use client';

import Script from 'next/script';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from './auth-provider';

type GoogleAccountsId = {
  initialize: (options: { client_id: string; callback: (response: { credential?: string }) => void }) => void;
  renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
};

declare global {
  interface Window {
    google?: { accounts?: { id?: GoogleAccountsId } };
  }
}

export function GoogleSignInButton() {
  const { apiConfigured, signInWithGoogleCredential } = useAuth();
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const initialize = useCallback(() => {
    const googleId = window.google?.accounts?.id;
    const container = containerRef.current;
    if (!googleId || !container || !clientId || !apiConfigured) return;

    container.replaceChildren();
    googleId.initialize({
      client_id: clientId,
      callback: (response) => {
        if (!response.credential) {
          setError('Google no devolvió una credencial válida.');
          return;
        }
        setSubmitting(true);
        setError(null);
        void signInWithGoogleCredential(response.credential)
          .catch(() => setError('No se ha podido iniciar sesión. Inténtalo de nuevo.'))
          .finally(() => setSubmitting(false));
      },
    });
    googleId.renderButton(container, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      shape: 'pill',
      text: 'continue_with',
      locale: 'es',
      width: 300,
    });
  }, [apiConfigured, clientId, signInWithGoogleCredential]);

  useEffect(() => {
    if (window.google?.accounts?.id) initialize();
  }, [initialize]);

  if (!apiConfigured || !clientId) {
    return <p className="auth-setup-note">El acceso personal se activará al conectar el servidor y Google Identity.</p>;
  }

  return (
    <div className="google-signin-wrap" aria-busy={submitting}>
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onLoad={initialize} />
      <div ref={containerRef} />
      {submitting ? <small>Entrando en Mágina…</small> : null}
      {error ? <small role="alert">{error}</small> : null}
    </div>
  );
}
