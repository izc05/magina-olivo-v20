import { expect, test, type Page } from '@playwright/test';

const directory = {
  entries: [
    {
      id: '51000000-0000-4000-8000-000000000001',
      type: 'directory',
      slug: 'taller-agricola-bedmar-e2e',
      title: 'Taller Agrícola E2E',
      summary: 'Maquinaria, mantenimiento y reparación para el trabajo agrícola.',
      content_json: {
        territory_place_id: '51000000-0000-4000-8000-000000000101',
        territory_place_name: 'Bedmar',
        territory_place_slug: 'bedmar',
        municipality_id: '51000000-0000-4000-8000-000000000201',
        municipality_name: 'Bedmar y Garcíez',
        body: 'Servicio local de prueba publicado desde el directorio de Mágina Olivo.',
        phone: '+34 953 00 11 22',
        email: 'taller@example.com',
        address: 'Polígono Agrícola, nave 4',
        services: ['Maquinaria agrícola', 'Reparaciones'],
        opening_hours: 'L-V · 08:00–18:00',
        instagram: 'https://instagram.com/example',
        facebook: 'https://facebook.com/example',
        campaign_notes: 'Horario ampliado en campaña.',
        coordinates: { latitude: 37.82, longitude: -3.41 },
        cta_label: 'Visitar web',
      },
      featured: true,
      starts_at: null,
      ends_at: null,
      media_url: null,
      external_url: 'https://example.com/taller',
      sort_order: 0,
      updated_at: '2026-09-11T21:00:00.000Z',
      published_at: '2026-09-11T21:00:00.000Z',
    },
    {
      id: '51000000-0000-4000-8000-000000000002',
      type: 'directory',
      slug: 'servicio-jimena-e2e',
      title: 'Servicio Jimena E2E',
      summary: 'Ficha de prueba con contactos no válidos.',
      content_json: {
        territory_place_id: '51000000-0000-4000-8000-000000000102',
        territory_place_name: 'Jimena',
        territory_place_slug: 'jimena',
        municipality_id: '51000000-0000-4000-8000-000000000202',
        municipality_name: 'Jimena',
        body: 'Segunda ficha del directorio.',
        phone: 'abc',
        email: 'not-an-email',
        address: 'Centro urbano',
        services: ['Asistencia local'],
        opening_hours: '',
        instagram: 'data:text/html,unsafe',
        facebook: 'javascript:alert(1)',
        campaign_notes: '',
        coordinates: { latitude: 999, longitude: -3.5 },
        cta_label: 'Enlace inseguro',
      },
      featured: false,
      starts_at: null,
      ends_at: null,
      media_url: '//evil.example/service.jpg',
      external_url: 'javascript:alert(1)',
      sort_order: 1,
      updated_at: '2026-09-11T21:00:00.000Z',
      published_at: '2026-09-11T21:00:00.000Z',
    },
  ],
};

async function mockDirectory(page: Page) {
  await page.route(/\/api\/v1\/public\/content\?type=directory$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(directory) });
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

test('servicios searches and opens a published directory entry', async ({ page }) => {
  await mockDirectory(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/servicios');

  await expect(page.getByRole('heading', { name: 'Servicios locales', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Taller Agrícola E2E', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Servicio Jimena E2E', exact: true })).toBeVisible();

  await page.getByRole('searchbox', { name: 'Buscar empresa, servicio, pueblo o municipio' }).fill('Reparaciones');
  await expect(page.getByText('1 resultado', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Taller Agrícola E2E', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Servicio Jimena E2E', exact: true })).toHaveCount(0);

  await page.getByRole('link', { name: 'Ver servicio →' }).click();
  await expect(page).toHaveURL(/\/servicios\/?\?slug=taller-agricola-bedmar-e2e$/);
  await expect(page.getByRole('heading', { name: 'Taller Agrícola E2E', exact: true })).toBeVisible();
  await expect(page.getByText('Maquinaria agrícola', { exact: true })).toBeVisible();
  await expect(page.getByText('Reparaciones', { exact: true })).toBeVisible();
  await expect(page.locator('dl').getByText('Bedmar', { exact: true })).toBeVisible();
  await expect(page.locator('dl').getByText('Bedmar y Garcíez', { exact: true })).toBeVisible();
  await expect(page.getByText('Horario ampliado en campaña.', { exact: true })).toBeVisible();

  await expect(page.getByRole('link', { name: /Llamar/ })).toHaveAttribute('href', 'tel:+34953001122');
  await expect(page.getByRole('link', { name: /Email/ })).toHaveAttribute('href', 'mailto:taller@example.com');
  await expect(page.getByRole('link', { name: 'Visitar web ↗' })).toHaveAttribute('href', 'https://example.com/taller');
  await expect(page.getByRole('link', { name: 'Instagram ↗' })).toHaveAttribute('href', 'https://instagram.com/example');
  await expect(page.getByRole('link', { name: 'Facebook ↗' })).toHaveAttribute('href', 'https://facebook.com/example');
  await expectNoHorizontalOverflow(page);
});

test('servicios rejects unsafe contacts and media', async ({ page }) => {
  await mockDirectory(page);
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto('/servicios?slug=servicio-jimena-e2e');

  await expect(page.getByRole('heading', { name: 'Servicio Jimena E2E', exact: true })).toBeVisible();
  await expect(page.getByText('Asistencia local', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /Llamar/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Email/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Enlace inseguro/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Instagram/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Facebook/ })).toHaveCount(0);
  await expect(page.locator('img[src="//evil.example/service.jpg"]')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('servicios shows a truthful missing-entry state', async ({ page }) => {
  await mockDirectory(page);
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/servicios?slug=no-existe');

  await expect(page.getByText('No encontramos este servicio', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Volver al directorio' })).toHaveAttribute('href', /^\/servicios\/?$/);
  await expectNoHorizontalOverflow(page);
});

for (const width of [360, 390, 430]) {
  test(`servicios does not overflow at ${width}px`, async ({ page }) => {
    await mockDirectory(page);
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/servicios');
    await expect(page.getByRole('heading', { name: 'Taller Agrícola E2E', exact: true })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}
