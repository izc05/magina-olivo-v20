import type { AdventureWeatherState } from '../../../../lib/weather-source';
import styles from './weather-scene.module.css';

export function WeatherScene({ weather }: { weather: AdventureWeatherState }) {
  return (
    <div
      className={styles.scene}
      data-weather-scene="true"
      data-weather-condition={weather.condition}
      data-weather-intensity={weather.intensity}
      data-weather-phase={weather.dayPhase}
      aria-hidden="true"
    />
  );
}
