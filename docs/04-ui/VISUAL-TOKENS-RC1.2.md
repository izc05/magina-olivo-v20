# RC1.2 Visual Tokens — Phase 3 Draft

**Status:** APPROVED DIRECTION / IMPLEMENTATION DRAFT  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Purpose:** provide one exact visual token source for Compose and Figma Phase 3 work.

## 1. Color tokens

These values are the current implementation draft and must be contrast-checked before Gate 3.

### Primitive palette

```text
olive/900   #2F3A1C
olive/800   #3F4D25
olive/700   #556B2F
olive/600   #6D803E
olive/500   #7F944B

sage/600    #78935F
sage/500    #8DA874
sage/200    #DDE7D3

cream/50    #FFFDF8
cream/100   #F8F4E9
cream/200   #F1EAD8

earth/500   #C39A68
earth/600   #95693F

charcoal/900 #20231D
charcoal/700 #4C5148
charcoal/500 #71766C

line/200    #E1E5DA
white       #FFFFFF
black       #000000
```

### Semantic color mapping

```text
color/bg/app              cream/50
color/bg/subtle           cream/100
color/bg/card             white
color/bg/olive            olive/700
color/bg/oliveStrong      olive/900
color/bg/sageSoft         sage/200

color/text/primary        charcoal/900
color/text/secondary      charcoal/700
color/text/onOlive        white
color/text/accent         olive/700
color/text/earth          earth/600

color/border/default      line/200
color/border/strong       sage/500

color/action/primary      olive/700
color/action/pressed      olive/900
color/action/secondary    sage/200

color/state/success       #3F7D43
color/state/warning       #A3650E
color/state/error         #B6463A
color/state/info          #47789A
```

## 2. Typography

Use a modern highly readable sans-serif.

Preferred working family:

```text
Inter
```

Reason: robust Android availability during prototyping, strong data readability, broad weight support, no decorative bias.

Final brand font may change at Naming Gate if the logo system requires it, but app body typography should remain highly legible.

### Type scale

```text
display/lg    32sp / 38sp / 700
headline/lg   28sp / 34sp / 700
headline/md   24sp / 30sp / 700
title/lg      20sp / 26sp / 700
title/md      18sp / 24sp / 600
body/lg       16sp / 24sp / 400
body/md       14sp / 20sp / 400
label/lg      14sp / 18sp / 600
label/md      12sp / 16sp / 600
metric/xl     30sp / 34sp / 700
metric/lg     24sp / 28sp / 700
```

Rules:

- monetary values and key agronomic metrics use tabular numerals when available;
- never use body text under 12sp;
- screen titles must survive Android font scaling;
- avoid all-caps except short section labels where contrast/readability remains good.

## 3. Spacing

Base grid:

```text
space/2xs   4dp
space/xs    8dp
space/sm    12dp
space/md    16dp
space/lg    24dp
space/xl    32dp
space/2xl   48dp
```

Screen horizontal padding:

```text
compact phone   16dp
common phone    16dp
large phone     20dp
```

## 4. Radius

```text
radius/sm     10dp
radius/md     14dp
radius/lg     18dp
radius/xl     24dp
radius/full   999dp
```

Preferred:

- field/input: 14dp;
- ordinary card: 18dp;
- hero/farm-cover card: 24dp;
- status chip: full.

## 5. Elevation / shadow

Use elevation sparingly.

```text
elevation/0    none
elevation/1    subtle card separation
elevation/2    sticky/action surface
elevation/3    modal/bottom-sheet
```

Avoid heavy floating-card shadows across analytics screens.

## 6. Iconography

Direction:

- simple outlined/filled hybrid;
- 20dp ordinary inline;
- 24dp standard action;
- 28–32dp hero/metric;
- use one icon language consistently.

Key families:

- farm/parcel;
- olive/production;
- water/irrigation;
- treatment;
- fertilizer;
- pruning;
- soil;
- machinery;
- document/OCR;
- expense/income;
- weather;
- calendar/reminder;
- cooperative/mill;
- map/location.

## 7. Charts

Production-quality chart rules:

- use olive for primary production series;
- use earth accent for secondary/yield series;
- never rely only on color; provide legend/label/shape;
- no 3D charts;
- no decorative donut where a direct metric is clearer;
- line/bar charts use readable axes and units;
- partial yield analysis must visibly disclose coverage;
- unknown data is absent/unknown, not zero.

## 8. Metric cards

Default metric card:

```text
min height      96dp
padding         16dp
radius          18dp
background      card
border          subtle
label           label/md
value           metric/lg or metric/xl
unit            body/md or label/lg
support text    body/md
```

Cards must support:

- normal;
- partial data;
- estimate;
- unavailable.

## 9. Buttons

Primary:

```text
height        52dp
radius        14dp
min touch     48dp
fill          olive/700
text          white
```

Secondary:

- transparent or sage-soft;
- clear outline/text;
- same touch target.

Danger:

- only for destructive actions;
- never olive-colored.

## 10. Bottom navigation

Frozen structure:

```text
Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil
```

Rules:

- 5 destinations only;
- center Register action visually emphasized;
- selected state uses olive accent + label;
- unselected icons remain legible;
- safe-area aware;
- no extra permanent tab for Campaña/Costes/Rentabilidad.

## 11. Farm photography

Farm cover:

- optional;
- 16:9 or ~1.8:1 crop for hero;
- scrim only when overlaying text;
- fallback gradient/olive texture when missing;
- do not block offline display if cloud image unavailable.

## 12. Weather visual effect

Home supports presentation states:

```text
CLEAR
CLOUDY
RAIN
FOG
WIND
STORM
```

Effects are lightweight and secondary to content.

Reduced-motion mode:

- freeze cloud motion;
- replace rain animation with static subtle overlay;
- remove wind leaf movement;
- keep condition icon/text.

## 13. Responsive phone targets

Reference frames:

```text
360 × 800 dp
393 × 852 dp
412 × 915 dp
480 × 960 dp
```

Primary acceptance target: 393–412dp portrait.

## 14. Accessibility

Mandatory:

- 48dp touch targets;
- contrast WCAG-aligned where applicable;
- no color-only status;
- support Android font scaling;
- TalkBack labels;
- charts expose textual summaries;
- motion respects system settings.

## 15. Naming/brand placeholder

Until Naming Gate closes:

- working concept may display `OleaTrack`;
- production UI code must obtain display brand from resources/config;
- no domain entity references the brand name;
- no final launcher/package rename until approved.


## 16. Contrast review — 2026-09-18

Key text/background pairs were checked against WCAG contrast ratios.

Verified examples:

- charcoal/900 on cream/50: 15.65:1;
- charcoal/700 on cream/50: 8.01:1;
- olive/700 on white: 5.95:1;
- olive/700 on cream/100: 5.41:1;
- success on white: 4.97:1;
- info on white: 4.75:1;
- error on white: 5.35:1;
- earth/600 adjusted to #95693F: 4.81:1 on white;
- warning adjusted to #A3650E: 4.75:1 on white.

This is a token-level contrast check, not a substitute for final screen-by-screen visual QA, large-text exceptions, disabled-state review or chart accessibility review.
