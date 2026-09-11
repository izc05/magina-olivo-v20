import { expect, test, type Page } from '@playwright/test';

const mills = {
  entries: [
    {
      id: '31000000-0000-4000-8000-000000000001',
      type: 'mill',
      slug: 'cooperativa-bedmar-e2e',
      title: 'Cooperativa del Olivar E2E',
      summary: 'Recepción de aceituna y servicios para socios.',
      content_json: {
        body: 'Entidad de prueba publicada desde el CMS para validar el directorio público.',
        location: 'Zona cooperativa',
        town: 'Bedmar',
        phone: '953 00 00 01',
        address: 'Avenida del Olivar 1',
        cta_label: 'Visitar web',
      },
      featured: true,
      starts_at: null,
      ends_at: null,
      media_url: null,
      external_url: 'https://example.com/cooperativa',
      sort_order: 0,
      updated_at: '2026-09-11T18:00:00.000Z',
      published_at: '2026-09-11T18:00:00.000Z',
    },
    {
      id: '31000000-0000-4000-8000-000000000002',
      type: 'mill',
      slug: 'almazara-jodar-e2e',
      title: 'Almazara Sierra E2E',
      summary: 'Molturación y atención durante la campaña.',
      content_json: {
        body: 'Almazara de prueba para validar búsqueda y alias público.',
        town: 'Jódar',
        phone: '953 00 00 02',
        address: 'Camino de la Campaña 2',
      },
      featured: false,
      starts_at: null,
      ends_at: null,
      media_url: null,
      external_url: null,
      sort_order: 1,
      updated_at: '2026-09-11T18:00:00.000Z',
      published_at: '2026-09-11T18:00:00.000Z',
    },
  ],
};

async function mockMills(page: Page) {
  await page.route(/\/api\/v1\/public\/content\?type=mill$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mills) });
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

test('cooperatives directory searches and opens a published CMS record', async ({ page }) => {
  await mockMills(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/cooperativas');

  await expect(page.getByRole('heading', { name: 'Cooperativas y almazaras' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toBeVisible();

  await page.getByRole('searchbox', { name: 'Buscar por nombre, pueblo o dirección' }).fill('Bedmar');
  await expect(page.getByText('1 resultado')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toHaveCount(0);

  await page.getByRole('link', { name: 'Ver ficha →' }).click();
  await expect(page).toHaveURL(/\/cooperativas\/?\?slug=cooperativa-bedmar-e2e$/);
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByText('Avenida del Olivar 1')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Llamar' })).toHaveAttribute('href', 'tel:953000001');
  await expect(page.getByRole('link', { name: 'Visitar web ↗' })).toHaveAttribute('href', 'https://example.com/cooperativa');
  await expectNoHorizontalOverflow(page);
});

test('almazaras alias uses the same real CMS contract and preserves its route', async ({ page }) => {
  await mockMills(page);
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto('/almazaras?slug=almazara-jodar-e2e');

  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toBeVisible();
  await expect(page.getByText('Jódar', { exact: true })).toBeVisible();
  await expect(page.getByText('Camino de la Campaña 2')).toBeVisible();
  await expect(page.getByRole('link', { name: '← Cooperativas y almazaras' })).toHaveAttribute('href', '/almazaras');
  await expectNoHorizontalOverflow(page);
});

for (const width of [360, 390, 430]) {
  test(`cooperatives directory does not overflow at ${width}px`, async ({ page }) => {
    await mockMills(page);
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/cooperativas');
    await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}
