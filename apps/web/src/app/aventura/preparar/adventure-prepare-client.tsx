'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { loadPublicRoute, type PublicRouteDetail } from '../../../lib/public-routes-source';
import {
  loadCurrentWeather,
  requestWeatherPosition,
  weatherConditionLabel,
  weatherSafetySummary,
  type AdventureWeatherState,
} from '../../../lib/weather-source';
import styles from './prepare.module.css';

function distance(value: number | null) {
  return value == null ? '—' : `${(value / 1000).toFixed(1)} km`;
}

function duration(value: number | null) {
  if (value == null) return '—';
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  return hours ? `${hours} h${minutes ? ` ${minutes} min` : ''}` : `${minutes} min`;
}

function difficulty(value: PublicRouteDetail['route']['difficulty']) {
  if (value === 'easy') return 'Fácil';
  if (value === 'moderate') return 'Moderada';
  if (value === 'hard') return 'Difícil';
  if (value === 'very_hard') return 'Muy difícil';
  return 'Sin clasificar';
}

function temperature(value: number | null) {
  return value == null ? '—' : `${Math.round(value)} °C`;
}

function wind(value: number | null) {
  return value == null ? '—' : `${Math.round(value)} km/h`;
}

function permissionDenied(cause: unknown) {
  if (!cause || typeof cause !== 'object' || !('code' in cause)) return false;
  return Number((cause as { code?: unknown }).code) === 1;
}

export function AdventurePrepareClient() {
  const [slug, setSlug] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicRouteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [weather, setWeather] = useState<AdventureWeatherState | null>(null);
  const [weatherBusy, setWeatherBusy] = useState(false);
  const [weatherError, setWeatherError] = useState<string | null>(null);

  useEffect(() => {
    const nextSlug = new URLSearchParams(window.location.search).get('slug');
    setSlug(nextSlug);
    if (!nextSlug) {
      setError(true);
      setLoading(false);
      return;
    }
    let cancelled = false;
    loadPublicRoute(nextSlug)
      .then((value) => {
        if (!cancelled) setDetail(value);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  async function checkWeather() {
    setWeatherBusy(true);
    setWeatherError(null);
    try {
      const coordinates = await requestWeatherPosition();
      const result = await loadCurrentWeather(coordinates.latitude, coordinates.longitude);
      setWeather(result.weather);
    } catch (cause) {
      setWeather(null);
      if (permissionDenied(cause)) {
        setWeatherError('No has dado permiso de ubicación. Puedes iniciar la aventura igualmente y consultar avisos oficiales por tu cuenta.');
      } else {
        setWeatherError('No hemos podido obtener el clima local. La ruta sigue disponible, pero revisa la previsión y los avisos oficiales antes de salir.');
      }
    } finally {
      setWeatherBusy(false);
    }
  }

  if (loading) return <main className={styles.page}><section className={styles.state}><strong>Preparando la salida…</strong><p>Cargando la ficha real y las comprobaciones previas.</p></section></main>;
  if (error || !detail || !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>No podemos preparar esta ruta</h1><p>La ruta puede estar en revisión o no disponer de una ficha publicada.</p><Link className={styles.primary} href="/aventura">Volver a Mágina Aventura</Link></section></main>;

  const route = detail.route;
  const safetyTone = weather?.officialAlert || weather?.condition === 'storm' || (weather?.intensity ?? 0) >= 3 ? 'warning' : 'normal';

  return <main className={styles.page}>
    <header className={styles.topbar}>
      <Link href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>← Ficha de ruta</Link>
      <span>PREPARACIÓN DE EXPEDICIÓN</span>
    </header>

    <section className={styles.hero}>
      <div>
        <span className={styles.kicker}>{route.municipality_name ?? 'Sierra Mágina'} · ANTES DE SALIR</span>
        <h1>Preparar aventura</h1>
        <h2>{route.name}</h2>
        <p>Comprueba la ruta real, el clima de tu posición y las condiciones básicas antes de activar la experiencia de exploración.</p>
      </div>
      <div className={styles.routeFacts}>
        <article><span>Distancia</span><strong>{distance(route.distance_m)}</strong></article>
        <article><span>Duración</span><strong>{duration(route.duration_minutes)}</strong></article>
        <article><span>Dificultad</span><strong>{difficulty(route.difficulty)}</strong></article>
        <article><span>Desnivel +</span><strong>{route.elevation_gain_m == null ? '—' : `${Math.round(route.elevation_gain_m)} m`}</strong></article>
      </div>
    </section>

    <section className={styles.grid}>
      <article className={styles.weatherCard} data-weather-condition={weather?.condition ?? 'inactive'}>
        <div className={styles.cardHeading}>
          <div><span className={styles.kicker}>CLIMA LOCAL · GPS OPCIONAL</span><h2>{weather ? weatherConditionLabel(weather) : 'Clima sin activar'}</h2></div>
          {weather ? <strong className={styles.temperature}>{temperature(weather.temperatureC)}</strong> : <span className={styles.weatherIcon}>◌</span>}
        </div>

        {weather ? <>
          <div className={styles.weatherStats}>
            <div><span>Sensación</span><strong>{temperature(weather.feelsLikeC)}</strong></div>
            <div><span>Viento</span><strong>{wind(weather.windSpeedKmh)}</strong></div>
            <div><span>Precipitación</span><strong>{weather.precipitationMm == null ? '—' : `${weather.precipitationMm.toFixed(1)} mm`}</strong></div>
            <div><span>Fuente</span><strong>{weather.provider}</strong></div>
          </div>
          <div className={`${styles.weatherAdvice} ${safetyTone === 'warning' ? styles.weatherWarning : ''}`}>
            <strong>{weather.officialAlert ? 'Aviso oficial' : 'Lectura para la salida'}</strong>
            <p>{weatherSafetySummary(weather)}</p>
            {weather.stale ? <small>Mostrando la última lectura disponible porque la actualización en directo no ha respondido.</small> : null}
          </div>
        </> : <p className={styles.muted}>Solo pediremos tu ubicación cuando pulses el botón. Las coordenadas se usan para consultar el clima y no se guardan como historial meteorológico personal.</p>}

        <button type="button" className={styles.weatherButton} disabled={weatherBusy} onClick={checkWeather}>
          {weatherBusy ? 'Consultando clima…' : weather ? 'Actualizar clima local' : 'Comprobar clima local'}
        </button>
        {weatherError ? <p className={styles.error} role="status">{weatherError}</p> : null}
      </article>

      <article className={styles.checkCard}>
        <span className={styles.kicker}>LISTA DE SALIDA</span>
        <h2>Todo listo para empezar</h2>
        <ul className={styles.checklist}>
          <li><span>✓</span><div><strong>Ficha real cargada</strong><small>Distancia, desnivel, dificultad y notas proceden de la ruta publicada.</small></div></li>
          <li><span>✓</span><div><strong>GPS bajo tu control</strong><small>La ubicación se solicita solo al activar funciones que la necesitan.</small></div></li>
          <li><span>{weather ? '✓' : '○'}</span><div><strong>Clima local {weather ? 'comprobado' : 'pendiente'}</strong><small>{weather ? `${weatherConditionLabel(weather)} · ${temperature(weather.temperatureC)}` : 'Recomendado antes de iniciar; no bloquea la ruta.'}</small></div></li>
          <li><span>{detail.track ? '✓' : '!'}</span><div><strong>{detail.track ? 'Track disponible' : 'Track no disponible en esta ficha'}</strong><small>Durante la marcha manda el trazado validado, la señalización y los cierres oficiales.</small></div></li>
        </ul>

        {(route.safety_notes || route.access_notes || route.restrictions) ? <div className={styles.routeSafety}>
          <strong>Notas de seguridad de la ruta</strong>
          {route.safety_notes ? <p>{route.safety_notes}</p> : null}
          {route.access_notes ? <p>{route.access_notes}</p> : null}
          {route.restrictions ? <p>{route.restrictions}</p> : null}
        </div> : null}
      </article>
    </section>

    <section className={styles.launch}>
      <div><span className={styles.kicker}>MODO AVENTURA</span><h2>Empieza cuando estés físicamente preparado</h2><p>La gamificación nunca sustituye la navegación, la señalización, los cierres ni los avisos oficiales.</p></div>
      <Link className={styles.primary} href={`/aventura/en-curso?slug=${encodeURIComponent(slug)}`}>Comenzar aventura →</Link>
    </section>
  </main>;
}
