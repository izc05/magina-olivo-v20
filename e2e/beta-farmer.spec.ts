import { expect, test } from '@playwright/test';

test('agricultor crea finca, registra trabajo y lo ve reflejado', async ({ page }) => {
  await page.goto('/mi-campo/fincas/nueva');

  await expect(page.getByRole('heading', { name: '¿Cómo llamáis a esta finca?' })).toBeVisible();
  await page.getByLabel('Nombre de la finca *').fill('Finca E2E');
  await page.getByLabel('Nº de olivas *').fill('120');

  const placeSelect = page.getByLabel('Pueblo / localidad');
  await expect(placeSelect.locator('option')).not.toHaveCount(1);
  await placeSelect.selectOption({ index: 1 });

  await page.getByRole('button', { name: 'Continuar →' }).click();
  await expect(page.getByRole('heading', { name: 'Guarda primero la finca' })).toBeVisible();
  await expect(page.getByText(/No se guardará una localización ficticia/)).toBeVisible();

  await page.getByRole('button', { name: 'Guardar finca →' }).click();
  await expect(page.getByText('FINCA GUARDADA EN MI CAMPO')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Finca E2E' })).toBeVisible();

  await page.getByRole('link', { name: /Registrar trabajo/ }).click();
  await expect(page.getByRole('heading', { name: '¿Qué quieres registrar?' })).toBeVisible();

  const workLink = page.getByRole('link').filter({ hasText: 'Trabajo' }).first();
  await workLink.click();
  await expect(page.getByRole('heading', { name: 'Registrar trabajo' })).toBeVisible();

  await page.locator('input[name="date"]').fill('2026-09-11');
  await page.locator('input[name="title"]').fill('Desbroce E2E');
  await page.locator('input[name="workerName"]').fill('Cuadrilla E2E');
  await page.locator('input[name="quantity"]').fill('1');
  await page.locator('input[name="laborCost"]').fill('90');
  await page.getByRole('button', { name: 'Guardar trabajo →' }).click();

  await expect(page.getByRole('heading', { name: 'Trabajo registrado' })).toBeVisible();
  await page.getByRole('link', { name: 'Volver a Mi Campo' }).click();
  await expect(page.getByText('Finca E2E')).toBeVisible();

  await page.getByRole('link', { name: /Finca E2E/ }).click();
  await expect(page.getByRole('heading', { name: 'Finca E2E' })).toBeVisible();
  const workMetric = page.locator('article').filter({ hasText: 'trabajos registrados' }).first();
  await expect(workMetric).toContainText('1');

  await page.goto('/mi-campo/campana');
  await expect(page.getByRole('heading', { name: 'Campaña' })).toBeVisible();
  await expect(page.getByRole('combobox')).toContainText('2026/27');
});
