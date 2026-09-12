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
  await expect(page.getByRole('link', { name: /Servicios/ })).toHaveAttribute('href', /^\/servicios\/?$/);
  await expect(page.getByRole('link', { name: /Consejos del campo/ })).toHaveAttribute('href', /^\/consejos\/?$/);

  const pending = page.locator('article').filter({ hasText: 'Rutas y experiencias' });
  await expect(pending).toContainText('En preparación');
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
