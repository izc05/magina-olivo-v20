import Link from 'next/link';
import styles from './adventure-experience-nav.module.css';

export function AdventureExperienceNav() {
  return <nav className={styles.nav} aria-label="Áreas de Mágina Aventura">
    <a href="#magina-aventura-title"><span>⌂</span><strong>Inicio</strong></a>
    <a href="#aventuras"><span>⌁</span><strong>Rutas</strong></a>
    <Link href="/aventura/en-curso"><span>◉</span><strong>En curso</strong></Link>
    <a href="#mi-aventura"><span>△</span><strong>Mi Aventura</strong></a>
    <a href="#album"><span>✦</span><strong>Colecciones</strong></a>
    <a href="#comunidad"><span>◎</span><strong>Comunidad</strong></a>
  </nav>;
}
