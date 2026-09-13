# Visual Design QA — V20 Premium Pass

- Source of truth: `C:\Users\ISICIO\.codex\generated_images\01a0976d-97f9-79a2-abf5-be87b023bd34\exec-9637592d-0440-4c62-8b68-24a9019820c1.png`
- Implementation evidence: `C:\Users\ISICIO\.codex\visualizations\2026\09\13\magina-v20-visual-premium-pass\inicio-1440.png`
- Reference dimensions: 1487 × 1058
- Implementation viewport: 1440 × 1024 (full-page capture: 1440 × 1657)
- State: public home with repository fallback content; no fabricated application data

## Final comparison

The implementation preserves the selected “Cuaderno de Olivar” direction: deep olive and limestone palette, editorial serif display type, restrained Sierra Mágina photography, generous desktop composition, tactile cards, and clear territorial calls to action. It adapts the concept to the existing managed-content and responsive architecture instead of replacing working product flows.

## Iteration history

1. The first desktop hero was taller than the reference and pushed the editorial grid below the fold. Reduced the desktop hero height, tightened the title scale, and removed redundant top spacing.
2. Explore category tiles used emoji artwork that conflicted with the premium icon language. Replaced them with the existing accessible SVG icon set.
3. The Admin access gate was being overridden by route-level styles. Increased scoped selector specificity so the photographic corporate treatment renders consistently.

## Verification

- Typography, palette, photography, hierarchy, card rhythm, and responsive behavior were compared at 390 px and 1440 px.
- Inicio, Explorar, Mi Campo, Finca, Campaña, and Admin were visually inspected at mobile and desktop sizes.
- Automated responsive coverage passed at 360, 390, 430, 768, 1024, 1280, 1440, and 1920 px.
- No P0, P1, or P2 visual discrepancies remain.
- Accepted P3 differences: the production hero remains compatible with CMS imagery and uses a full-bleed crop rather than the concept’s exact split geometry; the official logo remains unchanged until the user supplies the approved asset.

Final result: **passed**.
