import { expect, test, type Page } from '@playwright/test';

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    body: document.body.scrollWidth,
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  expect(Math.max(dimensions.body, dimensions.document)).toBeLessThanOrEqual(dimensions.viewport + 1);
}

test('Mágina Aventura is discoverable from Explore on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/explorar');

  const adventure = page.getByRole('link', { name: /Mágina Aventura/ });
  await expect(adventure).toBeVisible();
  await expect(adventure).toHaveAttribute('href', /^\/aventura\/?$/);
  await expectNoHorizontalOverflow(page);
});

test('Mágina Aventura renders its public mobile hub without fictional fallback data', async ({ page }) => {
  await page.route('**/api/v1/public/adventures', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ adventures: [], notice: 'Catálogo conectado a rutas reales.' }),
    });
  });
  await page.route('**/api/v1/adventures/me', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'authentication_required' }) });
  });
  await page.route('**/api/v1/activities/active', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'authentication_required' }) });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura');

  await expect(page.getByRole('heading', { level: 1, name: /Mágina Aventura/ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver aventuras' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver rutas' })).toHaveAttribute('href', /^\/rutas\/?$/);
  await expect(page.getByText('Las primeras aventuras están en preparación')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('Aventura en curso has a dedicated mobile expedition screen', async ({ page }) => {
  const routeDetail = {
    route: {
      id: '22222222-2222-4222-8222-222222222222',
      slug: 'ruta-demo',
      name: 'Ruta Demo de Mágina',
      route_type: 'hiking',
      difficulty: 'moderate',
      distance_m: 7200,
      duration_minutes: 180,
      elevation_gain_m: 540,
      elevation_loss_m: 540,
      min_altitude_m: 780,
      max_altitude_m: 1320,
      circular: true,
      family_friendly: false,
      short_description: 'Ruta real de prueba para la experiencia móvil de Mágina Aventura.',
      description: null,
      safety_notes: 'Revisa meteorología y lleva agua suficiente.',
      access_notes: 'Acceso por sendero señalizado.',
      water_notes: null,
      shade_level: null,
      mobile_coverage: null,
      recommended_seasons: ['spring'],
      restrictions: null,
      source_summary: null,
      last_verified_at: null,
      municipality_id: null,
      municipality_name: 'Bedmar y Garcíez',
      place_name: null,
    },
    track: null,
    elevation: [],
    points: [],
    media: [],
    sources: [],
    segments: [],
    related: [],
  };

  await page.route('**/api/v1/public/routes/ruta-demo', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(routeDetail) });
  });
  await page.route('**/api/v1/public/routes/ruta-demo/adventure', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: false, route: { id: routeDetail.route.id, slug: 'ruta-demo', name: routeDetail.route.name }, adventure: null, checkpoints: [] }),
    });
  });
  await page.route('**/api/v1/activities/active', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'authentication_required' }) });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura/en-curso?slug=ruta-demo');

  await expect(page.getByRole('heading', { level: 1, name: 'Ruta Demo de Mágina' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Mapa de expedición' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Grabar recorrido' })).toBeVisible();
  await expect(page.getByText('Revisa meteorología y lleva agua suficiente.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ficha completa', exact: true })).toHaveAttribute('href', /\/rutas\/detalle\/?\?slug=ruta-demo$/);
  await expectNoHorizontalOverflow(page);
});
