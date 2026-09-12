import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const CAMPAIGN_RULE_VERSION = 'mi-olivo-v5';
const badgeIds = ['none', 'roots', 'harvest', 'explorer', 'campaign'] as const;
const appearanceSchema = z.object({ badge: z.enum(badgeIds) });

type BadgeId = (typeof badgeIds)[number];
type CampaignRow = {
  id: string;
  name: string;
  status: string;
  start_date: string | Date;
  end_date: string | Date | null;
};
type CampaignMetricsRow = {
  delivery_count: number;
  delivered_kg: number;
  has_confirmed_yield: boolean;
};
type ProfileState = { enabled: boolean };
type SelectedBadgeRow = { selected_badge: BadgeId };
type EventTypeRow = { event_type: string };
type IdRow = { id: string };
type PointsRow = { total: number };

type CampaignMission = {
  id: string;
  event_type: string;
  title: string;
  detail: string;
  reward: number;
  completed: boolean;
  progress_current: number;
  progress_target: number;
};

const badgeDefinitions: Array<{
  id: BadgeId;
  title: string;
  detail: string;
  symbol: string;
  requires_event: string | null;
}> = [
  { id: 'none', title: 'Sin distintivo', detail: 'El olivo se muestra sin emblema adicional.', symbol: '·', requires_event: null },
  { id: 'roots', title: 'Primeras raíces', detail: 'Desbloqueado al registrar tu primera actividad real.', symbol: '🌱', requires_event: 'first_activity' },
  { id: 'harvest', title: 'Primera cosecha', detail: 'Desbloqueado al registrar tu primera entrega.', symbol: '🫒', requires_event: 'first_harvest_delivery' },
  { id: 'explorer', title: 'Raíces en Mágina', detail: 'Desbloqueado al explorar tu primer pueblo.', symbol: '⛰️', requires_event: 'territory_viewed' },
  { id: 'campaign', title: 'Mil kilos', detail: 'Desbloqueado al documentar 1.000 kg propios en una campaña.', symbol: '🏅', requires_event: 'campaign_1000kg' },
];

async function readAppearance(database: DatabaseClient, userId: string) {
  const selected = await sql<SelectedBadgeRow>`
    SELECT selected_badge
    FROM mi_olivo_appearance
    WHERE user_id = ${userId}::uuid
  `.execute(database);

  const events = await sql<EventTypeRow>`
    SELECT DISTINCT event_type
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND points > 0
  `.execute(database);
  const unlockedEvents = new Set(events.rows.map((row) => row.event_type));

  const options = badgeDefinitions.map((badge) => ({
    id: badge.id,
    title: badge.title,
    detail: badge.detail,
    symbol: badge.symbol,
    unlocked: badge.requires_event === null || unlockedEvents.has(badge.requires_event),
  }));

  const current = selected.rows[0]?.selected_badge ?? 'none';
  const selectedIsUnlocked = options.some((option) => option.id === current && option.unlocked);

  return {
    selected_badge: selectedIsUnlocked ? current : 'none',
    options,
  };
}

async function currentCampaign(database: DatabaseClient, workspaceId: string) {
  const result = await sql<CampaignRow>`
    SELECT id::text, name, status, start_date, end_date
    FROM campaigns
    WHERE workspace_id = ${workspaceId}::uuid
    ORDER BY
      CASE status WHEN 'active' THEN 0 WHEN 'planned' THEN 1 ELSE 2 END,
      start_date DESC
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function campaignMetrics(database: DatabaseClient, workspaceId: string, userId: string, campaignId: string) {
  const result = await sql<CampaignMetricsRow>`
    SELECT
      COUNT(DISTINCT hd.id)::int AS delivery_count,
      COALESCE(SUM(hd.total_kg), 0)::double precision AS delivered_kg,
      EXISTS (
        SELECT 1
        FROM delivery_results dr
        JOIN harvest_deliveries hd_yield ON hd_yield.id = dr.delivery_id
        WHERE hd_yield.workspace_id = ${workspaceId}::uuid
          AND hd_yield.campaign_id = ${campaignId}::uuid
          AND hd_yield.created_by = ${userId}::uuid
          AND dr.status = 'confirmed'
      ) AS has_confirmed_yield
    FROM harvest_deliveries hd
    WHERE hd.workspace_id = ${workspaceId}::uuid
      AND hd.campaign_id = ${campaignId}::uuid
      AND hd.created_by = ${userId}::uuid
  `.execute(database);

  return result.rows[0] ?? {
    delivery_count: 0,
    delivered_kg: 0,
    has_confirmed_yield: false,
  };
}

function buildCampaignMissions(metrics: CampaignMetricsRow): CampaignMission[] {
  return [
    {
      id: 'campaign-deliveries-3',
      event_type: 'campaign_three_deliveries',
      title: 'Tres entregas en orden',
      detail: 'Documenta tres entregas propias dentro de la misma campaña.',
      reward: 15,
      completed: metrics.delivery_count >= 3,
      progress_current: Math.min(metrics.delivery_count, 3),
      progress_target: 3,
    },
    {
      id: 'campaign-1000kg',
      event_type: 'campaign_1000kg',
      title: 'Mil kilos documentados',
      detail: 'Alcanza 1.000 kg propios registrados en la campaña. No exige producir más: solo documentar lo que ya has entregado.',
      reward: 20,
      completed: metrics.delivered_kg >= 1000,
      progress_current: Math.min(Math.round(metrics.delivered_kg), 1000),
      progress_target: 1000,
    },
    {
      id: 'campaign-yield',
      event_type: 'campaign_first_yield',
      title: 'Primer rendimiento confirmado',
      detail: 'Vincula un rendimiento confirmado a una de tus entregas de campaña.',
      reward: 15,
      completed: metrics.has_confirmed_yield,
      progress_current: metrics.has_confirmed_yield ? 1 : 0,
      progress_target: 1,
    },
  ];
}

async function awardCampaignMission(
  database: DatabaseClient,
  input: {
    userId: string;
    workspaceId: string;
    campaignId: string;
    eventType: string;
    points: number;
    reason: string;
  },
) {
  const result = await sql<IdRow>`
    INSERT INTO mi_olivo_ledger (
      user_id, workspace_id, event_type, source_type, source_id,
      points, reason, rule_version, idempotency_key
    ) VALUES (
      ${input.userId}::uuid,
      ${input.workspaceId}::uuid,
      ${input.eventType},
      'campaign',
      ${input.campaignId},
      ${input.points},
      ${input.reason},
      ${CAMPAIGN_RULE_VERSION},
      ${`${CAMPAIGN_RULE_VERSION}:${input.eventType}:${input.campaignId}`}
    )
    ON CONFLICT (user_id, idempotency_key) DO NOTHING
    RETURNING id::text AS id
  `.execute(database);
  return result.rows.length > 0;
}

export function registerMiOlivoCampaignRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/mi-olivo/campaign', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const profile = await sql<ProfileState>`
      SELECT enabled
      FROM mi_olivo_profiles
      WHERE user_id = ${context.userId}::uuid
    `.execute(database);
    const enabled = profile.rows[0]?.enabled ?? true;
    const appearanceBeforeAwards = await readAppearance(database, context.userId);
    const campaign = await currentCampaign(database, context.workspaceId);

    if (!campaign) {
      return {
        enabled,
        rule_version: CAMPAIGN_RULE_VERSION,
        campaign: null,
        metrics: null,
        missions: [],
        newly_awarded_points: 0,
        campaign_olives_earned: 0,
        appearance: appearanceBeforeAwards,
      };
    }

    const metrics = await campaignMetrics(database, context.workspaceId, context.userId, campaign.id);
    const missions = buildCampaignMissions(metrics);
    let newlyAwardedPoints = 0;

    if (enabled) {
      for (const mission of missions) {
        if (!mission.completed) continue;
        const awarded = await awardCampaignMission(database, {
          userId: context.userId,
          workspaceId: context.workspaceId,
          campaignId: campaign.id,
          eventType: mission.event_type,
          points: mission.reward,
          reason: `${mission.title} · ${campaign.name}`,
        });
        if (awarded) newlyAwardedPoints += mission.reward;
      }
    }

    const total = await sql<PointsRow>`
      SELECT COALESCE(SUM(points), 0)::int AS total
      FROM mi_olivo_ledger
      WHERE user_id = ${context.userId}::uuid
        AND workspace_id = ${context.workspaceId}::uuid
        AND rule_version = ${CAMPAIGN_RULE_VERSION}
        AND points > 0
    `.execute(database);

    return {
      enabled,
      rule_version: CAMPAIGN_RULE_VERSION,
      campaign,
      metrics,
      missions,
      newly_awarded_points: newlyAwardedPoints,
      campaign_olives_earned: Math.max(0, total.rows[0]?.total ?? 0),
      appearance: await readAppearance(database, context.userId),
    };
  });

  app.put('/api/v1/mi-olivo/appearance', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(appearanceSchema, request.body, reply);
    if (!input) return;

    const appearance = await readAppearance(database, context.userId);
    const option = appearance.options.find((candidate) => candidate.id === input.badge);
    if (!option?.unlocked) {
      return reply.code(409).send({ error: 'badge_locked' });
    }

    await sql`
      INSERT INTO mi_olivo_appearance (user_id, selected_badge, updated_at)
      VALUES (${context.userId}::uuid, ${input.badge}, now())
      ON CONFLICT (user_id)
      DO UPDATE SET selected_badge = EXCLUDED.selected_badge, updated_at = now()
    `.execute(database);

    return readAppearance(database, context.userId);
  });
}
