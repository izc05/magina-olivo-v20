import { expect, test } from '@playwright/test';

const apiUrl = 'http://127.0.0.1:3001';
const fieldId = '91919191-9191-4191-8191-919191919191';
const farmName = 'Finca Planificar E2E';

test.beforeAll(async ({ request }) => {
  const response = await request.post(`${apiUrl}/api/v1/fields`, {
    data: {
      client_operation_id: '92929292-9292-4292-8292-929292929292',
      entity_id: fieldId,
      name: farmName,
      tree_count: 95,
      water_regime: 'secano',
    },
  });
  expect([200, 201]).toContain(response.status());
});

test('planifica una tarea y solo la completa al registrar el trabajo real', async ({ page }, testInfo) => {
  const taskTitle = `Desbroce planificado E2E ${testInfo.retry + 1}`;
  await page.goto(`/mi-campo/planificar?fieldId=${fieldId}&source=api`);

  await expect(page.getByRole('heading', { name: 'Planificar', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '¿Qué quieres dejar preparado?' })).toBeVisible();
  await expect(page.getByLabel('Finca').first()).toHaveValue(fieldId);

  await page.getByLabel('Tarea').fill(taskTitle);
  await page.getByLabel('Fecha y hora').fill('2026-09-14T08:00');
  await page.getByLabel('Notas').fill('Linde norte · prueba de planificación');
  await page.getByRole('button', { name: 'Guardar tarea' }).click();

  await expect(page.getByText('Tarea guardada. Ya forma parte de tu planificación.')).toBeVisible();
  const taskCard = page.locator('article').filter({ hasText: taskTitle }).first();
  await expect(taskCard).toContainText(farmName);
  await expect(taskCard).toContainText('Pendiente');

  const executeLink = taskCard.getByRole('link', { name: 'Registrar realizado →' });
  const executeHref = await executeLink.getAttribute('href');
  expect(executeHref).toContain(`fieldId=${fieldId}`);
  expect(executeHref).toContain('plannedEventId=');
  await executeLink.click();

  await expect(page.getByRole('heading', { name: 'Registrar trabajo' })).toBeVisible();
  await page.locator('input[name="date"]').fill('2026-09-14');
  await page.locator('input[name="title"]').fill(`Desbroce realizado E2E ${testInfo.retry + 1}`);
  await page.locator('input[name="workerName"]').fill('Cuadrilla Planificar E2E');
  await page.locator('input[name="quantity"]').fill('1');
  await page.getByRole('button', { name: 'Guardar trabajo →' }).click();

  await expect(page.getByRole('heading', { name: 'Trabajo registrado' })).toBeVisible();
  await expect(page.getByText('La tarea prevista ha quedado enlazada al trabajo realizado.')).toBeVisible();

  await page.goto(`/mi-campo/planificar?fieldId=${fieldId}&source=api`);
  await page.getByRole('button', { name: 'Realizadas' }).click();
  const completedCard = page.locator('article').filter({ hasText: taskTitle }).first();
  await expect(completedCard).toContainText('Realizada');
  await expect(completedCard).toContainText('Enlazada con el registro realizado');
  await expect(completedCard.getByRole('link', { name: 'Registrar realizado →' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Calendario' }).click();
  await expect(page.getByLabel(/Calendario septiembre de 2026/i)).toBeVisible();
  await expect(page.getByTitle(`${farmName}: ${taskTitle}`)).toBeVisible();
});

for (const width of [360, 390, 430]) {
  test(`Planificar no desborda el documento a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await page.goto(`/mi-campo/planificar?fieldId=${fieldId}&source=api`);
    await expect(page.getByRole('heading', { name: 'Planificar', exact: true })).toBeVisible();
    await page.waitForTimeout(250);

    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));
    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
    expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  });
}
