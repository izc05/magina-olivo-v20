import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';

test.describe('Aceite y Mercado', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('expone un snapshot público trazable y cacheable', async ({ request }) => {
    const response = await request.get(`${apiUrl}/api/v1/public/market/olive-oil`);
    expect(response.status()).toBe(200);
    expect(response.headers()['cache-control']).toContain('public');
    expect(response.headers().etag).toBe('"junta-andalucia-olive-oil-2026-w36-v1"');

    const payload = (await response.json()) as {
      market: {
        schemaVersion: number;
        revision: string;
        period: { week: number; end: string };
        source: { publishedOn: string; validatedThrough: string };
        series: Array<{ id: string; latest: { priceEurKg: number } }>;
      };
    };

    expect(payload.market.schemaVersion).toBe(1);
    expect(payload.market.revision).toBe('junta-andalucia-olive-oil-2026-w36-v1');
    expect(payload.market.period).toMatchObject({ week: 36, end: '2026-09-06' });
    expect(payload.market.source).toMatchObject({ publishedOn: '2026-09-09', validatedThrough: '2026-09-06' });
    expect(payload.market.series.map((entry) => [entry.id, entry.latest.priceEurKg])).toEqual([
      ['virgen-extra', 3.42],
      ['virgen', 3.25],
      ['lampante', 3.17],
    ]);

    const cached = await request.get(`${apiUrl}/api/v1/public/market/olive-oil`, {
      headers: { 'if-none-match': response.headers().etag },
    });
    expect(cached.status()).toBe(304);
  });

  test('muestra precios trazables hidratados desde API y no desborda en móvil', async ({ page, request }) => {
    const apiResponse = await request.get(`${apiUrl}/api/v1/public/market/olive-oil`);
    const payload = (await apiResponse.json()) as {
      market: { series: Array<{ latest: { priceEurKg: number } }> };
    };

    await page.goto('/mercado');

    await expect(page.getByRole('heading', { name: 'El precio del aceite, explicado sin ruido.' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Último dato validado' })).toBeVisible();
    await expect(page.locator('[data-market-source="api"]')).toBeVisible();
    await expect(page.getByText('Dato API validado', { exact: true })).toBeVisible();

    for (const series of payload.market.series) {
      const formattedPrice = series.latest.priceEurKg.toLocaleString('es-ES', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
      await expect(page.getByText(formattedPrice, { exact: true }).first()).toBeVisible();
    }
    await expect(page.getByRole('link', { name: 'Ver fuente oficial' })).toHaveAttribute('href', /juntadeandalucia\.es/);

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });

  test('recalcula el valor teórico de una cosecha', async ({ page }) => {
    await page.goto('/mercado');
    await expect(page.locator('[data-market-source="api"]')).toBeVisible();

    const kilos = page.getByLabel('Kilos de aceituna');
    const yieldInput = page.getByLabel('Rendimiento industrial');
    const price = page.getByLabel('Precio del aceite por kilogramo');

    await kilos.fill('10000');
    await yieldInput.fill('18');
    await price.fill('3.50');

    await expect(page.getByText(/1[.\s]?800 kg/)).toBeVisible();
    await expect(page.getByText(/6[.\s]?300,00/)).toBeVisible();
    await expect(page.getByText(/0,63/)).toBeVisible();
    await expect(page.getByText(/No es una liquidación ni una oferta de compra/)).toBeVisible();
  });
});
