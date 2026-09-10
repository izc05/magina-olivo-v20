'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { lasCenillas } from '@/lib/demo-data';
import { getPreviewFarms, loadWorkspaceFarms } from '@/lib/farm-data-source';

export type FieldContext = {
  id: string;
  name: string;
  municipality: string;
  oliveTrees?: number;
  campaign: string;
  source: 'api' | 'local' | 'demo';
  local: boolean;
  returnHref: string;
};

const demoContext: FieldContext = {
  id: lasCenillas.id,
  name: lasCenillas.name,
  municipality: lasCenillas.municipality,
  oliveTrees: lasCenillas.oliveTrees,
  campaign: lasCenillas.campaign,
  source: 'demo',
  local: false,
  returnHref: '/mi-campo/fincas/las-cenillas',
};

function detailHref(id: string, source: FieldContext['source']) {
  if (source === 'demo' && id === lasCenillas.id) return '/mi-campo/fincas/las-cenillas';
  return `/mi-campo/fincas/ver?id=${encodeURIComponent(id)}&source=${source}`;
}

export function useFieldContext() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [context, setContext] = useState<FieldContext>(demoContext);
  const [ready, setReady] = useState(false);
  const [found, setFound] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function resolve() {
      const params = new URLSearchParams(window.location.search);
      const fieldId = params.get('fieldId');
      const requestedSource = params.get('source') as FieldContext['source'] | null;

      if (!fieldId || (fieldId === lasCenillas.id && requestedSource !== 'api')) {
        if (!cancelled) {
          setContext(demoContext);
          setFound(true);
          setReady(true);
        }
        return;
      }

      try {
        if (requestedSource === 'api' && apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const farm = (await loadWorkspaceFarms(selectedWorkspaceId)).find((item) => item.id === fieldId);
          if (!cancelled && farm) {
            setContext({
              id: farm.id,
              name: farm.name,
              municipality: farm.municipality ?? 'Sin municipio',
              oliveTrees: farm.oliveTrees,
              campaign: 'Campaña activa',
              source: 'api',
              local: false,
              returnHref: detailHref(farm.id, 'api'),
            });
            setFound(true);
            setReady(true);
            return;
          }
        }

        const farm = getPreviewFarms().find((item) => item.id === fieldId && (!requestedSource || item.source === requestedSource));
        if (!cancelled && farm) {
          const resolvedSource = farm.source;
          setContext({
            id: farm.id,
            name: farm.name,
            municipality: farm.municipality ?? 'Sin municipio',
            oliveTrees: farm.oliveTrees,
            campaign: lasCenillas.campaign,
            source: resolvedSource,
            local: resolvedSource === 'local',
            returnHref: detailHref(farm.id, resolvedSource),
          });
          setFound(true);
        } else if (!cancelled) {
          setFound(false);
        }
      } catch (error) {
        console.error('Unable to resolve finca context', error);
        if (!cancelled) setFound(false);
      } finally {
        if (!cancelled) setReady(true);
      }
    }

    if (requestedApiNeedsAuth(status, apiConfigured)) return;
    void resolve();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  return { context, ready, found };
}

function requestedApiNeedsAuth(status: 'loading' | 'anonymous' | 'authenticated', apiConfigured: boolean) {
  if (!apiConfigured) return false;
  const params = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
  return params?.get('source') === 'api' && status === 'loading';
}

export function withFieldQuery(path: string, fieldId: string, source?: FieldContext['source']) {
  if (fieldId === lasCenillas.id && (!source || source === 'demo')) return path;
  const query = new URLSearchParams({ fieldId });
  if (source) query.set('source', source);
  return `${path}?${query.toString()}`;
}
