import Link from 'next/link';
import { CompassIcon, HomeIcon, MoreIcon, SproutIcon } from '@/components/icons';

const items = [
  ['/', HomeIcon, 'Inicio'],
  ['/mi-campo', SproutIcon, 'Mi Campo'],
  ['/explorar', CompassIcon, 'Explorar'],
  ['/perfil', MoreIcon, 'Más'],
] as const;

export function BottomNav({ active }: { active: string }) {
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {items.map(([href, Icon, label]) => (
        <Link key={href} href={href} className={active === href ? 'active' : undefined}>
          <span className="nav-icon" aria-hidden><Icon /></span>
          <span className="nav-label">{label}</span>
        </Link>
      ))}
    </nav>
  );
}
