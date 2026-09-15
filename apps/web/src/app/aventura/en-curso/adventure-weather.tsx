'use client';

import { useEffect, useRef, useState } from 'react';
import {
  loadCurrentWeather,
  requestWeatherPosition,
  weatherConditionLabel,
  weatherSafetySummary,
  type AdventureWeatherCondition,
  type AdventureWeatherDayPhase,
  type AdventureWeatherIntensity,
  type AdventureWeatherState,
} from '../../../lib/weather-source';
import styles from './adventure-weather.module.css';

const REFRESH_MS = 10 * 60 * 1000;

const conditionClass: Record<AdventureWeatherCondition, string> = {
  clear: styles.clear,
  cloudy: styles.cloudy,
  rain: styles.rain,
  storm: styles.storm,
  fog: styles.fog,
  snow: styles.snow,
  wind: styles.wind,
};

const phaseClass: Record<AdventureWeatherDayPhase, string> = {
  day: styles.day,
  golden_hour: styles.goldenHour,
  dusk: styles.dusk,
  night: styles.night,
};

const labPresets: Array<{ label: string; condition: AdventureWeatherCondition; intensity: AdventureWeatherIntensity; phase?: AdventureWeatherDayPhase }> = [
  { label: 'Sol', condition: 'clear', intensity: 0 },
  { label: 'Nublado', condition: 'cloudy', intensity: 2 },
  { label: 'Lluvia suave', condition: 'rain', intensity: 1 },
  { label: 'Lluvia intensa', condition: 'rain', intensity: 3 },
  { label: 'Tormenta', condition: 'storm', intensity: 3 },
  { label: 'Niebla', condition: 'fog', intensity: 2 },
  { label: 'Nieve', condition: 'snow', intensity: 2 },
  { label: 'Viento', condition: 'wind', intensity: 2 },
  { label: 'Noche', condition: 'clear', intensity: 0, phase: 'night' },
];

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

function simulatedWeather(condition: AdventureWeatherCondition, intensity: AdventureWeatherIntensity, phase: AdventureWeatherDayPhase = 'day'): AdventureWeatherState {
  return {
    provider: 'Simulation',
    condition,
    intensity,
    temperatureC: null,
    feelsLikeC: null,
    windSpeedKmh: condition === 'wind' ? 45 : null,
    windDirectionDeg: condition === 'wind' ? 225 : null,
    windGustsKmh: condition === 'wind' ? 65 : null,
    precipitationMm: condition === 'rain' || condition === 'storm' ? intensity === 3 ? 7 : 0.8 : null,
    visibilityM: condition === 'fog' ? 450 : null,
    cloudCoverPercent: condition === 'cloudy' || condition === 'rain' || condition === 'storm' ? 95 : null,
    weatherCode: null,
    dayPhase: phase,
    observedAt: null,
    expiresAt: null,
    officialAlert: null,
    stale: false,
  };
}

export function AdventureWeather({ routeName }: { routeName: string }) {
  const [weather, setWeather] = useState<AdventureWeatherState | null>(null);
  const [active, setActive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [labEnabled, setLabEnabled] = useState(false);
  const [labMode, setLabMode] = useState(false);
  const mounted = useRef(true);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const localHost = window.location.hostname === '127.0.0.1' || window.location.hostname === 'localhost';
    setLabEnabled(localHost && params.get('weatherLab') === '1');
    return () => { mounted.current = false; };
  }, []);

  async function refreshWeather(background = false) {
    if (!background) setBusy(true);
    setMessage(null);
    try {
      const coordinates = await requestWeatherPosition();
      const result = await loadCurrentWeather(coordinates.latitude, coordinates.longitude);
      if (!mounted.current) return;
      setWeather(result.weather);
      setLabMode(false);
      setActive(true);
    } catch (cause) {
      if (!mounted.current) return;
      if (!background) {
        setMessage(permissionDenied(cause)
          ? 'Ubicación no autorizada. La aventura sigue funcionando sin clima local.'
          : 'No se ha podido actualizar el clima local. Mantén como referencia los avisos oficiales y la situación del terreno.');
      }
    } finally {
      if (mounted.current && !background) setBusy(false);
    }
  }

  useEffect(() => {
    if (!active || labMode) return;
    const timer = window.setInterval(() => { void refreshWeather(true); }, REFRESH_MS);
    return () => window.clearInterval(timer);
  // El temporizador solo depende de que el usuario haya activado explícitamente el clima.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, labMode]);

  function applyLab(condition: AdventureWeatherCondition, intensity: AdventureWeatherIntensity, phase: AdventureWeatherDayPhase = 'day') {
    setActive(false);
    setLabMode(true);
    setMessage(null);
    setWeather(simulatedWeather(condition, intensity, phase));
  }

  return <>
    {weather ? <div
      className={`${styles.visualLayer} ${conditionClass[weather.condition]} ${phaseClass[weather.dayPhase]}`}
      data-weather-condition={weather.condition}
      data-weather-intensity={weather.intensity}
      aria-hidden="true"
    /> : null}

    <section className={styles.dock} aria-labelledby="adventure-weather-title">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>WEATHER ENGINE · GPS OPCIONAL</span>
          <h2 id="adventure-weather-title">Clima local</h2>
        </div>
        {weather ? <span className={labMode ? styles.labPill : styles.live}>{labMode ? '● LAB' : '● AUTO'}</span> : <span className={styles.off}>○ OFF</span>}
      </div>

      {weather ? <div className={styles.liveGrid}>
        <div className={styles.condition}>
          <span>{weather.dayPhase === 'night' ? '◐' : weather.condition === 'rain' ? '☂' : weather.condition === 'storm' ? 'ϟ' : weather.condition === 'fog' ? '≋' : weather.condition === 'snow' ? '✦' : weather.condition === 'wind' ? '≈' : weather.condition === 'cloudy' ? '☁' : '☀'}</span>
          <div><strong>{weatherConditionLabel(weather)}</strong><small>{routeName}</small></div>
        </div>
        <div className={styles.metric}><span>Temperatura</span><strong>{temperature(weather.temperatureC)}</strong></div>
        <div className={styles.metric}><span>Sensación</span><strong>{temperature(weather.feelsLikeC)}</strong></div>
        <div className={styles.metric}><span>Viento</span><strong>{wind(weather.windSpeedKmh)}</strong></div>
        <div className={styles.metric}><span>Lluvia</span><strong>{weather.precipitationMm == null ? '—' : `${weather.precipitationMm.toFixed(1)} mm`}</strong></div>
      </div> : <p className={styles.intro}>Activa el clima para adaptar el ambiente de la expedición a las condiciones de tu posición. La ubicación solo se solicita al activar esta función.</p>}

      {weather ? <div className={`${styles.advice} ${weather.officialAlert || weather.condition === 'storm' || weather.intensity >= 3 ? styles.alert : ''}`}>
        <span>!</span><p>{weatherSafetySummary(weather)}</p>
        {weather.provider === 'Simulation' ? <small>Solo laboratorio visual local · no usar para decisiones de seguridad.</small> : weather.stale ? <small>Última lectura disponible · actualización en directo temporalmente no disponible.</small> : <small>Actualización automática aproximada cada 10 minutos.</small>}
      </div> : null}

      <div className={styles.actions}>
        <button type="button" onClick={() => void refreshWeather(false)} disabled={busy}>
          {busy ? 'Actualizando clima…' : weather && !labMode ? 'Actualizar ahora' : 'Activar clima local'}
        </button>
        {weather ? <button type="button" className={styles.secondary} onClick={() => { setWeather(null); setActive(false); setLabMode(false); setMessage(null); }}>Desactivar efectos</button> : null}
      </div>

      {labEnabled ? <div className={styles.lab}>
        <div><strong>Laboratorio de clima</strong><small>Solo disponible en entorno local. Simula el renderer sin consultar GPS ni APIs.</small></div>
        <div className={styles.labButtons}>{labPresets.map((preset) => <button key={preset.label} type="button" onClick={() => applyLab(preset.condition, preset.intensity, preset.phase)}>{preset.label}</button>)}</div>
      </div> : null}
      {message ? <p className={styles.message} role="status">{message}</p> : null}
    </section>
  </>;
}
