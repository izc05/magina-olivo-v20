# REDIAM geometry validation — Sierra Mágina

Verified source-discovery date: 2026-09-13.

## Scope

The 17 official signposted trails in the Sierra Mágina Natural Park core now have an authoritative Junta de Andalucía / REDIAM `CODIGOEQUI` captured from their official download links.

This closes **source identification**, not geometry validation.

## Authoritative service

- Authority: Junta de Andalucía — REDIAM.
- Service: `REDIAM_WFS_Patrimonio_Natural`.
- Feature type: `senderos:senderos`.
- Source CRS: `EPSG:25830`.
- Route selector: `CODIGOEQUI`.

The canonical source registry is `data/routes/sierra-magina-rediam-track-sources.json`.

## Validation stages

A route must pass these stages in order:

1. `source_located` — official download link and `CODIGOEQUI` identified.
2. `fetched` — WFS response successfully retrieved.
3. `parsed` — non-empty line-compatible geometry extracted.
4. `crs_checked` — source CRS is known and coordinates are plausible for EPSG:25830.
5. `transformed` — geometry/bounds can be transformed to WGS84 for application use.
6. `length_checked` — calculated geometry length is compared with the official route sheet.
7. `identity_reviewed` — route identity, directionality and circular/linear semantics are reviewed.
8. `geometry_validated` — evidence is recorded and the track may become eligible for publication.

No stage is implied by the presence of a KML/GML/GPX button alone.

## Distance comparison

The auditor calculates planar length in EPSG:25830, whose units are metres. The first automatic plausibility window is intentionally broad because official sheets may describe one-way distance while a downloaded geometry can represent a complete or circular route.

A close numerical match is supporting evidence, not sufficient evidence by itself. A material mismatch requires manual review and must never be silently corrected by inventing a value.

## Reproducible audit

Run:

```bash
pnpm audit:routes-rediam
```

This calls `scripts/audit-rediam-geometries.mjs` and writes `data/routes/sierra-magina-rediam-geometry-audit.json`.

A non-blocking GitHub Actions workflow runs the same audit and uploads the report as an artifact. It is intentionally non-blocking because availability of an external institutional WFS must not determine whether application code CI is green.

## Publication rule

`track_found=true` or a confirmed `CODIGOEQUI` is never enough to publish a route as geometry-validated.

Publication eligibility requires geometry evidence plus the existing catalog safety rules. Operational closures remain a separate state: a route can be fully documented and geometrically validated while temporarily closed to visitors.
