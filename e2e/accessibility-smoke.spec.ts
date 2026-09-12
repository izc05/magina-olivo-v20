import { expect, test, type Page } from '@playwright/test';

const fieldId = 'dddddddd-4444-4444-8444-dddddddddddd';

const routes = [
  '/',
  '/explorar',
  '/noticias',
  '/eventos',
  '/pueblos',
  '/mercado',
  '/cooperativas',
  '/servicios',
  '/consejos',
  '/planes',
  '/ayuda',
  '/herramientas',
  '/mi-olivo',
  '/mi-campo',
  `/mi-campo/fincas/ver?id=${fieldId}&source=api`,
  '/mi-campo/hoy',
  '/mi-campo/campana',
  '/mi-campo/profesional',
  '/perfil',
  `/radar?fieldId=${fieldId}`,
] as const;

async function gotoStable(page: Page, route: string) {
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  const response = await page.goto(route);
  expect(response, `${route} did not produce a navigation response`).not.toBeNull();
  expect(response!.status(), `${route} returned ${response!.status()}`).toBeLessThan(400);
  await expect(page.locator('body')).toBeVisible();
  await page.waitForTimeout(450);
  expect(pageErrors, `${route} raised browser page errors: ${pageErrors.join(' | ')}`).toEqual([]);
}

async function auditSemantics(page: Page, route: string) {
  await gotoStable(page, route);

  await expect(page.locator('main'), `${route} must expose one main landmark`).toHaveCount(1);
  await expect(page.locator('main')).toBeVisible();
  expect(await page.locator('h1').count(), `${route} must expose a page-level h1`).toBeGreaterThanOrEqual(1);

  const duplicateIds = await page.locator('[id]').evaluateAll((nodes) => {
    const counts = new Map<string, number>();
    for (const node of nodes) {
      const id = (node as HTMLElement).id.trim();
      if (!id) continue;
      counts.set(id, (counts.get(id) ?? 0) + 1);
    }
    return [...counts.entries()].filter(([, count]) => count > 1);
  });
  expect(duplicateIds, `${route} contains duplicate DOM ids: ${JSON.stringify(duplicateIds)}`).toEqual([]);

  const imagesWithoutAlt = await page.locator('img').evaluateAll((nodes) =>
    nodes
      .filter((node) => !node.hasAttribute('alt'))
      .map((node) => ({ src: (node as HTMLImageElement).src, className: (node as HTMLElement).className })),
  );
  expect(imagesWithoutAlt, `${route} contains images without an alt attribute: ${JSON.stringify(imagesWithoutAlt)}`).toEqual([]);

  const unnamedActions = await page.locator('a[href], button, [role="button"], [role="link"]').evaluateAll((nodes) => {
    function isVisible(element: HTMLElement) {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
    }

    function labelledByText(element: HTMLElement) {
      const ids = (element.getAttribute('aria-labelledby') ?? '').trim().split(/\s+/).filter(Boolean);
      return ids.map((id) => document.getElementById(id)?.textContent?.trim() ?? '').join(' ').trim();
    }

    function accessibleText(element: HTMLElement) {
      const aria = element.getAttribute('aria-label')?.trim() ?? '';
      const labelled = labelledByText(element);
      const title = element.getAttribute('title')?.trim() ?? '';
      const text = element.textContent?.trim() ?? '';
      const imageAlt = [...element.querySelectorAll('img[alt]')]
        .map((image) => image.getAttribute('alt')?.trim() ?? '')
        .join(' ')
        .trim();
      return aria || labelled || text || imageAlt || title;
    }

    return nodes.flatMap((node) => {
      const element = node as HTMLElement;
      if (!isVisible(element)) return [];
      if (element.getAttribute('aria-hidden') === 'true') return [];
      if (element instanceof HTMLButtonElement && element.disabled) return [];
      if (accessibleText(element)) return [];
      return [{ tag: element.tagName, role: element.getAttribute('role'), className: element.className }];
    });
  });
  expect(unnamedActions, `${route} contains visible actions without an accessible name: ${JSON.stringify(unnamedActions)}`).toEqual([]);

  const unlabeledFields = await page.locator('input:not([type="hidden"]), select, textarea').evaluateAll((nodes) => {
    function isVisible(element: HTMLElement) {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
    }

    return nodes.flatMap((node) => {
      const element = node as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
      if (!isVisible(element)) return [];
      if (element.disabled) return [];
      const type = element instanceof HTMLInputElement ? element.type : '';
      if (['submit', 'reset', 'button', 'image'].includes(type)) return [];

      const aria = element.getAttribute('aria-label')?.trim();
      const labelledBy = element.getAttribute('aria-labelledby')?.trim();
      const labels = 'labels' in element ? element.labels : null;
      const hasRealLabel = Boolean(aria || labelledBy || (labels && labels.length > 0));
      if (hasRealLabel) return [];

      return [{
        tag: element.tagName,
        type,
        name: element.getAttribute('name'),
        id: element.id,
        placeholder: element.getAttribute('placeholder'),
      }];
    });
  });
  expect(unlabeledFields, `${route} contains visible form fields without a real label: ${JSON.stringify(unlabeledFields)}`).toEqual([]);
}

for (const route of routes) {
  test(`${route} cumple el contrato accesible básico`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await auditSemantics(page, route);
  });
}

for (const [route, label] of [
  ['/', 'Inicio'],
  ['/mi-campo', 'Mi Campo'],
  ['/explorar', 'Explorar'],
  ['/perfil', 'Más'],
] as const) {
  test(`${route} expone la sección actual del dock`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await gotoStable(page, route);
    const link = page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', { name: label });
    await expect(link).toHaveAttribute('aria-current', 'page');
  });
}

test('la navegación principal conserva foco visible con teclado', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await gotoStable(page, '/');

  await page.locator('body').click({ position: { x: 2, y: 2 } });
  await page.keyboard.press('Tab');

  const firstFocus = await page.evaluate(() => {
    const element = document.activeElement as HTMLElement | null;
    if (!element || element === document.body) return null;
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return {
      tag: element.tagName,
      text: element.textContent?.trim().slice(0, 80) ?? '',
      visible: rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none',
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      boxShadow: style.boxShadow,
    };
  });

  expect(firstFocus, 'Tab must move focus to an interactive element').not.toBeNull();
  expect(firstFocus!.visible).toBe(true);
  const outlineWidth = Number.parseFloat(firstFocus!.outlineWidth) || 0;
  expect(
    (firstFocus!.outlineStyle !== 'none' && outlineWidth > 0) || firstFocus!.boxShadow !== 'none',
    `Focused element has no visible focus indicator: ${JSON.stringify(firstFocus)}`,
  ).toBe(true);

  const visited = new Set<string>();
  for (let index = 0; index < 8; index += 1) {
    const descriptor = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null;
      if (!element || element === document.body) return 'body';
      return `${element.tagName}:${element.getAttribute('href') ?? element.getAttribute('aria-label') ?? element.textContent?.trim().slice(0, 40) ?? ''}`;
    });
    visited.add(descriptor);
    await page.keyboard.press('Tab');
  }
  expect(visited.size, `Keyboard focus did not advance through enough controls: ${JSON.stringify([...visited])}`).toBeGreaterThanOrEqual(4);
});

test.describe('reduced motion', () => {
  test.use({ reducedMotion: 'reduce', viewport: { width: 390, height: 844 } });

  test('respeta la preferencia de movimiento reducido', async ({ page }) => {
    await gotoStable(page, '/');

    const result = await page.evaluate(() => {
      const mediaMatches = matchMedia('(prefers-reduced-motion: reduce)').matches;
      const candidate = document.querySelector('.bottom-nav a, .primary, .icon-button') as HTMLElement | null;
      if (!candidate) return { mediaMatches, transitionSeconds: null, animationSeconds: null };
      const style = getComputedStyle(candidate);
      const toSeconds = (value: string) => {
        const first = value.split(',')[0]?.trim() ?? '0s';
        if (first.endsWith('ms')) return (Number.parseFloat(first) || 0) / 1000;
        return Number.parseFloat(first) || 0;
      };
      return {
        mediaMatches,
        transitionSeconds: toSeconds(style.transitionDuration),
        animationSeconds: toSeconds(style.animationDuration),
      };
    });

    expect(result.mediaMatches).toBe(true);
    expect(result.transitionSeconds ?? 0).toBeLessThanOrEqual(0.001);
    expect(result.animationSeconds ?? 0).toBeLessThanOrEqual(0.001);
  });
});
