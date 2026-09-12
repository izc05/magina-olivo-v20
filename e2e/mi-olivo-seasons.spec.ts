import { expect, test } from '@playwright/test';

const activeCampaign = {
  id: '11111111-aaaa-4aaa-8aaa-111111111111',
  workspace_id: 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
  name: '2026/27',
  start_date: '2026-09-01',
  end_date: '2027-03-31',
  status: 'active',
};

const closedCampaign = {
  id: '22222222-bbbb-4bbb-8bbb-222222222222',
  workspace_id: 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
  name: '2025/26',
  start_date: '2025-09-01',
  end_date: '2026-03-31',
  status: 'closed',
};

function summary(campaign: typeof activeCampaign, values: { kg: number; deliveries: number; fields: number; yieldPercent: number | null }) {
  return {
    campaign,
    delivered_kg: values.kg,
    weighted_yield_percent: values.yieldPercent,
    pending_result_kg: 0,
    total_cost_eur: 0,
    accrued_income_eur: 0,
    collected_income_eur: 0,
    delivery_count: values.deliveries,
    field_count: values.fields,
  };
}

async function mockCampaignMemory(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/campaigns', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ campaigns: [activeCampaign, closedCampaign] }),
    });
  });

  await page.route(`**/api/v1/campaigns/${activeCampaign.id}/summary`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary(activeCampaign, { kg: 1842, deliveries: 4, fields: 2, yieldPercent: 21.35 })),
    });
  });

  await page.route(`**/api/v1/campaigns/${closedCampaign.id}/summary`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary(closedCampaign as typeof activeCampaign, { kg: 6320, deliveries: 9, fields: 3, yieldPercent: 22.1 })),
    });
  });
}

test('Mi Olivo V6 conserva memoria de campañas reales sin inventar avance', async ({ page }) => {
  await mockCampaignMemory(page);
  await page.goto('/mi-olivo');

  const memory = page.getByRole('region', { name: 'Memoria real de campañas de Mi Olivo' });
  await expect(memory).toBeVisible();
  await expect(memory.getByRole('heading', { name: 'Tu olivo guarda memoria' })).toBeVisible();
  await expect(memory.getByText('MOMENTO DE CAMPAÑA', { exact: true })).toBeVisible();
  await expect(memory.getByText('Campaña activa', { exact: true })).toBeVisible();
  await expect(memory.getByText('1.842 kg', { exact: true })).toBeVisible();

  const closed = memory.getByRole('article', { name: 'Memoria de campaña 2025/26' });
  await expect(closed).toBeVisible();
  await expect(closed.getByText('6.320 kg', { exact: true })).toBeVisible();
  await expect(closed.getByText('9', { exact: true })).toBeVisible();
  await expect(closed.getByText('22,1 %', { exact: true })).toBeVisible();

  await expect(memory.getByText(/porcentaje de avance/i)).toBeVisible();
});

test.describe('Mi Olivo V6 móvil', () => {
  test.use({ viewport: { width: 360, height: 844 } });

  test('momento de campaña y anillos no desbordan en 360 px', async ({ page }) => {
    await mockCampaignMemory(page);
    await page.goto('/mi-olivo');

    const memory = page.getByRole('region', { name: 'Memoria real de campañas de Mi Olivo' });
    await expect(memory).toBeVisible();
    await expect(memory.getByRole('article', { name: 'Memoria de campaña 2025/26' })).toBeVisible();

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);

    const campaignLink = memory.getByRole('link', { name: 'Ver campañas →' });
    const box = await campaignLink.boundingBox();
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
  });
});
