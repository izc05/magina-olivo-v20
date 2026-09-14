import { expect, test } from '@playwright/test';

const fieldId = '55555555-eeee-4eee-8eee-555555555555';

const activity = [
  {
    id: 'a1111111-1111-4111-8111-111111111111',
    occurred_at: '2026-09-10T08:30:00.000Z',
    domain_type: 'irrigation',
    domain_record_id: 'b1111111-1111-4111-8111-111111111111',
    title: 'Riego',
    summary: '2 h · sector norte',
    icon_key: 'irrigation',
  },
  {
    id: 'a2222222-2222-4222-8222-222222222222',
    occurred_at: '2026-09-05T07:00:00.000Z',
    domain_type: 'pruning',
    domain_record_id: 'b2222222-2222-4222-8222-222222222222',
    title: 'Poda',
    summary: 'formación · 2 personas',
    icon_key: 'pruning',
  },
  {
    id: 'a3333333-3333-4333-8333-333333333333',
    occurred_at: '2026-08-29T09:00:00.000Z',
    domain_type: 'treatment',
    domain_record_id: 'b3333333-3333-4333-8333-333333333333',
    title: 'Tratamiento · preventivo',
    summary: 'Producto registrado',
    icon_key: 'treatment',
  },
];

test('Mi Olivo muestra cuidados solo desde actividad real de Mi Campo', async ({ page }) => {
  await page.route('**/api/v1/fields', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ fields: [{ id: fieldId, name: 'Las Lomas' }] }),
    });
  });

  await page.route(`**/api/v1/fields/${fieldId}/activity`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ field: { id: fieldId, name: 'Las Lomas' }, items: activity }),
    });
  });

  await page.goto('/mi-olivo');

  const care = page.getByRole('region', { name: 'Cuidados reales de Mi Olivo' });
  await expect(care).toBeVisible();
  await expect(care.getByRole('heading', { name: 'Lo que realmente has registrado' })).toBeVisible();
  await expect(care.getByText('Riego', { exact: true }).first()).toBeVisible();
  await expect(care.getByText('Poda', { exact: true }).first()).toBeVisible();
  await expect(care.getByText('Tratamientos', { exact: true })).toBeVisible();
  await expect(care.getByText('Las Lomas').first()).toBeVisible();
  await expect(care.getByText('2 h · sector norte')).toBeVisible();
  await expect(care.getByText(/No estimamos cuándo se realizó por última vez/).first()).toBeVisible();
});
