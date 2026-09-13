import assert from 'node:assert/strict';
import test from 'node:test';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const DATABASE_URL = process.env.DATABASE_URL;
const userId = '91111111-1111-4111-8111-111111111111';
const otherUserId = '92222222-2222-4222-8222-222222222222';
const workspaceId = '93333333-3333-4333-8333-333333333333';
const routeId = '94444444-4444-4444-8444-444444444444';
const trackId = '95555555-5555-4555-8555-555555555555';

function headers(user = userId) {
  return { 'x-user-id': user, 'x-workspace-id': workspaceId };
}

test('route activity recording is explicit, segmented, private, exportable and deletable', { skip: !DATABASE_URL }, async () => {
  process.env.ALLOW_DEV_AUTH_HEADERS = 'true';
  process.env.NODE_ENV = 'test';
  process.env.RATE_LIMIT_ENABLED = 'false';

  const db = createDatabase(DATABASE_URL!);
  const app = buildApp({ db });
  const now = Date.now();

  try {
    await sql`
      INSERT INTO users (id, primary_email, display_name, status)
      VALUES
        (${userId}::uuid, 'activity-ci@example.invalid', 'Activity CI', 'active'),
        (${otherUserId}::uuid, 'activity-other@example.invalid', 'Other CI', 'active')
      ON CONFLICT (id) DO NOTHING
    `.execute(db);

    await sql`
      INSERT INTO routes (id, slug, name, route_type, distance_m, elevation_gain_m, duration_minutes, created_by, updated_by)
      VALUES (${routeId}::uuid, 'actividad-ci', 'Actividad CI', 'hiking', 5200, 340, 110, ${userId}::uuid, ${userId}::uuid)
      ON CONFLICT (id) DO NOTHING
    `.execute(db);

    await sql`
      INSERT INTO route_tracks(
        id, route_id, version, geometry, geometry_type, original_format,
        source_name, validation_status, validated_by, validated_at
      ) VALUES (
        ${trackId}::uuid, ${routeId}::uuid, 1,
        ST_GeomFromText('LINESTRING(-3.5000 37.7000,-3.4950 37.7050)', 4326),
        'LineString', 'manual', 'Activity CI track', 'validated', ${userId}::uuid, now()
      ) ON CONFLICT (id) DO NOTHING
    `.execute(db);

    await sql`
      UPDATE routes
      SET status = 'published', track_status = 'validated', validation_status = 'editorial',
          published_at = now(), last_verified_at = now()
      WHERE id = ${routeId}::uuid
    `.execute(db);

    const anonymous = await app.inject({ method: 'GET', url: '/api/v1/activities/active' });
    assert.equal(anonymous.statusCode, 401);

    const start = await app.inject({
      method: 'POST', url: `/api/v1/routes/${routeId}/activities/start`, headers: headers(),
    });
    assert.equal(start.statusCode, 201, start.body);
    const started = start.json() as { activity: { id: string; status: string; visibility: string } };
    assert.equal(started.activity.status, 'recording');
    assert.equal(started.activity.visibility, 'private');
    const activityId = started.activity.id;

    const duplicateStart = await app.inject({
      method: 'POST', url: `/api/v1/routes/${routeId}/activities/start`, headers: headers(),
    });
    assert.equal(duplicateStart.statusCode, 200, duplicateStart.body);
    assert.equal((duplicateStart.json() as { resumed_existing?: boolean }).resumed_existing, true);

    const pointsA = await app.inject({
      method: 'POST', url: `/api/v1/activities/${activityId}/points`, headers: headers(),
      payload: { points: [
        { sequence: 1001, recorded_at: new Date(now).toISOString(), latitude: 37.7000, longitude: -3.5000, altitude_m: 700, horizontal_accuracy_m: 5, vertical_accuracy_m: 5 },
        { sequence: 1002, recorded_at: new Date(now + 10_000).toISOString(), latitude: 37.7001, longitude: -3.5000, altitude_m: 705, horizontal_accuracy_m: 5, vertical_accuracy_m: 5 },
        { sequence: 1003, recorded_at: new Date(now + 20_000).toISOString(), latitude: 37.7002, longitude: -3.5000, altitude_m: 710, horizontal_accuracy_m: 5, vertical_accuracy_m: 5 },
      ] },
    });
    assert.equal(pointsA.statusCode, 200, pointsA.body);
    assert.equal((pointsA.json() as { accepted: number }).accepted, 3);

    const pause = await app.inject({ method: 'POST', url: `/api/v1/activities/${activityId}/pause`, headers: headers() });
    assert.equal(pause.statusCode, 200, pause.body);

    const rejectedWhilePaused = await app.inject({
      method: 'POST', url: `/api/v1/activities/${activityId}/points`, headers: headers(),
      payload: { points: [{ sequence: 1004, recorded_at: new Date(now + 30_000).toISOString(), latitude: 37.7003, longitude: -3.5000, horizontal_accuracy_m: 5 }] },
    });
    assert.equal(rejectedWhilePaused.statusCode, 409);

    const resume = await app.inject({ method: 'POST', url: `/api/v1/activities/${activityId}/resume`, headers: headers() });
    assert.equal(resume.statusCode, 200, resume.body);
    assert.equal((resume.json() as { activity: { current_segment: number } }).activity.current_segment, 2);

    const pointsB = await app.inject({
      method: 'POST', url: `/api/v1/activities/${activityId}/points`, headers: headers(),
      payload: { points: [
        { sequence: 1004, recorded_at: new Date(now + 120_000).toISOString(), latitude: 37.7010, longitude: -3.5000, altitude_m: 720, horizontal_accuracy_m: 5, vertical_accuracy_m: 5 },
        { sequence: 1005, recorded_at: new Date(now + 130_000).toISOString(), latitude: 37.7011, longitude: -3.5000, altitude_m: 725, horizontal_accuracy_m: 5, vertical_accuracy_m: 5 },
      ] },
    });
    assert.equal(pointsB.statusCode, 200, pointsB.body);

    const privateRead = await app.inject({ method: 'GET', url: `/api/v1/activities/${activityId}`, headers: headers(otherUserId) });
    assert.equal(privateRead.statusCode, 404);

    const finish = await app.inject({ method: 'POST', url: `/api/v1/activities/${activityId}/finish`, headers: headers() });
    assert.equal(finish.statusCode, 200, finish.body);
    const completed = (finish.json() as { activity: { status: string; distance_m: number; duration_seconds: number; elevation_gain_m: number | null; points_count: number } }).activity;
    assert.equal(completed.status, 'completed');
    assert.equal(completed.points_count, 5);
    assert.equal(completed.duration_seconds, 30, 'pause/resume must split duration into independent GPS segments');
    assert.ok(completed.distance_m > 30 && completed.distance_m < 60, `unexpected distance ${completed.distance_m}`);
    assert.equal(completed.elevation_gain_m, 15);

    const history = await app.inject({ method: 'GET', url: '/api/v1/activities/me?limit=10', headers: headers() });
    assert.equal(history.statusCode, 200, history.body);
    assert.equal((history.json() as { activities: Array<{ id: string }> }).activities[0]?.id, activityId);

    const profile = await app.inject({ method: 'GET', url: '/api/v1/adventures/me', headers: headers() });
    assert.equal(profile.statusCode, 200, profile.body);
    const recorded = (profile.json() as { recorded: { activity_count: number; recorded_distance_m: number; recorded_duration_seconds: number } }).recorded;
    assert.equal(recorded.activity_count, 1);
    assert.equal(recorded.recorded_duration_seconds, 30);
    assert.ok(recorded.recorded_distance_m > 30);

    const gpx = await app.inject({ method: 'GET', url: `/api/v1/activities/${activityId}/gpx`, headers: headers() });
    assert.equal(gpx.statusCode, 200, gpx.body);
    assert.match(gpx.body, /<gpx/);
    assert.equal((gpx.body.match(/<trkseg>/g) ?? []).length, 2, 'pause/resume must export two GPX segments');
    assert.match(String(gpx.headers['cache-control']), /private/);

    const remove = await app.inject({ method: 'DELETE', url: `/api/v1/activities/${activityId}`, headers: headers() });
    assert.equal(remove.statusCode, 204);
    const pointCount = await sql<{ total: number }>`
      SELECT COUNT(*)::int AS total FROM route_activity_points WHERE recording_id = ${activityId}::uuid
    `.execute(db);
    assert.equal(pointCount.rows[0]?.total, 0, 'deleting a recording must cascade to exact GPS points');
  } finally {
    await app.close();
    await sql`DELETE FROM route_tracks WHERE id = ${trackId}::uuid`.execute(db).catch(() => undefined);
    await sql`DELETE FROM routes WHERE id = ${routeId}::uuid`.execute(db).catch(() => undefined);
    await sql`DELETE FROM users WHERE id IN (${userId}::uuid, ${otherUserId}::uuid)`.execute(db).catch(() => undefined);
    await db.destroy();
  }
});
