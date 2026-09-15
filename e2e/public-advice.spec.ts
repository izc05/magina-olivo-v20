import { expect, test } from '@playwright/test';

const widths = [360, 390, 430];

test.describe('Consejos del campo', () => {
  for (const width of widths) {
    test(`catálogo seguro y sin overflow a ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      const response = await page.goto('/consejos');

      expect(response?.status()).toBeLessThan(400);
      await expect(page.getByRole('heading', { name: 'Consejos del campo', exact: true })).toBeVisible();
      await expect(page.getByTestId('advice-safety-note')).toContainText('Orientación general, no una receta');
      await expect(page.getByTestId('advice-card')).toHaveCount(8);

      const search = page.getByRole('searchbox', { name: 'Buscar en las guías' });
      const searchBox = await search.boundingBox();
      expect(searchBox?.height ?? 0).toBeGreaterThanOrEqual(44);

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
    });
  }

  test('filtra por tema, busca y mantiene la frontera fitosanitaria explícita', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/consejos');

    await page.getByRole('button', { name: 'Sanidad vegetal' }).click();
    await expect(page.getByTestId('advice-card')).toHaveCount(1);
    await expect(page.getByRole('heading', { name: 'Antes de cualquier tratamiento, confirma qué estás viendo' })).toBeVisible();

    await page.getByLabel('Ver guía: Antes de cualquier tratamiento, confirma qué estás viendo').click();
    await expect(page.getByText(/no prescribe fitosanitarios ni calcula dosis/i)).toBeVisible();
    await expect(page.getByText(/comprueba que su uso esté autorizado/i)).toBeVisible();

    await page.getByRole('button', { name: 'Limpiar filtros' }).click();
    await page.getByRole('searchbox', { name: 'Buscar en las guías' }).fill('albarán');
    await expect(page.getByTestId('advice-card')).toHaveCount(1);
    await expect(page.getByRole('heading', { name: 'Prepara la trazabilidad antes de que empiece la cosecha' })).toBeVisible();

    await page.getByRole('searchbox', { name: 'Buscar en las guías' }).fill('consulta imposible xyz');
    await expect(page.getByTestId('advice-empty')).toBeVisible();
    await page.getByRole('button', { name: 'Ver todos los consejos' }).click();
    await expect(page.getByTestId('advice-card')).toHaveCount(8);
  });
});
