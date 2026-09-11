import { expect, test } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';
const documentId = 'ffffffff-6666-4666-8666-ffffffffffff';
const customerId = '44444444-aaaa-4aaa-8aaa-444444444444';
const quoteId = '66666666-cccc-4ccc-8ccc-666666666666';
const apiUrl = 'http://127.0.0.1:3001';

const routes = [
  '/',
  '/mi-campo',
  `/mi-campo/fincas/ver?id=${fieldId}&source=api`,
  `/mi-campo/mapa?fieldId=${fieldId}`,
  `/mi-campo/registrar?fieldId=${fieldId}`,
  `/mi-campo/registrar/trabajo?fieldId=${fieldId}`,
  '/mi-campo/hoy',
  '/mi-campo/campana',
  `/mi-campo/documentos/revisar?documentId=${documentId}&fieldId=${fieldId}&source=api`,
  '/mi-campo/profesional',
  `/mi-campo/profesional/cliente?id=${customerId}`,
  '/mi-campo/profesional/presupuestos',
  `/mi-campo/profesional/presupuestos?quoteId=${quoteId}&customerId=${customerId}`,
  '/perfil',
];

test.beforeAll(async ({ request }) => {
  const response = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: 'eeeeeeee-5555-4555-8555-eeeeeeeeeeee',
      entity_id: fieldId,
      name: 'Finca Mobile Audit',
      tree_count: 80,
      water_regime: 'secano',
    },
  });
  expect([200, 201]).toContain(response.status());
});

for (const width of [360, 390, 430]) {
  test.describe(`mobile ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } });

    for (const route of routes) {
      test(`${route} no desborda horizontalmente`, async ({ page }) => {
        await page.goto(route);
        await expect(page.locator('body')).toBeVisible();
        await page.waitForTimeout(350);

        const dimensions = await page.evaluate(() => ({
          viewport: document.documentElement.clientWidth,
          documentWidth: document.documentElement.scrollWidth,
          bodyWidth: document.body.scrollWidth,
        }));

        expect(dimensions.documentWidth, `${route} document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
        expect(dimensions.bodyWidth, `${route} body overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
      });

      test(`${route} no tiene controles críticos minúsculos`, async ({ page }) => {
        await page.goto(route);
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
