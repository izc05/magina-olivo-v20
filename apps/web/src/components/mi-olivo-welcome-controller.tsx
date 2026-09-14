'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-welcome-controller.module.css';

type MiOlivoSnapshot = {
  enabled: boolean;
  balance: number;
};

type FieldsPayload = {
  fields: Array<{ id: string }>;
};

type CampaignsPayload = {
  campaigns: Array<{ id: string }>;
};

type WelcomeSnapshot = {
  balance: number;
  enabled: boolean;
  fieldCount: number;
  campaignCount: number;
};

export function MiOlivoWelcomeController() {
  const { status, selectedWorkspaceId, apiConfigured, profile } = useAuth();
  const [snapshot, setSnapshot] = useState<WelcomeSnapshot | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
        setSnapshot(null);
        return;
      }

      try {
        const [olivo, fields, campaigns] = await Promise.all([
          apiFetch<MiOlivoSnapshot>('/api/v1/mi-olivo', { workspaceId: selectedWorkspaceId }),
          apiFetch<FieldsPayload>('/api/v1/fields', { workspaceId: selectedWorkspaceId }),
          apiFetch<CampaignsPayload>('/api/v1/campaigns', { workspaceId: selectedWorkspaceId }),
        ]);

        if (!cancelled) {
          setSnapshot({
            balance: olivo.balance,
            enabled: olivo.enabled,
            fieldCount: fields.fields.length,
            campaignCount: campaigns.campaigns.length,
          });
        }
      } catch (error) {
        console.warn('Unable to load Mi Olivo welcome state', error);
        if (!cancelled) setSnapshot(null);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const profileReady = Boolean(profile?.municipality && profile?.public_role);
  const steps = useMemo(() => [
    {
      id: 'profile',
      label: 'Completa tu perfil',
      detail: 'Indica tu municipio y tu relación con el territorio.',
      href: '/perfil',
      done: profileReady,
    },
    {
      id: 'field',
      label: 'Añade tu primera finca',
      detail: 'Solo si tienes olivar. Mi Olivo también funciona sin finca.',
      href: '/mi-campo/fincas/nueva',
      done: (snapshot?.fieldCount ?? 0) > 0,
    },
    {
      id: 'explore',
      label: 'Explora Sierra Mágina',
      detail: 'Descubre pueblos y contenido real para empezar a construir tu historia.',
      href: '/explorar',
      done: (snapshot?.balance ?? 0) > 0,
    },
  ], [profileReady, snapshot?.balance, snapshot?.fieldCount]);

  if (!snapshot || !snapshot.enabled) return null;

  const firstRun = snapshot.balance === 0 && snapshot.fieldCount === 0 && snapshot.campaignCount === 0;
  if (!firstRun) return null;

  const completed = steps.filter((step) => step.done).length;

  return (
    <section className={styles.shell} aria-label="Primeros pasos de Mi Olivo">
      <div className={styles.hero}>
        <div className={styles.copy}>
          <span className={styles.eyebrow}>MI OLIVO · PRIMER DÍA</span>
          <h2>Este es tu olivo</h2>
          <p className={styles.lead}>Acabas de plantar tu historia en Mágina. No necesitas tener una finca ni una campaña para empezar.</p>
          <div className={styles.startState}>
            <span>Brote</span>
            <strong>Nivel 1 · 0 aceitunas</strong>
            <small>Crecerá solo con acciones y datos reales.</small>
          </div>
        </div>

        <div className={styles.seedling} aria-hidden="true">
          <span className={styles.ground} />
          <span className={styles.stem} />
          <span className={`${styles.leaf} ${styles.leafLeft}`} />
          <span className={`${styles.leaf} ${styles.leafRight}`} />
        </div>
      </div>

      <div className={styles.journey}>
        <div className={styles.journeyHeading}>
          <div>
            <span className={styles.eyebrow}>EMPIEZA A TU MANERA</span>
            <h3>Tres primeros pasos</h3>
          </div>
          <strong>{completed}/3</strong>
        </div>

        <div className={styles.steps}>
          {steps.map((step, index) => (
            <Link href={step.href} className={styles.step} key={step.id} data-done={step.done ? 'true' : 'false'}>
              <span className={styles.stepNumber}>{step.done ? '✓' : index + 1}</span>
              <span className={styles.stepCopy}>
                <strong>{step.label}</strong>
                <small>{step.detail}</small>
              </span>
              <span className={styles.arrow} aria-hidden="true">→</span>
            </Link>
          ))}
        </div>

        <p className={styles.note}>Mi Campo guarda la parte técnica. Mi Olivo convierte esa actividad real en progreso, memoria y una experiencia más visual.</p>
      </div>
    </section>
  );
}
