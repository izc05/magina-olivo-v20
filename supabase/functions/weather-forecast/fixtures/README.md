# Test fixtures — synthetic, not reference data

These files follow the documented formats of AEMET OpenData and MET Norway so the tests run
offline. They are **not** live captures and **not** a source of truth:

- `aemet-municipios.json` is a hand-made excerpt. Names and coordinates are approximate and the
  **INE codes are illustrative**. Bedmar y Garcíez uses the sentinel code `23000`, which is not a
  real municipality code (municipality number 000 does not exist), so nobody reads it as a fact.
- Verified live (2026-09-25, `docs/06-testing/evidence/phase20b/`): `23019` is **Campillo de
  Arenas** in AEMET's master list. Bedmar y Garcíez's real code is pending verification; `23902`
  (the code the owner's first deployed function received) is the candidate, not confirmed.

Real responses belong in `docs/06-testing/evidence/`, never here.
