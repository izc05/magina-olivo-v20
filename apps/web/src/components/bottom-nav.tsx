import Link from 'next/link';
import { CompassIcon, HomeIcon, MoreIcon, SproutIcon } from '@/components/icons';
import styles from './bottom-nav.module.css';

const items = [
  ['/', HomeIcon, 'Inicio'],
  ['/mi-campo', SproutIcon, 'Mi Campo'],
  ['/explorar', CompassIcon, 'Explorar'],
  ['/perfil', MoreIcon, 'Más'],
] as const;

export function BottomNav({ active }: { active: string }) {
  return (
    <nav className={styles.root} aria-label="Navegación principal">
      {items.map(([href, Icon, label]) => {
        const current = active === href;
        return (
          <Link
            key={href}
            href={href}
            className={`${styles.link} ${current ? styles.active : ''}`.trim()}
            aria-current={current ? 'page' : undefined}
          >
            <span className={styles.icon} aria-hidden><Icon /></span>
            <span className={styles.label}>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
