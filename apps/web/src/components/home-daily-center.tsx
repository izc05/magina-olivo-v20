'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { HomePriorityCard } from '@/components/home-priority-card';
import { ArrowIcon, MapPinIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { emptyAgenda, loadApiAgenda, type AgendaView } from '@/lib/agenda-data-source';
import { demoContext, demoFarmSummary } from '@/lib/demo-data';
import { getPreviewFarms, loadWorkspaceFarms, summarizeFarms, type FarmListItem } from '@/lib/farm-data-source';
import {
  formatHomeTimestamp,
  loadHomeRecentActivity,
  loadHomeWeather,
  weatherProviderLabel,
  type HomeActivityItem,
  type HomeWeatherView,
} from '@/lib/home-daily-data-source';

function farmHref(farm: FarmListItem) {
  const params = new URLSearchParams({ id: farm.id, source: farm.source });
  return `/mi-campo/fincas/ver?${params.toString()}`;
}

function fieldActionHref(path: string, farm: FarmListItem) {
  const params = new URLSearchParams({ fieldId: farm.id, source: farm.source });
  return `${path}?${params.toString()}`;
}

function todayIso() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function HomeDailyCenter() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
  const [farms, setFarms] = useState<FarmListItem[]>([]);
  const [agenda, setAgenda] = useState<AgendaView>(emptyAgenda());
  const [weather, setWeather] = useState<HomeWeatherView | null>(null);
  const [recentActivity, setRecentActivity] = useState<HomeActivityItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [farmError, setFarmError] = useState(false);
  const [agendaError, setAgendaError] = useState(false);
  const [weatherError, setWeatherError] = useState(false);
  const [activityError, setActivityError] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
        const [farmResult, agendaResult] = await Promise.allSettled([
          loadWorkspaceFarms(selectedWorkspaceId),
          loadApiAgenda(selectedWorkspaceId),
        ]);
        if (cancelled) return;

        if (farmResult.status === 'fulfilled') {
          setFarms(farmResult.value);
          setFarmError(false);
        } else {
          console.warn('Home farms unavailable', farmResult.reason);
          setFarms([]);
          setFarmError(true);
        }

        if (agendaResult.status === 'fulfilled') {
          setAgenda(agendaResult.value);
          setAgendaError(false);
        } else {
          console.warn('Home agenda unavailable', agendaResult.reason);
          setAgenda(emptyAgenda());
          setAgendaError(true);
        }
      } else if (!apiConfigured && previewEnabled) {
        setFarms(getPreviewFarms());
        setAgenda(emptyAgenda());
        setFarmError(false);
        setAgendaError(false);
      } else if (apiConfigured && status === 'loading') {
        return;
      } else {
        setFarms([]);
        setAgenda(emptyAgenda());
        setFarmError(false);
        setAgendaError(false);
      }
      if (!cancelled) setLoading(false);
    }
    void load().catch((error) => {
      console.warn('Home daily center unavailable', error);
      if (!cancelled) {
        setFarmError(true);
        setAgendaError(true);
        setLoading(false);
      }
    });
    return () => { cancelled = true; };
  }, [apiConfigured, previewEnabled, revision, selectedWorkspaceId, status]);

  const focusFarm = useMemo(() => {
    const priority = [...agenda.overdue, ...agenda.today, ...agenda.upcoming].find((item) => item.fieldId);
    if (priority?.fieldId) return farms.find((farm) => farm.id === priority.fieldId) ?? farms[0];
    return farms[0];
  }, [agenda, farms]);

  useEffect(() => {
    if (!focusFarm || focusFarm.source !== 'api' || !selectedWorkspaceId) {
      setWeather(null);
      setRecentActivity([]);
      setWeatherError(false);
      setActivityError(false);
      return;
    }
    const workspaceId = selectedWorkspaceId;
    let cancelled = false;

    setWeather(null);
    setRecentActivity([]);
    setWeatherError(false);
    setActivityError(false);

    async function loadFocusContext() {
      const [weatherResult, activityResult] = await Promise.allSettled([
        loadHomeWeather(workspaceId, focusFarm.id),
        loadHomeRecentActivity(workspaceId, focusFarm.id, 4),
      ]);
      if (cancelled) return;

      if (weatherResult.status === 'fulfilled') {
        setWeather(weatherResult.value);
        setWeatherError(false);
      } else {
        console.warn('Home weather unavailable', weatherResult.reason);
        setWeather(null);
        setWeatherError(true);
      }

      if (activityResult.status === 'fulfilled') {
        setRecentActivity(activityResult.value);
        setActivityError(false);
      } else {
        console.warn('Home activity unavailable', activityResult.reason);
        setRecentActivity([]);
        setActivityError(true);
      }
    }

    void loadFocusContext();
    return () => { cancelled = true; };
  }, [focusFarm, revision, selectedWorkspaceId]);

  const summary = summarizeFarms(farms);
  const forecastDay = weather?.days.find((day) => day.date.slice(0, 10) === todayIso()) ?? weather?.days[0];
  const nextTask = agenda.overdue[0] ?? agenda.today[0] ?? agenda.upcoming[0];
  const hasAgendaItems = agenda.counts.overdue + agenda.counts.today + agenda.counts.upcoming > 0;
  const unavailableCount = [farmError, agendaError, weatherError, activityError].filter(Boolean).length;

  if (!apiConfigured && previewEnabled) {
    return <>
      <section className="hero home-hero"><div className="hero-content">
        <div className="location-chip"><MapPinIcon /> {demoContext.municipality} <span>· demostración</span></div>
        <div className="hero-spacer" />
        <div className="hero-weather"><div><div className="weather-temp">{demoContext.temperatureC}°</div><strong>{demoContext.condition}</strong></div><div className="weather-meta">Viento {demoContext.windKmh} km/h<br/>Humedad {demoContext.humidityPercent} %</div></div>
        <div className="hero-rule"/><p className="hero-message">Datos de ejemplo para conocer cómo será tu centro diario.</p>
      </div></section>
      <section className="section card field-summary premium-summary"><div className="field-summary-top"><div className="summary-brand"><span className="summary-mark"><SproutIcon /></span><div><h2>Mi Campo</h2><p>Así se verá el resumen de tus fincas</p></div></div><Link href="/mi-campo" className="detail-link">Ver detalle <ArrowIcon /></Link></div><div className="stats"><div className="stat"><b>{demoFarmSummary.farms}</b><span>fincas de ejemplo</span></div><div className="stat"><b>{demoFarmSummary.oliveTrees}</b><span>olivas de ejemplo</span></div><div className="stat"><b>—</b><span>sin tareas reales</span></div></div></section>
    </>;
  }

  if (!apiConfigured) {
    return <section className="section card"><h2>Mi Campo no está disponible</h2><p>No se ha podido conectar con tus datos privados. Vuelve a intentarlo cuando el servicio esté disponible.</p></section>;
  }

  if (status !== 'authenticated') {
    return <section className="section card"><h2>Tu campo, cuando entres</h2><p>Inicia sesión para ver tareas, fincas y contexto meteorológico real.</p><Link href="/perfil" className="primary action-link">Ir a mi cuenta <ArrowIcon /></Link></section>;
  }

  return <>
    <section className="hero home-hero"><div className="hero-content">
      <div className="location-chip"><MapPinIcon /> {focusFarm?.municipality ?? 'Mi Campo'} <span>{focusFarm ? `· ${focusFarm.name}` : farmError ? '· datos no disponibles' : '· sin finca prioritaria'}</span></div>
      <div className="hero-spacer" />
      <div className="hero-weather">
        <div><div className="weather-temp">{forecastDay?.temperatureMaxC != null ? `${Math.round(forecastDay.temperatureMaxC)}°` : '—'}</div><strong>{weather ? `${weatherProviderLabel(weather.provider)} · hoy` : weatherError ? 'Tiempo no disponible' : focusFarm ? 'Consultando el tiempo' : 'Añade una finca'}</strong></div>
        <div className="weather-meta">Lluvia {forecastDay?.precipitationProbabilityPercent ?? '—'} %<br/>Viento máx. {forecastDay?.windMaxKmh ?? '—'} km/h</div>
      </div>
      <div className="hero-rule" />
      <p className="hero-message">{agendaError ? 'La agenda no está disponible ahora mismo.' : nextTask ? `${nextTask.bucket === 'overdue' ? 'Pendiente: ' : 'Siguiente: '}${nextTask.title}` : loading ? 'Cargando tu campo…' : 'Sin tareas pendientes próximas'}</p>
      {weather?.stale ? <small>Previsión desactualizada; úsala solo como referencia.</small> : weather ? <small>{weatherProviderLabel(weather.provider)}{formatHomeTimestamp(weather.fetchedAt) ? ` · actualizado ${formatHomeTimestamp(weather.fetchedAt)}` : ''}</small> : null}
    </div></section>

    {unavailableCount ? <section className="section card" role="status">
      <div className="section-head"><div><h2>Parte del centro diario no está disponible</h2><small>{unavailableCount} fuente{unavailableCount === 1 ? '' : 's'} sin respuesta. El resto de la información sigue siendo válida.</small></div><button className="secondary-action" type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar</button></div>
    </section> : null}

    {!loading && !farmError && farms.length === 0 ? <section className="section card">
      <h2>Añade tu primera finca</h2><p>Inicio se organiza alrededor de tus fincas reales: tareas, tiempo, actividad y accesos rápidos.</p><Link href="/mi-campo/fincas/nueva" className="primary action-link">Crear finca <ArrowIcon /></Link>
    </section> : null}

    <HomePriorityCard />

    {!agendaError && hasAgendaItems ? <section className="section section-overlap">
      <div className="section-head"><h2>Hoy en tu campo</h2><Link href="/mi-campo/hoy">Abrir agenda <ArrowIcon /></Link></div>
      <div className="alert-grid">
        {agenda.counts.overdue > 0 ? <Link href="/mi-campo/hoy" className="card alert rose"><span className="alert-icon">!</span><strong>{agenda.counts.overdue} atrasada{agenda.counts.overdue === 1 ? '' : 's'}</strong><small>Hasta registrar o reprogramar.</small></Link> : null}
        {agenda.counts.today > 0 ? <Link href="/mi-campo/hoy" className="card alert green"><span className="alert-icon">✓</span><strong>{agenda.counts.today} para hoy</strong><small>{nextTask?.fieldName ?? 'Tareas programadas'}</small></Link> : null}
        {agenda.counts.weatherSensitive > 0 ? <Link href="/mi-campo/hoy" className="card alert blue"><span className="alert-icon">☁</span><strong>{agenda.counts.weatherSensitive} sensibles al clima</strong><small>Previsión y radar aportan contexto.</small></Link> : null}
      </div>
    </section> : !loading && !agendaError && focusFarm ? <section className="section card"><div className="section-head"><div><h2>Agenda al día</h2><small>No tienes tareas pendientes próximas.</small></div><Link href={fieldActionHref('/mi-campo/planificar', focusFarm)}>Planificar <ArrowIcon /></Link></div></section> : null}

    {focusFarm && !activityError ? <section className="section">
      <div className="section-head"><div><h2>Actividad reciente</h2><small>{focusFarm.name}</small></div><Link href={farmHref(focusFarm)}>Abrir finca <ArrowIcon /></Link></div>
      {recentActivity.length ? <div className="card feed today-list">
        {recentActivity.map((item) => <div className="feed-row" key={item.id}><div className="feed-copy"><strong>{item.title}</strong><small>{formatHomeTimestamp(item.occurredAt) ?? item.occurredAt}</small>{item.summary ? <small>{item.summary}</small> : null}</div></div>)}
      </div> : <div className="card"><p>Todavía no hay actividad registrada en esta finca.</p><Link href={fieldActionHref('/mi-campo/registrar', focusFarm)} className="secondary-action action-link">Registrar primera actividad</Link></div>}
    </section> : null}

    <section className="section card field-summary premium-summary">
      <div className="field-summary-top"><div className="summary-brand"><span className="summary-mark"><SproutIcon /></span><div><h2>Mi Campo</h2><p>{farmError ? 'Resumen temporalmente no disponible' : focusFarm ? `Foco ahora: ${focusFarm.name}` : 'Tus fincas'}</p></div></div><Link href="/mi-campo" className="detail-link">Ver detalle <ArrowIcon /></Link></div>
      <div className="stats"><div className="stat"><b>{farmError ? '—' : summary.farms}</b><span>fincas</span></div><div className="stat"><b>{farmError ? '—' : summary.oliveTrees}</b><span>olivas</span></div><div className="stat"><b>{farmError ? '—' : summary.areaHa !== undefined ? `${summary.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : '—'}</b><span>superficie</span></div></div>
      <div className="record-actions">
        {focusFarm ? <Link href={farmHref(focusFarm)} className="secondary-action action-link">Abrir {focusFarm.name}</Link> : null}
        {focusFarm ? <Link href={fieldActionHref('/mi-campo/planificar', focusFarm)} className="secondary-action action-link">Planificar</Link> : null}
        {focusFarm ? <Link href={fieldActionHref('/mi-campo/registrar', focusFarm)} className="primary action-link"><PlusIcon /> Registrar</Link> : !farmError ? <Link href="/mi-campo/fincas/nueva" className="primary action-link">Añadir finca</Link> : null}
      </div>
    </section>
  </>;
}
