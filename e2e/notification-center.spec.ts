import { expect, test } from '@playwright/test';

test('campana abre el centro de avisos y filtra historial real', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });

  await page.route('**/api/v1/notifications?limit=100', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: '81111111-1111-4111-8111-111111111111',
            field_id: '82222222-2222-4222-8222-222222222222',
            field_name: 'Las Cenillas',
            kind: 'agronomy_task_warning',
            source_type: 'scheduled_event',
            title: 'Mágina · Las Cenillas',
            body: 'Tratamiento: evita trabajar con la lluvia prevista.',
            status: 'dispatched',
            created_at: '2026-09-12T06:30:00.000Z',
            dispatched_at: '2026-09-12T06:31:00.000Z',
            action_path: '/mi-campo/hoy/',
          },
          {
            id: '83333333-3333-4333-8333-333333333333',
            field_id: null,
            field_name: null,
            kind: 'financial_collection_pending',
            source_type: 'harvest_settlement',
            title: 'Mágina · cobro pendiente',
            body: 'Liquidación LIQ-26: 760 € pendientes.',
            status: 'pending',
            created_at: '2026-09-12T06:20:00.000Z',
            dispatched_at: null,
            action_path: '/mi-campo/campana/',
          },
          {
            id: '84444444-4444-4444-8444-444444444444',
            field_id: null,
            field_name: null,
            kind: 'document_ocr_failed',
            source_type: 'document',
            title: 'Mágina · documento sin leer',
            body: 'Albarán 44: el OCR ha fallado y requiere revisión.',
            status: 'failed',
            created_at: '2026-09-12T06:10:00.000Z',
            dispatched_at: null,
            action_path: null,
          },
        ],
        counts: { total: 3, pending: 1, dispatched: 1, suppressed: 0, failed: 1 },
        semantics: 'delivery_history_not_read_state',
      }),
    });
  });

  await page.goto('/');
  const bell = page.getByRole('link', { name: 'Abrir centro de avisos' });
  await expect(bell).toHaveAttribute('href', '/mi-campo/avisos');
  await bell.click();

  await expect(page.getByRole('heading', { name: 'Centro de avisos' })).toBeVisible();
  await expect(page.getByText('Mágina · Las Cenillas', { exact: true })).toBeVisible();
  await expect(page.getByText('Mágina · cobro pendiente', { exact: true })).toBeVisible();
  await expect(page.getByText('Mágina · documento sin leer', { exact: true })).toBeVisible();
  await expect(page.getByText(/no significa que lo hayas leído/i)).toBeVisible();

  const fieldAction = page.getByRole('link', { name: 'Abrir información →' }).first();
  await expect(fieldAction).toHaveAttribute('href', '/mi-campo/hoy/');

  await page.getByLabel('Filtrar avisos por tipo').selectOption('documents');
  await expect(page.getByText('Mágina · documento sin leer', { exact: true })).toBeVisible();
  await expect(page.getByText('Mágina · cobro pendiente', { exact: true })).toHaveCount(0);

  await page.getByLabel('Filtrar avisos por estado').selectOption('dispatched');
  await expect(page.getByText('No hay avisos con estos filtros')).toBeVisible();

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});
