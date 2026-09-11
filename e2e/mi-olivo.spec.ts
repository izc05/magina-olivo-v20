import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';

type MiOlivoApi = {
  enabled: boolean;
  balance: number;
  level: number;
  rhythm: { active_weeks: number; grace_active: boolean };
  missions: Array<{ id: string; completed: boolean; progress_current: number; progress_target: number }>;
  rewards: Array<{ id: string; unlocked: boolean }>;
};

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  const first = await request.get(`${apiUrl}/api/v1/mi-olivo`);
  expect(first.status(), first.statusText()).toBe(200);
  const firstBody = await first.json() as MiOlivoApi;
  expect(firstBody.enabled).toBe(true);
  expect(firstBody.balance).toBeGreaterThanOrEqual(25);
  expect(firstBody.rewards.some((reward) => reward.unlocked)).toBe(true);

  const repeated = await request.get(`${apiUrl}/api/v1/mi-olivo`);
  expect(repeated.status(), repeated.statusText()).toBe(200);
  const repeatedBody = await repeated.json() as MiOlivoApi;
  expect(repeatedBody.balance).toBe(firstBody.balance);

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

for (const width of [360, 390, 430]) {
  test.describe(`Mi Olivo ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } });

    test('muestra progreso real sin desbordamiento horizontal', async ({ page }) => {
      await page.goto('/mi-olivo');

      await expect(page.getByRole('heading', { name: 'Tu olivo digital' })).toBeVisible();
      await expect(page.getByRole('img', { name: /Olivo digital en fase/ })).toBeVisible();
      await expect(page.getByText('aceitunas', { exact: true })).toBeVisible();
      await expect(page.getByText(/Ritmo del cuaderno ·/)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Pequeños pasos útiles' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Distintivos digitales' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Por qué ha crecido' })).toBeVisible();

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
