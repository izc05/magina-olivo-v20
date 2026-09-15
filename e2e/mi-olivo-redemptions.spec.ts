import { expect, test, type Page } from '@playwright/test';

const TOKEN_A = '71000000-0000-4000-8000-000000000001.AbcdefghijkLMN12';
const TOKEN_B = '71000000-0000-4000-8000-000000000002.AbcdefghijkLMN34';

async function mockRedemptions(page: Page) {
  const now = Date.now();
  const expiresSoon = new Date(now + 2 * 24 * 60 * 60 * 1000).toISOString();
  const expiresLater = new Date(now + 6 * 24 * 60 * 60 * 1000).toISOString();
  const createdRecently = new Date(now - 60 * 60 * 1000).toISOString();
  const createdEarlier = new Date(now - 2 * 60 * 60 * 1000).toISOString();
  const historyCreated = new Date(now - 3 * 24 * 60 * 60 * 1000).toISOString();

  await page.route(/\/api\/v1\/my\/almazara-redemptions$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        redemptions: [
          {
            id: '61000000-0000-4000-8000-000000000002',
            code: TOKEN_B,
            status: 'reserved',
            olivesSpent: 700,
            expiresAt: expiresLater,
            redeemedAt: null,
            cancelledAt: null,
            createdAt: createdEarlier,
            productTitle: 'Caja AOVE selección',
            businessName: 'Almazara Sierra E2E',
            qrPayload: TOKEN_B,
            qrReady: true,
          },
          {
            id: '61000000-0000-4000-8000-000000000003',
            code: null,
            status: 'cancelled',
            olivesSpent: 300,
            expiresAt: new Date(now + 4 * 24 * 60 * 60 * 1000).toISOString(),
            redeemedAt: null,
            cancelledAt: new Date(now - 2 * 24 * 60 * 60 * 1000).toISOString(),
            createdAt: historyCreated,
            productTitle: 'Botella cancelada',
            businessName: 'Cooperativa Histórica E2E',
            qrPayload: null,
            qrReady: false,
          },
          {
            id: '61000000-0000-4000-8000-000000000001',
            code: TOKEN_A,
            status: 'reserved',
            olivesSpent: 500,
            expiresAt: expiresSoon,
            redeemedAt: null,
            cancelledAt: null,
            createdAt: createdRecently,
            productTitle: 'Botella AOVE urgente',
            businessName: 'Cooperativa del Olivar E2E',
            qrPayload: TOKEN_A,
            qrReady: true,
          },
        ],
      }),
    });
  });
  await page.route(/\/api\/v1\/my\/almazara-redemption-pickups$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        pickups: [
          { redemptionId: '61000000-0000-4000-8000-000000000001', businessSlug: 'cooperativa-bedmar-e2e' },
          { redemptionId: '61000000-0000-4000-8000-000000000002', businessSlug: 'almazara-sierra-e2e' },
          { redemptionId: '61000000-0000-4000-8000-000000000003', businessSlug: 'cooperativa-historica-e2e' },
        ],
      }),
    });
  });
}

test('Mi Olivo redemptions prioritizes pickup-ready reservations and links to the mill', async ({ page }) => {
  await mockRedemptions(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/mi-olivo/canjes');

  await expect(page.getByRole('heading', { name: 'Mis canjes' })).toBeVisible();
  await expect(page.getByText('2 premios pendientes de recoger.')).toBeVisible();

  const pending = page.getByRole('region', { name: 'Pendientes de recoger' });
  const pendingCards = pending.locator('article');
  await expect(pendingCards).toHaveCount(2);
  await expect(pendingCards.nth(0).getByRole('heading', { name: 'Botella AOVE urgente' })).toBeVisible();
  await expect(pendingCards.nth(1).getByRole('heading', { name: 'Caja AOVE selección' })).toBeVisible();
  await expect(pendingCards.nth(0).getByText(/Quedan \d+ (hora|horas|día|días) para recogerlo\./)).toBeVisible();
  await expect(pendingCards.nth(0).getByRole('img', { name: 'Código QR firmado de recogida' })).toBeVisible();
  await expect(pendingCards.nth(0).getByRole('link', { name: 'Ver ficha, dirección y contacto de la almazara →' }))
    .toHaveAttribute('href', '/almazaras/?slug=cooperativa-bedmar-e2e');

  const history = page.getByRole('region', { name: 'Historial' });
  await expect(history.getByRole('heading', { name: 'Botella cancelada' })).toBeVisible();
  await expect(history.getByText('Las aceitunas de esta reserva se devuelven automáticamente a tu saldo.')).toBeVisible();
  await expect(history.getByRole('link', { name: 'Volver a ver la almazara →' }))
    .toHaveAttribute('href', '/almazaras/?slug=cooperativa-historica-e2e');
});
