import { expect, test } from '@playwright/test';

const emptyOlivo = {
  enabled: true,
  rule_version: 'mi-olivo-v1',
  engagement_rule_version: 'mi-olivo-v2',
  balance: 0,
  xp: 0,
  level: 1,
  level_label: 'Brote',
  tree_stage: 1,
  next_level: { level: 2, name: 'Rama nueva', min_xp: 100 },
  progress: { current: 0, target: 100, percent: 0 },
  rhythm: { active_weeks: 0, grace_active: false, label: 'Sin ritmo activo', message: 'Se activa cuando registras una actividad útil. No pierdes puntos por descansar.' },
  today: { earned: 0, cap: 20, remaining: 20 },
  weekly: { earned: 0, goal: 40, percent: 0 },
  earning_actions: [
    { id: 'field-work', title: 'Trabaja en tu finca', detail: 'Registra trabajo real.', reward_label: '+4' },
    { id: 'territory', title: 'Explora Mágina', detail: 'Descubre el territorio.', reward_label: '+3' },
  ],
  missions: [],
  achievements: [],
  rewards: [],
  levels: [
    { level: 1, slug: 'brote', name: 'Brote', min_xp: 0, tree_stage: 1, badge_title: 'Primer brote', description: 'El comienzo de tu historia en Mágina.', unlocked: true },
    { level: 2, slug: 'rama-nueva', name: 'Rama nueva', min_xp: 100, tree_stage: 2, badge_title: 'Rama nueva', description: 'Tu olivo empieza a ganar forma y constancia.', unlocked: false },
    { level: 3, slug: 'olivo-joven', name: 'Olivo joven', min_xp: 250, tree_stage: 3, badge_title: 'Olivo joven', description: 'Primeras raíces firmes y una copa en crecimiento.', unlocked: false },
    { level: 4, slug: 'olivo-arraigado', name: 'Olivo arraigado', min_xp: 450, tree_stage: 4, badge_title: 'Raíces de Mágina', description: 'Tu progreso ya forma parte de tu rutina.', unlocked: false },
    { level: 5, slug: 'olivo-en-flor', name: 'Olivo en flor', min_xp: 700, tree_stage: 5, badge_title: 'Floración', description: 'El árbol entra en una nueva etapa visual.', unlocked: false },
    { level: 6, slug: 'olivo-de-cosecha', name: 'Olivo de cosecha', min_xp: 1000, tree_stage: 6, badge_title: 'Primera cosecha', description: 'La copa madura y aparecen más frutos.', unlocked: false },
    { level: 7, slug: 'olivo-maduro', name: 'Olivo maduro', min_xp: 1400, tree_stage: 7, badge_title: 'Olivo maduro', description: 'Un árbol consolidado por actividad útil y real.', unlocked: false },
    { level: 8, slug: 'olivo-centenario', name: 'Olivo centenario', min_xp: 1900, tree_stage: 8, badge_title: 'Centenario', description: 'Un símbolo de continuidad, territorio y memoria.', unlocked: false },
    { level: 9, slug: 'guardian-del-olivar', name: 'Guardián del Olivar', min_xp: 2500, tree_stage: 9, badge_title: 'Guardián del Olivar', description: 'Has construido una trayectoria excepcional.', unlocked: false },
    { level: 10, slug: 'leyenda-de-magina', name: 'Leyenda de Mágina', min_xp: 3200, tree_stage: 10, badge_title: 'Leyenda de Mágina', description: 'La máxima etapa de esta temporada de Mi Olivo.', unlocked: false },
  ],
  recent: [],
};

async function mockNewAccount(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/mi-olivo', async (route) => {
    if (route.request().method() !== 'GET') return route.continue();
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyOlivo) });
  });
  await page.route('**/api/v1/fields', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ fields: [] }) }));
  await page.route('**/api/v1/campaigns', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ campaigns: [] }) }));
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
    await expect(welcome.getByRole('link', { name: /Completa tu perfil/ })).toHaveAttribute('href', /^\/perfil\/?$/);
    await expect(welcome.getByRole('link', { name: /Añade tu primera finca/ })).toHaveAttribute('href', /^\/mi-campo\/fincas\/nueva\/?$/);
    await expect(welcome.getByRole('link', { name: /Explora Sierra Mágina/ })).toHaveAttribute('href', /^\/explorar\/?$/);
    const timeline = page.getByRole('region', { name: 'Línea temporal real de Mi Olivo' });
    await expect(timeline).toBeVisible();
    await expect(timeline.getByRole('heading', { name: 'Lo que ya ha pasado' })).toBeVisible();
    await expect(timeline.getByText('Tu historia empieza aquí.', { exact: true })).toBeVisible();
    const dimensions = await page.evaluate(() => ({ viewport: document.documentElement.clientWidth, documentWidth: document.documentElement.scrollWidth, bodyWidth: document.body.scrollWidth }));
    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });
});
