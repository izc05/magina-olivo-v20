'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';

type PushConfig = {
  configured: boolean;
  public_key: string | null;
  semantics: 'explicit_opt_in_web_push';
};

type PushStatus = {
  configured: boolean;
  active_subscriptions: number;
};

type LocalState = 'checking' | 'unsupported' | 'inactive' | 'active' | 'denied' | 'error';

function base64UrlToUint8Array(value: string) {
  const padding = '='.repeat((4 - value.length % 4) % 4);
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

function serviceWorkerUrl() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
  return `${basePath}/sw.js` || '/sw.js';
}

function serviceWorkerScope() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
  return `${basePath}/` || '/';
}

async function registration() {
  const existing = await navigator.serviceWorker.getRegistration(serviceWorkerScope());
  return existing ?? navigator.serviceWorker.register(serviceWorkerUrl(), { scope: serviceWorkerScope() });
}

export function PushNotificationSettings({ fieldId }: { fieldId: string }) {
  const { apiConfigured, status: authStatus, selectedWorkspaceId } = useAuth();
  const [state, setState] = useState<LocalState>('checking');
  const [serverCount, setServerCount] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const supported = typeof window !== 'undefined'
    && 'serviceWorker' in navigator
    && 'PushManager' in window
    && 'Notification' in window;

  useEffect(() => {
    if (!apiConfigured || authStatus !== 'authenticated') {
      setState('inactive');
      return;
    }
    if (!supported) {
      setState('unsupported');
      return;
    }

    let cancelled = false;
    void (async () => {
      try {
        const reg = await registration();
        const browserSubscription = await reg.pushManager.getSubscription();
        const status = await apiFetch<PushStatus>('/api/v1/push/status');
        if (cancelled) return;
        setServerCount(status.active_subscriptions);
        if (Notification.permission === 'denied') setState('denied');
        else setState(browserSubscription ? 'active' : 'inactive');
      } catch {
        if (!cancelled) setState('error');
      }
    })();

    return () => { cancelled = true; };
  }, [apiConfigured, authStatus, supported]);

  async function enable() {
    if (!supported || busy) return;
    setBusy(true);
    setMessage('');
    try {
      const config = await apiFetch<PushConfig>('/api/v1/push/config');
      if (!config.configured || !config.public_key) {
        setState('error');
        setMessage('El servidor todavía no tiene configuradas las claves de notificación.');
        return;
      }

      const permission = Notification.permission === 'granted'
        ? 'granted'
        : await Notification.requestPermission();
      if (permission !== 'granted') {
        setState(permission === 'denied' ? 'denied' : 'inactive');
        setMessage('No se activaron los avisos. Puedes intentarlo de nuevo cuando quieras.');
        return;
      }

      const reg = await registration();
      const current = await reg.pushManager.getSubscription();
      const subscription = current ?? await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: base64UrlToUint8Array(config.public_key),
      });
      const json = subscription.toJSON();
      if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) throw new Error('Incomplete PushSubscription');

      await apiFetch('/api/v1/push/subscriptions', {
        method: 'POST',
        body: JSON.stringify({
          endpoint: json.endpoint,
          expiration_time: json.expirationTime ?? null,
          keys: { p256dh: json.keys.p256dh, auth: json.keys.auth },
        }),
      });
      setState('active');
      setServerCount((currentCount) => Math.max(1, currentCount));
      setMessage('Avisos activados en este dispositivo.');
    } catch {
      setState('error');
      setMessage('No se han podido activar los avisos en este dispositivo.');
    } finally {
      setBusy(false);
    }
  }

  async function disable() {
    if (!supported || busy) return;
    setBusy(true);
    setMessage('');
    try {
      const reg = await registration();
      const subscription = await reg.pushManager.getSubscription();
      if (subscription) {
        await apiFetch('/api/v1/push/subscriptions', {
          method: 'DELETE',
          body: JSON.stringify({ endpoint: subscription.endpoint }),
        });
        await subscription.unsubscribe();
      }
      setState('inactive');
      setServerCount((currentCount) => Math.max(0, currentCount - 1));
      setMessage('Avisos desactivados en este dispositivo.');
    } catch {
      setState('error');
      setMessage('No se han podido desactivar los avisos.');
    } finally {
      setBusy(false);
    }
  }

  async function sendTest() {
    if (!selectedWorkspaceId || busy) return;
    setBusy(true);
    setMessage('');
    try {
      const result = await apiFetch<{ dispatch_queued: boolean }>('/api/v1/push/test', {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({ field_id: fieldId }),
      });
      setMessage(result.dispatch_queued
        ? 'Prueba enviada a la cola. Debería aparecer como notificación del sistema.'
        : 'Prueba guardada. El dispatcher periódico la recogerá cuando esté disponible.');
    } catch {
      setMessage('No se ha podido preparar la notificación de prueba.');
    } finally {
      setBusy(false);
    }
  }

  if (!apiConfigured || authStatus !== 'authenticated') return null;

  return <div className="push-settings-block">
    <div className="radar-alert-help">
      <b>Avisos en el móvil:</b> el permiso se solicita solo al pulsar Activar. La suscripción queda vinculada a tu sesión actual; cerrar sesión impide nuevos envíos a esa vinculación.
    </div>
    <div className="radar-alert-actions">
      {state === 'active'
        ? <button type="button" className="radar-alert-save" onClick={() => void disable()} disabled={busy}>{busy ? 'Procesando…' : 'Desactivar en este dispositivo'}</button>
        : <button type="button" className="radar-alert-save" onClick={() => void enable()} disabled={busy || state === 'unsupported' || state === 'denied'}>{busy ? 'Procesando…' : 'Activar avisos en este dispositivo'}</button>}
      {state === 'active' ? <button type="button" className="outline-action" onClick={() => void sendTest()} disabled={busy}>Enviar prueba</button> : null}
    </div>
    <p className="radar-alert-status">
      {state === 'unsupported' ? 'Este navegador no admite Web Push.' : null}
      {state === 'denied' ? 'El navegador tiene bloqueadas las notificaciones para Mágina Olivo.' : null}
      {state === 'active' ? `Dispositivo activo · ${serverCount} suscripción${serverCount === 1 ? '' : 'es'} activa${serverCount === 1 ? '' : 's'} en tu cuenta.` : null}
      {state === 'error' && !message ? 'No se ha podido comprobar el estado de notificaciones.' : null}
      {message ? ` ${message}` : null}
    </p>
  </div>;
}
