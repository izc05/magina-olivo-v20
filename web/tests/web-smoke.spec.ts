import { expect, test } from "@playwright/test";

const routes = [
  "/",
  "/funciones",
  "/como-funciona",
  "/novedades",
  "/ayuda",
  "/privacidad",
  "/terminos",
  "/aviso-legal",
  "/descargar",
  "/anunciate",
  "/v3-review",
  "/mi",
  "/mi/fincas",
  "/mi/fincas/demo",
  "/mi/parcelas",
  "/mi/parcelas/demo",
  "/mi/mapa",
  "/mi/campanas",
  "/mi/campanas/demo",
  "/mi/cuaderno",
  "/mi/documentos",
  "/mi/informes",
  "/mi/mercado",
  "/mi/tiempo",
  "/mi/cooperativa",
  "/mi/perfil",
  "/mi/cuenta",
];

for (const route of routes) {
  test(`${route} renders without 404 or horizontal overflow`, async ({
    page,
  }) => {
    const response = await page.goto(route);

    expect(response?.status(), `HTTP status for ${route}`).toBe(200);
    await expect(page.locator("main h1")).toBeVisible();

    const hasHorizontalOverflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth >
        document.documentElement.clientWidth + 2,
    );
    expect(hasHorizontalOverflow, `horizontal overflow on ${route}`).toBe(
      false,
    );
  });
}

test("Mi layout is explicitly a demo and retains desktop sidebar navigation", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/mi");

  await expect(page.getByText("Demo · interfaz de preparación")).toBeVisible();
  await expect(
    page.getByRole("navigation", { name: "Navegación Mi Mágina Olivo" }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Fincas" })).toBeVisible();
});

test("mobile Mi menu opens without horizontal overflow", async ({
  page,
}, testInfo) => {
  test.skip(
    !testInfo.project.name.includes("mobile"),
    "mobile-only navigation",
  );
  await page.goto("/mi");

  const menu = page.locator(".mobile-nav");
  await menu.locator("summary").click();
  await expect(menu.locator("nav")).toBeVisible();
  await expect(menu.getByRole("link", { name: "Fincas" })).toBeVisible();

  const hasHorizontalOverflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth >
      document.documentElement.clientWidth + 2,
  );
  expect(hasHorizontalOverflow).toBe(false);
});

test("preview metadata stays noindex by default", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /noindex,\s*nofollow/,
  );
});

test("public home follows the V3 visual reference and keeps demo data labeled", async ({
  page,
}) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Tu olivar, claro y al día." }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Del campo al móvil" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Todo lo importante en una sola app" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Tu explotación, siempre bajo control" }),
  ).toBeVisible();
  await expect(page.getByText("Demo visual")).toBeVisible();
  await expect(page.locator(".home-step")).toHaveCount(3);
  await expect(page.locator(".home-feature-card")).toHaveCount(6);
  await expect(page.locator(".home-community-card")).toHaveCount(4);
  await expect(page.locator(".home-step-photo, .community-photo")).toHaveCount(
    7,
  );

  const hero = page.locator(".home-hero-image");
  await expect
    .poll(() =>
      hero.evaluate((image) => (image as HTMLImageElement).naturalWidth),
    )
    .toBeGreaterThan(0);
});

test("official Mágina Olivo logo loads from the public brand assets", async ({
  page,
}) => {
  await page.goto("/");

  const logoLink = page
    .getByRole("link", { name: "Mágina Olivo, inicio" })
    .first();
  await expect(logoLink.locator("img")).toHaveAttribute(
    "src",
    /\/brand\/logo-horizontal\.png$/,
  );

  const logoResponse = await page.request.get("/brand/logo-horizontal.png");
  expect(logoResponse.status()).toBe(200);
});

test("V3 review exposes all keyframes as pending and stays noindex", async ({
  page,
}) => {
  await page.goto("/v3-review");

  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /noindex/,
  );
  await expect(page.locator(".v3-frame")).toHaveCount(12);
  const conceptImage = page.getByAltText(
    "Lámina conceptual generada para revisar continuidad entre olivar, agricultor, móvil, producto y escritorio",
  );
  await expect(conceptImage).toBeVisible();
  await expect
    .poll(() =>
      conceptImage.evaluate(
        (image) => (image as HTMLImageElement).naturalWidth,
      ),
    )
    .toBeGreaterThan(0);
  await expect(
    page.getByText("continuidad y movimiento pendientes", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByText("Fuente: producción original V3 pendiente").first(),
  ).toBeVisible();
  const references = page.locator(".v3-reference img");
  await expect(references).toHaveCount(2);
  await expect(references.first()).toBeVisible();
  await expect(references.last()).toBeVisible();
});

test("health route returns a minimal healthy status", async ({ request }) => {
  const response = await request.get("/api/health");

  expect(response.status()).toBe(200);
  await expect(response).toBeOK();
  await expect(response.json()).resolves.toEqual({ status: "ok" });
});
