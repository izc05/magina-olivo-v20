import Link from 'next/link';
import { Brand } from '@/components/brand';
import { BellIcon } from '@/components/icons';

export function Topbar() {
  return (
    <header className="topbar">
      <Brand />
      <Link href="/mi-campo/avisos" className="icon-button notification-button" aria-label="Abrir centro de avisos">
        <BellIcon />
      </Link>
    </header>
  );
}
