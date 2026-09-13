import { expect, test, type Page } from '@playwright/test';

const news = {
  entries: [{
    id: '10000000-0000-4000-8000-000000000001',
    type: 'news',
    slug: 'campana-aceituna-maginas',
    title: 'La campaña de aceituna encara su tramo principal',
    summary: 'Cooperativas y agricultores preparan las próximas semanas de recolección.',
    content_json: {
      body: 'La actividad aumenta en los municipios de Sierra Mágina.\n\nLa recomendación es revisar accesos y documentación antes de la entrega.',
      town: 'Bedmar',
    },
    featured: true,
    starts_at: null,
    ends_at: null,
    media_url: null,
    external_url: null,
    sort_order: 0,
    published_at: '2026-09-11T18:00:00.000Z',
  }],
};

const events = {
  entries: [{
    id: '20000000-0000-4000-8000-000000000002',
    type: 'event',
    slug: 'jornada-olivar-jodar',
    title: 'Jornada práctica de olivar en Jódar',
    summary: 'Encuentro abierto sobre manejo y preparación de campaña.',
    content_json: {
      body: 'Una jornada pensada para agricultores y profesionales del territorio.',
      location: 'Casa de la Cultura',
      town: 'Jódar',
      event_start: '2026-10-03T10:00:00+02:00',
      event_end: '2026-10-03T13:30:00+02:00',
    },
    featured: false,
    starts_at: null,
    ends_at: null,
    media_url: null,
    external_url: 'https://example.com/jornada',
    sort_order: 0,
    published_at: '2026-09-11T18:00:00.000Z',
  }],
};

async function mockEditorial(page: Page) {
  await page.route(/\/api\/v1\/public\/content\?type=news$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(news) });
  });
  await page.route(/\/api\/v1\/public\/content\?type=event$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(events) });
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }));
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
}

test('news list and detail use published CMS content', async ({ page }) => {
  await mockEditorial(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/noticias');

  await expect(page.getByRole('heading', { name: 'Noticias de Mágina' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'La campaña de aceituna encara su tramo principal' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Bedmar.*La campaña de aceituna encara su tramo principal/ })).toBeVisible();

  await page.getByPlaceholder('Buscar noticias…').fill('cooperativas');
  await expect(page.getByText('1 resultado')).toBeVisible();

  await page.getByRole('heading', { name: 'La campaña de aceituna encara su tramo principal' }).click();
  await expect(page).toHaveURL(/\/noticias\/?\?slug=campana-aceituna-maginas$/);
  await expect(page.getByText('La recomendación es revisar accesos y documentación antes de la entrega.')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('event detail shows agenda information and remains usable on narrow screens', async ({ page }) => {
  await mockEditorial(page);
  await page.setViewportSize({ width: 360, height: 844 });
  await page.goto('/eventos?slug=jornada-olivar-jodar');

  await expect(page.getByRole('heading', { name: 'Eventos en Mágina' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Jornada práctica de olivar en Jódar' })).toBeVisible();
  await expect(page.getByText('Casa de la Cultura', { exact: true })).toBeVisible();
  await expect(page.getByText('Jódar', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Más información ↗' })).toHaveAttribute('href', 'https://example.com/jornada');
  await expectNoHorizontalOverflow(page);
});

test('Explore links to public modules that are already available', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/explorar');

  await expect(page.getByRole('link', { name: /Noticias/ })).toHaveAttribute('href', /^\/noticias\/?$/);
  await expect(page.getByRole('link', { name: /Eventos/ })).toHaveAttribute('href', /^\/eventos\/?$/);
  await expect(page.getByRole('link', { name: /Aceite y mercado/ })).toHaveAttribute('href', /^\/mercado\/?$/);
  await expect(page.getByRole('link', { name: /Almazaras y cooperativas/ })).toHaveAttribute('href', /^\/cooperativas\/?$/);
  await expect(page.getByRole('link', { name: /Empresas y servicios/ })).toHaveAttribute('href', /^\/explorar\/empresas\/?$/);
  await expect(page.getByRole('link', { name: /Rutas y experiencias/ })).toHaveAttribute('href', /^\/rutas\/?$/);
  await expect(page.getByRole('link', { name: /Consejos del campo/ })).toHaveAttribute('href', /^\/consejos\/?$/);
  await expectNoHorizontalOverflow(page);
});

test('town hub connects territory with routes businesses editorial mills and verified official links', async ({ page }) => {
  const placeId = '30000000-0000-4000-8000-000000000001';
  const municipalityId = '30000000-0000-4000-8000-000000000002';
  await page.route(/\/api\/v1\/public\/territory\/places\/bedmar$/, async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ place: { id: placeId, name: 'Bedmar', slug: 'bedmar', kind: 'municipal_seat', hero_asset_key: null, center: null, municipality_id: municipalityId, municipality_name: 'Bedmar y Garcíez', municipality_slug: 'bedmar-y-garciez', ine_code: '23902', aemet_code: '23902', province_name: 'Jaén', official_links: [{ id: '70000000-0000-4000-8000-000000000001', kind: 'town_hall', label: 'Ayuntamiento de Bedmar y Garcíez', url: 'https://www.bedmargarciez.es/', source_url: 'https://www.bedmargarciez.es/aviso-legal/', verified_at: '2026-09-13T12:00:00.000Z' }, { id: '70000000-0000-4000-8000-000000000002', kind: 'electronic_office', label: 'Sede electrónica de Bedmar y Garcíez', url: 'https://bedmargarciez.sedelectronica.es/info.0', source_url: 'https://bedmargarciez.sedelectronica.es/info.0', verified_at: '2026-09-13T12:00:00.000Z' }] } }),
  }));
  await page.route(/\/api\/v1\/public\/routes\?.*municipality_id=/, async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ routes: [{ id: '40000000-0000-4000-8000-000000000001', slug: 'sendero-bedmar-ci', name: 'Sendero de Bedmar CI', route_type: 'hiking', difficulty: 'easy', distance_m: 4200, duration_minutes: 90, elevation_gain_m: 180, elevation_loss_m: 180, circular: true, family_friendly: true, short_description: 'Ruta validada de prueba.', municipality_name: 'Bedmar y Garcíez', place_name: 'Bedmar', hero_url: null }] }),
  }));
  await page.route(/\/api\/v1\/public\/businesses\?.*municipalityId=/, async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ businesses: [{ id: '50000000-0000-4000-8000-000000000001', slug: 'aceite-bedmar-ci', name: 'Aceite Bedmar CI', shortDescription: 'Productor local publicado.', territory: { municipalityId, municipalityName: 'Bedmar y Garcíez', municipalitySlug: 'bedmar-y-garciez', placeId, placeName: 'Bedmar', placeSlug: 'bedmar' }, address: null, location: null, distanceKm: null, categories: [{ slug: 'aove', name: 'AOVE', primary: true }], verificationStatus: 'verified', verified: true, placement: { label: null, sponsored: false, featured: false }, logoUrl: null, coverImageUrl: null }], meta: { limit: 8, offset: 0, count: 1, sponsoredDisclosure: '' } }),
  }));
  await page.route(/\/api\/v1\/public\/content\?type=news$/, async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(news) }));
  await page.route(/\/api\/v1\/public\/content\?type=event$/, async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ entries: [{ ...events.entries[0], id: '20000000-0000-4000-8000-000000000003', slug: 'fiesta-bedmar-ci', title: 'Fiesta de Bedmar CI', content_json: { ...events.entries[0].content_json, town: 'Bedmar' } }] }) }));
  await page.route(/\/api\/v1\/public\/content\?type=mill$/, async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ entries: [{ id: '60000000-0000-4000-8000-000000000001', type: 'mill', slug: 'cooperativa-bedmar-ci', title: 'Cooperativa Bedmar CI', summary: 'Entidad oleícola local.', content_json: { town: 'Bedmar' }, featured: false, media_url: null, external_url: null, sort_order: 0, updated_at: '2026-09-13T12:00:00.000Z', published_at: '2026-09-13T12:00:00.000Z' }] }) }));

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/pueblos/territorio?slug=bedmar');

  await expect(page.getByRole('heading', { level: 1, name: 'Bedmar' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ayuntamiento y servicios públicos' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Ayuntamiento de Bedmar y Garcíez/ })).toHaveAttribute('href', 'https://www.bedmargarciez.es/');
  await expect(page.getByRole('link', { name: /Sede electrónica de Bedmar y Garcíez/ })).toHaveAttribute('href', 'https://bedmargarciez.sedelectronica.es/info.0');
  await expect(page.getByRole('heading', { name: 'Rutas y experiencias' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Sendero de Bedmar CI/ })).toHaveAttribute('href', /fromPlace=bedmar/);
  await expect(page.getByRole('link', { name: /Aceite Bedmar CI/ })).toHaveAttribute('href', /fromPlace=bedmar/);
  await expect(page.getByRole('link', { name: /Fiesta de Bedmar CI/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /La campaña de aceituna encara su tramo principal/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /Cooperativa Bedmar CI/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

for (const width of [360, 390, 430]) {
  test(`public editorial lists do not overflow at ${width}px`, async ({ page }) => {
    await mockEditorial(page);
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/noticias');
    await expect(page.getByRole('heading', { name: 'La campaña de aceituna encara su tramo principal' })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}
