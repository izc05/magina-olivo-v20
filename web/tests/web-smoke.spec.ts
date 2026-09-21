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
  "/v2-review",
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

test("home keeps the V2 cinematic structure and primary anchors", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  await expect(page.locator(".v2-hero")).toBeVisible();
  await expect(page.locator(".v2-story")).toHaveCount(1);
  await expect(page.locator("#historia")).toHaveCount(1);
  await expect(page.locator("#producto")).toHaveCount(1);
  await expect(page.locator("#descarga")).toHaveCount(1);

  const heroHeading = page.locator(".v2-hero-copy h1");
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

  await expect(page.locator(".v2-hero")).toBeVisible();
  await expect(page.locator(".v2-story")).toBeVisible();
  await expect(page.locator(".v2-sequence-poster")).toBeVisible();

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

test("captures visual evidence for the V2 landing", async ({ page }, testInfo) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const suffix = testInfo.project.name.includes("mobile") ? "mobile" : "desktop";

  await page.locator(".v2-hero").screenshot({
    path: `test-results/v2-home-hero-${suffix}.png`,
  });

  const story = page.locator(".v2-story");
  await story.scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, window.innerHeight * 1.7));
  await page.waitForTimeout(180);
  await page.screenshot({
    path: `test-results/v2-home-story-${suffix}.png`,
    fullPage: false,
  });

  const productMoments = page.locator("[data-v2-product-step]");

  for (const [label, index] of [["start", 0], ["middle", 3], ["end", 7]] as const) {
    const moment = productMoments.nth(index);
    await moment.scrollIntoViewIfNeeded();
    await page.waitForTimeout(180);
    await page.screenshot({
      path: `test-results/v2-home-product-${label}-${suffix}.png`,
      fullPage: false,
    });
  }

  await page.locator(".v2-final").screenshot({
    path: `test-results/v2-home-final-${suffix}.png`,
  });
});

test("loads the approved hero photograph instead of the fallback", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const heroImage = page.locator(".v2-hero-media img");
  await expect(heroImage).toBeVisible();

  const imageState = await heroImage.evaluate((image) => {
    const element = image as HTMLImageElement;
    return {
      currentSrc: element.currentSrc,
      naturalWidth: element.naturalWidth,
      naturalHeight: element.naturalHeight,
      complete: element.complete,
    };
  });

  expect(imageState.complete).toBe(true);
  expect(imageState.naturalWidth).toBeGreaterThan(0);
  expect(
    decodeURIComponent(imageState.currentSrc),
    `hero currentSrc=${imageState.currentSrc}`,
  ).toContain("/media/v2/hero-photo-clean.webp");
});


test("canvas sequence becomes ready in normal motion mode", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const canvas = page.locator(".v2-sequence-canvas");
  await expect(canvas).toHaveCount(1);
  await expect(canvas).toHaveAttribute("data-ready", "true", { timeout: 12_000 });
});


test("V2 canvas sequence advances with scroll", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const story = page.locator(".v2-story");
  const engine = page.locator(".v2-story-canvas-root");
  const canvas = page.locator(".v2-sequence-canvas");

  await story.scrollIntoViewIfNeeded();
  await expect(canvas).toHaveAttribute("data-ready", "true");

  const before = await engine.evaluate((element) => ({
    frame: Number((element as HTMLElement).dataset.frame || "0"),
    frames: Number((element as HTMLElement).dataset.frames || "0"),
  }));

  const targetY = await story.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    const top = window.scrollY + rect.top;
    const travel = Math.max(1, (element as HTMLElement).offsetHeight - window.innerHeight);
    return top + travel * 0.68;
  });

  await page.evaluate((y) => window.scrollTo({ top: y, behavior: "instant" }), targetY);
  await page.waitForTimeout(180);

  const after = await engine.evaluate((element) => ({
    frame: Number((element as HTMLElement).dataset.frame || "0"),
    frames: Number((element as HTMLElement).dataset.frames || "0"),
  }));

  expect(before.frames).toBeGreaterThanOrEqual(24);
  expect(after.frames).toBe(before.frames);
  expect(after.frame).toBeGreaterThan(before.frame);
});


test("V2 product takeover becomes visible near sequence end", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const story = page.locator(".v2-story");
  const takeover = page.locator(".v2-story-device-takeover");

  const targetY = await story.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    const top = window.scrollY + rect.top;
    const travel = Math.max(1, (element as HTMLElement).offsetHeight - window.innerHeight);
    return top + travel * 0.92;
  });

  await page.evaluate((y) => window.scrollTo({ top: y, behavior: "instant" }), targetY);
  await page.waitForTimeout(180);

  await expect(takeover).toBeVisible();

  const opacity = await takeover.evaluate((element) =>
    Number.parseFloat(getComputedStyle(element).opacity),
  );

  expect(opacity).toBeGreaterThan(0.75);
  await expect(takeover.locator(".phone-screen-welcome")).toHaveCount(1);
});


test("V2 review board exposes all canonical keyframes", async ({ page }) => {
  await page.goto("/v2-review", { waitUntil: "networkidle" });

  await expect(page.locator(".v2-review")).toBeVisible();
  await expect(page.locator(".v2-review-card")).toHaveCount(24);
  await expect(page.locator(".v2-review-card").filter({ hasText: "K01" }).first()).toBeVisible();
  await expect(page.locator(".v2-review-card").filter({ hasText: "K24" }).first()).toBeVisible();
});


test("V2 product scrollytelling reaches all eight core moments", async ({ page }) => {
  await page.goto("/", { waitUntil: "networkidle" });

  const product = page.locator("#producto");
  const moments = page.locator("[data-v2-product-step]");

  await expect(moments).toHaveCount(8);
  await expect(page.locator(".v2-product-device")).toBeVisible();

  const last = moments.nth(7);
  await last.scrollIntoViewIfNeeded();
  await page.waitForTimeout(220);

  await expect(last).toHaveClass(/is-active/);
  await expect(page.locator(".phone-screen-history")).toHaveCount(1);
  await expect(page.locator(".v2-product-progress > span").first()).toHaveText("08");

  const bounds = await product.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return {
      width: Math.round(rect.width),
      viewport: document.documentElement.clientWidth,
    };
  });

  expect(bounds.width).toBeLessThanOrEqual(bounds.viewport + 2);
});
