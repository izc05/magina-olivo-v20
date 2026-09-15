import { expect, test, type Page } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';

type MiOlivoApi = {
  enabled: boolean;
  balance: number;
  level: number;
  rhythm: { active_weeks: number; grace_active: boolean };
  today: { earned: number; cap: number; remaining: number };
  weekly: { earned: number; goal: number; percent: number };
  earning_actions: Array<{ id: string; reward_label: string }>;
  missions: Array<{ id: string; completed: boolean; progress_current: number; progress_target: number }>;
  rewards: Array<{ id: string; unlocked: boolean }>;
};

type AwardResponse = {
  awarded: boolean;
  points: number;
  status: string;
  daily: { earned: number; cap: number; remaining: number };
};

async function assertNoDuplicateIds(page: Page) {
  const duplicateIds = await page.evaluate(() => {
    const counts = new Map<string, number>();
    document.querySelectorAll<HTMLElement>('[id]').forEach((element) => {
      if (!element.id) return;
      counts.set(element.id, (counts.get(element.id) ?? 0) + 1);
    });
    return Array.from(counts.entries())
      .filter(([, count]) => count > 1)
      .map(([id, count]) => `${id}:${count}`);
  });

  expect(duplicateIds).toEqual([]);
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  const first = await request.get(`${apiUrl}/api/v1/mi-olivo`);
  expect(first.status(), first.statusText()).toBe(200);
  const firstBody = await first.json() as MiOlivoApi;
  expect(firstBody.enabled).toBe(true);
  expect(firstBody.balance).toBeGreaterThanOrEqual(25);
  expect(firstBody.rewards.some((reward) => reward.unlocked)).toBe(true);
  expect(firstBody.today.cap).toBe(20);
  expect(firstBody.weekly.goal).toBe(40);
  expect(firstBody.earning_actions.map((action) => action.id)).toEqual(expect.arrayContaining([
    'field-work', 'territory', 'content', 'weather', 'learning',
  ]));

  const repeated = await request.get(`${apiUrl}/api/v1/mi-olivo`);
  expect(repeated.status(), repeated.statusText()).toBe(200);
  const repeatedBody = await repeated.json() as MiOlivoApi;
  expect(repeatedBody.balance).toBe(firstBody.balance);

  const learningSource = 'consejo:recorrido-observacion';
  const award = await request.post(`${apiUrl}/api/v1/mi-olivo/events`, {
    data: { event_type: 'learning_completed', source_id: learningSource },
  });
  expect(award.status(), award.statusText()).toBe(200);
  const awardBody = await award.json() as AwardResponse;
  expect(['awarded', 'already_recognized']).toContain(awardBody.status);
  expect(awardBody.points).toBe(awardBody.status === 'awarded' ? 5 : 0);
  expect(awardBody.awarded).toBe(awardBody.status === 'awarded');
  expect(awardBody.daily.cap).toBe(20);

  const duplicate = await request.post(`${apiUrl}/api/v1/mi-olivo/events`, {
    data: { event_type: 'learning_completed', source_id: learningSource },
  });
  expect(duplicate.status(), duplicate.statusText()).toBe(200);
  const duplicateBody = await duplicate.json() as AwardResponse;
  expect(duplicateBody.awarded).toBe(false);
  expect(duplicateBody.points).toBe(0);
  expect(duplicateBody.status).toBe('already_recognized');

  const inventedTerritory = await request.post(`${apiUrl}/api/v1/mi-olivo/events`, {
    data: { event_type: 'territory_viewed', source_id: 'pueblo:no-existe' },
  });
  expect(inventedTerritory.status(), inventedTerritory.statusText()).toBe(200);
  const inventedTerritoryBody = await inventedTerritory.json() as AwardResponse;
  expect(inventedTerritoryBody.awarded).toBe(false);
  expect(inventedTerritoryBody.points).toBe(0);

  const invalid = await request.post(`${apiUrl}/api/v1/mi-olivo/events`, {
    data: { event_type: 'territory_viewed', source_id: 'not-a-territory-source' },
  });
  expect(invalid.status(), invalid.statusText()).toBe(400);

  const pause = await request.put(`${apiUrl}/api/v1/mi-olivo/preferences`, {
    data: { enabled: false },
  });
  expect(pause.status(), pause.statusText()).toBe(200);
  expect((await pause.json()).enabled).toBe(false);

  const paused = await request.get(`${apiUrl}/api/v1/mi-olivo`);
  expect(paused.status(), paused.statusText()).toBe(200);
  expect((await paused.json()).enabled).toBe(false);

  const resume = await request.put(`${apiUrl}/api/v1/mi-olivo/preferences`, {
    data: { enabled: true },
  });
  expect(resume.status(), resume.statusText()).toBe(200);
  expect((await resume.json()).enabled).toBe(true);
});

test('premia una consulta real del radar', async ({ page }) => {
  await page.goto('/radar');
  const rewardToast = page.getByRole('status');
  await expect(rewardToast).toContainText('+2 aceitunas', { timeout: 12_000 });
  await expect(rewardToast).toContainText('Clima revisado antes de organizar el campo');
});

for (const width of [360, 390, 430]) {
  test.describe(`Mi Olivo ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } });

    test('cumple el contrato visual V7 y no desborda horizontalmente', async ({ page }) => {
      await page.goto('/mi-olivo');

      await expect(page.getByRole('heading', { name: 'Mi Olivo', exact: true }).first()).toBeVisible();
      await expect(page.getByText('SIERRA MÁGINA · TU PROGRESO', { exact: true })).toBeVisible();
      await expect(page.getByRole('img', { name: /Olivo digital en fase/ }).first()).toBeVisible();
      await expect(page.getByText('TU OLIVO AHORA', { exact: true })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'La vida de tu olivo' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Del olivo al territorio' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Acciones que cuidan tu progreso' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Pequeños pasos útiles' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Tu colección' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Evolución completa del olivo' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Movimientos de aceitunas' })).toBeVisible();

      const tabs = page.getByRole('navigation', { name: 'Secciones de Mi Olivo' });
      await expect(tabs).toContainText('Resumen');
      await expect(tabs).toContainText('Cuidados');
      await expect(tabs).toContainText('Historia');
      await expect(tabs).toContainText('Recompensas');

      const forecast = page.getByRole('region', { name: 'Previsión real que ambienta Mi Olivo' });
      await expect(forecast).toBeVisible();
      await expect(forecast).toContainText('AEMET · PREVISIÓN DE HOY');
      await expect(forecast).toContainText('Finca Mobile Audit · Huelma');
      await expect(forecast).toContainText('35 %');
      await expect(forecast).toContainText('14° / 27°');
      await expect(forecast).toContainText('20 km/h');

      await assertNoDuplicateIds(page);

      const dimensions = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth,
      }));
      expect(dimensions.documentWidth, `document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
      expect(dimensions.bodyWidth, `body overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);

      const pauseButton = page.getByRole('button', { name: 'Pausar Mi Olivo' });
      await expect(pauseButton).toBeVisible();
      const box = await pauseButton.boundingBox();
      expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
    });
  });
}
