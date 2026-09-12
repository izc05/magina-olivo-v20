import { expect, test } from '@playwright/test';

const widths = [360, 390, 430];

test.describe('Herramientas rápidas del agricultor', () => {
  for (const width of widths) {
    test(`calcula localmente y no desborda a ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/herramientas');

      await expect(page.getByRole('heading', { name: 'Calcula sin guardar nada' })).toBeVisible();
      await expect(page.getByText('Los cálculos se hacen en este navegador y no se envían ni se guardan.')).toBeVisible();
      await expect(page.getByText(/No calcula dosis, mezclas, tratamientos/i)).toBeVisible();

      const surfaceInput = page.getByLabel('Superficie', { exact: true });
      await surfaceInput.fill('12500');
      await expect(page.getByTestId('surface-result')).toContainText('1,25 ha');
      await expect(page.getByTestId('surface-result')).toContainText('12.500 m²');

      await page.getByRole('combobox', { name: 'Unidad', exact: true }).selectOption('ha');
      await surfaceInput.fill('1,5');
      await expect(page.getByTestId('surface-result')).toContainText('1,5 ha');
      await expect(page.getByTestId('surface-result')).toContainText('15.000 m²');

      await page.getByLabel('Distancia entre filas (m)', { exact: true }).fill('7');
      await page.getByLabel('Distancia entre árboles (m)', { exact: true }).fill('7');
      await page.getByLabel('Superficie de referencia (ha) · opcional', { exact: true }).fill('2');
      await expect(page.getByTestId('density-result')).toContainText('204 olivos/ha');
      await expect(page.getByTestId('density-result')).toContainText('≈ 408 olivos');

      await page.getByLabel('Coste total (€)', { exact: true }).fill('1200');
      await page.getByLabel('Producción (kg)', { exact: true }).fill('4000');
      await page.getByLabel('Superficie (ha) · opcional', { exact: true }).fill('2');
      await expect(page.getByTestId('cost-result')).toContainText(/0,30\s*€\/kg/);
      await expect(page.getByTestId('cost-result')).toContainText(/600,00\s*€\/ha/);

      const surfaceBox = await surfaceInput.boundingBox();
      expect(surfaceBox?.height ?? 0).toBeGreaterThanOrEqual(44);

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
    });
  }
});
