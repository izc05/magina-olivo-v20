import { expect, test, type Page } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
const documentId = 'ffffffff-6666-4666-8666-ffffffffffff';
const customerId = '44444444-aaaa-4aaa-8aaa-444444444444';
const quoteId = '66666666-cccc-4ccc-8ccc-666666666666';
const campaignId = 'cccccccc-3333-4333-8333-cccccccccccc';
const workId = '12121212-abcd-4abc-8abc-121212121212';
const workOperationId = '13131313-abcd-4abc-8abc-131313131313';
const invoiceId = '14141414-abcd-4abc-8abc-141414141414';
const invoiceOperationId = '15151515-abcd-4abc-8abc-151515151515';
const apiUrl = 'http://127.0.0.1:3001';

const routes = [
  '/',
  '/explorar',
  '/mi-campo',
  '/mi-campo/fincas/nueva',
  `/mi-campo/fincas/ver?id=${fieldId}&source=api`,
  `/mi-campo/mapa?fieldId=${fieldId}`,
  `/mi-campo/planificar?fieldId=${fieldId}&source=api`,
  `/mi-campo/registrar?fieldId=${fieldId}`,
  `/mi-campo/registrar/trabajo?fieldId=${fieldId}`,
  `/mi-campo/registrar/cosecha?fieldId=${fieldId}&source=api`,
  `/mi-campo/registrar/rendimiento?fieldId=${fieldId}&source=api`,
  `/mi-campo/documentos/nuevo?fieldId=${fieldId}&source=api`,
  '/mi-campo/hoy',
  '/mi-campo/campana',
  `/mi-campo/documentos/revisar?documentId=${documentId}&fieldId=${fieldId}&source=api`,
  '/mi-campo/profesional',
  `/mi-campo/profesional/cliente?id=${customerId}`,
  '/mi-campo/profesional/cliente',
  '/mi-campo/profesional/presupuestos',
  `/mi-campo/profesional/presupuestos?quoteId=${quoteId}&customerId=${customerId}`,
  `/mi-campo/profesional/documento?type=invoice&id=${invoiceId}`,
  '/mi-campo/profesional/documento',
  '/perfil',
];

const widerRoutes = [
  '/',
  '/explorar',
  `/mi-campo/fincas/ver?id=${fieldId}&source=api`,
  `/mi-campo/mapa?fieldId=${fieldId}`,
  `/mi-campo/planificar?fieldId=${fieldId}&source=api`,
  `/mi-campo/profesional/cliente?id=${customerId}`,
  `/mi-campo/profesional/documento?type=invoice&id=${invoiceId}`,
];

async function expectNoHorizontalOverflow(page: Page, route: string, width: number) {
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  const response = await page.goto(route);
  expect(response, `${route} did not produce a navigation response at ${width}px`).not.toBeNull();
  expect(response!.status(), `${route} returned ${response!.status()} at ${width}px`).toBeLessThan(400);
  await expect(page.locator('body')).toBeVisible();
  await page.waitForTimeout(350);

  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }));

  expect(pageErrors, `${route} raised browser page errors at ${width}px: ${pageErrors.join(' | ')}`).toEqual([]);
  expect(dimensions.documentWidth, `${route} document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.bodyWidth, `${route} body overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
}

test.beforeAll(async ({ request }) => {
  const fieldResponse = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: 'eeeeeeee-5555-4555-8555-eeeeeeeeeeee',
      entity_id: fieldId,
      name: 'Finca Mobile Audit',
      tree_count: 80,
      water_regime: 'secano',
    },
  });
  expect([200, 201]).toContain(fieldResponse.status());

  const workResponse = await request.post(`${apiUrl}/api/v1/works`, {
    data: {
      client_operation_id: workOperationId,
      entity_id: workId,
      field_id: fieldId,
      campaign_id: campaignId,
      type: 'manual-work',
      occurred_on: '2026-10-10',
      title: 'Trabajo facturable auditoría móvil',
      performed_for: 'third-party',
      customer_party_id: customerId,
      charge_eur: 121,
      collected_eur: 21,
      participants: [{ display_name: 'Operario auditoría móvil', cost_eur: 40 }],
      resources: [],
    },
  });
  expect([200, 201]).toContain(workResponse.status());

  const invoiceResponse = await request.post(`${apiUrl}/api/v1/professional/invoices`, {
    data: {
      client_operation_id: invoiceOperationId,
      entity_id: invoiceId,
      customer_party_id: customerId,
      invoice_number: 'F-E2E-MOBILE-001',
      issued_on: '2026-10-12',
      due_on: '2026-11-12',
      status: 'issued',
      subtotal_eur: 100,
      tax_eur: 21,
      total_eur: 121,
      works: [{ work_id: workId, amount_eur: 121 }],
    },
  });
  expect([200, 201]).toContain(invoiceResponse.status());
});

for (const width of [360, 390, 430]) {
  test.describe(`mobile ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } });

    for (const route of routes) {
      test(`${route} no desborda horizontalmente ni lanza errores de página`, async ({ page }) => {
        await expectNoHorizontalOverflow(page, route, width);
      });

      test(`${route} no tiene controles críticos minúsculos`, async ({ page }) => {
        const response = await page.goto(route);
        expect(response, `${route} did not produce a navigation response at ${width}px`).not.toBeNull();
        expect(response!.status(), `${route} returned ${response!.status()} at ${width}px`).toBeLessThan(400);
        await expect(page.locator('body')).toBeVisible();
        await page.waitForTimeout(350);

        const tooSmall = await page.locator('button, input:not([type="hidden"]), select, textarea, .primary, .secondary-action').evaluateAll((nodes) =>
          nodes.flatMap((node) => {
            const element = node as HTMLElement;
            const style = getComputedStyle(element);
            if (style.display === 'none' || style.visibility === 'hidden') return [];
            const rect = element.getBoundingClientRect();
            if (rect.width === 0 || rect.height === 0) return [];
            if (rect.width >= 28 && rect.height >= 28) return [];
            return [{
              tag: element.tagName,
              text: (element.textContent || (element as HTMLInputElement).value || '').trim().slice(0, 60),
              width: Math.round(rect.width),
              height: Math.round(rect.height),
            }];
          }),
        );

        expect(tooSmall, `${route} has controls below 28px at ${width}px: ${JSON.stringify(tooSmall)}`).toEqual([]);
      });
    }
  });
}

test.describe('estados vacíos profesionales en móvil', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('cliente sin seleccionar conserva un estado útil', async ({ page }) => {
    await page.goto('/mi-campo/profesional/cliente');
    await expect(page.getByRole('heading', { name: 'Cliente no disponible' })).toBeVisible();
    await expectNoHorizontalOverflow(page, '/mi-campo/profesional/cliente', 360);
  });

  test('documento sin identificar conserva un estado útil', async ({ page }) => {
    await page.goto('/mi-campo/profesional/documento');
    await expect(page.getByRole('heading', { name: 'Documento no disponible' })).toBeVisible();
    await expectNoHorizontalOverflow(page, '/mi-campo/profesional/documento', 360);
  });
});

for (const viewport of [
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1280, height: 900 },
]) {
  test.describe(`${viewport.name} ${viewport.width}px`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } });

    for (const route of widerRoutes) {
      test(`${route} mantiene el layout`, async ({ page }) => {
        await expectNoHorizontalOverflow(page, route, viewport.width);
      });
    }
  });
}
