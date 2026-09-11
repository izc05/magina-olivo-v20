import { expect, test } from '@playwright/test';

const routes = [
  '/',
  '/mi-campo',
  '/mi-campo/hoy',
  '/mi-campo/campana',
  '/perfil',
];

for (const width of [360, 390, 430]) {
  test.describe(`mobile ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } });

    for (const route of routes) {
      test(`${route} no desborda horizontalmente`, async ({ page }) => {
        await page.goto(route);
        await expect(page.locator('body')).toBeVisible();
        await page.waitForTimeout(250);

        const dimensions = await page.evaluate(() => ({
          viewport: document.documentElement.clientWidth,
          documentWidth: document.documentElement.scrollWidth,
          bodyWidth: document.body.scrollWidth,
        }));

        expect(dimensions.documentWidth, `${route} document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
        expect(dimensions.bodyWidth, `${route} body overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
      });
    }
  });
}
