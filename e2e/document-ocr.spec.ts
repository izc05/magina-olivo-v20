import { expect, test } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
const documentId = 'ffffffff-6666-4666-8666-ffffffffffff';

for (const width of [360, 390, 430]) {
  test(`sube y verifica un documento real a ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 844 });
    const title = `Albarán subido E2E ${width} ${testInfo.retry + 1}`;
    const filename = `albaran-subida-e2e-${width}-${testInfo.retry + 1}.pdf`;
    const pdfFixture = Buffer.from('%PDF-1.4\n% Magina Olivo V20 E2E controlled storage fixture\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n');

    await page.goto(`/mi-campo/documentos/nuevo?fieldId=${fieldId}&source=api`);

    await expect(page.getByRole('heading', { name: 'Añadir documento' })).toBeVisible();
    await page.getByLabel('Tipo').selectOption('delivery_ticket');
    await page.getByLabel('Título').fill(title);
    await page.getByLabel('Archivo').setInputFiles({
      name: filename,
      mimeType: 'application/pdf',
      buffer: pdfFixture,
    });

    await page.getByRole('button', { name: 'Guardar documento' }).click();

    await expect(page.getByRole('heading', { name: 'Documento guardado' })).toBeVisible();
    await expect(page.getByText(/subida ha pasado por la comprobación de integridad/)).toBeVisible();

    await page.getByRole('link', { name: 'Volver a la finca' }).click();
    await expect(page.getByRole('heading', { name: 'Finca Mobile Audit', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Documentos', exact: true }).click();
    const documentsSection = page.locator('section.section').filter({
      has: page.getByRole('heading', { name: 'Documentos', exact: true }),
    });
    await expect(documentsSection.getByText(title, { exact: true })).toBeVisible();
  });
}

test('revisa OCR, corrige kilos y abre entrega prellenada', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
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
