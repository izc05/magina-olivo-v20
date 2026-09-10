import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const RULE_VERSION = 'weather-task-v1';

type Target = { municipality_id: string | null; municipality_name: string | null; aemet_code: string | null };

function taskKind(value?: string) {
  if (value === 'treatment' || value === 'irrigation' || value === 'pruning' || value === 'harvest' || value === 'work') return value;
  return 'work';
}

export function registerAgronomyRoutes(app: FastifyInstance, db: DatabaseClient | null, provider: MunicipalityWeatherProvider) {
  app.get('/api/v1/fields/:fieldId/agronomy/advisory', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId) return reply.code(400).send({ error: 'invalid_field_id' });

    const query = request.query as { date?: string; task?: string };
    const targetDate = query.date?.trim() || new Date().toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) return reply.code(400).send({ error: 'invalid_date' });
    const task = taskKind(query.task);

    const targetResult = await sql<Target>`
      SELECT m.id AS municipality_id, m.name AS municipality_name, m.aemet_code
      FROM fields f
      LEFT JOIN territory_municipalities m ON m.id = f.municipality_id
      WHERE f.id = ${fieldId}::uuid
        AND f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
      LIMIT 1
    `.execute(database);
    const target = targetResult.rows[0];
    if (!target) return reply.code(404).send({ error: 'field_not_found' });
    if (!target.municipality_id || !target.aemet_code) return reply.code(409).send({ error: 'field_weather_context_unavailable' });

    const cached = await getCachedMunicipalityForecast(database, provider, target.municipality_id, target.aemet_code);
    const day = cached.forecast.days.find((item) => item.date.slice(0, 10) === targetDate) ?? cached.forecast.days[0];
    if (!day) return reply.code(409).send({ error: 'forecast_day_unavailable' });

    const rain = day.precipitationProbabilityPercent;
    const wind = day.windMaxKmh;
    const tMin = day.temperatureMinC;
    const tMax = day.temperatureMaxC;
    const reasons: string[] = [];
    let suitability: 'good' | 'caution' | 'avoid' | 'unknown' = 'good';
    let risk: 'none' | 'low' | 'medium' | 'high' | 'unknown' = 'low';

    if (task === 'treatment') {
      if (rain !== null && rain >= 70) { suitability = 'avoid'; risk = 'high'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
      else if (rain !== null && rain >= 40) { suitability = 'caution'; risk = 'medium'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
      if (wind !== null && wind >= 30) { suitability = 'avoid'; risk = 'high'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
      else if (wind !== null && wind >= 20 && suitability !== 'avoid') { suitability = 'caution'; risk = 'medium'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
    } else if (task === 'harvest' || task === 'pruning' || task === 'work') {
      if (rain !== null && rain >= 70) { suitability = 'caution'; risk = 'medium'; reasons.push(`Probabilidad de precipitación ${rain}%`); }
      if (wind !== null && wind >= 40) { suitability = 'caution'; risk = 'medium'; reasons.push(`Viento máximo previsto ${wind} km/h`); }
    } else if (task === 'irrigation') {
      if (rain !== null && rain >= 70) { suitability = 'caution'; risk = 'medium'; reasons.push(`Lluvia probable (${rain}%): conviene revisar la necesidad de riego`); }
    }

    if (!reasons.length) reasons.push('No se detectan señales meteorológicas destacadas con las reglas actuales');

    return {
      field_id: fieldId,
      date: targetDate,
      task,
      suitability,
      risk_level: risk,
      title: suitability === 'avoid' ? 'Condiciones poco favorables' : suitability === 'caution' ? 'Conviene revisar las condiciones' : 'Sin alertas meteorológicas destacadas',
      summary: reasons.join(' · '),
      reasons,
      evidence: {
        source: cached.forecast.provider,
        municipality: target.municipality_name,
        forecast_date: day.date,
        precipitation_probability_percent: rain,
        wind_max_kmh: wind,
        temperature_min_c: tMin,
        temperature_max_c: tMax,
        fetched_at: cached.fetchedAt,
        stale: cached.cacheStatus === 'stale',
      },
      confidence: cached.cacheStatus === 'fresh' ? 'medium' : 'low',
      rule_version: RULE_VERSION,
      requires_user_judgement: true,
      disclaimer: 'Contexto orientativo. No sustituye la etiqueta del producto, la normativa aplicable ni el criterio técnico del usuario.',
    };
  });
}
