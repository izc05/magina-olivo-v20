import type { ReactNode } from 'react';
import Link from 'next/link';
import { Topbar } from './topbar';
import { BottomNav } from './bottom-nav';
import styles from './territory-shell.module.css';

export function TerritoryShell({ children }: { children: ReactNode }) {
  return <div className="app-shell">
    <Topbar />
    <nav className={styles.navigation} aria-label="Explorar Sierra Mágina">
      <Link href="/explorar">← Explorar</Link>
      <Link href="/rutas">Rutas y aventuras</Link>
      <Link href="/explorar/empresas">Empresas</Link>
      <Link href="/experiencias">Experiencias</Link>
      <Link href="/ayuntamientos">Ayuntamientos</Link>
      <Link href="/magina-pass">Mágina Pass</Link>
    </nav>
    <div className={styles.content}>{children}</div>
    <BottomNav active="/explorar" />
  </div>;
}
