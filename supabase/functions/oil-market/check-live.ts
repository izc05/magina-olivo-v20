// Reads a saved copy of the live Junta page and prints what `oil-market` would take from it.
// Used by the manual deploy workflow before deploying; exits non-zero if the page is not understood.
//   node --experimental-strip-types supabase/functions/oil-market/check-live.ts page.html

import { readFileSync } from "node:fs";
import { parseJuntaPage } from "./junta.ts";

const values = parseJuntaPage(readFileSync(process.argv[2], "utf8"), new Date());
for (const value of values) {
  console.log(`${value.category}\tsemana ${value.isoWeek}/${value.isoYear}\t${value.periodStart}..${value.periodEnd}\t${value.value} €/kg`);
}
