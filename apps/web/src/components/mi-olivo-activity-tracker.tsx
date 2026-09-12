'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-activity-tracker.module.css';

export type MiOlivoInteractionType = 'content_read' | 'territory_viewed' | 'weather_checked' | 'learning_completed';

export type MiOlivoTrackRequest = {
  eventType: MiOlivoInteractionType;
  sourceId: string;
};

type TrackableAction = MiOlivoTrackRequest & {
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

  return null;
}

export function requestMiOlivoTracking(detail: MiOlivoTrackRequest) {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent<MiOlivoTrackRequest>('magina:mi-olivo-track', { detail }));
}

export function MiOlivoActivityTracker() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [toast, setToast] = useState<ToastState | null>(null);

  const action = useMemo(() => actionForRoute(pathname, query), [pathname, query]);

  const recordAction = useCallback(async (trackRequest: MiOlivoTrackRequest) => {
    if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) return;

    const sessionKey = `magina:mi-olivo:${selectedWorkspaceId}:${trackRequest.eventType}:${trackRequest.sourceId}`;
    if (window.sessionStorage.getItem(sessionKey) === 'sent') return;

    window.sessionStorage.setItem(sessionKey, 'sent');
    try {
      const response = await apiFetch<AwardResponse>('/api/v1/mi-olivo/events', {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          event_type: trackRequest.eventType,
          source_id: trackRequest.sourceId,
        }),
      });

      if (response.awarded && response.points > 0) {
        setToast({ points: response.points, message: response.message });
        window.dispatchEvent(new CustomEvent('magina:mi-olivo-award', { detail: response }));
      }
    } catch (error) {
      window.sessionStorage.removeItem(sessionKey);
      console.debug('Mi Olivo did not record this optional interaction.', error);
    }
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => {
    if (!action) return;
    const timer = window.setTimeout(() => {
      void recordAction({ eventType: action.eventType, sourceId: action.sourceId });
    }, action.delayMs);
    return () => window.clearTimeout(timer);
  }, [action, recordAction]);

  useEffect(() => {
    const handleTrackRequest = (event: Event) => {
      const detail = (event as CustomEvent<MiOlivoTrackRequest>).detail;
      if (!detail?.eventType || !detail.sourceId) return;
      void recordAction(detail);
    };

    window.addEventListener('magina:mi-olivo-track', handleTrackRequest);
    return () => window.removeEventListener('magina:mi-olivo-track', handleTrackRequest);
  }, [recordAction]);

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
