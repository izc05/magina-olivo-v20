import { expect, test } from '@playwright/test';

function isoToday() {
  return new Date().toISOString().slice(0, 10);
}

async function assertNoDocumentOverflow(page: import('@playwright/test').Page) {
  await expect.poll(async () => page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))).toEqual(expect.objectContaining({ clientWidth: expect.any(Number) }));
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1);
}

test('Inicio y Hoy conectan finca, actividad y tarea real', async ({ page }, testInfo) => {
  const suffix = `${testInfo.workerIndex}`;
  const farmName = `Finca Inicio Hoy ${suffix}`;
  const workTitle = `Desbroce diario ${suffix}`;
  const taskTitle = `Revisar lindero ${suffix}`;
  const today = isoToday();

  await page.goto('/mi-campo/fincas/nueva');
  await expect(page.getByRole('heading', { name: '¿Cómo llamáis a esta finca?' })).toBeVisible();
  await page.getByLabel('Nombre de la finca *').fill(farmName);
  await page.getByLabel('Nº de olivas *').fill('85');
  const placeSelect = page.getByLabel('Pueblo / localidad');
  await expect.poll(async () => placeSelect.locator('option').count()).toBeGreaterThan(1);
  await placeSelect.selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Continuar →' }).click();
  await page.getByRole('button', { name: 'Guardar finca →' }).click();
  await expect(page.getByText('FINCA GUARDADA EN MI CAMPO')).toBeVisible();

  const registerHref = await page.getByRole('link', { name: /Registrar trabajo/ }).getAttribute('href');
  expect(registerHref).toBeTruthy();
  const fieldId = new URL(registerHref!, 'http://127.0.0.1:3000').searchParams.get('fieldId');
  expect(fieldId).toBeTruthy();

  await page.goto(`/mi-campo/registrar/trabajo?fieldId=${encodeURIComponent(fieldId!)}&source=api`);
  await expect(page.getByRole('heading', { name: 'Registrar trabajo' })).toBeVisible();
  await page.locator('input[name="date"]').fill(today);
  await page.locator('input[name="title"]').fill(workTitle);
  await page.locator('input[name="workerName"]').fill('Cuadrilla diaria');
  await page.locator('input[name="quantity"]').fill('1');
  await page.locator('input[name="laborCost"]').fill('75');
  await page.getByRole('button', { name: 'Guardar trabajo →' }).click();
  await expect(page.getByRole('heading', { name: 'Trabajo registrado' })).toBeVisible();

  await page.goto(`/mi-campo/planificar?fieldId=${encodeURIComponent(fieldId!)}&source=api`);
  await expect(page.getByRole('heading', { name: '¿Qué quieres dejar preparado?' })).toBeVisible();
  await page.getByRole('combobox', { name: /^Tipo\b/ }).selectOption('observation');
  await page.getByRole('textbox', { name: 'Tarea' }).fill(taskTitle);
  await page.locator('input[type="datetime-local"]').fill(`${today}T12:00`);
  await page.getByRole('button', { name: 'Guardar tarea' }).click();
  await expect(page.getByText('Tarea guardada. Ya forma parte de tu planificación.')).toBeVisible();

  await page.goto('/');
  const activitySection = page.getByRole('heading', { name: 'Actividad reciente' }).locator('xpath=ancestor::section');
  await expect(activitySection).toBeVisible();
  await expect(activitySection.getByText(workTitle, { exact: true }).first()).toBeVisible();
  await expect(page.getByText(`Siguiente: ${taskTitle}`, { exact: true })).toBeVisible();
  await expect(page.getByText(farmName, { exact: false }).first()).toBeVisible();

  const activeFarmHref = await activitySection.getByRole('link', { name: 'Abrir finca', exact: true }).getAttribute('href');
  expect(activeFarmHref).toBeTruthy();
  const activeFieldId = new URL(activeFarmHref!, 'http://127.0.0.1:3000').searchParams.get('id');
  expect(activeFieldId).toBeTruthy();
  if (testInfo.retry === 0) {
    expect(activeFieldId).toBe(fieldId);
  }

  await page.goto('/mi-campo/hoy');
  await expect(page.getByRole('heading', { name: 'Hoy', exact: true })).toBeVisible();
  await expect(page.getByText(taskTitle, { exact: true }).first()).toBeVisible();

  const planNewHref = await page.getByRole('link', { name: 'Planificar nueva', exact: true }).getAttribute('href');
  expect(planNewHref).toBeTruthy();
  const planNewUrl = new URL(planNewHref!, 'http://127.0.0.1:3000');
  expect(planNewUrl.pathname.replace(/\/+$/, '')).toBe('/mi-campo/planificar');
  expect(planNewUrl.searchParams.get('fieldId')).toBe(activeFieldId);
  expect(planNewUrl.searchParams.get('source')).toBe('api');

  const todayFilter = page.getByRole('button', { name: /^Hoy \(\d+\)$/ });
  await expect(todayFilter).toBeVisible();
  await todayFilter.click();
  await expect(todayFilter).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText(taskTitle, { exact: true }).first()).toBeVisible();
});

for (const width of [360, 390, 430]) {
  test(`Inicio y Hoy no desbordan a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/');
    await expect(page.locator('main')).toBeVisible();
    await assertNoDocumentOverflow(page);

    await page.goto('/mi-campo/hoy');
    await expect(page.getByRole('heading', { name: 'Hoy', exact: true })).toBeVisible();
    await assertNoDocumentOverflow(page);
  });
}
