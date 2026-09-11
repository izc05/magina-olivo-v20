import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';
const fieldId = '73737373-7373-4373-8373-737373737373';
const farmName = 'Finca Registro Campaña E2E';

test.beforeAll(async ({ request }) => {
  const response = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: '74747474-7474-4474-8474-747474747474',
      entity_id: fieldId,
      name: farmName,
      tree_count: 130,
      water_regime: 'secano',
    },
  });
  expect([200, 201]).toContain(response.status());
});

test('recorre entrega, rendimiento, liquidación y cobro hasta Campaña', async ({ page }, testInfo) => {
  const ticket = `REG-CAMP-${testInfo.retry + 1}`;
  const settlementNumber = `LIQ-REG-${testInfo.retry + 1}`;

  await page.goto(`/mi-campo/registrar?fieldId=${fieldId}&source=api`);
  await expect(page.getByRole('heading', { name: '¿Qué quieres registrar?' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'De la entrega al cobro' })).toBeVisible();

  for (const label of ['Entrega de cosecha', 'Rendimiento', 'Liquidación', 'Cobro']) {
    const link = page.getByRole('link').filter({ hasText: label }).first();
    await expect(link).toBeVisible();
    const href = await link.getAttribute('href');
    expect(href).toContain(`fieldId=${fieldId}`);
  }

  await page.getByRole('link').filter({ hasText: 'Entrega de cosecha' }).first().click();
  await expect(page.getByRole('heading', { name: 'Registrar entrega' })).toBeVisible();
  await page.locator('input[name="date"]').fill('2026-12-20');
  await page.locator('input[name="kg"]').fill('1000');
  await page.locator('input[name="cooperative"]').fill('SCA Registro E2E');
  await page.locator('input[name="ticket"]').fill(ticket);
  await page.getByRole('button', { name: 'Guardar entrega →' }).click();
  await expect(page.getByText('COSECHA GUARDADA EN MÁGINA')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Añadir rendimiento' })).toBeVisible();

  await page.getByRole('link', { name: 'Añadir rendimiento' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar rendimiento' })).toBeVisible();
  await expect(page.getByRole('combobox')).toContainText(ticket);
  await page.locator('input[name="date"]').fill('2026-12-22');
  await page.locator('input[name="yield"]').fill('20');
  await page.getByRole('button', { name: 'Guardar rendimiento →' }).click();
  await expect(page.getByRole('heading', { name: 'Rendimiento guardado' })).toBeVisible();

  await page.goto(`/mi-campo/registrar/liquidacion?fieldId=${fieldId}&source=api`);
  await expect(page.getByRole('heading', { name: 'Registrar liquidación' })).toBeVisible();
  const currentDelivery = page.locator('label.feed-row').filter({ hasText: ticket });
  await expect(currentDelivery).toBeVisible();
  const checkbox = currentDelivery.getByRole('checkbox');
  if (!(await checkbox.isChecked())) await checkbox.check();
  await page.locator('input[name="date"]').fill('2027-01-10');
  await page.locator('input[name="number"]').fill(settlementNumber);
  await page.locator('input[name="counterparty"]').fill('SCA Registro E2E');
  await page.locator('input[name="gross"]').fill('200');
  await page.locator('input[name="deductions"]').fill('20');
  await page.locator('input[name="net"]').fill('180');
  await page.getByRole('button', { name: 'Guardar liquidación →' }).click();
  await expect(page.getByRole('heading', { name: 'Liquidación guardada' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Registrar cobro →' })).toBeVisible();

  await page.getByRole('link', { name: 'Registrar cobro →' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar cobro' })).toBeVisible();
  await expect(page.getByRole('combobox')).toContainText(settlementNumber);
  await page.locator('input[name="date"]').fill('2027-01-15');
  await page.getByRole('button', { name: 'Todo lo pendiente' }).click();
  await page.getByRole('button', { name: 'Guardar cobro →' }).click();
  await expect(page.getByRole('heading', { name: 'Cobro registrado' })).toBeVisible();

  await page.getByRole('link', { name: 'Ver campaña' }).click();
  await expect(page.getByRole('heading', { name: 'Campaña', exact: true })).toBeVisible();
  const farmRow = page.locator('.feed-row').filter({ hasText: farmName }).first();
  await expect(farmRow).toContainText('1.000 kg');
  await expect(farmRow).toContainText('liquidado 180 €');
  await expect(farmRow).toContainText('cobrado 180 €');
  await expect(farmRow).toContainText('pendiente 0 €');
  await expect(farmRow.getByRole('link', { name: 'Rendimiento' })).toBeVisible();
  await expect(farmRow.getByRole('link', { name: 'Liquidación' })).toBeVisible();
  await expect(farmRow.getByRole('link', { name: 'Cobro' })).toBeVisible();
});

test('Rendimiento guía a registrar entrega cuando la finca no tiene cosecha', async ({ page, request }, testInfo) => {
  const emptyFieldId = `75757575-7575-4575-8575-${String(testInfo.retry + 1).padStart(12, '0')}`;
  const response = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: `76767676-7676-4676-8676-${String(testInfo.retry + 1).padStart(12, '0')}`,
      entity_id: emptyFieldId,
      name: `Finca sin cosecha ${testInfo.retry + 1}`,
      tree_count: 40,
      water_regime: 'secano',
    },
  });
  expect([200, 201]).toContain(response.status());

  await page.goto(`/mi-campo/registrar/rendimiento?fieldId=${emptyFieldId}&source=api`);
  await expect(page.getByRole('heading', { name: 'Primero registra una entrega' })).toBeVisible();
  const deliveryLink = page.getByRole('link', { name: 'Registrar entrega →' });
  await expect(deliveryLink).toBeVisible();
  await expect(deliveryLink).toHaveAttribute('href', new RegExp(`fieldId=${emptyFieldId}`));
});

for (const width of [360, 390, 430]) {
  test(`Registrar y Campaña no desbordan el documento a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    for (const url of [
      `/mi-campo/registrar?fieldId=${fieldId}&source=api`,
      '/mi-campo/campana',
    ]) {
      await page.goto(url);
      await page.waitForTimeout(250);
      const dimensions = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth,
      }));
      expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
      expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    }
  });
}
