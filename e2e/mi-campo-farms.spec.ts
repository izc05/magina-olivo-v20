import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';
const secondFieldId = 'abababab-1212-4212-8212-abababababab';
const secondFieldName = 'Finca Selección E2E';

test.beforeAll(async ({ request }) => {
  const response = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: 'cdcdcdcd-3434-4434-8434-cdcdcdcdcdcd',
      entity_id: secondFieldId,
      name: secondFieldName,
      municipality: 'Bedmar',
      tree_count: 125,
      water_regime: 'regadio',
    },
  });
  expect([200, 201]).toContain(response.status());
});

test('Mi Campo obliga a elegir finca y Registrar conserva la selección', async ({ page }) => {
  await page.goto('/mi-campo');

  await expect(page.getByRole('heading', { name: 'Mi Campo', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Finca Mobile Audit', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: secondFieldName, exact: true })).toBeVisible();

  const chooseFarmFirst = page.locator('.quick').filter({ hasText: 'Registrar' }).filter({ hasText: 'Elige primero la finca' });
  await expect(chooseFarmFirst).toBeVisible();

  await page.getByLabel('Buscar finca').fill('Selección E2E');
  await expect(page.getByRole('heading', { name: secondFieldName, exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Finca Mobile Audit', exact: true })).toHaveCount(0);

  const selectedFarm = page.locator('article.farm-card').filter({
    has: page.getByRole('heading', { name: secondFieldName, exact: true }),
  });
  await expect(selectedFarm).toBeVisible();
  await expect(selectedFarm).toContainText('125 olivas');
  await expect(selectedFarm).toContainText('Bedmar');

  await selectedFarm.getByRole('link', { name: 'Registrar', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`fieldId=${secondFieldId}`));
  await expect(page).toHaveURL(/source=api/);
  await expect(page.getByRole('heading', { name: '¿Qué quieres registrar?', exact: true })).toBeVisible();
  await expect(page.getByText(`Estás registrando en ${secondFieldName}.`, { exact: true })).toBeVisible();
});
