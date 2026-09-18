# RC1.2 Design System Catalog

**Status:** ACTIVE REFERENCE  
**Phase:** 3 — Design System + Reference Screens

This file is the naming contract for reusable UI pieces.

Do not create duplicate components with alternate names unless a documented requirement proves the existing component cannot support the case.

| Purpose | Compose name | Planned Figma name |
|---|---|---|
| Primary button | `MoPrimaryButton` | Button / Primary |
| Secondary button | `MoSecondaryButton` | Button / Secondary |
| Icon button | `MoIconButton` | IconButton |
| Text field | `MoTextField` | Field / Text |
| Select field | `MoSelectField` | Field / Select |
| Date field | `MoDateField` | Field / Date |
| Metric | `MoMetricCard` | MetricCard |
| Status | `MoStatusChip` | StatusChip |
| Section heading | `MoSectionHeader` | SectionHeader |
| Farm card | `MoFarmCard` | FarmCard |
| Parcel row | `MoParcelRow` | ParcelRow |
| Weather hero | `MoWeatherHero` | WeatherCard |
| Chart frame | `MoChartContainer` | ChartContainer |
| Simple bar chart | `MoSimpleBarChart` | Chart / Bar |
| Offline notice | `MoOfflineBanner` | OfflineBanner |
| Data freshness | `MoSourceFreshness` | SourceFreshness |
| Sync state | `MoSyncStatus` | SyncStatus |
| Empty state | `MoEmptyState` | EmptyState |
| Error state | `MoErrorState` | ErrorState |
| Loading list | `MoListSkeleton` | Skeleton / List |
| OCR field | `MoOcrReviewField` | OCR / FieldReview |
| OCR panel | `MoOcrReviewPanel` | OCR / ReviewPanel |
| Top app bar | `MoTopAppBar` | TopAppBar |
| Bottom nav preview | `MoBottomBarPreview` | BottomNavigation |
| Photo fallback | `MoPhotoCoverFallback` | PhotoCover / Fallback |
| Confirmation sheet | `MoConfirmationSheet` | Sheet / Confirmation |
| Action sheet | `MoBottomActionSheet` | Sheet / Actions |

## Naming rules

- `Mo` means the product UI namespace while the public brand is still provisional.
- Do not rename all UI components when the final marketing name is chosen.
- Domain-independent UI primitives stay product-owned and stable.
- Do not place business formulas inside components.
- Components receive already-prepared display/projection data.
- Components must not open Room/Supabase/network providers directly.
- Demo/reference components and production components must use the same tokens.

## State rules

Every data-bearing component should support the states relevant to it.

Common state vocabulary:

```text
normal
partial
estimate
unavailable
pending
confirmed
error
offline
stale
sync-pending
```

Unknown data is never silently rendered as zero.

## Accessibility rules

- actionable surfaces: at least 48dp target;
- icon-only actions require content descriptions;
- selected navigation state cannot rely on color only;
- charts require textual context;
- OCR confidence/status must include text;
- reduced-motion behavior applies to weather effects.

## Reference-screen rule

Reference screens may use fixtures only inside DEV.

Fixtures must remain obviously demonstrative and must never be promoted into persistence seeds or production business data without a separate approved fixture/data plan.
