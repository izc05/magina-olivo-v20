'use client';

import { useEffect, useState } from 'react';
import { lasCenillas } from '@/lib/demo-data';
import { getLocalFields } from '@/lib/local-prototype-store';

export type FieldContext = {
  id: string;
  name: string;
  municipality: string;
  oliveTrees?: number;
  campaign: string;
  local: boolean;
  returnHref: string;
};

const demoContext: FieldContext = {
  id: lasCenillas.id,
  name: lasCenillas.name,
  municipality: lasCenillas.municipality,
  oliveTrees: lasCenillas.oliveTrees,
  campaign: lasCenillas.campaign,
  local: false,
  returnHref: '/mi-campo/fincas/las-cenillas',
};

export function useFieldContext() {
  const [context, setContext] = useState<FieldContext>(demoContext);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const fieldId = params.get('fieldId');
    if (!fieldId || fieldId === lasCenillas.id) {
      setContext(demoContext);
      setReady(true);
      return;
    }

    const local = getLocalFields().find((field) => field.id === fieldId);
    if (local) {
      setContext({
        id: local.id,
        name: local.name,
        municipality: local.municipality ?? 'Sin municipio',
        oliveTrees: local.oliveTrees,
        campaign: lasCenillas.campaign,
        local: true,
        returnHref: `/mi-campo/fincas/local?id=${encodeURIComponent(local.id)}`,
      });
    } else {
      setContext(demoContext);
    }
    setReady(true);
  }, []);

  return { context, ready };
}

export function withFieldQuery(path: string, fieldId: string) {
  if (fieldId === lasCenillas.id) return path;
  return `${path}?fieldId=${encodeURIComponent(fieldId)}`;
}
