import type { CSSProperties } from 'react';
import type { WeatherVisualModel } from '@magina/weather/visual';
import styles from './weather-scene.module.css';

type ParticleKind = 'rain' | 'snow';

type ParticleStyle = CSSProperties & {
  '--particle-x': string;
  '--particle-delay-ms': string;
  '--particle-drift': string;
  '--particle-scale': string;
};

type ParticleFieldStyle = CSSProperties & {
  '--weather-particle-opacity': string;
  '--weather-particle-duration-ms': string;
  '--weather-rain-angle': string;
};

const PARTICLE_CAP = {
  full: 120,
  balanced: 72,
  minimal: 0,
} as const;

const BALANCED_PEAK_COUNT: Record<ParticleKind, number> = {
  rain: 24,
  snow: 16,
};

const FULL_PEAK_COUNT: Record<ParticleKind, number> = {
  rain: 48,
  snow: 32,
};

const PEAK_AMOUNT: Record<ParticleKind, number> = {
  rain: 0.9,
  snow: 0.85,
};

function precipitationFor(model: WeatherVisualModel): { kind: ParticleKind; amount: number } | null {
  if (model.snowAmount > 0) return { kind: 'snow', amount: model.snowAmount };
  if (model.rainAmount > 0) return { kind: 'rain', amount: model.rainAmount };
  return null;
}

function particleCount(kind: ParticleKind, amount: number, tier: WeatherVisualModel['performanceTier']) {
  if (tier === 'minimal') return 0;

  const peakCount = tier === 'full' ? FULL_PEAK_COUNT[kind] : BALANCED_PEAK_COUNT[kind];
  const scaledCount = Math.max(1, Math.round(peakCount * Math.min(1, amount / PEAK_AMOUNT[kind])));
  return Math.min(PARTICLE_CAP[tier], scaledCount);
}

function particleStyle(index: number): ParticleStyle {
  const x = (index * 37 + 11) % 101;
  const delay = -((index * 173) % 1800);
  const drift = ((index * 19) % 23) - 11;
  const scale = 0.72 + ((index * 13) % 8) * 0.06;

  return {
    '--particle-x': `${x}%`,
    '--particle-delay-ms': `${delay}ms`,
    '--particle-drift': `${drift}px`,
    '--particle-scale': scale.toFixed(2),
  };
}

function fieldStyle(kind: ParticleKind, amount: number, rainAngleDeg: number): ParticleFieldStyle {
  const durationMs = kind === 'rain'
    ? Math.round(1300 - amount * 550)
    : Math.round(5600 - amount * 2200);

  return {
    '--weather-particle-opacity': (0.38 + amount * 0.48).toFixed(2),
    '--weather-particle-duration-ms': `${durationMs}ms`,
    '--weather-rain-angle': `${rainAngleDeg}deg`,
  };
}

export function WeatherParticles({ model }: { model: WeatherVisualModel }) {
  const precipitation = precipitationFor(model);
  if (!precipitation) return null;

  const { kind, amount } = precipitation;
  const count = particleCount(kind, amount, model.performanceTier);
  const staticOnly = model.performanceTier === 'minimal' || model.reducedMotion;
  const style = fieldStyle(kind, amount, model.rainAngleDeg);

  return (
    <>
      <div
        className={styles.particleField}
        data-weather-particles-mode="animated"
        data-weather-particle-kind={kind}
        data-weather-particle-count={count}
        data-weather-particles-static-only={staticOnly ? 'true' : 'false'}
        style={style}
      >
        {Array.from({ length: count }, (_, index) => (
          <span
            className={`${styles.particle} ${kind === 'rain' ? styles.rainParticle : styles.snowParticle}`}
            style={particleStyle(index)}
            key={`${kind}-${index}`}
          />
        ))}
      </div>
      <div
        className={styles.particleFallback}
        data-weather-particles-mode="static"
        data-weather-particle-kind={kind}
        data-weather-particles-static-only={staticOnly ? 'true' : 'false'}
        style={{ opacity: Math.min(0.72, 0.2 + amount * 0.55) }}
      />
    </>
  );
}
