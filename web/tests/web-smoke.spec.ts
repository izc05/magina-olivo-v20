import { expect, test } from "@playwright/test";

const routes = [
  "/mi/fincas/salinillas",
  "/mi/parcelas/las-lomas",
  "/mi/parcelas/el-cerrillo",
  "/mi/campanas/2025-2026",
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

  await expect(
    page.getByText("Demo · Datos ficticios para revisión visual"),
  ).toBeVisible();
  await expect(
    page.getByRole("navigation", { name: "Navegación Mi Mágina Olivo" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Fincas", exact: true }),
  ).toBeVisible();
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
  await expect(page.locator(".v3-frame-state")).toHaveCount(12);
  await expect(page.locator(".v3-frame-state")).toHaveText(
    Array.from({ length: 12 }, () => "Pendiente"),
  );
  await expect(page.locator(".v3-frame-id")).toHaveText(
    Array.from(
      { length: 12 },
      (_, index) => `K${String(index + 1).padStart(2, "0")}`,
    ),
  );
  const conceptImage = page.getByAltText(
    "Lámina conceptual de 12 escenas en el olivar, desde el amanecer y el agricultor hasta el móvil, la aplicación y el cierre",
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
    page.getByText("12 keyframes conceptuales generados", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByText("Concepto: lámina IA · original V3 pendiente").first(),
  ).toBeVisible();
  const references = page.locator(".v3-reference img");
  await expect(references).toHaveCount(2);
  await expect(references.first()).toBeVisible();
  await expect(references.last()).toBeVisible();
});

const publicPageContent = [
  ["/funciones", ["Mi Campo", "Cuaderno", "Pesadas", "Jornales"]],
  ["/como-funciona", ["Finca", "Parcela", "Informe"]],
  ["/novedades", ["Aún no hay novedades publicadas"]],
  ["/ayuda", ["Crear una finca o parcela", "Registrar una pesada"]],
  [
    "/descargar",
    ["Descarga oficial de Android", "El enlace oficial está pendiente"],
  ],
  [
    "/anunciate",
    ["Publicidad local", "Canal de contacto pendiente de confirmar"],
  ],
  ["/privacidad", ["Contenido provisional"]],
  ["/terminos", ["Contenido provisional"]],
  ["/aviso-legal", ["Contenido provisional"]],
] as const;

for (const [route, expectedText] of publicPageContent) {
  test(`${route} contains its specific public information`, async ({
    page,
  }) => {
    const response = await page.goto(route);
    expect(response?.status()).toBe(200);
    await expect(page.locator("main h1")).toBeVisible();
    await expect(page.locator("meta[name=description]")).toHaveAttribute(
      "content",
      /.+/,
    );
    const canonical = await page
      .locator("link[rel=canonical]")
      .getAttribute("href");
    expect(canonical).toContain(route);

    for (const text of expectedText) {
      await expect(
        page.locator("main").getByText(text, { exact: false }).first(),
      ).toBeVisible();
    }

    const unresolvedLinks = await page
      .locator('a[href="#"], a[href=""]')
      .count();
    expect(unresolvedLinks).toBe(0);

    const brokenAnchors = await page.evaluate(
      () =>
        Array.from(document.querySelectorAll('a[href^="#"]')).filter(
          (anchor) => {
            const target = anchor.getAttribute("href")?.slice(1);
            return !target || !document.getElementById(target);
          },
        ).length,
    );
    expect(brokenAnchors).toBe(0);
  });
}

test("public navigation reaches the advertised businesses section", async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name.includes("mobile"),
    "header anchor navigation checked on desktop",
  );
  await page.goto("/");
  await page.getByRole("link", { name: "Empresas", exact: true }).click();
  await expect(page).toHaveURL(/\/anunciate\/?#empresas$/);
  await expect(
    page.getByRole("heading", { name: "Espacios locales" }),
  ).toBeVisible();
});

test("health route returns a minimal healthy status", async ({ request }) => {
  const response = await request.get("/api/health");

  expect(response.status()).toBe(200);
  await expect(response).toBeOK();
  await expect(response.json()).resolves.toEqual({ status: "ok" });
});

test("Mi dashboard follows the approved private visual hierarchy with demo-only data", async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name.includes("mobile"),
    "desktop dashboard assertions",
  );
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/mi");

  await expect(
    page.getByRole("searchbox", { name: "Buscar en Mi Mágina Olivo" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("img", { name: "Avisos Demo sin datos conectados" }),
  ).toBeVisible();

  for (const heading of [
    "Panel general",
    "Tiempo en tu zona",
    "Radar de lluvia",
    "Mis fincas",
    "Producción total",
    "Resumen por fincas",
    "Mercado del aceite",
    "Cuaderno de hoy",
    "Jornales y maquinaria",
  ]) {
    await expect(page.getByRole("heading", { name: heading })).toBeVisible();
  }

  for (const label of [
    "Fincas",
    "Parcelas",
    "Superficie",
    "Olivos",
    "Kg campaña",
    "Rendimiento",
    "Coste/kg",
  ]) {
    await expect(page.getByText(label, { exact: true }).first()).toBeVisible();
  }

  await expect(page.getByText("Demo", { exact: false }).first()).toBeVisible();
  await expect(page.getByText(/sin cuenta conectada/i)).toBeVisible();
});

test("Mi sidebar links every prepared route and marks the current page semantically", async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name.includes("mobile"),
    "desktop sidebar assertions",
  );
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/mi/fincas");

  const sidebar = page.locator(".mi-sidebar");
  for (const href of [
    "/mi",
    "/mi/fincas",
    "/mi/parcelas",
    "/mi/mapa",
    "/mi/campanas",
    "/mi/cuaderno",
    "/mi/documentos",
    "/mi/informes",
    "/mi/mercado",
    "/mi/tiempo",
    "/mi/cooperativa",
    "/mi/perfil",
    "/mi/cuenta",
  ]) {
    await expect(
      sidebar.locator(`.mi-desktop-navigation a[href="${href}"]`).first(),
    ).toBeVisible();
  }
  await expect(
    sidebar.locator('.mi-desktop-navigation a[href="/mi/fincas"]').first(),
  ).toHaveAttribute("aria-current", "page");
});

const miPageContent = [
  ["/mi/fincas", ["Estacas", "Salinillas", "Superficie total"]],
  ["/mi/fincas/demo", ["Parcelas de Estacas", "Los Llanos", "Campaña"]],
  ["/mi/fincas/salinillas", ["Finca Salinillas", "Las Lomas", "El Cerrillo"]],
  ["/mi/parcelas", ["Los Llanos", "Las Lomas", "Finca"]],
  ["/mi/parcelas/demo", ["Parcela Los Llanos", "Superficie", "Historial"]],
  ["/mi/parcelas/las-lomas", ["Parcela Las Lomas", "Salinillas", "Historial"]],
  [
    "/mi/parcelas/el-cerrillo",
    ["Parcela El Cerrillo", "Salinillas", "Historial"],
  ],
  ["/mi/mapa", ["Mapa de parcelas", "Vista de mapa Demo"]],
  ["/mi/campanas", ["2026–2027", "2025–2026", "Campaña activa"]],
  ["/mi/campanas/demo", ["Resumen de campaña", "Coste/kg", "Pesadas"]],
  [
    "/mi/campanas/2025-2026",
    ["Resumen de campaña 2025–2026", "Coste/kg", "Pesadas"],
  ],
  ["/mi/cuaderno", ["Actividad reciente", "Recolección", "Consulta"]],
  ["/mi/documentos", ["Documentos de demo", "Certificado", "Sin archivos"]],
  ["/mi/informes", ["Informe de campaña", "PDF de ejemplo"]],
  ["/mi/mercado", ["Precio de referencia", "Fuente demo", "€/kg"]],
  ["/mi/tiempo", ["Previsión demo", "Radar de lluvia", "Bedmar"]],
  ["/mi/cooperativa", ["Sin cooperativa asignada", "Datos de demostración"]],
  ["/mi/perfil", ["Perfil de demostración", "Municipio", "Jaén"]],
  ["/mi/cuenta", ["Cuenta de demostración", "Sin sesión", "Sync no conectado"]],
] as const;

for (const [route, expectedText] of miPageContent) {
  test(`${route} shows a useful demo view instead of the preparation placeholder`, async ({
    page,
  }) => {
    const response = await page.goto(route);
    expect(response?.status()).toBe(200);
    await expect(page.locator("main h1")).toBeVisible();
    for (const text of expectedText) {
      await expect(
        page.locator("main").getByText(text, { exact: false }).first(),
      ).toBeVisible();
    }
  });
}
