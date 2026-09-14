'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { Topbar } from '@/components/topbar';
import { useAuth } from '@/components/auth-provider';
import { savePersonalPreferences } from '@/lib/profile-settings-source';
import { findMaginaTown, MAGINA_TOWNS, townModuleHref, type MaginaTown } from '@/lib/towns';
import styles from './my-towns-page.module.css';

export function MyTownsPage() {
  const { status, profile, preferences, refreshMe } = useAuth();
  const [savingSlug, setSavingSlug] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const preferredTown = useMemo(
    () => findMaginaTown(preferences?.preferred_municipality ?? profile?.municipality),
    [preferences?.preferred_municipality, profile?.municipality],
  );

  async function setPrimaryTown(town: MaginaTown) {
    if (status !== 'authenticated') return;
    setSavingSlug(town.slug);
    setError(null);
    try {
      await savePersonalPreferences({
        theme: preferences?.theme ?? 'system',
        unit_system: 'metric',
        preferred_municipality: town.name,
        locale: preferences?.locale ?? 'es-ES',
        community_notifications: preferences?.community_notifications ?? true,
        weather_alerts: preferences?.weather_alerts ?? true,
      });
      await refreshMe();
    } catch (cause) {
      console.error('Unable to save preferred town', cause);
      setError('No se ha podido guardar tu pueblo principal. Inténtalo de nuevo.');
    } finally {
      setSavingSlug(null);
    }
  }

  return <main className="app-shell">
    <Topbar />
    <div className={styles.page}>
      <header className={styles.hero}>
        <span>MI MÁGINA</span>
        <h1>Mis pueblos</h1>
        <p>Elige tu pueblo principal para personalizar el contenido local. La siguiente fase permitirá seguir varios pueblos con la misma cuenta.</p>
      </header>

      {status === 'loading' ? <section className={styles.stateCard}><strong>Cargando tus preferencias…</strong></section> : null}

      {status === 'anonymous' ? <section className={styles.stateCard}>
        <strong>Accede para guardar tus pueblos</strong>
        <p>La guía pública sigue disponible sin cuenta, pero tus preferencias necesitan estar asociadas a tu perfil.</p>
        <GoogleSignInButton />
      </section> : null}

      {status === 'authenticated' ? <>
        <section className={styles.primaryCard}>
          <div>
            <span>PUEBLO PRINCIPAL</span>
            <h2>{preferredTown?.name ?? 'Aún no has elegido uno'}</h2>
            <p>{preferredTown ? 'Se usará como referencia para tiempo, avisos y contenido local.' : 'Selecciona uno de los 16 municipios de Sierra Mágina.'}</p>
          </div>
          {preferredTown ? <div className={styles.quickLinks}>
            <Link href={townModuleHref('/eventos', preferredTown)}>Eventos</Link>
            <Link href={townModuleHref('/noticias', preferredTown)}>Noticias</Link>
            <Link href={townModuleHref('/empresas', preferredTown)}>Empresas</Link>
            <Link href={townModuleHref('/explorar', preferredTown)}>Explorar</Link>
          </div> : null}
        </section>

        {error ? <p className={styles.error} role="alert">{error}</p> : null}

        <section className={styles.catalogue} aria-labelledby="town-selector-title">
          <div className={styles.heading}>
            <span>SIERRA MÁGINA · {MAGINA_TOWNS.length} MUNICIPIOS</span>
            <h2 id="town-selector-title">Elige tu pueblo</h2>
            <p>Puedes cambiarlo cuando quieras. Elegir un municipio no modifica tu dirección ni los datos privados de Mi Campo.</p>
          </div>
          <div className={styles.grid}>
            {MAGINA_TOWNS.map((town) => {
              const selected = preferredTown?.slug === town.slug;
              const saving = savingSlug === town.slug;
              return <article className={`${styles.townCard} ${selected ? styles.selected : ''}`} key={town.slug}>
                <div className={styles.townMeta}>
                  <span>{town.inNaturalPark ? 'Parque Natural' : 'Comarca Sierra Mágina'}</span>
                  {selected ? <span>Principal ✓</span> : null}
                </div>
                <h3>{town.name}</h3>
                {town.aliases.length ? <p>{town.aliases.join(' · ')}</p> : <p>Sierra Mágina · Jaén</p>}
                <div className={styles.actions}>
                  <button type="button" disabled={selected || saving || savingSlug !== null} onClick={() => void setPrimaryTown(town)}>
                    {selected ? 'Pueblo principal' : saving ? 'Guardando…' : 'Elegir como principal'}
                  </button>
                  <Link href={townModuleHref('/explorar', town)}>Descubrir →</Link>
                </div>
              </article>;
            })}
          </div>
        </section>
      </> : null}
    </div>
    <BottomNav active="/perfil" />
  </main>;
}
