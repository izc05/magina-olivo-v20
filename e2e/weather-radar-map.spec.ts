import { expect, test, type Page } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
const overlayPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+X8nOAAAAAElFTkSuQmCC',
  'base64',
);

async function serveOverlay(page: Page) {
  await page.route(`**/api/v1/fields/${fieldId}/radar/latest/overlay.png`, async (route) => {
    await route.fulfill({ status: 200, contentType: 'image/png', body: overlayPng });
  });
}

test('clima de finca combina geometría, AEMET y radar real sin mezclar semánticas', async ({ page }) => {
  await serveOverlay(page);
  await page.goto(`/radar?fieldId=${fieldId}`);

  await expect(page.getByRole('heading', { name: 'Radar de lluvia' })).toBeVisible();
  await expect(page.getByText('Finca Mobile Audit', { exact: true })).toBeVisible();
  await expect(page.getByText(/Finca: geometría lista/)).toBeVisible();
  await expect(page.getByText(/AEMET: actual/)).toBeVisible();
  await expect(page.getByText(/Radar: actual/)).toBeVisible();
  await expect(page.getByLabel('Mapa de finca con radar observado')).toBeVisible();
  await expect(page.getByText(/Radar observado/).first()).toBeVisible();
  await expect(page.getByText(/Reflectividad observada; no es pronóstico ni estima hora de llegada/)).toBeVisible();
  await expect(page.getByText(/no convierte automáticamente dBZ en mm\/h ni calcula nowcast o ETA/)).toBeVisible();
});

test('si AEMET previsión falla, radar y mapa de finca siguen disponibles', async ({ page }) => {
  await serveOverlay(page);
  await page.route(`**/api/v1/fields/${fieldId}/weather/daily`, async (route) => {
    await route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ error: 'weather_upstream_unavailable' }) });
  });
  await page.goto(`/radar?fieldId=${fieldId}`);

  await expect(page.getByText(/AEMET: error/)).toBeVisible();
  await expect(page.getByText(/La previsión AEMET no está disponible ahora/)).toBeVisible();
  await expect(page.getByText(/Radar: actual/)).toBeVisible();
  await expect(page.getByLabel('Mapa de finca con radar observado')).toBeVisible();
});

for (const width of [360, 390, 430]) {
  test(`radar finca no desborda a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await serveOverlay(page);
    await page.goto(`/radar?fieldId=${fieldId}`);
    await expect(page.getByText('Finca Mobile Audit', { exact: true })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
}
