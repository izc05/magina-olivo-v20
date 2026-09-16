import type { CSSProperties } from 'react';
import type { AdventureWeatherState } from '../../../../lib/weather-source';
import { WeatherParticles } from './weather-particles';
import { buildWeatherSceneModel } from './weather-scene-model';
import styles from './weather-scene.module.css';

type WeatherSceneStyle = CSSProperties & {
  '--weather-transition-ms': string;
};

export function WeatherScene({ weather }: { weather: AdventureWeatherState }) {
  const model = buildWeatherSceneModel(weather);
  const sceneStyle: WeatherSceneStyle = {
    '--weather-transition-ms': `${model.transitionDurationMs}ms`,
  };

  return (
    <div
      className={styles.scene}
      data-weather-scene="true"
      data-weather-condition={weather.condition}
      data-weather-intensity={weather.intensity}
      data-weather-phase={weather.dayPhase}
      data-weather-sky={model.skyPreset}
      data-weather-tone={model.ambientTone}
      data-weather-cloud-density={model.cloudDensity}
      data-weather-fog-density={model.fogDensity}
      data-weather-tier={model.performanceTier}
      style={sceneStyle}
      aria-hidden="true"
    >
      <div className={styles.sky} />
      <div className={styles.ambient} style={{ opacity: model.ambientOpacity }} />
      <div className={styles.clouds} style={{ opacity: model.cloudDensity }} />
      <div className={styles.fog} style={{ opacity: model.fogDensity }} />
      <WeatherParticles model={model} />
    </div>
  );
}
