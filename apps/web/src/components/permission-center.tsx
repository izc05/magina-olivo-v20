'use client';

import { useState } from 'react';

type Status = 'Sin solicitar' | 'Permitido' | 'Denegado' | 'No disponible';

export function PermissionCenter() {
  const [location, setLocation] = useState<Status>('Sin solicitar');
  const [notifications, setNotifications] = useState<Status>(() => {
    if (typeof window === 'undefined' || !('Notification' in window)) return 'Sin solicitar';
    return Notification.permission === 'granted' ? 'Permitido' : Notification.permission === 'denied' ? 'Denegado' : 'Sin solicitar';
  });
  const [storage, setStorage] = useState<Status>('Sin solicitar');

  function requestLocation() {
    if (!navigator.geolocation) {
      setLocation('No disponible');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      () => setLocation('Permitido'),
      (error) => setLocation(error.code === error.PERMISSION_DENIED ? 'Denegado' : 'No disponible'),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  async function requestNotifications() {
    if (!('Notification' in window)) {
      setNotifications('No disponible');
      return;
    }
    const result = await Notification.requestPermission();
    setNotifications(result === 'granted' ? 'Permitido' : result === 'denied' ? 'Denegado' : 'Sin solicitar');
  }

  async function requestPersistentStorage() {
    if (!navigator.storage?.persist) {
      setStorage('No disponible');
      return;
    }
    const persisted = await navigator.storage.persist();
    setStorage(persisted ? 'Permitido' : 'Denegado');
  }

  return (
    <section className="card permission-center">
      <div className="permission-head"><div><span className="eyebrow dark">PRIVACIDAD</span><h3>Permisos del móvil</h3><p>Solo los pedimos cuando decides utilizar cada función.</p></div></div>
      <div className="permission-row"><div><strong>📍 Ubicación</strong><small>Municipio, tiempo y lugares cercanos.</small></div><button type="button" onClick={requestLocation}>{location}</button></div>
      <div className="permission-row"><div><strong>🔔 Notificaciones</strong><small>Lluvia, riegos y tareas que tú actives.</small></div><button type="button" onClick={requestNotifications}>{notifications}</button></div>
      <div className="permission-row"><div><strong>📷 Cámara</strong><small>Se solicitará al fotografiar un albarán o documento.</small></div><span className="context-pill">Contextual</span></div>
      <div className="permission-row"><div><strong>⇩ Modo sin conexión</strong><small>Solicita almacenamiento persistente cuando lo actives.</small></div><button type="button" onClick={requestPersistentStorage}>{storage}</button></div>
    </section>
  );
}
