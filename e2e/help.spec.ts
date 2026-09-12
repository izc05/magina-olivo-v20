import { expect, test } from '@playwright/test';

const widths = [360, 390, 430];

for (const width of widths) {
  test(`Ayuda mantiene recorrido legible a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    const response = await page.goto('/ayuda');
    expect(response?.status()).toBeLessThan(400);
    await expect(page.getByRole('heading', { name: 'Empieza por lo sencillo' })).toBeVisible();
    await expect(page.getByTestId('help-step')).toHaveCount(6);
    await expect(page.getByRole('link', { name: /Abrir Mi Campo/i })).toHaveAttribute('href', /\/mi-campo\/?$/);
    await expect(page.getByRole('link', { name: /Nueva finca/i })).toHaveAttribute('href', /\/mi-campo\/fincas\/nueva\/?$/);
    await expect(page.getByRole('link', { name: /Añadir documento/i })).toHaveAttribute('href', /\/mi-campo\/documentos\/nuevo\/?$/);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
}

test('Ayuda explica los límites de OCR, planificación y privacidad', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/ayuda');

  await page.getByText('¿Planificar crea un trabajo realizado?').click();
  await expect(page.getByText(/Planificar representa una tarea futura/i)).toBeVisible();

  await page.getByText('¿El OCR guarda automáticamente kilos, importes o fechas?').click();
  await expect(page.getByText(/OCR propone una lectura/i)).toBeVisible();

  await page.getByText('¿Explorar publica mis fincas?').click();
  await expect(page.getByText(/no significa publicar geometrías, cosechas, costes ni documentos privados/i)).toBeVisible();

  await expect(page.getByRole('link', { name: /Trabajo para terceros/i })).toHaveAttribute('href', /\/mi-campo\/profesional\/?$/);
  await expect(page.getByRole('link', { name: /Cuenta y permisos/i })).toHaveAttribute('href', /\/perfil\/?$/);
});
