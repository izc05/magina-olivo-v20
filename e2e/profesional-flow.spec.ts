import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';
const customerId = '82828282-8282-4282-8282-828282828282';
const siteId = '83838383-8383-4383-8383-838383838383';
const customerName = 'Cliente Profesional E2E';
const siteName = 'Finca Cliente Profesional E2E';

test.beforeAll(async ({ request }) => {
  const customer = await request.post(`${apiUrl}/api/v1/parties`, {
    data: {
      client_operation_id: '84848484-8484-4484-8484-848484848484',
      entity_id: customerId,
      kind: 'person',
      display_name: customerName,
      email: 'cliente-profesional@example.com',
      roles: ['customer'],
    },
  });
  expect([200, 201]).toContain(customer.status());

  const site = await request.post(`${apiUrl}/api/v1/customer-sites`, {
    data: {
      client_operation_id: '85858585-8585-4585-8585-858585858585',
      entity_id: siteId,
      customer_party_id: customerId,
      name: siteName,
      municipality: 'Bedmar',
    },
  });
  expect([200, 201]).toContain(site.status());
});

test('ficha de cliente preselecciona el trabajo profesional', async ({ page }) => {
  await page.goto(`/mi-campo/profesional/cliente?id=${customerId}`);
  await expect(page.getByRole('heading', { name: customerName })).toBeVisible();

  const workLink = page.getByRole('link', { name: 'Registrar trabajo' }).first();
  await expect(workLink).toHaveAttribute('href', new RegExp(`customerId=${customerId}`));
  await workLink.click();

  await expect(page.getByText('Cliente preseleccionado desde Profesional')).toBeVisible();
  await expect(page.getByLabel('Cliente existente')).toHaveValue(customerId);
  await expect(page.getByRole('combobox', { name: 'Tipo', exact: true })).toHaveValue('third-party');
});

test('recorre cliente, presupuesto, trabajo, factura, documento y cobro', async ({ page }, testInfo) => {
  const attempt = testInfo.retry + 1;
  const quoteNumber = `P-E2E-${attempt}`;
  const invoiceNumber = `F-E2E-${attempt}`;
  const title = `Poda profesional E2E ${attempt}`;

  await page.goto(`/mi-campo/profesional/cliente?id=${customerId}`);
  await page.getByRole('link', { name: 'Nuevo presupuesto' }).first().click();

  await expect(page.getByRole('heading', { name: 'Presupuestos' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Cliente', exact: true })).toHaveValue(customerId);
  await page.getByRole('combobox', { name: /^Finca \/ sitio del cliente/ }).selectOption(siteId);
  await page.getByLabel('Concepto').fill(title);
  await page.getByLabel('Nº presupuesto').fill(quoteNumber);
  await page.getByLabel('Estado inicial').selectOption('sent');
  await page.getByLabel('Fecha', { exact: true }).fill('2026-09-11');
  await page.getByLabel('Cantidad').fill('1');
  await page.getByLabel('Precio unidad').fill('100');
  await page.getByLabel('IVA %').fill('0');
  await page.getByRole('button', { name: 'Guardar presupuesto →' }).click();

  await expect(page.getByText('Presupuesto guardado')).toBeVisible();
  const quoteCard = page.locator('article.activity-item').filter({ hasText: quoteNumber }).first();
  await expect(quoteCard).toContainText(title);
  await quoteCard.getByRole('button', { name: 'Aceptar' }).click();
  await expect(quoteCard).toContainText('accepted');
  await quoteCard.getByRole('button', { name: 'Convertir a trabajo' }).click();

  await expect(page.getByRole('heading', { name: 'Convertir presupuesto aceptado' })).toBeVisible();
  await page.getByRole('combobox', { name: 'Finca / sitio', exact: true }).selectOption(siteId);
  await page.getByLabel('Fecha trabajo').fill('2026-09-12');
  await page.getByLabel('Coste real mano de obra').fill('20');
  await page.getByLabel('Otros costes reales').fill('5');
  await page.getByRole('button', { name: 'Crear trabajo desde presupuesto →' }).click();

  await expect(page.getByRole('heading', { name: 'Trabajo creado desde presupuesto' })).toBeVisible();
  await page.getByRole('link', { name: 'Facturar este trabajo →' }).click();

  await expect(page.getByRole('heading', { name: 'Nueva factura' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Cliente', exact: true })).toHaveValue(customerId);
  await expect(page.locator('label.card.activity-item').filter({ hasText: title })).toBeVisible();
  await page.getByLabel('Estado').selectOption('issued');
  await page.getByLabel('Nº factura').fill(invoiceNumber);
  await page.getByLabel('Fecha emisión').fill('2026-09-13');
  await page.getByRole('button', { name: 'Emitir factura →' }).click();

  await expect(page.getByRole('heading', { name: 'Factura emitida' })).toBeVisible();
  const documentLink = page.getByRole('link', { name: 'Ver / compartir factura →' });
  const collectionLink = page.getByRole('link', { name: 'Registrar cobro' });
  const documentHref = await documentLink.getAttribute('href');
  const collectionHref = await collectionLink.getAttribute('href');
  expect(documentHref).toContain('/mi-campo/profesional/documento?type=invoice&id=');
  expect(collectionHref).toContain('/mi-campo/profesional/cobrar?workId=');

  await page.goto(documentHref!);
  await expect(page.getByRole('heading', { name: 'Compartir y registrar' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Preparar y compartir PDF' })).toBeVisible();

  await page.goto(collectionHref!);
  await expect(page.getByRole('heading', { name: 'Registrar cobro' })).toBeVisible();
  await expect(page.getByLabel('Trabajo')).toContainText(title);
  await page.getByLabel('Fecha', { exact: true }).fill('2026-09-14');
  await page.getByRole('button', { name: 'Todo lo pendiente' }).click();
  await page.getByRole('button', { name: 'Guardar cobro →' }).click();

  await expect(page.getByRole('heading', { name: 'Cobro registrado' })).toBeVisible();
  await expect(page.getByText('El trabajo queda completamente cobrado.')).toBeVisible();

  await page.goto(`/mi-campo/profesional/cliente?id=${customerId}`);
  await expect(page.getByRole('heading', { name: customerName })).toBeVisible();
  await expect(page.locator('article.activity-item').filter({ hasText: invoiceNumber }).first()).toContainText('cobrada');
  await expect(page.locator('article.activity-item').filter({ hasText: title }).last()).toContainText('cobrado');
});

for (const width of [360, 390, 430]) {
  test(`Profesional no desborda el documento a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    for (const url of [
      '/mi-campo/profesional',
      `/mi-campo/profesional/cliente?id=${customerId}`,
      `/mi-campo/profesional/presupuestos?customerId=${customerId}`,
      `/mi-campo/profesional/facturas/nueva?customerId=${customerId}`,
      '/mi-campo/profesional/cobrar',
    ]) {
      await page.goto(url);
      await page.waitForTimeout(200);
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
