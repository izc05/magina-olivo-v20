'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-activity-tracker.module.css';

type InteractionType = 'content_read' | 'territory_viewed' | 'weather_checked' | 'learning_completed';

type TrackableAction = {
  eventType: InteractionType;
  sourceId: string;
  delayMs: number;
};

type AwardResponse = {
  awarded: boolean;
  points: number;
  status: 'awarded' | 'already_recognized' | 'daily_cap' | 'paused';
  message: string;
  daily: { earned: number; cap: number; remaining: number };
};

type ToastState = {
  points: number;
  message: string;
};

function actionForRoute(pathname: string, query: string): TrackableAction | null {
  const params = new URLSearchParams(query);
  const slug = params.get('slug')?.trim();

  if (pathname === '/noticias' && slug) {
    return { eventType: 'content_read', sourceId: `noticia:${slug}`, delayMs: 8_000 };
  }
  if (pathname === '/eventos' && slug) {
    return { eventType: 'content_read', sourceId: `evento:${slug}`, delayMs: 8_000 };
  }
  if (pathname === '/pueblos' && slug) {
    return { eventType: 'territory_viewed', sourceId: `pueblo:${slug}`, delayMs: 6_000 };
  }
  if (pathname === '/radar') {
    return { eventType: 'weather_checked', sourceId: 'radar', delayMs: 5_000 };
  }
  if (pathname === '/consejos') {
    return { eventType: 'learning_completed', sourceId: 'consejo:cuaderno-practico', delayMs: 12_000 };
  }

  return null;
}

export function MiOlivoActivityTracker() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [toast, setToast] = useState<ToastState | null>(null);

  const action = useMemo(() => actionForRoute(pathname, query), [pathname, query]);

  useEffect(() => {
    if (!action || status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) return;

    const sessionKey = `magina:mi-olivo:${selectedWorkspaceId}:${action.eventType}:${action.sourceId}`;
    if (window.sessionStorage.getItem(sessionKey) === 'sent') return;

    const timer = window.setTimeout(async () => {
      window.sessionStorage.setItem(sessionKey, 'sent');
      try {
        const response = await apiFetch<AwardResponse>('/api/v1/mi-olivo/events', {
          method: 'POST',
          workspaceId: selectedWorkspaceId,
          body: JSON.stringify({
            event_type: action.eventType,
            source_id: action.sourceId,
          }),
        });

        if (response.awarded && response.points > 0) {
          setToast({ points: response.points, message: response.message });
          window.dispatchEvent(new CustomEvent('magina:mi-olivo-award', { detail: response }));
        }
      } catch (error) {
        console.debug('Mi Olivo did not record this optional interaction.', error);
      }
    }, action.delayMs);

    return () => window.clearTimeout(timer);
  }, [action, apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4_500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!toast) return null;

  return (
    <aside className={styles.toast} role="status" aria-live="polite">
      <span className={styles.olive}>🫒</span>
      <div>
        <strong>+{toast.points} aceitunas</strong>
        <span>{toast.message}</span>
      </div>
    </aside>
  );
}
