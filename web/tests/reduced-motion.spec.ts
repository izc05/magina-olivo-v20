import { expect, test } from "@playwright/test";

for (const route of ["/", "/mi"]) {
  test(`reduced motion limits computed durations on ${route}`, async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto(route);
    await expect(page.locator("main")).toBeVisible();

    const violations = await page.evaluate(() => {
      const toMilliseconds = (value: string) =>
        value.split(",").map((part) => {
          const duration = part.trim();
          return (
            Number.parseFloat(duration) * (duration.endsWith("ms") ? 1 : 1000)
          );
        });
      return Array.from(document.querySelectorAll("*"))
        .filter((element) => element.getClientRects().length > 0)
        .flatMap((element) => {
          const style = getComputedStyle(element);
          const durations = [
            ...toMilliseconds(style.animationDuration),
            ...toMilliseconds(style.transitionDuration),
          ];
          return durations.some((duration) => duration > 0.02) ||
            style.scrollBehavior === "smooth"
            ? [`${element.tagName}.${element.className}`]
            : [];
        });
    });
    expect(violations).toEqual([]);
    await expect(
      page.getByRole("link", { name: "Saltar al contenido" }),
    ).toHaveAttribute("href", route === "/" ? "#contenido" : "#mi-contenido");
  });
}
