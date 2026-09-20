import { expect, test } from "@playwright/test";

const publicRoutes = [
  "/",
  "/producto",
  "/beneficios",
  "/territorio",
  "/contacto",
  "/privacidad",
  "/terminos",
  "/aviso-legal",
];

for (const route of publicRoutes) {
  test(`${route} renders without runtime errors or horizontal overflow`, async ({ page }) => {
    const runtimeErrors: string[] = [];

    page.on("pageerror", (error) => runtimeErrors.push(error.message));
    page.on("console", (message) => {
      if (message.type() === "error") runtimeErrors.push(message.text());
    });

    const response = await page.goto(route, { waitUntil: "networkidle" });

    expect(response?.status(), `unexpected HTTP status for ${route}`).toBeLessThan(400);
    await expect(page.locator("body")).toBeVisible();

    const overflow = await page.evaluate(() => {
      const clientWidth = document.documentElement.clientWidth;
      const offenders = Array.from(document.querySelectorAll<HTMLElement>("body *"))
        .map((element) => {
          const rect = element.getBoundingClientRect();
          const rightOverflow = Math.max(0, rect.right - clientWidth);
          const leftOverflow = Math.max(0, -rect.left);
          return {
            tag: element.tagName.toLowerCase(),
            className: typeof element.className === "string" ? element.className : "",
            left: Math.round(rect.left),
            right: Math.round(rect.right),
            width: Math.round(rect.width),
            overflow: Math.round(Math.max(rightOverflow, leftOverflow)),
          };
        })
        .filter((item) => item.overflow > 2)
        .sort((a, b) => b.overflow - a.overflow)
        .slice(0, 12);

      return {
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth,
        offenders,
      };
    });

    expect(
      overflow.scrollWidth,
      `horizontal overflow on ${route}: ${overflow.scrollWidth}px > ${overflow.clientWidth}px; offenders=${JSON.stringify(overflow.offenders)}`,
    ).toBeLessThanOrEqual(overflow.clientWidth + 2);

    expect(runtimeErrors, `runtime errors on ${route}`).toEqual([]);
  });
}

test("home keeps the cinematic structure and primary anchors", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  await expect(page.locator(".cinema-hero")).toBeVisible();
  await expect(page.locator(".field-phone-film")).toHaveCount(1);
  await expect(page.locator("#funciones")).toHaveCount(1);
  await expect(page.locator("#descarga")).toHaveCount(1);
  await expect(page.locator("#contacto")).toHaveCount(1);

  const heroHeading = page.locator(".cinema-copy h1");
  await expect(heroHeading).toBeVisible();
  await expect(heroHeading).toContainText(/olivar/i);
});

test("mobile menu opens, locks navigation and closes with Escape", async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes("mobile"), "mobile-only behavior");

  await page.goto("/", { waitUntil: "networkidle" });

  const toggle = page.locator(".menu-toggle");
  await expect(toggle).toBeVisible();
  await toggle.click();

  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await expect(page.locator(".nav")).toBeVisible();

  await page.keyboard.press("Escape");
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
});

test("reduced motion mode remains usable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/", { waitUntil: "networkidle" });

  await expect(page.locator(".cinema-hero")).toBeVisible();
  await expect(page.locator(".field-phone-film")).toBeVisible();

  const overflow = await page.evaluate(() => {
    const clientWidth = document.documentElement.clientWidth;
    const offenders = Array.from(document.querySelectorAll<HTMLElement>("body *"))
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName.toLowerCase(),
          className: typeof element.className === "string" ? element.className : "",
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
          overflow: Math.round(Math.max(0, rect.right - clientWidth, -rect.left)),
        };
      })
      .filter((item) => item.overflow > 2)
      .sort((a, b) => b.overflow - a.overflow)
      .slice(0, 12);

    return {
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth,
      offenders,
    };
  });

  expect(
    overflow.scrollWidth,
    `reduced-motion horizontal overflow: ${overflow.scrollWidth}px > ${overflow.clientWidth}px; offenders=${JSON.stringify(overflow.offenders)}`,
  ).toBeLessThanOrEqual(overflow.clientWidth + 2);
});


test("captures visual evidence for the approved landing", async ({ page }, testInfo) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const suffix = testInfo.project.name.includes("mobile") ? "mobile" : "desktop";

  await page.locator(".cinema-hero").screenshot({
    path: `test-results/home-hero-${suffix}.png`,
  });

  const film = page.locator(".field-phone-film");
  await film.scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, window.innerHeight * 1.9));
  await page.waitForTimeout(150);
  await page.screenshot({
    path: `test-results/home-film-${suffix}.png`,
    fullPage: false,
  });

  await page.locator(".app-cycle-overview").screenshot({
    path: `test-results/home-app-cycle-${suffix}.png`,
  });

  await page.locator(".download-section").screenshot({
    path: `test-results/home-download-${suffix}.png`,
  });
});
