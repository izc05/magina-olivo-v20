'use client';

import { municipalityAdminHref, publicMunicipalityHref } from '@/lib/municipality-admin-context';
import styles from './municipality-admin-nav.module.css';

type Section = 'ficha' | 'contenido' | 'portada' | 'preview' | 'patrimonio' | 'actualidad' | 'cobertura' | 'huecos' | 'historial';

// Final municipal QA validates this shared workspace navigation end to end.
const SECTIONS: Array<{ key: Section; label: string; path: string }> = [
  { key: 'ficha', label: 'Ficha', path: '/admin/ayuntamientos' },
  { key: 'contenido', label: 'Contenido', path: '/admin/ayuntamientos/contenido' },
  { key: 'portada', label: 'Portada', path: '/admin/ayuntamientos/editorial' },
  { key: 'preview', label: 'Preview', path: '/admin/ayuntamientos/preview' },
  { key: 'patrimonio', label: 'Patrimonio', path: '/admin/ayuntamientos/patrimonio' },
  { key: 'actualidad', label: 'Actualidad', path: '/admin/ayuntamientos/actualidad' },
  { key: 'cobertura', label: 'Cobertura', path: '/admin/ayuntamientos/cobertura' },
  { key: 'huecos', label: 'Huecos', path: '/admin/ayuntamientos/huecos' },
  { key: 'historial', label: 'Historial', path: '/admin/ayuntamientos/historial' },
];

export function MunicipalityAdminNav({ slug, name, active }: { slug?: string | null; name?: string | null; active: Section }) {
  return <section className={styles.workspace} aria-label="Navegación del municipio">
    <div className={styles.identity}>
      <span>MUNICIPIO ACTUAL</span>
      <strong>{name || 'Selecciona un municipio'}</strong>
    </div>
    <nav className={styles.nav}>
      {SECTIONS.map((section) => <a key={section.key} href={municipalityAdminHref(section.path, slug)} aria-current={active === section.key ? 'page' : undefined} data-active={active === section.key ? 'true' : 'false'}>{section.label}</a>)}
      <a className={styles.publicLink} href={publicMunicipalityHref(slug)} target="_blank" rel="noreferrer">Ver público ↗</a>
    </nav>
  </section>;
}
