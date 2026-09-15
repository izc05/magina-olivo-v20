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
          {
            redemptionId: '61000000-0000-4000-8000-000000000001',
            businessSlug: 'cooperativa-bedmar-e2e',
            address: 'Avenida de Mágina 12',
            municipalityName: 'Bedmar y Garcíez',
            phone: '+34 953 000 123',
            longitude: -3.4123,
            latitude: 37.9971,
          },
          {
            redemptionId: '61000000-0000-4000-8000-000000000002',
            businessSlug: 'almazara-sierra-e2e',
            address: 'Calle Sierra 8',
            municipalityName: 'Huelma',
            phone: null,
            longitude: null,
            latitude: null,
          },
          {
            redemptionId: '61000000-0000-4000-8000-000000000003',
            businessSlug: 'cooperativa-historica-e2e',
            address: null,
            municipalityName: null,
            phone: null,
            longitude: null,
            latitude: null,
          },
        ],
      }),
    });
  });
}

test('Mi Olivo redemptions prioritizes pickup-ready reservations and provides real pickup actions', async ({ page }) => {
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
  await expect(pendingCards.nth(0).getByText('Avenida de Mágina 12 · Bedmar y Garcíez')).toBeVisible();
  await expect(pendingCards.nth(0).getByRole('img', { name: 'Código QR firmado de recogida' })).toBeVisible();
  await expect(pendingCards.nth(0).getByRole('link', { name: 'Llamar a la almazara' }))
    .toHaveAttribute('href', 'tel:+34953000123');
  await expect(pendingCards.nth(0).getByRole('link', { name: 'Cómo llegar ↗' }))
    .toHaveAttribute('href', 'https://www.google.com/maps/dir/?api=1&destination=37.9971%2C-3.4123');
  await expect(pendingCards.nth(0).getByRole('link', { name: 'Cómo llegar ↗' }))
    .toHaveAttribute('target', '_blank');
  await expect(pendingCards.nth(0).getByRole('link', { name: 'Ver ficha de la almazara →' }))
    .toHaveAttribute('href', '/almazaras/?slug=cooperativa-bedmar-e2e');

  await expect(pendingCards.nth(1).getByText('Calle Sierra 8 · Huelma')).toBeVisible();
  await expect(pendingCards.nth(1).getByRole('link', { name: 'Cómo llegar ↗' }))
    .toHaveAttribute('href', 'https://www.google.com/maps/dir/?api=1&destination=Calle%20Sierra%208%2C%20Huelma');
  await expect(pendingCards.nth(1).getByRole('link', { name: 'Llamar a la almazara' })).toHaveCount(0);

  const history = page.getByRole('region', { name: 'Historial' });
  await expect(history.getByRole('heading', { name: 'Botella cancelada' })).toBeVisible();
  await expect(history.getByText('Las aceitunas de esta reserva se devuelven automáticamente a tu saldo.')).toBeVisible();
  await expect(history.getByRole('link', { name: 'Volver a ver la almazara →' }))
    .toHaveAttribute('href', '/almazaras/?slug=cooperativa-historica-e2e');
});
