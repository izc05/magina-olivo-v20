# Android visual identity pass

**Status:** local visual work, pending integration after the active UI polish branch settles.

The owner supplied six onboarding mockups and one Mi Campo mockup on 2026-09-23.
They define the intended composition, photographic tone, olive branch mark,
editorial typography and card hierarchy. The numbers, place labels, map parcels,
weather, prices and notices in the mockups are illustrative, not application data.

## First implementation slice

- Replace the generic oval based Compose olive mark with one reusable vector mark.
- Use the same mark for the Android launcher icon on a warm cream background.
- Replace the welcome screen's abstract hills with a photographic olive grove
  asset, with a cream fade to preserve text legibility.
- Use that landscape in Inicio's hero until a user cover photo can be wired in.
- Remove sample weather, production, market and cooperative claims from Inicio;
  the cards now communicate their purpose without pretending to show live data.
- Let the production navigation shell own system bar insets. The onboarding
  and nested Inicio scaffold no longer add a second top inset, which had left
  an oversized blank band above the logo on the phone screenshots.
- The asset was generated with the built-in image tool using the owner's first
  onboarding mockup as a mood reference. It is decorative and does not claim to
  depict a particular real farm or geographic viewpoint.
- The app bundles a 720 × 900 JPEG derivative to keep decode and emulator
  rendering memory low; the full generated PNG remains outside the APK.

## Remaining visual sequence

1. Compare the six rendered onboarding screens at 360, 393 and 480 dp against
   the owner's mockups; adjust layout and illustrations without shrinking touch
   targets or losing font scaling.
2. Rework Inicio from truthful local data and available contextual services.
   Use the Mi Campo mockup for card hierarchy, while keeping the approved root
   navigation `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`.
3. Apply the same mark, photography, icon language and spacing across Farm,
   Parcel, Campaign, Activity, Harvest and other existing screens.
4. Validate contrast, TalkBack semantics, empty/offline states and emulator
   screenshots before offering the APK for physical review.
