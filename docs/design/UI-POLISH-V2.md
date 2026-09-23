# UI polish v2 — owner-directed usability pass

**Status:** in progress on `feat/android-ui-polish-v2` (not merged)
**Origin:** owner review on a physical Android device, 2026-09-23
**Scope:** visual/usability only. No change to architecture, Room, repositories,
synchronization, domain rules, offline behaviour or navigation roots.
**Contract:** keeps the locked identity (`VISUAL_DESIGN_LOCK.md`, `DESIGN_SYSTEM.md`):
warm cream surfaces, olive green, editorial serif titles, soft cards. The approved
mock-up board shared by the owner is a hierarchy reference only; none of its data is copied.

## A. Colour hierarchy

Device finding: too many elements (CTAs, chips, states, titles, secondary buttons) used
the same dark olive and competed with each other.

| Role | Token | Hex | Used for |
|---|---|---|---|
| Primary action | `MoOlivePrimary` | `#3E5A32` | primary CTA (`MoPrimaryButton`), selected bottom-bar item and the central “+” only |
| Selection / positive | `MoOliveMid` on `MoOliveTint` | `#4F6B39` / `#E9EEE0` | chips, success status, text actions, checkboxes, focused fields (Material `primary`) |
| Surfaces | `MoCream` / `MoWarmWhite` | `#F8F6EE` / `#FCFBF7` | background / cards |
| Body text | `MoInk` | `#2A2823` | default text and key figures (kg, €, hours) |
| Editorial titles | `MoOliveDark` | `#173122` | serif headlines (brand) |
| Secondary text | `MoTextSecondary` (warm grey) | `#67625A` | metadata, neutral status |
| Planning / info | `MoInfo` / `MoInfoText` | `#5E7D8C` / `#4C6876` | planned work, neutral information |
| Notices | `MoWarning` / `MoWarningText` | `#C49842` / `#805B18` | warnings and notices |
| Secondary buttons | `MoInk` on `MoWarmWhite`, border `MoOutlineStrong` `#D6D0C2` | | neutral, never competing with the CTA |

All text pairs are asserted ≥ 4.5:1 (WCAG AA) in `DesignContrastTest`.
