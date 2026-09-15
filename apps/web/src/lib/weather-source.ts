import { apiFetch } from './api-client';

export type AdventureWeatherCondition = 'clear' | 'cloudy' | 'rain' | 'storm' | 'fog' | 'snow' | 'wind';
export type AdventureWeatherIntensity = 0 | 1 | 2 | 3;
export type AdventureWeatherDayPhase = 'day' | 'golden_hour' | 'dusk' | 'night';

export type AdventureWeatherState = {
  provider: 'Open-Meteo' | 'AEMET OpenData';
  condition: AdventureWeatherCondition;
  intensity: AdventureWeatherIntensity;
  temperatureC: number | null;
  feelsLikeC: number | null;
  windSpeedKmh: number | null;
  windDirectionDeg: number | null;
  windGustsKmh: number | null;
  precipitationMm: number | null;
  visibilityM: number | null;
  cloudCoverPercent: number | null;
  weatherCode: number | null;
  dayPhase: AdventureWeatherDayPhase;
  observedAt: string | null;
  expiresAt: string | null;
  officialAlert: {
    source: 'AEMET';
    level: 'yellow' | 'orange' | 'red' | 'unknown';
    title: string;
    description: string | null;
    startsAt: string | null;
    endsAt: string | null;
  } | null;
  stale: boolean;
};

export type CurrentWeatherResponse = {
  weather: AdventureWeatherState;
  cache_status: 'fresh' | 'refreshed' | 'stale';
  fetched_at: string;
};

export type WeatherCoordinates = { latitude: number; longitude: number };

export async function loadCurrentWeather(latitude: number, longitude: number) {
  return apiFetch<CurrentWeatherResponse>('/api/v1/public/weather/current', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  });
}

export function requestWeatherPosition() {
  return new Promise<WeatherCoordinates>((resolve, reject) => {
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
      reject(new Error('geolocation_unavailable'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      reject,
      { enableHighAccuracy: false, timeout: 12_000, maximumAge: 5 * 60 * 1000 },
    );
  });
}

export function weatherConditionLabel(weather: AdventureWeatherState) {
  const intensity = weather.intensity;
  if (weather.condition === 'clear') return weather.dayPhase === 'night' ? 'Noche despejada' : 'Despejado';
  if (weather.condition === 'cloudy') return intensity >= 2 ? 'Muy nuboso' : 'Nublado';
  if (weather.condition === 'rain') return intensity >= 3 ? 'Lluvia intensa' : intensity === 2 ? 'Lluvia moderada' : 'Lluvia débil';
  if (weather.condition === 'storm') return intensity >= 3 ? 'Tormenta intensa' : 'Tormenta';
  if (weather.condition === 'fog') return intensity >= 2 ? 'Niebla densa' : 'Niebla';
  if (weather.condition === 'snow') return intensity >= 2 ? 'Nevada' : 'Nieve débil';
  return intensity >= 2 ? 'Viento fuerte' : 'Viento';
}

export function weatherSafetySummary(weather: AdventureWeatherState) {
  if (weather.officialAlert) return `Aviso oficial ${weather.officialAlert.level}: ${weather.officialAlert.title}`;
  if (weather.condition === 'storm') return 'Condiciones adversas: revisa avisos oficiales y valora posponer la salida.';
  if (weather.condition === 'fog' && weather.intensity >= 2) return 'Visibilidad reducida: extrema la orientación y no abandones el trazado validado.';
  if (weather.condition === 'wind' && weather.intensity >= 2) return 'Viento fuerte: extrema la precaución en zonas expuestas.';
  if (weather.condition === 'rain' && weather.intensity >= 2) return 'Lluvia relevante: puede empeorar el firme y aumentar el riesgo de resbalones.';
  if (weather.condition === 'snow') return 'Posible nieve: comprueba accesos, equipamiento y estado oficial de la ruta.';
  return 'Sin señales meteorológicas adversas en la lectura actual. Mantén la revisión de avisos oficiales.';
}
