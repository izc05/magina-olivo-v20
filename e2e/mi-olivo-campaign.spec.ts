import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';

type CampaignPayload = {
  enabled: boolean;
  rule_version: string;
  campaign: { id: string; name: string; status: string } | null;
  missions: Array<{
    id: string;
    completed: boolean;
    progress_current: number;
    progress_target: number;
    reward: number;
  }>;
  campaign_olives_earned: number;
  appearance: {
    selected_badge: string;
    options: Array<{ id: string; unlocked: boolean }>;
  };
};

test('Mi Olivo expone misiones de campaña y personalización segura', async ({ page, request }) => {
  const response = await request.get(`${apiUrl}/api/v1/mi-olivo/campaign`);
  expect(response.status(), response.statusText()).toBe(200);
  const payload = await response.json() as CampaignPayload;

  expect(payload.rule_version).toBe('mi-olivo-v5');
  expect(payload.enabled).toBe(true);
  expect(payload.campaign?.name).toBe('2026/27');
  expect(payload.missions.map((mission) => mission.id)).toEqual([
    'campaign-deliveries-3',
    'campaign-1000kg',
    'campaign-yield',
  ]);
  expect(payload.appearance.options.some((option) => option.id === 'none' && option.unlocked)).toBe(true);

  await page.goto('/mi-olivo');
  await expect(page.getByRole('heading', { name: 'Tu olivo digital' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Lo que ya haces, contado mejor' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Distintivo de tu olivo' })).toBeVisible();
  await expect(page.getByText('2026/27', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /Sin distintivo/ })).toBeVisible();
});

test.describe('Mi Olivo V5 móvil', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('misiones y distintivos no desbordan y mantienen objetivos táctiles', async ({ page }) => {
    await page.goto('/mi-olivo');
    await expect(page.getByRole('heading', { name: 'Lo que ya haces, contado mejor' })).toBeVisible();

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));
    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);

    const badge = page.getByRole('button', { name: /Sin distintivo/ });
    const box = await badge.boundingBox();
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
  });
});
