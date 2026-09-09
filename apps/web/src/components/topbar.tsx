import Link from 'next/link';
import { Brand } from '@/components/brand';
import { BellIcon } from '@/components/icons';

export function Topbar() {
  return (
    <header className="topbar">
      <Brand />
      <Link href="/perfil" className="icon-button notification-button" aria-label="Notificaciones y perfil">
        <BellIcon />
        <span className="notification-dot" />
      </Link>
    </header>
  );
}
