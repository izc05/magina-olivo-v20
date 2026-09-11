import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';
const seededFieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
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

test('la ficha mantiene sus secciones y acciones sobre la finca seleccionada', async ({ page }) => {
  await page.goto(`/mi-campo/fincas/ver?id=${seededFieldId}&source=api`);

  const farmHeader = page.locator('header.page-title');
  await expect(page.getByRole('heading', { name: 'Finca Mobile Audit', exact: true })).toBeVisible();
  await expect(farmHeader).toContainText('80 olivas');

  const registerLinks = page.locator(`a[href*="/mi-campo/registrar"][href*="fieldId=${seededFieldId}"]`);
  await expect(registerLinks.first()).toBeVisible();

  await page.getByRole('button', { name: 'Actividad', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Actividad', exact: true, level: 2 })).toBeVisible();

  await page.getByRole('button', { name: 'Cosecha', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Cosecha', exact: true, level: 2 })).toBeVisible();

  await page.getByRole('button', { name: 'Datos', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Datos', exact: true, level: 2 })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Gestionar límites', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Documentos', exact: true }).click();
  const documentsSection = page.locator('section.section').filter({
    has: page.getByRole('heading', { name: 'Documentos', exact: true, level: 2 }),
  });
  await expect(documentsSection).toBeVisible();
  await expect(documentsSection.getByText('Albarán OCR E2E', { exact: true })).toBeVisible();
});

test('la ficha conserva la finca si falla un agregado y recupera los datos al reintentar', async ({ page }) => {
  let failEconomicsOnce = true;
  await page.route(`**/api/v1/fields/${seededFieldId}/economics-summary`, async (route) => {
    if (failEconomicsOnce) {
      failEconomicsOnce = false;
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'temporary_economics_outage' }),
      });
      return;
    }
    await route.continue();
  });

  await page.goto(`/mi-campo/fincas/ver?id=${seededFieldId}&source=api`);

  const farmHeader = page.locator('header.page-title');
  await expect(page.getByRole('heading', { name: 'Finca Mobile Audit', exact: true })).toBeVisible();
  await expect(farmHeader).toContainText('80 olivas');
  await expect(page.getByRole('heading', { name: 'Finca no encontrada', exact: true })).toHaveCount(0);

  const degradedHeading = page.getByRole('heading', { name: 'Datos de la finca temporalmente no disponibles', exact: true });
  await expect(degradedHeading).toBeVisible();
  await expect(page.getByText(/No mostramos cifras de cosecha o economía/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Economía agrícola', exact: true })).toHaveCount(0);

  const safeRegister = page.locator(`a[href*="/mi-campo/registrar"][href*="fieldId=${seededFieldId}"]`).first();
  await expect(safeRegister).toBeVisible();

  await page.getByRole('button', { name: 'Reintentar datos', exact: true }).click();

  await expect(degradedHeading).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Resumen', exact: true, level: 2 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Economía agrícola', exact: true })).toBeVisible();
});
