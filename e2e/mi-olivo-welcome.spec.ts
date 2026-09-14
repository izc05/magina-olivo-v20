import { expect, test } from '@playwright/test';

const emptyOlivo = {
  enabled: true,
  rule_version: 'mi-olivo-v1',
  engagement_rule_version: 'mi-olivo-v2',
  balance: 0,
  level: 1,
  level_label: 'Brote',
  tree_stage: 1,
  progress: { current: 0, target: 100, percent: 0 },
  rhythm: {
    active_weeks: 0,
    grace_active: false,
    label: 'Sin ritmo activo',
    message: 'Se activa cuando registras una actividad útil. No pierdes puntos por descansar.',
  },
  today: { earned: 0, cap: 20, remaining: 20 },
  weekly: { earned: 0, goal: 40, percent: 0 },
  earning_actions: [
    { id: 'field-work', title: 'Trabaja en tu finca', detail: 'Registra trabajo real.', reward_label: '+4' },
    { id: 'territory', title: 'Explora Mágina', detail: 'Descubre el territorio.', reward_label: '+3' },
  ],
  missions: [],
  achievements: [],
  rewards: [],
  recent: [],
};

async function mockNewAccount(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/mi-olivo', async (route) => {
    if (route.request().method() !== 'GET') return route.continue();
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyOlivo) });
  });

  await page.route('**/api/v1/fields', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ fields: [] }) });
  });

  await page.route('**/api/v1/campaigns', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ campaigns: [] }) });
  });
}

test.describe('Mi Olivo · primera cuenta', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('está disponible sin finca ni campaña y ofrece un inicio real', async ({ page }) => {
    await mockNewAccount(page);
    await page.goto('/mi-olivo');

    const welcome = page.getByRole('region', { name: 'Primeros pasos de Mi Olivo' });
    await expect(welcome).toBeVisible();
    await expect(welcome.getByRole('heading', { name: 'Este es tu olivo' })).toBeVisible();
    await expect(welcome.getByText('Nivel 1 · 0 aceitunas', { exact: true })).toBeVisible();
    await expect(welcome.getByText(/No necesitas tener una finca ni una campaña/)).toBeVisible();

    await expect(welcome.getByRole('link', { name: /Completa tu perfil/ })).toHaveAttribute('href', '/perfil');
    await expect(welcome.getByRole('link', { name: /Añade tu primera finca/ })).toHaveAttribute('href', '/mi-campo/fincas/nueva');
    await expect(welcome.getByRole('link', { name: /Explora Sierra Mágina/ })).toHaveAttribute('href', '/explorar');

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });
});
