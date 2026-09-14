import { expect, test, type Page } from '@playwright/test';

const SIGNED_REWARD_TOKEN = '61000000-0000-4000-8000-000000000001.AbcdefghijkLMN12';
const REWARD_ID = '41000000-0000-4000-8000-000000000001';

const mills = {
  almazaras: [
    {
      id: '31000000-0000-4000-8000-000000000001',
      slug: 'cooperativa-bedmar-e2e',
      name: 'Cooperativa del Olivar E2E',
      shortDescription: 'Recepción de aceituna y AOVE de Sierra Mágina.',
      municipalityName: 'Bedmar',
      address: 'Avenida del Olivar 1',
      phone: '953 00 00 01',
      website: 'https://example.com/cooperativa',
      logoUrl: null,
      coverImageUrl: null,
      millKind: 'cooperativa',
      oliveVarieties: ['Picual'],
      hasShop: true,
      acceptsVisits: true,
      rewardCount: 1,
    },
    {
      id: '31000000-0000-4000-8000-000000000002',
      slug: 'almazara-jodar-e2e',
      name: 'Almazara Sierra E2E',
      shortDescription: 'Molturación y atención durante la campaña.',
      municipalityName: 'Jódar',
      address: 'Camino de la Campaña 2',
      phone: '953 00 00 02',
      website: null,
      logoUrl: null,
      coverImageUrl: null,
      millKind: 'almazara',
      oliveVarieties: ['Picual'],
      hasShop: false,
      acceptsVisits: false,
      rewardCount: 0,
    },
  ],
};

const rewards = {
  rewards: [
    {
      id: REWARD_ID,
      businessId: '31000000-0000-4000-8000-000000000001',
      businessName: 'Cooperativa del Olivar E2E',
      slug: 'aove-500-e2e',
      title: 'Botella AOVE 500 ml',
      description: 'Premio físico de prueba.',
      imageUrl: null,
      volumeMl: 500,
      oliveCost: 500,
      availableStock: 12,
      maxPerUser: 1,
      startsAt: null,
      endsAt: null,
    },
  ],
};

const publicUnlocks = {
  rewards: [{
    rewardId: REWARD_ID,
    requiredLevel: 6,
    requiredLevelName: 'Olivo de cosecha',
    minXp: 1000,
  }],
};

async function mockMills(page: Page, options: { unlocked?: boolean; balance?: number } = {}) {
  const unlocked = options.unlocked ?? true;
  const balance = options.balance ?? 1200;
  await page.route(/\/api\/v1\/public\/almazaras(?:\?.*)?$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mills) });
  });
  await page.route(/\/api\/v1\/public\/almazaras\/[^/]+\/rewards$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(rewards) });
  });
  await page.route(/\/api\/v1\/public\/almazaras\/[^/]+\/reward-unlocks$/, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(publicUnlocks) });
  });
  await page.route(/\/api\/v1\/my\/almazaras\/[^/]+\/reward-unlocks$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        xp: unlocked ? 1200 : 700,
        balance,
        currentLevel: unlocked ? 6 : 5,
        currentLevelName: unlocked ? 'Olivo de cosecha' : 'Olivo en flor',
        rewards: [{ ...publicUnlocks.rewards[0], unlocked }],
      }),
    });
  });
  await page.route(/\/api\/v1\/almazara-rewards\/[^/]+\/redeem$/, async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ redemption: {
        id: '51000000-0000-4000-8000-000000000001',
        token: SIGNED_REWARD_TOKEN,
        status: 'reserved',
        qrPayload: SIGNED_REWARD_TOKEN,
        expiresAt: '2026-09-21T12:00:00.000Z',
        productTitle: 'Botella AOVE 500 ml',
        businessName: 'Cooperativa del Olivar E2E',
        olivesSpent: 500,
      } }),
    });
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }));
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewport + 1);
}

test('cooperatives directory searches, opens a business-backed mill and exposes rewards', async ({ page }) => {
  await mockMills(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/cooperativas');

  await expect(page.getByRole('heading', { name: 'Cooperativas y almazaras' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toBeVisible();

  await page.getByRole('searchbox', { name: 'Buscar por nombre, pueblo, variedad o dirección' }).fill('Bedmar');
  await expect(page.getByText('1 resultado')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toHaveCount(0);

  await page.getByRole('link', { name: 'Ver ficha y premios →' }).click();
  await expect(page).toHaveURL(/\/cooperativas\/?\?slug=cooperativa-bedmar-e2e$/);
  await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
  await expect(page.getByText('Avenida del Olivar 1')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Llamar' })).toHaveAttribute('href', 'tel:953000001');
  await expect(page.getByRole('link', { name: 'Web oficial ↗' })).toHaveAttribute('href', 'https://example.com/cooperativa');
  await expect(page.getByRole('heading', { name: 'Premios con Mi Olivo' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Botella AOVE 500 ml' })).toBeVisible();
  await expect(page.getByText('500 aceitunas')).toBeVisible();
  await expect(page.getByText('Nivel 6 · Olivo de cosecha')).toBeVisible();
  await expect(page.getByText('Saldo disponible:')).toBeVisible();
  await expect(page.getByText('✅ Desbloqueado con tu nivel 6 y saldo suficiente.')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('reward redemption displays a signed single-use collection credential and updates local balance', async ({ page }) => {
  await mockMills(page, { balance: 700 });
  await page.goto('/almazaras?slug=cooperativa-bedmar-e2e');
  await expect(page.getByText('700 aceitunas')).toBeVisible();
  await page.getByRole('button', { name: 'Canjear premio' }).click();
  await expect(page.getByText('✅ Premio reservado: Botella AOVE 500 ml')).toBeVisible();
  await expect(page.getByText(SIGNED_REWARD_TOKEN)).toBeVisible();
  await expect(page.getByRole('img', { name: 'Código QR firmado de recogida' })).toBeVisible();
  await expect(page.getByText('200 aceitunas')).toBeVisible();
  await expect(page.getByText('🫒 Nivel desbloqueado. Te faltan 300 aceitunas para canjearlo.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Te faltan 300 aceitunas' })).toBeDisabled();
});

test('reward remains visibly locked when permanent Mi Olivo level is too low', async ({ page }) => {
  await mockMills(page, { unlocked: false });
  await page.goto('/almazaras?slug=cooperativa-bedmar-e2e');
  await expect(page.getByText('Tu nivel:')).toBeVisible();
  await expect(page.getByText('🔒 Bloqueado: necesitas nivel 6. Te faltan 300 XP.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nivel 6 requerido' })).toBeDisabled();
});

test('reward shows insufficient olive balance before attempting redemption', async ({ page }) => {
  let redemptionCalls = 0;
  await mockMills(page, { balance: 320 });
  await page.route(/\/api\/v1\/almazara-rewards\/[^/]+\/redeem$/, async (route) => {
    redemptionCalls += 1;
    await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ error: 'should_not_be_called' }) });
  });
  await page.goto('/almazaras?slug=cooperativa-bedmar-e2e');
  await expect(page.getByText('🫒 Nivel desbloqueado. Te faltan 180 aceitunas para canjearlo.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Te faltan 180 aceitunas' })).toBeDisabled();
  expect(redemptionCalls).toBe(0);
});

test('almazaras alias preserves its route and business-backed detail', async ({ page }) => {
  await mockMills(page);
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto('/almazaras?slug=almazara-jodar-e2e');
  await expect(page.getByRole('heading', { name: 'Almazara Sierra E2E' })).toBeVisible();
  await expect(page.locator('dl').getByText('Jódar', { exact: true })).toBeVisible();
  await expect(page.getByText('Camino de la Campaña 2')).toBeVisible();
  await expect(page.getByRole('link', { name: '← Cooperativas y almazaras' })).toHaveAttribute('href', /^\/almazaras\/?$/);
  await expectNoHorizontalOverflow(page);
});

for (const width of [360, 390, 430]) {
  test(`cooperatives directory does not overflow at ${width}px`, async ({ page }) => {
    await mockMills(page);
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/cooperativas');
    await expect(page.getByRole('heading', { name: 'Cooperativa del Olivar E2E' })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}
