import { buildApp } from '../apps/api/dist/app.js';
import { createDatabase } from '../apps/api/dist/db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
process.env.ALLOW_DEV_AUTH_HEADERS = 'true';
process.env.NODE_ENV = 'test';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });
const userId = '73000000-0000-4000-8000-000000000001';
const businessId = '73000000-0000-4000-8000-000000000002';
const workspaceId = '73000000-0000-4000-8000-000000000099';
const headers = { 'x-user-id': userId, 'x-workspace-id': workspaceId, 'content-type': 'application/json' };

function expectStatus(response, expected, label) {
  if (response.statusCode !== expected) throw new Error(`${label}: expected ${expected}, got ${response.statusCode}: ${response.body}`);
}

try {
  await db.insertInto('users').values({ id:userId, primary_email:'owner-management@example.com', display_name:'Owner management', status:'active' }).execute();
  await db.insertInto('businesses').values({ id:businessId, slug:'experiencias-management-ci', name:'Experiencias Management CI', status:'published', verification_status:'verified', published_at:new Date() }).execute();
  await db.insertInto('business_memberships').values({ business_id:businessId, user_id:userId, role:'owner', status:'active' }).execute();
  await app.ready();

  const created = await app.inject({ method:'POST', url:`/api/v1/my/businesses/${businessId}/experiences`, headers, payload:{ slug:'cata-management-ci', title:'Cata Management CI', experienceType:'aove_tasting', minPartySize:1, maxPartySize:8, priceCents:1500, bookingMode:'request', status:'published' } });
  expectStatus(created, 201, 'create experience');
  const experienceId = created.json().experience.id;
  const startsAt = new Date(Date.now() + 2 * 86400000).toISOString();
  const slotResponse = await app.inject({ method:'POST', url:`/api/v1/my/businesses/${businessId}/experiences/${experienceId}/slots`, headers, payload:{ startsAt, capacity:4, status:'open' } });
  expectStatus(slotResponse, 201, 'create slot');
  const slotId = slotResponse.json().slot.id;

  const createBooking = (name, email, partySize) => app.inject({ method:'POST', url:'/api/v1/public/businesses/experiencias-management-ci/experiences/cata-management-ci/bookings', payload:{ slotId, contactName:name, contactEmail:email, partySize, consentBusinessContact:true } });
  const first = await createBooking('Grupo Tres', 'tres@example.com', 3);
  const second = await createBooking('Grupo Dos', 'dos@example.com', 2);
  expectStatus(first, 201, 'first booking'); expectStatus(second, 201, 'second booking');
  const firstId = first.json().booking.id; const secondId = second.json().booking.id;

  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-bookings/${firstId}`, headers, payload:{status:'confirmed'} }), 200, 'confirm first booking');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-bookings/${secondId}`, headers, payload:{status:'confirmed'} }), 409, 'prevent over-capacity confirmation');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-slots/${slotId}`, headers, payload:{capacity:2} }), 409, 'prevent capacity below confirmed');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-slots/${slotId}`, headers, payload:{capacity:5} }), 200, 'increase capacity');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-bookings/${secondId}`, headers, payload:{status:'confirmed'} }), 200, 'confirm second after capacity increase');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experiences/${experienceId}`, headers, payload:{title:'Cata Management Actualizada',priceCents:1750} }), 200, 'edit experience');

  const row = await db
    .selectFrom('business_experience_slots as s')
    .innerJoin('business_experiences as e', 'e.id', 's.experience_id')
    .select(['s.confirmed_count', 's.capacity', 'e.title', 'e.price_cents'])
    .where('s.id', '=', slotId)
    .executeTakeFirst();
  if (!row || row.confirmed_count !== 5 || row.capacity !== 5 || row.title !== 'Cata Management Actualizada' || row.price_cents !== 1750) throw new Error(`unexpected state: ${JSON.stringify(row)}`);

  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-slots/${slotId}`, headers, payload:{status:'cancelled'} }), 409, 'prevent cancelling occupied slot');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-bookings/${firstId}`, headers, payload:{status:'cancelled'} }), 200, 'cancel first booking');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-bookings/${secondId}`, headers, payload:{status:'cancelled'} }), 200, 'cancel second booking');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experience-slots/${slotId}`, headers, payload:{status:'cancelled'} }), 200, 'cancel empty slot');
  expectStatus(await app.inject({ method:'PATCH', url:`/api/v1/my/businesses/${businessId}/experiences/${experienceId}`, headers, payload:{status:'archived'} }), 200, 'archive experience');
  const publicList = await app.inject({ method:'GET', url:'/api/v1/public/experiences?businessSlug=experiencias-management-ci' });
  expectStatus(publicList, 200, 'public list after archive');
  if (publicList.json().experiences.length !== 0) throw new Error('archived experience is still public');

  console.log('Business experience management smoke: OK');
} finally {
  await app.close().catch(() => undefined);
  await db.destroy().catch(() => undefined);
}
