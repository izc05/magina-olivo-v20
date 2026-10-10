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

test("health route returns a minimal healthy status", async ({ request }) => {
  const response = await request.get("/api/health");

  expect(response.status()).toBe(200);
  await expect(response).toBeOK();
  await expect(response.json()).resolves.toEqual({ status: "ok" });
});
