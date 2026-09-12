import { expect, test, type Page, type TestInfo } from '@playwright/test';

async function runFarmerJourney(page: Page, testInfo: TestInfo, width: number) {
  const farmName = `Finca E2E ${width} ${testInfo.retry + 1}`;

  await page.goto('/mi-campo/fincas/nueva');

  await expect(page.getByRole('heading', { name: '¿Cómo llamáis a esta finca?' })).toBeVisible();
  await page.getByLabel('Nombre de la finca *').fill(farmName);
  await page.getByLabel('Nº de olivas *').fill('120');

  const placeSelect = page.getByLabel('Pueblo / localidad');
  await expect.poll(async () => placeSelect.locator('option').count()).toBeGreaterThan(1);
  await placeSelect.selectOption({ index: 1 });

  await page.getByRole('button', { name: 'Continuar →' }).click();
  await expect(page.getByRole('heading', { name: 'Guarda primero la finca' })).toBeVisible();
  await expect(page.getByText(/No se guardará una localización ficticia/)).toBeVisible();

  await page.getByRole('button', { name: 'Guardar finca →' }).click();
  await expect(page.getByText('FINCA GUARDADA EN MI CAMPO')).toBeVisible();
  await expect(page.getByRole('heading', { name: farmName, exact: true })).toBeVisible();

  const registerHref = await page.getByRole('link', { name: /Registrar trabajo/ }).getAttribute('href');
  expect(registerHref).toBeTruthy();
  const fieldId = new URL(registerHref!, 'http://127.0.0.1:3000').searchParams.get('fieldId');
  expect(fieldId).toBeTruthy();

  const addBoundariesLink = page.getByRole('link', { name: /Añadir límites/ });
  const addBoundariesHref = await addBoundariesLink.getAttribute('href');
  expect(addBoundariesHref).toBeTruthy();
  const addBoundariesUrl = new URL(addBoundariesHref!, 'http://127.0.0.1:3000');
  expect(addBoundariesUrl.pathname.replace(/\/$/, '')).toBe('/mi-campo/mapa');
  expect(addBoundariesUrl.searchParams.get('fieldId')).toBe(fieldId);
  await addBoundariesLink.click();
  await expect(page.getByRole('heading', { name: 'Tu finca sobre el terreno' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Añade los límites reales' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Finca', exact: true })).toHaveValue(fieldId!);
  await expect(page.getByLabel('Referencia catastral')).toBeVisible();
  await expect(page.getByLabel('ID del recinto SIGPAC')).toBeVisible();

  await page.goto(`/mi-campo/registrar?fieldId=${encodeURIComponent(fieldId!)}`);
  await expect(page.getByRole('heading', { name: '¿Qué quieres registrar?' })).toBeVisible();

  const workLink = page.getByRole('link').filter({ hasText: 'Trabajo' }).first();
  await workLink.click();
  await expect(page.getByRole('heading', { name: 'Registrar trabajo' })).toBeVisible();

  await page.locator('input[name="date"]').fill('2026-09-11');
  await page.locator('input[name="title"]').fill(`Desbroce E2E ${width}`);
  await page.locator('input[name="workerName"]').fill('Cuadrilla E2E');
  await page.locator('input[name="quantity"]').fill('1');
  await page.locator('input[name="laborCost"]').fill('90');
  await page.getByRole('button', { name: 'Guardar trabajo →' }).click();

  await expect(page.getByRole('heading', { name: 'Trabajo registrado' })).toBeVisible();

  await page.goto(`/mi-campo/registrar/cosecha?fieldId=${encodeURIComponent(fieldId!)}&source=api`);
  await expect(page.getByRole('heading', { name: 'Registrar entrega' })).toBeVisible();
  await page.locator('input[name="date"]').fill('2026-12-12');
  await page.locator('input[name="kg"]').fill('1842');
  await page.locator('input[name="cooperative"]').fill('SCA E2E');
  await page.locator('input[name="ticket"]').fill(`E2E-${width}-${testInfo.retry + 1}`);
  await page.getByRole('button', { name: 'Guardar entrega →' }).click();
  await expect(page.getByText('COSECHA GUARDADA EN MÁGINA')).toBeVisible();
  await expect(page.getByRole('heading', { name: `1.842 kg en ${farmName}`, exact: true })).toBeVisible();

  await page.goto(`/mi-campo/registrar/rendimiento?fieldId=${encodeURIComponent(fieldId!)}&source=api`);
  await expect(page.getByRole('heading', { name: 'Registrar rendimiento' })).toBeVisible();
  await expect(page.getByRole('combobox')).toContainText('1.842 kg');
  await page.locator('input[name="date"]').fill('2026-12-15');
  await page.locator('input[name="yield"]').fill('21.4');
  await page.locator('input[name="moisture"]').fill('0.2');
  await page.getByRole('button', { name: 'Guardar rendimiento →' }).click();
  await expect(page.getByRole('heading', { name: 'Rendimiento guardado' })).toBeVisible();

  await page.goto('/mi-campo');
  const createdFarmLink = page.locator(`a[href*="id=${fieldId}"]`).filter({ hasText: farmName });
  await expect(createdFarmLink).toBeVisible();
  await createdFarmLink.click();
  await expect(page.getByRole('heading', { name: farmName, exact: true })).toBeVisible();
  const workMetric = page.locator('article').filter({ hasText: 'trabajos registrados' }).first();
  await expect(workMetric).toContainText('1');

  await page.getByRole('button', { name: 'Datos', exact: true }).click();
  const manageBoundariesLink = page.getByRole('link', { name: 'Gestionar límites', exact: true });
  const manageBoundariesHref = await manageBoundariesLink.getAttribute('href');
  expect(manageBoundariesHref).toBeTruthy();
  const manageBoundariesUrl = new URL(manageBoundariesHref!, 'http://127.0.0.1:3000');
  expect(manageBoundariesUrl.pathname.replace(/\/$/, '')).toBe('/mi-campo/mapa');
  expect(manageBoundariesUrl.searchParams.get('fieldId')).toBe(fieldId);
  await manageBoundariesLink.click();
  await expect(page.getByRole('heading', { name: 'Tu finca sobre el terreno' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Finca', exact: true })).toHaveValue(fieldId!);

  await page.goto('/mi-campo/campana');
  await expect(page.getByRole('heading', { name: 'Campaña', exact: true })).toBeVisible();
  await expect(page.getByRole('combobox')).toContainText('2026/27');
  const farmCampaignRow = page.locator('.feed-row').filter({ hasText: farmName }).first();
  await expect(farmCampaignRow).toContainText('1.842 kg');
  const yieldMetric = page.locator('article').filter({ hasText: 'rendimiento ponderado' }).first();
  await expect(yieldMetric.getByText('21,4 %', { exact: true })).toBeVisible();
}

for (const width of [360, 390, 430]) {
  test(`agricultor completa finca, trabajo, cosecha y rendimiento a ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 844 });
    await runFarmerJourney(page, testInfo, width);
  });
}
