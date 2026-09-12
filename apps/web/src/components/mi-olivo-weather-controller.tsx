'use client';

import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-weather-controller.module.css';

type Field = {
  id: string;
  name: string;
  municipality_id: string | null;
};

type WeatherDay = {
  date: string;
  precipitationProbabilityPercent: number | null;
  temperatureMinC: number | null;
  temperatureMaxC: number | null;
  windMaxKmh: number | null;
};

type FieldWeather = {
  municipality: {
    id: string;
    name: string;
    aemet_code: string;
  };
  forecast: {
    provider: 'AEMET OpenData';
    municipalityCode: string;
    municipalityName: string | null;
    province: string | null;
    elaboratedAt: string | null;
    days: WeatherDay[];
  };
  cache_status: string;
  fetched_at: string | null;
  stale: boolean;
};

type WeatherContext = {
  field: Field;
  weather: FieldWeather;
  day: WeatherDay;
};

function numberOrDash(value: number | null, suffix: string) {
  return value === null ? '—' : `${Math.round(value)}${suffix}`;
}

export function MiOlivoWeatherController() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [context, setContext] = useState<WeatherContext | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
        if (!cancelled) setContext(null);
        return;
      }

      try {
        const fieldsPayload = await apiFetch<{ fields: Field[] }>('/api/v1/fields', {
          workspaceId: selectedWorkspaceId,
        });
        const field = fieldsPayload.fields.find((item) => Boolean(item.municipality_id));
        if (!field) {
          if (!cancelled) setContext(null);
          return;
        }

        const weather = await apiFetch<FieldWeather>(`/api/v1/fields/${field.id}/weather/daily`, {
          workspaceId: selectedWorkspaceId,
        });
        const day = weather.forecast.days[0];
        if (!day) {
          if (!cancelled) setContext(null);
          return;
        }

        if (!cancelled) setContext({ field, weather, day });
      } catch (error) {
        console.warn('Unable to load real weather context for Mi Olivo', error);
        if (!cancelled) setContext(null);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const visualFlags = useMemo(() => {
    const day = context?.day;
    if (!day || context?.weather.stale) return { rain: false, windy: false, heat: false, cold: false };
    return {
      rain: (day.precipitationProbabilityPercent ?? 0) >= 70,
      windy: (day.windMaxKmh ?? 0) >= 35,
      heat: (day.temperatureMaxC ?? -100) >= 33,
      cold: (day.temperatureMaxC ?? 100) <= 12,
    };
  }, [context]);

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.miOlivoRain = visualFlags.rain ? 'likely' : 'no';
    root.dataset.miOlivoWindy = visualFlags.windy ? 'true' : 'false';
    root.dataset.miOlivoHeat = visualFlags.heat ? 'true' : 'false';
    root.dataset.miOlivoCold = visualFlags.cold ? 'true' : 'false';

    return () => {
      delete root.dataset.miOlivoRain;
      delete root.dataset.miOlivoWindy;
      delete root.dataset.miOlivoHeat;
      delete root.dataset.miOlivoCold;
    };
  }, [visualFlags]);

  if (!context) return null;

  const { field, weather, day } = context;
  const staleLabel = weather.stale ? ' · datos antiguos: la escena no cambia con esta previsión' : '';

  return (
    <section className={styles.strip} aria-label="Previsión real que ambienta Mi Olivo">
      <div>
        <span className={styles.kicker}>AEMET · PREVISIÓN DE HOY</span>
        <strong>{field.name} · {weather.municipality.name}</strong>
        <small>{weather.forecast.provider}{staleLabel}</small>
      </div>
      <dl className={styles.metrics}>
        <div><dt>Lluvia</dt><dd>{numberOrDash(day.precipitationProbabilityPercent, ' %')}</dd></div>
        <div><dt>Mín./máx.</dt><dd>{numberOrDash(day.temperatureMinC, '°')} / {numberOrDash(day.temperatureMaxC, '°')}</dd></div>
        <div><dt>Viento máx.</dt><dd>{numberOrDash(day.windMaxKmh, ' km/h')}</dd></div>
      </dl>
    </section>
  );
}
