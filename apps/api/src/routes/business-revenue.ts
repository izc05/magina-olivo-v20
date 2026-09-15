import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireDatabase } from '../http/helpers.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const EVENT_TYPES = [
  'directory_impression',
  'profile_view',
  'phone_click',
  'whatsapp_click',
  'email_click',
  'website_click',
  'directions_click',
  'offer_view',
  'offer_redeem',
] as const;

const eventSchema = z.object({
  eventType: z.enum(EVENT_TYPES),
  offerId: z.string().uuid().optional(),
  anonymousId: z.string().trim().max(160).optional(),
  sourceContext: z.string().trim().max(100).optional(),
  sourceKey: z.string().trim().max(240).optional(),
  metadata: z.record(z.string(), z.unknown()).default({}),
});

const leadSchema = z.object({
  kind: z.enum(['contact', 'quote', 'booking', 'availability', 'order']).default('contact'),
  contactName: z.string().trim().min(2).max(140),
  contactEmail: z.union([z.string().trim().email().max(254), z.literal('')]).optional(),
  contactPhone: z.string().trim().max(40).optional(),
  message: z.string().trim().max(2_500).optional(),
  requestedFor: z.coerce.date().nullable().optional(),
  partySize: z.coerce.number().int().min(1).max(500).nullable().optional(),
  offerId: z.string().uuid().optional(),
  anonymousId: z.string().trim().max(160).optional(),
  sourceContext: z.string().trim().max(100).optional(),
  sourceKey: z.string().trim().max(240).optional(),
  consentBusinessContact: z.literal(true),
}).superRefine((value, context) => {
  if (!value.contactEmail?.trim() && !value.contactPhone?.trim()) {
    context.addIssue({ code: 'custom', path: ['contactEmail'], message: 'email_or_phone_required' });
  }
});

type OfferRow = {
  id: string;
  slug: string;
  title: string;
  summary: string | null;
  description: string | null;
  offer_type: string;
  original_price_cents: number | null;
  offer_price_cents: number | null;
  currency: string;
  promo_code: string | null;
  redemption_mode: string;
  redemption_url: string | null;
  terms: string | null;
  valid_from: Date | null;
  valid_until: Date | null;
  max_redemptions: number | null;
};

function serializeOffer(row: OfferRow) {
  return {
    id: row.id,
    slug: row.slug,
    title: row.title,
    summary: row.summary,
    description: row.description,
    offerType: row.offer_type,
    pricing: {
      originalPriceCents: row.original_price_cents,
      offerPriceCents: row.offer_price_cents,
      currency: row.currency,
    },
    promoCode: row.promo_code,
    redemption: {
      mode: row.redemption_mode,
      url: row.redemption_url,
    },
    terms: row.terms,
    validFrom: row.valid_from,
    validUntil: row.valid_until,
    maxRedemptions: row.max_redemptions,
  };
}

async function resolvePublishedBusiness(database: DatabaseClient, slug: string) {
  const result = await sql<{ id: string; name: string }>`
    SELECT id, name
    FROM businesses
    WHERE slug = ${slug} AND status = 'published'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function offerBelongsToBusiness(database: DatabaseClient, offerId: string, businessId: string) {
  const result = await sql<{ id: string }>`
    SELECT id
    FROM business_offers
    WHERE id = ${offerId}::uuid
      AND business_id = ${businessId}::uuid
      AND status = 'published'
      AND (valid_from IS NULL OR valid_from <= now())
      AND (valid_until IS NULL OR valid_until >= now())
    LIMIT 1
  `.execute(database);
  return Boolean(result.rows[0]);
}

export function registerBusinessRevenueRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/businesses/:slug/offers', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });

    const business = await resolvePublishedBusiness(database, params.data.slug);
    if (!business) return reply.code(404).send({ error: 'business_not_found' });

    const result = await sql<OfferRow>`
      SELECT id, slug, title, summary, description, offer_type,
             original_price_cents, offer_price_cents, currency, promo_code,
             redemption_mode, redemption_url, terms, valid_from, valid_until, max_redemptions
      FROM business_offers
      WHERE business_id = ${business.id}::uuid
        AND status = 'published'
        AND (valid_from IS NULL OR valid_from <= now())
        AND (valid_until IS NULL OR valid_until >= now())
      ORDER BY sort_order, valid_until NULLS LAST, created_at DESC
      LIMIT 30
    `.execute(database);

    return { business: { id: business.id, slug: params.data.slug, name: business.name }, offers: result.rows.map(serializeOffer) };
  });

  app.post('/api/v1/public/businesses/:slug/events', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });
    const parsed = eventSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_event', issues: parsed.error.issues });

    const business = await resolvePublishedBusiness(database, params.data.slug);
    if (!business) return reply.code(404).send({ error: 'business_not_found' });
    if (parsed.data.offerId && !(await offerBelongsToBusiness(database, parsed.data.offerId, business.id))) {
      return reply.code(400).send({ error: 'business_offer_not_available' });
    }

    await sql`
      INSERT INTO business_events (
        business_id, offer_id, event_type, anonymous_id, source_context, source_key, metadata
      ) VALUES (
        ${business.id}::uuid,
        ${parsed.data.offerId ?? null}::uuid,
        ${parsed.data.eventType},
        ${parsed.data.anonymousId ?? null},
        ${parsed.data.sourceContext ?? null},
        ${parsed.data.sourceKey ?? null},
        ${JSON.stringify(parsed.data.metadata)}::jsonb
      )
    `.execute(database);

    return reply.code(202).send({ accepted: true });
  });

  app.post('/api/v1/public/businesses/:slug/leads', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });
    const parsed = leadSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_lead', issues: parsed.error.issues });

    const business = await resolvePublishedBusiness(database, params.data.slug);
    if (!business) return reply.code(404).send({ error: 'business_not_found' });
    if (parsed.data.offerId && !(await offerBelongsToBusiness(database, parsed.data.offerId, business.id))) {
      return reply.code(400).send({ error: 'business_offer_not_available' });
    }

    const email = parsed.data.contactEmail?.trim() || null;
    const phone = parsed.data.contactPhone?.trim() || null;
    const inserted = await sql<{ id: string; created_at: Date }>`
      INSERT INTO business_leads (
        business_id, offer_id, kind, contact_name, contact_email, contact_phone,
        message, requested_for, party_size, source_context, source_key, consent_business_contact
      ) VALUES (
        ${business.id}::uuid,
        ${parsed.data.offerId ?? null}::uuid,
        ${parsed.data.kind},
        ${parsed.data.contactName},
        ${email?.toLowerCase() ?? null},
        ${phone},
        ${parsed.data.message ?? null},
        ${parsed.data.requestedFor ?? null},
        ${parsed.data.partySize ?? null},
        ${parsed.data.sourceContext ?? null},
        ${parsed.data.sourceKey ?? null},
        true
      )
      RETURNING id, created_at
    `.execute(database);
    const lead = inserted.rows[0];
    if (!lead) return reply.code(500).send({ error: 'business_lead_create_failed' });

    await sql`
      INSERT INTO business_events (
        business_id, offer_id, event_type, anonymous_id, source_context, source_key,
        metadata
      ) VALUES (
        ${business.id}::uuid,
        ${parsed.data.offerId ?? null}::uuid,
        'lead_submit',
        ${parsed.data.anonymousId ?? null},
        ${parsed.data.sourceContext ?? null},
        ${parsed.data.sourceKey ?? null},
        ${JSON.stringify({ kind: parsed.data.kind, leadId: lead.id })}::jsonb
      )
    `.execute(database);

    return reply.code(201).send({
      lead: { id: lead.id, status: 'new', createdAt: lead.created_at },
      message: `Solicitud enviada a ${business.name}.`,
    });
  });
}
