'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Brand } from '@/components/brand';
import { BellIcon } from '@/components/icons';

export function Topbar() {
  const pathname = usePathname();
  const items = [
    ['/', 'Inicio'],
    ['/explorar', 'Explorar'],
    ['/mi-campo', 'Mi Campo'],
    ['/radar', 'Radar'],
    ['/perfil', 'Perfil'],
  ] as const;

  return (
    <header className="topbar">
      <Brand />
      <nav className="desktop-primary-nav" aria-label="Navegación principal de escritorio">
        {items.map(([href, label]) => {
          const current = href === '/' ? pathname === href : pathname.startsWith(href);
          return <Link key={href} href={href} className={current ? 'active' : undefined} aria-current={current ? 'page' : undefined}>{label}</Link>;
        })}
      </nav>
      <div className="topbar-actions">
        <Link href="/mi-campo/avisos" className="icon-button notification-button" aria-label="Abrir centro de avisos">
          <BellIcon />
        </Link>
        <Link href="/perfil" className="desktop-profile-link" aria-label="Abrir perfil">MO</Link>
      </div>
    </header>
  );
}
