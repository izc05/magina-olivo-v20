import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';

test.describe('Aceite y Mercado', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('expone un snapshot público persistido, trazable y cacheable', async ({ request }) => {
    const response = await request.get(`${apiUrl}/api/v1/public/market/olive-oil`);
    expect(response.status()).toBe(200);
    expect(response.headers()['cache-control']).toContain('public');
    expect(response.headers().etag).toBe('"junta-andalucia-olive-oil-2026-w36-v1"');

    const payload = (await response.json()) as {
      origin: string;
      market: {
        schemaVersion: number;
        revision: string;
        period: { week: number; end: string };
        source: { publishedOn: string; validatedThrough: string };
        series: Array<{ id: string; latest: { priceEurKg: number } }>;
      };
    };

    expect(payload.origin).toBe('database');
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

  test('sirve histórico persistido por ventana y valida el rango solicitado', async ({ request }) => {
    const response = await request.get(`${apiUrl}/api/v1/public/market/olive-oil/history?weeks=8`);
    expect(response.status()).toBe(200);
    expect(response.headers()['cache-control']).toContain('public');
    expect(response.headers().etag).toBe('"junta-andalucia-olive-oil-2026-w36-v1-history-8"');

    const payload = (await response.json()) as {
      history: {
        origin: string;
        revision: string;
        windowWeeks: number;
        availableFrom: string;
        availableThrough: string;
        series: Array<{
          id: string;
          points: Array<{ week: number; periodStart: string; periodEnd: string; priceEurKg: number }>;
        }>;
      };
    };

    expect(payload.history).toMatchObject({
      origin: 'database',
      revision: 'junta-andalucia-olive-oil-2026-w36-v1',
      windowWeeks: 8,
      availableFrom: '2026-07-13',
      availableThrough: '2026-09-06',
    });
    expect(payload.history.series).toHaveLength(3);
    expect(payload.history.series.every((series) => series.points.length === 8)).toBe(true);

    const aove = payload.history.series.find((series) => series.id === 'virgen-extra');
    expect(aove?.points.map((point) => [point.week, point.priceEurKg])).toEqual([
      [29, 3.76],
      [30, 3.6],
      [31, 3.7],
      [32, 3.63],
      [33, 3.44],
      [34, 3.49],
      [35, 3.7],
      [36, 3.42],
    ]);

    const fourWeeksResponse = await request.get(`${apiUrl}/api/v1/public/market/olive-oil/history?weeks=4`);
    expect(fourWeeksResponse.status()).toBe(200);
    const fourWeeksPayload = (await fourWeeksResponse.json()) as {
      history: { origin: string; windowWeeks: number; series: Array<{ points: Array<{ week: number }> }> };
    };
    expect(fourWeeksPayload.history.origin).toBe('database');
    expect(fourWeeksPayload.history.windowWeeks).toBe(4);
    expect(fourWeeksPayload.history.series[0]?.points.map((point) => point.week)).toEqual([33, 34, 35, 36]);

    const cached = await request.get(`${apiUrl}/api/v1/public/market/olive-oil/history?weeks=8`, {
      headers: { 'if-none-match': response.headers().etag },
    });
    expect(cached.status()).toBe(304);

    for (const invalidWeeks of ['0', '53', 'abc', '4.5']) {
      const invalid = await request.get(
        `${apiUrl}/api/v1/public/market/olive-oil/history?weeks=${encodeURIComponent(invalidWeeks)}`,
      );
      expect(invalid.status()).toBe(400);
      await expect(invalid.json()).resolves.toMatchObject({
        error: 'invalid_market_history_weeks',
        min: 1,
        max: 52,
      });
    }
  });

  test('muestra precios trazables, lectura rápida y no desborda en móvil', async ({ page, request }) => {
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

    await expect(page.getByRole('heading', { name: 'Qué dicen los datos' })).toBeVisible();
    await expect(page.getByText('-7,6%', { exact: true })).toBeVisible();
    await expect(page.getByText('3,42–3,76 €/kg', { exact: true })).toBeVisible();
    await expect(page.getByText('0,25 €/kg', { exact: true })).toBeVisible();
    await expect(page.getByText('No es una previsión de precios.', { exact: false })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver fuente oficial' })).toHaveAttribute('href', /juntadeandalucia\.es/);

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });

  test('recalcula con precio oficial y permite una referencia manual', async ({ page }) => {
    await page.goto('/mercado');
    await expect(page.locator('[data-market-source="api"]')).toBeVisible();

    const kilos = page.getByLabel('Kilos de aceituna');
    const yieldInput = page.getByLabel('Rendimiento industrial');
    const price = page.getByLabel('Precio del aceite por kilogramo');
    const virgenPreset = page.getByRole('button', { name: /^Virgen 3,25 €\/kg$/ });

    await kilos.fill('10000');
    await yieldInput.fill('18');
    await virgenPreset.click();

    await expect(virgenPreset).toHaveAttribute('aria-pressed', 'true');
    await expect(price).toHaveValue('3.25');
    await expect(page.getByText(/5[.\s]?850,00/)).toBeVisible();

    await price.fill('3.50');
    await expect(virgenPreset).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByText(/1[.\s]?800 kg/)).toBeVisible();
    await expect(page.getByText(/6[.\s]?300,00/)).toBeVisible();
    await expect(page.getByText(/0,63/)).toBeVisible();
    await expect(page.getByText(/No es una liquidación ni una oferta de compra/)).toBeVisible();
  });
});
