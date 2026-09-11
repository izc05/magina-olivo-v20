import { expect, test } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
const documentId = 'ffffffff-6666-4666-8666-ffffffffffff';

test('revisa OCR, corrige kilos y abre entrega prellenada', async ({ page }) => {
  await page.goto(`/mi-campo/documentos/revisar?documentId=${documentId}&fieldId=${fieldId}&source=api`);

  await expect(page.getByRole('heading', { name: 'Revisar lectura' })).toBeVisible();
  await expect(page.getByText('Albarán OCR E2E')).toBeVisible();
  await expect(page.getByText('succeeded')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Propuesta para revisar' })).toBeVisible();

  const kgInput = page.getByLabel('Kilos totales');
  await expect(kgInput).toHaveValue('1250');
  await kgInput.fill('1280');

  await page.getByRole('button', { name: 'Confirmar datos revisados' }).click();
  await expect(page.getByText(/Revisión humana guardada/)).toBeVisible();
  await expect(kgInput).toBeDisabled();
  await expect(kgInput).toHaveValue('1280');

  const createDelivery = page.getByRole('link', { name: /Crear entrega con estos datos/ });
  await expect(createDelivery).toBeVisible();
  await createDelivery.click();

  await expect(page.getByRole('heading', { name: 'Registrar entrega' })).toBeVisible();
  await expect(page.locator('input[name="date"]')).toHaveValue('2026-12-15');
  await expect(page.locator('input[name="kg"]')).toHaveValue('1280');
  await expect(page.locator('input[name="ticket"]')).toHaveValue('ALB-TEST-01');
  await expect(page.getByText(/Datos prellenados desde un albarán revisado/)).toBeVisible();
});
