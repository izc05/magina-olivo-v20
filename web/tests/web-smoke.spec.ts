import { expect, test } from "@playwright/test";

function relativeLuminance(color: string) {
  const channels = color
    .match(/[\d.]+/g)
    ?.slice(0, 3)
    .map(Number);
  if (channels?.length !== 3) {
    throw new Error(`Unsupported computed color: ${color}`);
  }
  const linear = channels.map((channel) => {
    const value = channel / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
}

function contrastRatio(foreground: string, background: string) {
  const first = relativeLuminance(foreground);
  const second = relativeLuminance(background);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

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

test("mobile Mi menu can be opened from the keyboard", async ({
  page,
}, testInfo) => {
  test.skip(
    !testInfo.project.name.includes("mobile"),
    "mobile-only keyboard navigation",
  );
  await page.goto("/mi");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");

  const summary = page.locator(".mobile-nav summary");
  await expect(summary).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator(".mobile-nav")).toHaveAttribute("open", "");
  await expect(
    page.getByRole("navigation", { name: "Navegación Mi Mágina Olivo" }).last(),
  ).toBeVisible();
});

test("private workspace starts keyboard navigation with a skip link", async ({
  page,
}) => {
  await page.goto("/mi");
  await page.keyboard.press("Tab");

  const skipLink = page.getByRole("link", { name: "Saltar al contenido" });
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#mi-contenido$/);
  await expect(page.locator("#mi-contenido")).toBeFocused();
});

test("Mi muted navigation and keyboard focus retain readable contrast", async ({
  page,
}) => {
  await page.goto("/mi");
  const colors = await page.evaluate(() => {
    const pending = document.querySelector<HTMLElement>(
      ".mi-nav-link.nav-pending",
    );
    return {
      pendingText: pending ? getComputedStyle(pending).color : "",
      pageBackground: getComputedStyle(document.body).backgroundColor,
    };
  });
  expect(
    contrastRatio(colors.pendingText, colors.pageBackground),
  ).toBeGreaterThanOrEqual(4.5);

  await page.keyboard.press("Tab");
  const focus = await page.locator(":focus").evaluate((element) => ({
    color: getComputedStyle(element).outlineColor,
    width: getComputedStyle(element).outlineWidth,
  }));
  expect(focus.width).toBe("3px");
  expect(
    contrastRatio(focus.color, colors.pageBackground),
  ).toBeGreaterThanOrEqual(3);
});

test("public and private mobile navigation targets are at least 48px tall", async ({
  page,
}, testInfo) => {
  test.skip(
    !testInfo.project.name.includes("mobile"),
    "touch target sizing checked in mobile Chromium",
  );

  await page.goto("/");
  const publicTargetHeights = await page
    .locator(
      ".public-nav a:visible, .header-actions a:visible, .home-button:visible, .public-footer a:visible",
    )
    .evaluateAll((elements) =>
      elements.map((element) =>
        Math.round(element.getBoundingClientRect().height),
      ),
    );
  expect(publicTargetHeights.length).toBeGreaterThan(0);
  expect(Math.min(...publicTargetHeights)).toBeGreaterThanOrEqual(48);

  await page.goto("/mi");
  await page.locator(".mobile-nav summary").click();
  const privateTargetHeights = await page
    .locator(".mobile-nav summary:visible, .mobile-nav .mi-nav-link:visible")
    .evaluateAll((elements) =>
      elements.map((element) =>
        Math.round(element.getBoundingClientRect().height),
      ),
    );
  expect(privateTargetHeights.length).toBeGreaterThan(0);
  expect(Math.min(...privateTargetHeights)).toBeGreaterThanOrEqual(48);
});

test("public home starts keyboard navigation with a working skip link", async ({
  page,
}) => {
  await page.goto("/");
  await page.keyboard.press("Tab");

  const skipLink = page.getByRole("link", { name: "Saltar al contenido" });
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#contenido$/);
  await expect(page.locator("#contenido")).toBeFocused();
});

test("private routes inherit noindex in preview mode", async ({ page }) => {
  await page.goto("/mi");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /noindex,\s*nofollow/,
  );
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
});

test("production SEO indexes public pages but keeps Mi private", async ({
  page,
}) => {
  test.skip(
    process.env.ALLOW_INDEXING !== "true",
    "production SEO configuration only",
  );

  await page.goto("/");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /index,\s*follow/,
  );
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    "href",
    "https://magina-olivo.test",
  );

  const robotsResponse = await page.request.get("/robots.txt");
  expect(await robotsResponse.text()).toContain("Allow: /");
  const sitemapResponse = await page.request.get("/sitemap.xml");
  const sitemap = await sitemapResponse.text();
  expect(sitemap).toContain("https://magina-olivo.test/funciones");
  expect(sitemap).not.toContain("/mi");

  await page.goto("/mi");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /noindex,\s*nofollow/,
  );
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
});

test("public home and private dashboard reflow at the WEB-0F viewport matrix", async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name.includes("mobile"),
    "matrix exercised in the desktop Chromium project",
  );

  const viewports = [
    { width: 360, height: 800 },
    { width: 390, height: 844 },
    { width: 430, height: 932 },
    { width: 768, height: 1024 },
    { width: 1366, height: 768 },
    { width: 1440, height: 900 },
    { width: 1920, height: 1080 },
  ];

  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    for (const route of ["/", "/mi"]) {
      const response = await page.goto(route);
      expect(response?.status(), `${route} at ${viewport.width}px`).toBe(200);
      await expect(page.locator("main h1")).toBeVisible();
      const hasHorizontalOverflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth >
          document.documentElement.clientWidth + 2,
      );
      expect(
        hasHorizontalOverflow,
        `horizontal overflow on ${route} at ${viewport.width}px`,
      ).toBe(false);
      if (viewport.width === 390 && route === "/") {
        await page.screenshot({
          path: "docs/screenshots/web-0f-home-mobile.png",
          fullPage: true,
        });
      }
      if (viewport.width === 1440 && route === "/mi") {
        await page.screenshot({
          path: "docs/screenshots/web-0f-mi-desktop.png",
          fullPage: true,
        });
      }
      if (viewport.width === 390 && route === "/mi") {
        await page.screenshot({
          path: "docs/screenshots/web-0f-mi-mobile.png",
          fullPage: true,
        });
      }
    }
  }
});

test("all public home photography loads and reduced motion is respected", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");

  await expect(page.locator(".home-hero-image")).toBeVisible();
  for (const image of await page
    .locator(".home-step-photo, .community-photo")
    .all()) {
    await image.scrollIntoViewIfNeeded();
    await expect
      .poll(() =>
        image.evaluate((node) => (node as HTMLImageElement).naturalWidth),
      )
      .toBeGreaterThan(0);
  }
  expect(
    await page.evaluate(
      () => window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    ),
  ).toBe(true);
});

test("preview metadata stays noindex by default", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
    "content",
    /noindex,\s*nofollow/,
  );
});

test("SEO metadata exposes an absolute social image and a valid manifest", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute(
    "content",
    /images\/v3\/home-hero\.webp$/,
  );

  const response = await page.request.get("/manifest.webmanifest");
  expect(response.status()).toBe(200);
  const manifest = await response.json();
  expect(manifest).toMatchObject({
    name: "Mágina Olivo",
    start_url: "/",
    scope: "/",
    display: "standalone",
  });
  expect(manifest.icons).toContainEqual(
    expect.objectContaining({ src: "/brand/app-icon.png", type: "image/png" }),
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
