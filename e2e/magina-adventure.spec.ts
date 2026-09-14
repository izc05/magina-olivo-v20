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

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/aventura');

  await expect(page.getByRole('heading', { level: 1, name: /Mágina Aventura/ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver aventuras' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver rutas' })).toHaveAttribute('href', /^\/rutas\/?$/);
  await expect(page.getByText('Las primeras aventuras están en preparación')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
