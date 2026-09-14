'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-care-controller.module.css';

type Field = {
  id: string;
  name: string;
};

type ActivityItem = {
  id: string;
  occurred_at: string;
  domain_type: string;
  domain_record_id: string;
  title: string;
  summary: string | null;
  icon_key: string | null;
};

type CareItem = ActivityItem & {
  fieldId: string;
  fieldName: string;
};

const CARE_TYPES = [
  { key: 'irrigation', label: 'Riego', route: 'riego' },
  { key: 'pruning', label: 'Poda', route: 'poda' },
  { key: 'treatment', label: 'Tratamientos', route: 'tratamiento' },
  { key: 'fertilization', label: 'Abonado', route: 'abono' },
  { key: 'observation', label: 'Observaciones', route: 'observacion' },
] as const;

function timestamp(value: string) {
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Fecha registrada';
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
}

function activityHref(item: CareItem, route: string) {
  const params = new URLSearchParams({ fieldId: item.fieldId, source: 'api' });
  return `/mi-campo/registrar/${route}?${params.toString()}`;
}

export function MiOlivoCareController() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [fields, setFields] = useState<Field[]>([]);
  const [activity, setActivity] = useState<CareItem[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
        setFields([]);
        setActivity([]);
        setLoaded(false);
        return;
      }

      try {
        const fieldPayload = await apiFetch<{ fields: Field[] }>('/api/v1/fields', { workspaceId: selectedWorkspaceId });
        if (cancelled) return;
        setFields(fieldPayload.fields);

        const results = await Promise.allSettled(
          fieldPayload.fields.map(async (field) => {
            const payload = await apiFetch<{ field: Field; items: ActivityItem[] }>(`/api/v1/fields/${field.id}/activity`, {
              workspaceId: selectedWorkspaceId,
            });
            return payload.items.map((item) => ({ ...item, fieldId: field.id, fieldName: field.name }));
          }),
        );

        if (!cancelled) {
          setActivity(results.flatMap((result) => result.status === 'fulfilled' ? result.value : []));
          setLoaded(true);
        }
      } catch (error) {
        console.warn('Unable to load Mi Olivo care activity', error);
        if (!cancelled) {
          setFields([]);
          setActivity([]);
          setLoaded(false);
        }
      }
    }

    void load();
    const refresh = () => { void load(); };
    window.addEventListener('magina:mi-olivo-award', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:mi-olivo-award', refresh);
    };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const careActivity = useMemo(
    () => activity
      .filter((item) => CARE_TYPES.some((type) => type.key === item.domain_type))
      .sort((left, right) => timestamp(right.occurred_at) - timestamp(left.occurred_at)),
    [activity],
  );

  const latestByType = useMemo(() => Object.fromEntries(
    CARE_TYPES.map((type) => [type.key, careActivity.find((item) => item.domain_type === type.key) ?? null]),
  ) as Record<(typeof CARE_TYPES)[number]['key'], CareItem | null>, [careActivity]);

  if (status !== 'authenticated' || !apiConfigured || !loaded || fields.length === 0) return null;

  return (
    <section className={styles.shell} aria-label="Cuidados reales de Mi Olivo">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>CUIDADOS DEL OLIVAR</span>
          <h2>Lo que realmente has registrado</h2>
          <p>Esta vista reutiliza la actividad de tus fincas. No supone que una tarea esté hecha si no existe un registro en Mi Campo.</p>
        </div>
        <Link href="/mi-campo" className={styles.link}>Abrir Mi Campo →</Link>
      </div>

      <div className={styles.careGrid}>
        {CARE_TYPES.map((type) => {
          const item = latestByType[type.key];
          return (
            <article className={styles.careCard} key={type.key}>
              <span className={styles.careLabel}>{type.label}</span>
              {item ? (
                <>
                  <strong>{formatDate(item.occurred_at)}</strong>
                  <small>{item.fieldName}</small>
                  <Link href={activityHref(item, type.route)}>Registrar otro →</Link>
                </>
              ) : (
                <>
                  <strong>Sin registro reciente</strong>
                  <small>No estimamos cuándo se realizó por última vez.</small>
                  {fields.length === 1 ? (
                    <Link href={`/mi-campo/registrar/${type.route}?fieldId=${encodeURIComponent(fields[0].id)}&source=api`}>Registrar →</Link>
                  ) : <Link href="/mi-campo">Elegir finca →</Link>}
                </>
              )}
            </article>
          );
        })}
      </div>

      {careActivity.length > 0 ? (
        <div className={styles.recent}>
          <div className={styles.recentHeading}>
            <h3>Actividad reciente</h3>
            <span>Últimos registros cargados</span>
          </div>
          <div className={styles.recentList}>
            {careActivity.slice(0, 6).map((item) => (
              <article className={styles.recentItem} key={`${item.domain_type}:${item.domain_record_id}`}>
                <time>{formatDate(item.occurred_at)}</time>
                <div>
                  <strong>{item.title}</strong>
                  <small>{item.fieldName}{item.summary ? ` · ${item.summary}` : ''}</small>
                </div>
              </article>
            ))}
          </div>
        </div>
      ) : (
        <div className={styles.empty}>
          <strong>Todavía no hay cuidados registrados.</strong>
          <span>Cuando guardes un riego, poda, tratamiento, abonado u observación en Mi Campo aparecerá aquí.</span>
        </div>
      )}
    </section>
  );
}
