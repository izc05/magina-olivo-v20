import { expect, test } from '@playwright/test';

const latest = {
  id: '33333333-cccc-4ccc-8ccc-333333333333',
  workspace_id: 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
  name: '2025/26',
  start_date: '2025-09-01',
  end_date: '2026-03-31',
  status: 'closed',
};

const previous = {
  id: '44444444-dddd-4ddd-8ddd-444444444444',
  workspace_id: 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
  name: '2024/25',
  start_date: '2024-09-01',
  end_date: '2025-03-31',
  status: 'closed',
};

function summary(campaign: typeof latest, kg: number, deliveries: number, fields: number, yieldPercent: number) {
  return {
    campaign,
    delivered_kg: kg,
    weighted_yield_percent: yieldPercent,
    pending_result_kg: 0,
    total_cost_eur: 0,
    accrued_income_eur: 0,
    collected_income_eur: 0,
    delivery_count: deliveries,
    field_count: fields,
  };
}

test('Mi Olivo compara solo las dos últimas campañas cerradas con cifras reales', async ({ page }) => {
  await page.route('**/api/v1/campaigns', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ campaigns: [previous, latest] }),
    });
  });

  await page.route(`**/api/v1/campaigns/${latest.id}/summary`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary(latest, 6320, 9, 3, 22.1)),
    });
  });

  await page.route(`**/api/v1/campaigns/${previous.id}/summary`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary(previous as typeof latest, 5900, 8, 3, 20.5)),
    });
  });

  await page.goto('/mi-olivo');

  const comparison = page.getByRole('region', { name: 'Comparativa real de campañas de Mi Olivo' });
  await expect(comparison).toBeVisible();
  await expect(comparison.getByRole('heading', { name: 'Dos campañas, lado a lado' })).toBeVisible();
  await expect(comparison.getByRole('heading', { name: '2025/26' })).toBeVisible();
  await expect(comparison.getByRole('heading', { name: '2024/25' })).toBeVisible();
  await expect(comparison.getByText('6320 kg', { exact: true })).toBeVisible();
  await expect(comparison.getByText('5900 kg', { exact: true })).toBeVisible();
  await expect(comparison.getByText('+420 kg', { exact: true })).toBeVisible();
  await expect(comparison.getByText('+1,6%', { exact: true })).toBeVisible();
  await expect(comparison.getByText(/no calificamos una campaña como mejor o peor/i)).toBeVisible();
});
