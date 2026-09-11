import { expect, test } from '@playwright/test';

test.describe('Aceite y Mercado', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('muestra precios trazables y no desborda en móvil', async ({ page }) => {
    await page.goto('/mercado');

    await expect(page.getByRole('heading', { name: 'El precio del aceite, explicado sin ruido.' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Último dato validado' })).toBeVisible();
    await expect(page.getByText('3,42', { exact: true })).toBeVisible();
    await expect(page.getByText('3,25', { exact: true })).toBeVisible();
    await expect(page.getByText('3,17', { exact: true })).toBeVisible();
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
