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

test('Aventura en curso exposes one live hiking HUD fed by the active GPS recorder', async ({ page }) => {
  const routeId = '22222222-2222-4222-8222-222222222222';
  const activityId = '33333333-3333-4333-8333-333333333333';
  const checkpointId = '44444444-4444-4444-8444-444444444444';
  const routeDetail = {
    route: {
      id: routeId,
      slug: 'bedmar-piloto',
      name: 'Ruta piloto de Bedmar',
      route_type: 'hiking',
      difficulty: 'moderate',
      distance_m: 6400,
      duration_minutes: 155,
      elevation_gain_m: 410,
      elevation_loss_m: 410,
      min_altitude_m: 710,
      max_altitude_m: 1080,
      circular: true,
      family_friendly: false,
      short_description: 'Vertical de prueba para el contrato de senderismo de Mágina Aventura.',
      description: null,
      safety_notes: 'Mantén el track validado como referencia principal.',
      access_notes: null,
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
    track: {
      id: '55555555-5555-4555-8555-555555555555',
      geometry: {
        type: 'LineString',
        coordinates: [
          [-3.4128, 37.7231],
          [-3.4114, 37.7244],
          [-3.4098, 37.7255],
        ],
      },
    },
    elevation: [],
    points: [],
    media: [],
    sources: [],
    segments: [],
    related: [],
  };
  const adventure = {
    enabled: true,
    route: { id: routeId, slug: 'bedmar-piloto', name: 'Ruta piloto de Bedmar' },
    adventure: {
      title: 'Guardianes del olivar',
      intro: 'Sigue el track real y descubre el territorio.',
      completion_message: 'Aventura completada.',
      progression_mode: 'linear',
    },
    checkpoints: [
      {
        id: checkpointId,
        route_point_id: null,
        title: 'Mirador del olivar',
        description: 'Primer descubrimiento del piloto.',
        kind: 'landmark',
        collection_category: 'landscape',
        rarity: 'common',
        distance_m: 500,
        unlock_radius_m: 40,
        points: 50,
        is_required: true,
        question: null,
        answer_options: null,
        hint: null,
        sort_order: 1,
        latitude: 37.7244,
        longitude: -3.4114,
      },
    ],
    notice: 'La aventura no sustituye navegación ni seguridad.',
  };
  const progress = {
    run: {
      id: '66666666-6666-4666-8666-666666666666',
      route_id: routeId,
      status: 'active',
      score: 0,
      started_at: '2026-09-15T08:00:00.000Z',
      completed_at: null,
      updated_at: '2026-09-15T08:00:00.000Z',
      last_distance_m: null,
    },
    stats: {
      total_checkpoints: 1,
      required_checkpoints: 1,
      total_points: 50,
      unlocked_checkpoints: 0,
      required_unlocked: 0,
      required_remaining: 1,
    },
    unlocks: [],
    badges: [],
  };
  const activity = {
    id: activityId,
    user_id: '77777777-7777-4777-8777-777777777777',
    route_id: routeId,
    route_slug: 'bedmar-piloto',
    route_name: 'Ruta piloto de Bedmar',
    status: 'recording',
    visibility: 'private',
    current_segment: 1,
    started_at: '2026-09-15T08:00:00.000Z',
    completed_at: null,
    distance_m: 0,
    elevation_gain_m: 0,
    duration_seconds: 0,
    points_count: 0,
    updated_at: '2026-09-15T08:00:00.000Z',
  };

  await page.addInitScript(() => {
    let watchId = 0;
    const position = {
      timestamp: Date.now(),
      coords: {
        latitude: 37.7235,
        longitude: -3.4123,
        accuracy: 7,
        altitude: 820,
        altitudeAccuracy: 9,
        heading: null,
        speed: null,
      },
    };
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: {
        watchPosition(success: PositionCallback) {
          watchId += 1;
          window.setTimeout(() => success(position as GeolocationPosition), 20);
          return watchId;
        },
        clearWatch() {},
        getCurrentPosition(success: PositionCallback) {
          window.setTimeout(() => success(position as GeolocationPosition), 0);
        },
      },
    });
  });

  await page.route('**/api/v1/public/routes/bedmar-piloto', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(routeDetail) });
  });
  await page.route('**/api/v1/public/routes/bedmar-piloto/adventure', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(adventure) });
  });
  await page.route(`**/api/v1/routes/${routeId}/adventure/progress`, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(progress) });
  });
  await page.route('**/api/v1/activities/active', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ activity }) });
  });
  await page.route(`**/api/v1/activities/${activityId}/points`, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accepted: 1, ignored_duplicates: 0 }) });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura/en-curso?slug=bedmar-piloto');

  const hud = page.getByTestId('live-adventure-hud');
  await expect(hud).toBeVisible();
  await expect(hud.getByText('GPS activo')).toBeVisible();
  await expect(hud.getByText('±7 m')).toBeVisible();
  await expect(hud.getByText('Distancia recorrida')).toBeVisible();

  const nextCheckpoint = page.getByTestId('next-checkpoint-card');
  await expect(nextCheckpoint).toContainText('Mirador del olivar');
  await expect(page.getByRole('button', { name: 'Centrarme' })).toBeVisible();
  await expect(page.getByText('Mantén el track validado como referencia principal.')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
