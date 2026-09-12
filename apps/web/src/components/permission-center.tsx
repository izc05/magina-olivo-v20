'use client';

import { useEffect, useState } from 'react';

type Status = 'Comprobando…' | 'Sin solicitar' | 'Permitido' | 'Denegado' | 'No disponible';

function permissionLabel(status: Status) {
  if (status === 'Permitido') return 'Permitido ✓';
  if (status === 'Denegado') return 'Denegado';
  return status;
}

export function PermissionCenter() {
  const [location, setLocation] = useState<Status>('Comprobando…');
  const [notifications, setNotifications] = useState<Status>('Comprobando…');
  const [storage, setStorage] = useState<Status>('Comprobando…');
  const [busy, setBusy] = useState<'location' | 'notifications' | 'storage' | null>(null);

  useEffect(() => {
    let disposed = false;
    let removeLocationListener: (() => void) | null = null;

    async function hydratePermissions() {
      if (!navigator.geolocation) {
        if (!disposed) setLocation('No disponible');
      } else if (navigator.permissions?.query) {
        try {
          const permission = await navigator.permissions.query({ name: 'geolocation' });
          const syncLocation = () => {
            if (!disposed) setLocation(permission.state === 'granted' ? 'Permitido' : permission.state === 'denied' ? 'Denegado' : 'Sin solicitar');
          };
          syncLocation();
          permission.addEventListener?.('change', syncLocation);
          removeLocationListener = () => permission.removeEventListener?.('change', syncLocation);
        } catch {
          if (!disposed) setLocation('Sin solicitar');
        }
      } else if (!disposed) {
        setLocation('Sin solicitar');
      }

      if (!('Notification' in window)) {
        if (!disposed) setNotifications('No disponible');
      } else if (!disposed) {
        setNotifications(Notification.permission === 'granted' ? 'Permitido' : Notification.permission === 'denied' ? 'Denegado' : 'Sin solicitar');
      }

      if (!navigator.storage?.persisted) {
        if (!disposed) setStorage('No disponible');
      } else {
        try {
          const persisted = await navigator.storage.persisted();
          if (!disposed) setStorage(persisted ? 'Permitido' : 'Sin solicitar');
        } catch {
          if (!disposed) setStorage('No disponible');
        }
      }
    }

    void hydratePermissions();
    return () => {
      disposed = true;
      removeLocationListener?.();
    };
  }, []);

  function requestLocation() {
    if (!navigator.geolocation) {
      setLocation('No disponible');
      return;
    }
    setBusy('location');
    navigator.geolocation.getCurrentPosition(
      () => { setLocation('Permitido'); setBusy(null); },
      (error) => { setLocation(error.code === error.PERMISSION_DENIED ? 'Denegado' : 'No disponible'); setBusy(null); },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  async function requestNotifications() {
    if (!('Notification' in window)) {
      setNotifications('No disponible');
      return;
    }
    setBusy('notifications');
    try {
      const result = await Notification.requestPermission();
      setNotifications(result === 'granted' ? 'Permitido' : result === 'denied' ? 'Denegado' : 'Sin solicitar');
    } finally {
      setBusy(null);
    }
  }

  async function requestPersistentStorage() {
    if (!navigator.storage?.persist) {
      setStorage('No disponible');
      return;
    }
    setBusy('storage');
    try {
      const persisted = await navigator.storage.persist();
      setStorage(persisted ? 'Permitido' : 'Denegado');
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="card permission-center">
      <div className="permission-head"><div><span className="eyebrow dark">PRIVACIDAD</span><h3>Permisos del dispositivo</h3><p>Ves el estado real de este navegador. Mágina solo los solicita cuando decides activar cada función.</p></div></div>
      <div className="permission-row"><div><strong>📍 Ubicación</strong><small>Municipio, tiempo y lugares cercanos. No se activa en segundo plano desde esta pantalla.</small></div><button type="button" onClick={requestLocation} disabled={busy === 'location' || location === 'Permitido' || location === 'No disponible'} aria-label="Gestionar permiso de ubicación">{busy === 'location' ? 'Solicitando…' : permissionLabel(location)}</button></div>
      <div className="permission-row"><div><strong>🔔 Notificaciones</strong><small>Lluvia, riegos y tareas que tú actives. Si están denegadas, debes reactivarlas desde el navegador.</small></div><button type="button" onClick={requestNotifications} disabled={busy === 'notifications' || notifications === 'Permitido' || notifications === 'Denegado' || notifications === 'No disponible'} aria-label="Gestionar permiso de notificaciones">{busy === 'notifications' ? 'Solicitando…' : permissionLabel(notifications)}</button></div>
      <div className="permission-row"><div><strong>📷 Cámara</strong><small>Se solicita únicamente al fotografiar un albarán o documento.</small></div><span className="context-pill">Contextual</span></div>
      <div className="permission-row"><div><strong>⇩ Modo sin conexión</strong><small>El almacenamiento persistente ayuda a conservar datos locales cuando el sistema lo permite.</small></div><button type="button" onClick={requestPersistentStorage} disabled={busy === 'storage' || storage === 'Permitido' || storage === 'No disponible'} aria-label="Gestionar almacenamiento persistente">{busy === 'storage' ? 'Solicitando…' : permissionLabel(storage)}</button></div>
    </section>
  );
}
