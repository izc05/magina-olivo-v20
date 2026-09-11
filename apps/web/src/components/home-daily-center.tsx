'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { ArrowIcon, MapPinIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { apiFetch } from '@/lib/api-client';
import { emptyAgenda, loadApiAgenda, type AgendaView } from '@/lib/agenda-data-source';
import { demoContext, demoFarmSummary } from '@/lib/demo-data';
import { getPreviewFarms, loadWorkspaceFarms, summarizeFarms, type FarmListItem } from '@/lib/farm-data-source';

type WeatherDay = {
  date: string;
  precipitationProbabilityPercent: number | null;
  temperatureMinC: number | null;
  temperatureMaxC: number | null;
  windMaxKmh: number | null;
};

type WeatherResponse = {
  municipality: { name: string };
  forecast: { provider: string; days: WeatherDay[] };
  stale: boolean;
};

function farmHref(farm: FarmListItem) {
  const params = new URLSearchParams({ id: farm.id, source: farm.source });
  return `/mi-campo/fincas/ver?${params.toString()}`;
}

function todayIso() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function HomeDailyCenter() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [farms, setFarms] = useState<FarmListItem[]>([]);
  const [agenda, setAgenda] = useState<AgendaView>(emptyAgenda());
  const [weather, setWeather] = useState<WeatherResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const [nextFarms, nextAgenda] = await Promise.all([
            loadWorkspaceFarms(selectedWorkspaceId),
            loadApiAgenda(selectedWorkspaceId),
          ]);
          if (!cancelled) {
            setFarms(nextFarms);
            setAgenda(nextAgenda);
          }
        } else if (!apiConfigured && !cancelled) {
          setFarms(getPreviewFarms());
          setAgenda(emptyAgenda());
        }
      } catch (error) {
        console.warn('Home daily center unavailable', error);
        if (!cancelled) {
          setFarms([]);
          setAgenda(emptyAgenda());
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const focusFarm = useMemo(() => {
    const priority = [...agenda.overdue, ...agenda.today, ...agenda.upcoming].find((item) => item.fieldId);
    if (priority?.fieldId) return farms.find((farm) => farm.id === priority.fieldId) ?? farms[0];
    return farms[0];
  }, [agenda, farms]);

  useEffect(() => {
    if (!focusFarm || focusFarm.source !== 'api' || !selectedWorkspaceId) {
      setWeather(null);
      return;
    }
    let cancelled = false;
    apiFetch<WeatherResponse>(`/api/v1/fields/${encodeURIComponent(focusFarm.id)}/weather/daily`, { workspaceId: selectedWorkspaceId })
      .then((result) => { if (!cancelled) setWeather(result); })
      .catch((error) => { console.warn('Home weather unavailable', error); if (!cancelled) setWeather(null); });
    return () => { cancelled = true; };
  }, [focusFarm, selectedWorkspaceId]);

  const summary = summarizeFarms(farms);
  const forecastDay = weather?.forecast.days.find((day) => day.date.slice(0, 10) === todayIso()) ?? weather?.forecast.days[0];
  const nextTask = agenda.overdue[0] ?? agenda.today[0] ?? agenda.upcoming[0];

  if (!apiConfigured) {
    return <>
      <section className="hero home-hero"><div className="hero-content">
        <div className="location-chip"><MapPinIcon /> {demoContext.municipality} <span>· preview estructural</span></div>
        <div className="hero-spacer" />
        <div className="hero-weather"><div><div className="weather-temp">{demoContext.temperatureC}°</div><strong>{demoContext.condition}</strong></div><div className="weather-meta">Viento {demoContext.windKmh} km/h<br/>Humedad {demoContext.humidityPercent} %</div></div>
        <div className="hero-rule"/><p className="hero-message">Preview: en servidor este bloque usa tu finca prioritaria y AEMET.</p>
      </div></section>
      <section className="section card field-summary premium-summary"><div className="field-summary-top"><div className="summary-brand"><span className="summary-mark"><SproutIcon /></span><div><h2>Mi Campo</h2><p>Vista previa del centro diario</p></div></div><Link href="/mi-campo" className="detail-link">Ver detalle <ArrowIcon /></Link></div><div className="stats"><div className="stat"><b>{demoFarmSummary.farms}</b><span>fincas demo</span></div><div className="stat"><b>{demoFarmSummary.oliveTrees}</b><span>olivas demo</span></div><div className="stat"><b>—</b><span>agenda real</span></div></div></section>
    </>;
  }

  if (status !== 'authenticated') {
    return <section className="section card"><h2>Tu campo, cuando entres</h2><p>Inicia sesión para ver tareas, fincas y contexto meteorológico real.</p><Link href="/perfil" className="primary action-link">Ir a mi cuenta <ArrowIcon /></Link></section>;
  }

  return <>
    <section className="hero home-hero"><div className="hero-content">
      <div className="location-chip"><MapPinIcon /> {focusFarm?.municipality ?? 'Mi Campo'} <span>{focusFarm ? `· ${focusFarm.name}` : '· sin finca prioritaria'}</span></div>
      <div className="hero-spacer" />
      <div className="hero-weather">
        <div><div className="weather-temp">{forecastDay?.temperatureMaxC != null ? `${Math.round(forecastDay.temperatureMaxC)}°` : '—'}</div><strong>{weather ? 'Previsión de hoy' : 'Tiempo no disponible'}</strong></div>
        <div className="weather-meta">Lluvia {forecastDay?.precipitationProbabilityPercent ?? '—'} %<br/>Viento máx. {forecastDay?.windMaxKmh ?? '—'} km/h</div>
      </div>
      <div className="hero-rule" />
      <p className="hero-message">{nextTask ? `${nextTask.bucket === 'overdue' ? 'Pendiente: ' : 'Siguiente: '}${nextTask.title}` : loading ? 'Cargando tu campo…' : 'Sin tareas pendientes próximas'}</p>
      {weather?.stale ? <small>La previsión disponible está marcada como antigua.</small> : null}
    </div></section>

    <section className="section section-overlap">
      <div className="section-head"><h2>Hoy en tu campo</h2><Link href="/mi-campo/hoy">Abrir agenda <ArrowIcon /></Link></div>
      <div className="alert-grid">
        <Link href="/mi-campo/hoy" className="card alert rose"><span className="alert-icon">!</span><strong>{agenda.counts.overdue} atrasada{agenda.counts.overdue === 1 ? '' : 's'}</strong><small>Solo quedan aquí hasta que registres o reprogrames.</small></Link>
        <Link href="/mi-campo/hoy" className="card alert green"><span className="alert-icon">✓</span><strong>{agenda.counts.today} para hoy</strong><small>{nextTask?.fieldName ?? 'Sin tarea inmediata'}</small></Link>
        <Link href="/mi-campo/hoy" className="card alert blue"><span className="alert-icon">☁</span><strong>{agenda.counts.weatherSensitive} sensibles al clima</strong><small>AEMET y radar solo aportan contexto.</small></Link>
      </div>
    </section>

    <section className="section card field-summary premium-summary">
      <div className="field-summary-top"><div className="summary-brand"><span className="summary-mark"><SproutIcon /></span><div><h2>Mi Campo</h2><p>{focusFarm ? `Foco ahora: ${focusFarm.name}` : 'Tus fincas'}</p></div></div><Link href="/mi-campo" className="detail-link">Ver detalle <ArrowIcon /></Link></div>
      <div className="stats"><div className="stat"><b>{summary.farms}</b><span>fincas</span></div><div className="stat"><b>{summary.oliveTrees}</b><span>olivas</span></div><div className="stat"><b>{summary.areaHa !== undefined ? `${summary.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : '—'}</b><span>superficie</span></div></div>
      <div className="record-actions">
        {focusFarm ? <Link href={farmHref(focusFarm)} className="secondary-action action-link">Abrir {focusFarm.name}</Link> : null}
        <Link href="/mi-campo/registrar" className="primary action-link"><PlusIcon /> Registrar</Link>
      </div>
    </section>
  </>;
}
