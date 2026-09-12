import { expect, test } from '@playwright/test';

const token = 'beta-public-quote-token-2026-fixed';
const route = `/documento-publico?token=${token}`;

test.describe.configure({ mode: 'serial' });

for (const width of [360, 390, 430]) {
  test(`public quote is usable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await page.goto(route);

    await expect(page.getByRole('heading', { name: /Presupuesto P-E2E-001/ })).toBeVisible();
    await expect(page.getByText(/121,00/)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver PDF recibido' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Aceptar presupuesto' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Rechazar' })).toBeVisible();

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });
}

test('public quote can be accepted from mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(route);

  await expect(page.getByRole('button', { name: 'Aceptar presupuesto' })).toBeVisible();
  await page.getByLabel('Nombre / razón social').fill('Cliente Público E2E');
  await page.getByLabel('Comentario opcional').fill('Aceptado desde E2E móvil');
  await page.getByRole('button', { name: 'Aceptar presupuesto' }).click();

  await expect(page.getByText('Presupuesto aceptado ✓')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Aceptar presupuesto' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Rechazar' })).toHaveCount(0);
});
