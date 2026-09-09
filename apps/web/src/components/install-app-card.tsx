'use client';

import { useEffect, useState } from 'react';
import { ArrowIcon } from '@/components/icons';

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
};

export function InstallAppCard() {
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(false);
  const [message, setMessage] = useState('Acceso rápido desde tu móvil y una experiencia más cómoda en el campo.');

  useEffect(() => {
    const standalone = window.matchMedia('(display-mode: standalone)').matches;
    setInstalled(standalone);

    const onBeforeInstall = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as BeforeInstallPromptEvent);
    };
    const onInstalled = () => {
      setInstalled(true);
      setInstallEvent(null);
    };

    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  async function install() {
    if (!installEvent) {
      setMessage('Si tu navegador no muestra el botón automático, usa “Añadir a pantalla de inicio” desde su menú.');
      return;
    }
    await installEvent.prompt();
    const choice = await installEvent.userChoice;
    if (choice.outcome === 'accepted') {
      setInstalled(true);
      setInstallEvent(null);
    }
  }

  if (installed) {
    return (
      <section className="card install-card installed-card">
        <span className="install-symbol">✓</span>
        <div><strong>Mágina está instalada</strong><small>La estás usando como aplicación.</small></div>
      </section>
    );
  }

  return (
    <section className="card install-card">
      <span className="install-symbol">⇩</span>
      <div className="install-copy"><span className="eyebrow dark">PWA</span><h3>Instalar Mágina Olivo</h3><p>{message}</p></div>
      <button type="button" onClick={install} className="install-action">Instalar <ArrowIcon /></button>
    </section>
  );
}
