import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import {
  catastroReferenceSchema,
  gisBboxQuerySchema,
  linkCatastroReferenceSchema,
  linkSigpacReferenceSchema,
  sigpacFeatureIdSchema,
} from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import type { GisProviders } from '../gis/providers.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

function upstreamFailure(reply: FastifyReply, source: 'catastro' | 'sigpac', error: unknown) {
  const message = error instanceof Error ? error.message : 'Unknown upstream error';
  return reply.code(502).send({ error: `${source}_upstream_error`, message });
}

function canManageGeometry(role: string) {
  return role === 'owner' || role === 'admin' || role === 'manager' || role === 'development';
}

async function persistReference(
  db: DatabaseClient,
  input: {
    fieldId: string;
    source: 'catastro' | 'sigpac';
    reference: string;
    geometry: unknown;
    areaHa: number | null;
    metadata: Record<string, unknown>;
    setAsGeometry: boolean;
  },
) {
  const geometryJson = JSON.stringify(input.geometry);
  const checkedAt = new Date();

  await sql`
    INSERT INTO field_land_refs (
      field_id, source, reference, geometry, area_ha, status, metadata_json, checked_at, updated_at
    ) VALUES (
      ${input.fieldId}::uuid,
      ${input.source},
      ${input.reference},
      ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(${geometryJson}), 4326)),
      ${input.areaHa},
      'verified',
      ${JSON.stringify(input.metadata)}::jsonb,
      ${checkedAt},
      ${checkedAt}
    )
    ON CONFLICT (field_id, source, reference) WHERE reference IS NOT NULL
    DO UPDATE SET
      geometry = EXCLUDED.geometry,
      area_ha = EXCLUDED.area_ha,
      status = 'verified',
      metadata_json = EXCLUDED.metadata_json,
      checked_at = EXCLUDED.checked_at,
      updated_at = EXCLUDED.updated_at
  `.execute(db);

  if (input.setAsGeometry) {
    await sql`
      UPDATE fields
      SET geometry = ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(${geometryJson}), 4326)),
          calculated_area_ha = COALESCE(${input.areaHa}, ST_Area(ST_SetSRID(ST_GeomFromGeoJSON(${geometryJson}), 4326)::geography) / 10000.0),
          geometry_source = ${input.source},
          geometry_status = 'verified',
          geometry_checked_at = ${checkedAt},
          updated_at = ${checkedAt}
      WHERE id = ${input.fieldId}::uuid
    `.execute(db);
  }
}

export function registerGisRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  providers: GisProviders,
) {
  app.get('/api/v1/gis/catastro/parcels', async (request, reply) => {
    if (!requireContext(request, reply)) return;
    const parsed = gisBboxQuerySchema.safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_bbox', issues: parsed.error.issues });
    try {
      return { source: 'catastro', items: await providers.catastro.parcelsByBbox(parsed.data) };
    } catch (error) {
      return upstreamFailure(reply, 'catastro', error);
    }
  });

  app.get('/api/v1/gis/catastro/parcels/:reference', async (request, reply) => {
    if (!requireContext(request, reply)) return;
    const parsed = catastroReferenceSchema.safeParse(request.params);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_cadastral_reference' });
    try {
      return { source: 'catastro', item: await providers.catastro.parcelByReference(parsed.data.reference) };
    } catch (error) {
      return upstreamFailure(reply, 'catastro', error);
    }
  });

  app.get('/api/v1/gis/sigpac/recintos', async (request, reply) => {
    if (!requireContext(request, reply)) return;
    const parsed = gisBboxQuerySchema.safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_bbox', issues: parsed.error.issues });
    try {
      return { source: 'sigpac', items: await providers.sigpac.recintosByBbox(parsed.data) };
    } catch (error) {
      return upstreamFailure(reply, 'sigpac', error);
    }
  });

  app.get('/api/v1/gis/sigpac/recintos/:featureId', async (request, reply) => {
    if (!requireContext(request, reply)) return;
    const parsed = sigpacFeatureIdSchema.safeParse(request.params);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_sigpac_feature_id' });
    try {
      return { source: 'sigpac', item: await providers.sigpac.recintoById(parsed.data.featureId) };
    } catch (error) {
      return upstreamFailure(reply, 'sigpac', error);
    }
  });

  app.get('/api/v1/fields/:fieldId/land-references', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !await fieldBelongsToWorkspace(database, fieldId, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });

    const result = await sql<{
      id: string;
      source: string;
      reference: string | null;
      area_ha: number | null;
      status: string;
      metadata_json: Record<string, unknown>;
      checked_at: Date | null;
      geometry: unknown | null;
    }>`
      SELECT flr.id, flr.source, flr.reference, flr.area_ha, flr.status, flr.metadata_json, flr.checked_at,
             CASE WHEN flr.geometry IS NULL THEN NULL ELSE ST_AsGeoJSON(flr.geometry)::json END AS geometry
      FROM field_land_refs flr
      JOIN fields f ON f.id = flr.field_id
      WHERE flr.field_id = ${fieldId}::uuid AND f.workspace_id = ${context.workspaceId}::uuid
      ORDER BY flr.source, flr.reference NULLS LAST, flr.created_at
    `.execute(database);
    return { items: result.rows };
  });

  app.post('/api/v1/fields/:fieldId/land-references/catastro', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    if (!canManageGeometry(context.role)) return reply.code(403).send({ error: 'geometry_management_forbidden' });
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !await fieldBelongsToWorkspace(database, fieldId, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });
    const input = parseBody(linkCatastroReferenceSchema, request.body, reply);
    if (!input) return;

    try {
      const parcel = await providers.catastro.parcelByReference(input.reference);
      await persistReference(database, {
        fieldId,
        source: 'catastro',
        reference: parcel.nationalCadastralReference,
        geometry: parcel.geometry,
        areaHa: parcel.areaM2 == null ? null : parcel.areaM2 / 10_000,
        metadata: { label: parcel.label, begin_lifespan_version: parcel.beginLifespanVersion },
        setAsGeometry: input.set_as_geometry,
      });
      return reply.code(201).send({ linked: true, source: 'catastro', reference: parcel.nationalCadastralReference, canonical_geometry_updated: input.set_as_geometry });
    } catch (error) {
      return upstreamFailure(reply, 'catastro', error);
    }
  });

  app.post('/api/v1/fields/:fieldId/land-references/sigpac', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    if (!canManageGeometry(context.role)) return reply.code(403).send({ error: 'geometry_management_forbidden' });
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !await fieldBelongsToWorkspace(database, fieldId, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });
    const input = parseBody(linkSigpacReferenceSchema, request.body, reply);
    if (!input) return;

    try {
      const recinto = await providers.sigpac.recintoById(input.feature_id);
      await persistReference(database, {
        fieldId,
        source: 'sigpac',
        reference: recinto.id,
        geometry: recinto.geometry,
        areaHa: recinto.surfaceM2 == null ? null : recinto.surfaceM2 / 10_000,
        metadata: {
          provincia: recinto.provincia,
          municipio: recinto.municipio,
          agregado: recinto.agregado,
          zona: recinto.zona,
          poligono: recinto.poligono,
          parcela: recinto.parcela,
          recinto: recinto.recinto,
          uso_sigpac: recinto.usoSigpac,
          pendiente_media: recinto.pendienteMedia,
          altitud: recinto.altitud,
        },
        setAsGeometry: input.set_as_geometry,
      });
      return reply.code(201).send({ linked: true, source: 'sigpac', reference: recinto.id, canonical_geometry_updated: input.set_as_geometry });
    } catch (error) {
      return upstreamFailure(reply, 'sigpac', error);
    }
  });
}
