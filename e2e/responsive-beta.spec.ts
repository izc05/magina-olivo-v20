import { expect, test, type Page } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';

const priorityRoutes = [
  '/',
  '/explorar',
  '/mi-campo',
  '/mi-campo/fincas/las-cenillas',
  `/mi-campo/fincas/ver?id=${fieldId}&source=api`,
  '/mi-campo/hoy',
  '/mi-campo/campana',
  `/radar?fieldId=${fieldId}`,
  '/mi-campo/profesional',
  '/perfil',
  '/noticias',
  '/eventos',
  '/admin',
] as const;

const viewports = [
  { width: 360, height: 800 },
  { width: 390, height: 844 },
  { width: 430, height: 932 },
  { width: 768, height: 1024 },
  { width: 1024, height: 768 },
  { width: 1280, height: 900 },
  { width: 1440, height: 900 },
  { width: 1920, height: 1080 },
] as const;

async function openStable(page: Page, route: string) {
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const response = await page.goto(route);
  expect(response, `${route} did not return a navigation response`).not.toBeNull();
  expect(response!.status(), `${route} returned ${response!.status()}`).toBeLessThan(400);
  await expect(page.locator('body')).toBeVisible();
  await page.waitForTimeout(350);
  expect(pageErrors, `${route} raised browser errors: ${pageErrors.join(' | ')}`).toEqual([]);
}

async function assertNoHorizontalOverflow(page: Page, route: string, width: number) {
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }));

  expect(dimensions.documentWidth, `${route} document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.bodyWidth, `${route} body overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
}

for (const viewport of viewports) {
  test.describe(`responsive beta ${viewport.width}px`, () => {
    test.use({ viewport });

    for (const route of priorityRoutes) {
      test(`${route} uses the viewport without horizontal overflow`, async ({ page }, testInfo) => {
        await openStable(page, route);
        await assertNoHorizontalOverflow(page, route, viewport.width);
        if (route === '/' || viewport.width === 390 || viewport.width === 1440) {
          await page.screenshot({ path: testInfo.outputPath('responsive.png'), fullPage: true });
        }

        const shell = page.locator('.app-shell');
        if (viewport.width >= 1024 && await shell.count()) {
          const shellWidth = await shell.evaluate((element) => element.getBoundingClientRect().width);
          expect(shellWidth, `${route} still looks like a mobile column at ${viewport.width}px`).toBeGreaterThan(viewport.width * .74);
        }
      });
    }

    test('persistent navigation does not cover the content canvas', async ({ page }) => {
      await openStable(page, '/');
      const nav = page.getByRole('navigation', { name: 'Navegación principal' });
      await expect(nav).toBeVisible();

      const metrics = await page.evaluate(() => {
        const navigation = document.querySelector('.bottom-nav') as HTMLElement;
        const content = document.querySelector('.page') as HTMLElement;
        const navRect = navigation.getBoundingClientRect();
        const pageRect = content.getBoundingClientRect();
        const links = [...navigation.querySelectorAll('a')].map((link) => {
          const rect = link.getBoundingClientRect();
          return { width: rect.width, height: rect.height };
        });
        return {
          nav: { top: navRect.top, right: navRect.right, bottom: navRect.bottom, left: navRect.left, width: navRect.width, height: navRect.height },
          page: { left: pageRect.left, paddingBottom: Number.parseFloat(getComputedStyle(content).paddingBottom) },
          labelSize: Number.parseFloat(getComputedStyle(navigation.querySelector('.nav-label') as HTMLElement).fontSize),
          links,
        };
      });

      expect(metrics.labelSize).toBeGreaterThanOrEqual(12);
      expect(metrics.links.every((link) => link.width >= 44 && link.height >= 44)).toBe(true);

      if (viewport.width >= 1024) {
        expect(metrics.nav.height).toBeGreaterThan(metrics.nav.width * 2);
        expect(metrics.page.left).toBeGreaterThanOrEqual(metrics.nav.right + 8);
      } else {
        expect(metrics.nav.width).toBeGreaterThan(metrics.nav.height * 3);
        expect(metrics.page.paddingBottom).toBeGreaterThanOrEqual(metrics.nav.height + 24);
      }
    });
  });
}

test('mobile keyboard focus keeps a visible target and dismisses the dock in a short viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 520 });
  await openStable(page, '/explorar');

  const search = page.getByRole('searchbox', { name: 'Buscar pueblo o localidad' });
  await search.focus();
  await expect(search).toBeFocused();

  const state = await page.evaluate(() => {
    const focused = document.activeElement as HTMLElement;
    const focusStyle = getComputedStyle(focused);
    const navStyle = getComputedStyle(document.querySelector('.bottom-nav') as HTMLElement);
    return {
      outlineWidth: Number.parseFloat(focusStyle.outlineWidth),
      outlineStyle: focusStyle.outlineStyle,
      navOpacity: Number.parseFloat(navStyle.opacity),
      navPointerEvents: navStyle.pointerEvents,
    };
  });

  expect(state.outlineStyle).not.toBe('none');
  expect(state.outlineWidth).toBeGreaterThan(0);
  expect(state.navOpacity).toBe(0);
  expect(state.navPointerEvents).toBe('none');
});

test('editorial regions span the desktop canvas and profile sign-in copy stays readable', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await openStable(page, '/');
  const widths = await page.evaluate(() => {
    const content = document.querySelector('.home-page')!;
    const style = getComputedStyle(content);
    const available = content.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
    return [...document.querySelectorAll('.home-editorial-hero, .home-stories')].map(element => ({ width: element.getBoundingClientRect().width, available }));
  });
  for (const region of widths) expect(region.width).toBeGreaterThan(region.available * .95);
  await openStable(page, '/perfil');
  const copy = page.locator('.profile-progress h2').first();
  if (await copy.count()) expect(await copy.evaluate(element => element.getBoundingClientRect().width)).toBeGreaterThan(240);
});

test('last home action can scroll above the mobile dock and receive keyboard focus', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openStable(page, '/');
  const action = page.locator('.home-page > .territory-banner a');
  await action.focus();
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Tab');
  await expect(action).toBeFocused();
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  const target = await action.boundingBox();
  const dock = await page.locator('.bottom-nav').boundingBox();
  expect(target!.y + target!.height).toBeLessThan(dock!.y);
});
