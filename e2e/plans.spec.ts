import { expect, test } from '@playwright/test';

const widths = [360, 390, 430];

test.describe('Planes y monetización honesta', () => {
  for (const width of widths) {
    test(`catálogo público sin checkout ni overflow a ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/planes');

      await expect(page.getByRole('heading', { name: /Elige cómo quieres crecer/i })).toBeVisible();
      await expect(page.getByText('Beta sin cobros')).toBeVisible();
      await expect(page.getByTestId('plan-card')).toHaveCount(3);
      await expect(page.getByRole('heading', { name: 'Campo' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Pro' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Profesional' })).toBeVisible();
      await expect(page.getByText('Gratis')).toBeVisible();
      await expect(page.getByText('Precio por definir')).toHaveCount(2);
      await expect(page.getByText(/registrar interés no cambia tu plan/i)).toBeVisible();

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
    });
  }
});
