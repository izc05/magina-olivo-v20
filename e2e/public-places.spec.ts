import { expect, test, type Page } from '@playwright/test';

const places = {
  entries: [
    {
      id: '47000000-0000-4000-8000-000000000001',
      type: 'place',
      slug: 'bedmar-e2e',
      title: 'Bedmar E2E',
      summary: 'Un pueblo de Sierra Mágina entre olivos y montaña.',
      content_json: {
        body: 'Ficha territorial de prueba publicada desde el CMS para validar la experiencia pública de pueblos.',
        location: 'Sierra Mágina',
        town: 'Bedmar',
        address: 'Plaza de la Constitución',
        cta_label: 'Web municipal',
      },
      featured: true,
      starts_at: null,
      ends_at: null,
      media_url: null,
      external_url: 'https://example.com/bedmar',
      sort_order: 0,
      updated_at: '2026-09-11T20:00:00.000Z',
      published_at: '2026-09-11T20:00:00.000Z',
    },
    {
      id: '47000000-0000-4000-8000-000000000002',
      type: 'place',
      slug: 'jimena-e2e',
      title: 'Jimena E2E',
      summary: 'Localidad de prueba para validar búsqueda y seguridad de enlaces.',
      content_json: {
        body: 'Segunda ficha territorial publicada para la prueba.',
        location: 'Campiña de Mágina',
        town: 'Jimena',
        address: 'Centro urbano',
        cta_label: 'Enlace no seguro',
      },
      featured: false,
      starts_at: null,
      ends_at: null,
      media_url: null,
      external_url: 'javascript:alert(1)',
      sort_order: 1,
      updated_at: '2026-09-11T20:00:00.000Z',
      published_at: '2026-09-11T20:00:00.000Z',
    },
  ],
};

async function mockPlaces(page: Page) {
  await page.route(/\/api\/v1\/public\/content\?type=place$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(places) });
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

test('pueblos searches and opens a published CMS place', async ({ page }) => {
  await mockPlaces(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/pueblos');

  await expect(page.getByRole('heading', { name: 'Pueblos y lugares', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Bedmar E2E', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Jimena E2E', exact: true })).toBeVisible();

  await page.getByRole('searchbox', { name: 'Buscar por nombre, localidad o zona' }).fill('Bedmar');
  await expect(page.getByText('1 resultado', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Bedmar E2E', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Jimena E2E', exact: true })).toHaveCount(0);

  await page.getByRole('link', { name: 'Descubrir →' }).click();
  await expect(page).toHaveURL(/\/pueblos\/?\?slug=bedmar-e2e$/);
  await expect(page.getByRole('heading', { name: 'Bedmar E2E', exact: true })).toBeVisible();
  await expect(page.locator('dl').getByText('Bedmar', { exact: true })).toBeVisible();
  await expect(page.locator('dl').getByText('Sierra Mágina', { exact: true })).toBeVisible();
  await expect(page.getByText('Plaza de la Constitución', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Web municipal ↗' })).toHaveAttribute('href', 'https://example.com/bedmar');
  await expectNoHorizontalOverflow(page);
});

test('pueblos does not render unsafe external protocols', async ({ page }) => {
  await mockPlaces(page);
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto('/pueblos?slug=jimena-e2e');

  await expect(page.getByRole('heading', { name: 'Jimena E2E', exact: true })).toBeVisible();
  await expect(page.locator('dl').getByText('Jimena', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /Enlace no seguro/ })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('pueblos shows a truthful missing-place state', async ({ page }) => {
  await mockPlaces(page);
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/pueblos?slug=no-existe');

  await expect(page.getByText('No encontramos este pueblo o lugar', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Volver a pueblos' })).toHaveAttribute('href', /^\/pueblos\/?$/);
  await expectNoHorizontalOverflow(page);
});

for (const width of [360, 390, 430]) {
  test(`pueblos does not overflow at ${width}px`, async ({ page }) => {
    await mockPlaces(page);
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/pueblos');
    await expect(page.getByRole('heading', { name: 'Bedmar E2E', exact: true })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}
