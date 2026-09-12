import { expect, test } from '@playwright/test';

const cadastralReference = '23044A00100001';
const failingCadastralReference = '23044A00100099';
const sigpacFeatureId = '233788127';

// The regular beta browser suite uses production GIS providers. This focused suite
// is enabled only by GIS Check, which starts the deterministic fixture API server.
test.skip(process.env.E2E_GIS_FIXTURES !== 'true', 'Requires deterministic GIS fixture providers.');

async function fillBasics(page: import('@playwright/test').Page, name: string, trees = '120') {
  await page.goto('/mi-campo/fincas/nueva');
  await expect(page.getByRole('heading', { name: '¿Cómo llamáis a esta finca?' })).toBeVisible();
  await page.getByLabel('Nombre de la finca *').fill(name);
  await page.getByLabel('Nº de olivas *').fill(trees);
  const placeSelect = page.getByLabel('Pueblo / localidad');
  await expect.poll(async () => placeSelect.locator('option').count()).toBeGreaterThan(1);
  await placeSelect.selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Continuar →' }).click();
  await expect(page.getByRole('heading', { name: 'Localiza la finca con un límite real' })).toBeVisible();
}

test('alta GIS real persiste Catastro y recupera/sustituye geometría en edición', async ({ page }, testInfo) => {
  const farmName = `Finca GIS E2E ${testInfo.retry + 1}-${Date.now()}`;
  await fillBasics(page, farmName);

  await page.getByLabel('Referencia catastral').fill(cadastralReference);
  await page.getByRole('button', { name: 'Buscar referencia' }).click();
  await expect(page.getByText('Parcela Catastro E2E', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Límite seleccionado', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Usar este límite como geometría principal de la finca')).toBeChecked();

  await page.getByRole('button', { name: 'Guardar finca con límites →' }).click();
  await expect(page.getByText('FINCA GUARDADA EN MI CAMPO')).toBeVisible();
  await expect(page.getByText('✓ Límites reales guardados en la finca')).toBeVisible();

  const editLink = page.getByRole('link', { name: /Editar finca y límites/ });
  const editHref = await editLink.getAttribute('href');
  expect(editHref).toBeTruthy();
  const fieldId = new URL(editHref!, 'http://127.0.0.1:3000').searchParams.get('fieldId');
  expect(fieldId).toBeTruthy();
  await editLink.click();

  await expect(page.getByText('Geometría principal guardada', { exact: true })).toBeVisible();
  await expect(page.getByText(/Catastro · 1,2 ha/)).toBeVisible();
  await expect(page.getByText(/Referencias vinculadas: 1/)).toBeVisible();
  await expect(page.getByText('Esta finca ya tiene límites guardados. Puedes seleccionar otro para sustituir su geometría principal.')).toBeVisible();

  await page.getByLabel('Nombre de la finca *').fill(`${farmName} editada`);
  await page.getByRole('button', { name: 'Guardar datos de la finca' }).click();
  await expect(page.getByText('Datos de la finca guardados.')).toBeVisible();

  await page.reload();
  await expect(page.getByLabel('Nombre de la finca *')).toHaveValue(`${farmName} editada`);
  await expect(page.getByText(/Catastro · 1,2 ha/)).toBeVisible();

  await page.getByRole('button', { name: /SIGPAC/ }).click();
  await page.getByLabel('ID del recinto SIGPAC').fill(sigpacFeatureId);
  await page.getByRole('button', { name: 'Buscar referencia' }).click();
  await expect(page.getByText('Pol. 12 · Parc. 345 · Rec. 2', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Guardar límite seleccionado' }).click();
  await expect(page.getByText('Límite vinculado y guardado como geometría principal de la finca.')).toBeVisible();

  await page.reload();
  await expect(page.getByText(/SIGPAC · 0,8 ha/)).toBeVisible();
  await expect(page.getByText(/Referencias vinculadas: 2/)).toBeVisible();
});

test('finca sin geometría y error de proveedor quedan en estado recuperable', async ({ page }, testInfo) => {
  const farmName = `Finca sin geometría E2E ${testInfo.retry + 1}-${Date.now()}`;
  await fillBasics(page, farmName, '40');

  await expect(page.getByText('Esta finca todavía no tiene límites guardados.')).toBeVisible();
  await page.getByRole('button', { name: 'Guardar sin límites' }).click();
  await expect(page.getByText('FINCA GUARDADA EN MI CAMPO')).toBeVisible();
  await expect(page.getByText(/Finca guardada sin geometría/)).toBeVisible();

  await page.getByRole('link', { name: /Añadir límites/ }).click();
  await expect(page.getByText('Finca sin geometría', { exact: true })).toBeVisible();
  await expect(page.getByText('Esta finca todavía no tiene límites guardados.')).toBeVisible();

  await page.getByLabel('Referencia catastral').fill(failingCadastralReference);
  await page.getByRole('button', { name: 'Buscar referencia' }).click();
  await expect(page.getByRole('alert')).toContainText('No se ha podido consultar Catastro');
  await expect(page.getByText('Finca sin geometría', { exact: true })).toBeVisible();
  await expect(page.getByText(/Referencias vinculadas: 0/)).toBeVisible();
});
