import { expect, test, type Page } from '@playwright/test';

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
    short_description: 'Ruta real de prueba para clima dinámico.',
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

const weatherResponse = {
  weather: {
    provider: 'Open-Meteo',
    condition: 'rain',
    intensity: 2,
    temperatureC: 17,
    feelsLikeC: 16,
    windSpeedKmh: 22,
    windDirectionDeg: 220,
    windGustsKmh: 34,
    precipitationMm: 1.8,
    visibilityM: 9000,
    cloudCoverPercent: 92,
    weatherCode: 63,
    dayPhase: 'day',
    observedAt: '2026-09-15T18:00',
    expiresAt: '2026-09-15T18:15:00.000Z',
    officialAlert: null,
    stale: false,
  },
  cache_status: 'refreshed',
  fetched_at: '2026-09-15T18:00:00.000Z',
};

async function mockRoute(page: Page) {
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
}

async function mockWeather(page: Page, onRequest?: (payload: Record<string, unknown>) => void) {
  await page.route('**/api/v1/public/weather/current', async (route) => {
    const payload = route.request().postDataJSON() as Record<string, unknown>;
    onRequest?.(payload);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(weatherResponse) });
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    body: document.body.scrollWidth,
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  expect(Math.max(dimensions.body, dimensions.document)).toBeLessThanOrEqual(dimensions.viewport + 1);
}

test('Preparar aventura requests local weather only after explicit user action', async ({ page, context }) => {
  await context.grantPermissions(['geolocation']);
  await context.setGeolocation({ latitude: 37.73, longitude: -3.41 });
  await mockRoute(page);
  let weatherCalls = 0;
  await mockWeather(page, (payload) => {
    weatherCalls += 1;
    expect(payload.latitude).toBeCloseTo(37.73, 2);
    expect(payload.longitude).toBeCloseTo(-3.41, 2);
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura/preparar?slug=ruta-demo');

  await expect(page.getByRole('heading', { level: 1, name: 'Preparar aventura' })).toBeVisible();
  await expect(page.getByText('Ruta Demo de Mágina')).toBeVisible();
  expect(weatherCalls).toBe(0);

  await page.getByRole('button', { name: /Comprobar clima local/ }).click();

  await expect.poll(() => weatherCalls).toBe(1);
  await expect(page.getByText('17 °C', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Lluvia moderada', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('link', { name: /Comenzar aventura/ })).toHaveAttribute('href', /\/aventura\/en-curso\/?\?slug=ruta-demo$/);
  await expectNoHorizontalOverflow(page);
});

test('Aventura en curso renders weather HUD and an isolated decorative WeatherScene', async ({ page, context }) => {
  await context.grantPermissions(['geolocation']);
  await context.setGeolocation({ latitude: 37.73, longitude: -3.41 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRoute(page);
  await mockWeather(page);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura/en-curso?slug=ruta-demo');
  await page.getByRole('button', { name: /Activar clima local/ }).click();

  await expect(page.getByRole('heading', { name: 'Clima local' })).toBeVisible();
  await expect(page.getByText('17 °C', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Lluvia moderada', { exact: true }).first()).toBeVisible();

  const scene = page.locator('[data-weather-scene="true"]');
  await expect(scene).toHaveCount(1);
  await expect(scene).toHaveAttribute('aria-hidden', 'true');
  await expect(scene).toHaveAttribute('data-weather-condition', 'rain');
  await expect(scene).toHaveAttribute('data-weather-intensity', '2');
  await expect(scene.locator('button,a,input,select,textarea,[tabindex]')).toHaveCount(0);
  await expect(scene.getByRole('heading')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('local weather lab can simulate effects without requesting GPS weather', async ({ page }) => {
  await mockRoute(page);
  let weatherCalls = 0;
  await page.route('**/api/v1/public/weather/current', async (route) => {
    weatherCalls += 1;
    await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ error: 'should_not_be_called' }) });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura/en-curso?slug=ruta-demo&weatherLab=1');

  await expect(page.getByText('Laboratorio de clima')).toBeVisible();
  await page.getByRole('button', { name: 'Lluvia intensa' }).click();
  await expect(page.locator('[data-weather-condition="rain"][data-weather-intensity="3"]')).toHaveCount(1);
  expect(weatherCalls).toBe(0);
  await expectNoHorizontalOverflow(page);
});
