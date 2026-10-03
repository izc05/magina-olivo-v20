# Task 4 — CR012 canonical campaign/day surfaces

Read AGENTS/CURRENT-STATE, Issue309 complete, design locks/tokens and integrated
Slices2/3. No subagents; no root navigation changes or unrelated product expansion.

## Canonical costs/context

CampaignNotebook currently pulls farm/date-only Expenses into campaign projection.
CR012 requires EXPLICIT campaign context for financial costs. Campaign costs and
generatedCost/kg must include only Expense POSTED with campaignId==campaign.id;
outside pruning/fuel etc remains Farm/Parcel. Expense writer currently resolves
current campaign from farm alone: remove that implicit assignment for outside
expense flows and ensure linkedHarvest/explicitCampaign still resolves correctly.
Keep annual works view outside economic campaign summary; no lost original data.
Migration does not retroactively guess whether oldexpense was explicit.
Delivery denominator confirmed canonical campaign linked kg; no fictitious parcela
weights/oldmanualharvestkg. Null/no kg shows —, explicit zero cost may show0.

## Day of recollection

After production/kg/pesadas/parcelas show Recursos del día navigable cards:
Jornales(persons,duration,confirmed cost), Maquinaria(equipment/usages,cost), Otros
gastos(amount). Show Coste del día and coste/kg computed only with postedExpenses
and canonical day weighed kg. Each card opens canonical details/form from2/3.
Remove large independent workerCount/machineryText fields from NEW normal form;
historical values still read visibly without pretending canonical priced entries.
Method/notes secondary Más detalles. Do not repeat Pesadas information.

## Campaign / Cuaderno

Separate Producción (days,pesadas,yield) from Costes de recogida (labour
generated/paid/pending, equipment, others,total,costkg). No Trabajos0/Nada
planificado inside campaign economic summary, no Coste + Gastos duplicate money.
Compact navigable summary cards, no long worker/machine list on dashboard;
details use existing person history and movements. Campaign costs incorporate
all explicitly-contextual categories, not justHARVEST/TRANSPORT subset.
Campaign > Jornales shows required names,workdays/hours and balances; same data
via Day/Cuaderno, no duplicate saved record/forms. All loading/error/empty/closed
flows truthful and payments allowed after close.

## Visual contract

Surfaces MoWarmWhite/cream, icon+título+principal+secondary+accent+tap where useful.
Olive campaign, softGold pesos, labour terracotta, earth machinery, money other
expense, info blue-gray total/costkg, successpaid/warningpending/infopartial.
Reutilize Mo components, add shared token if missing not localhex. Semantics
text+icon+color, accessible contrast and largefont, no saturated full backgrounds.
Yield sage distinct cost; reduceMotion preserve design.

## Required evidence/tests

530total/3200kg=.165625 internal, rounded visual~.17, partial payments don't
change cost/kg; null/zero/no deliveries differentiated. Outside campaign
farm/date expenses never count; explicitFarmCampaign flows count once.
Compose card navigation, no duplicate totals/longlists, legacy unknown visible,
textlarge/360dp and errorloadingempty. Add emulator screenshots from actual
Day,Campaign,person/payment states; full instrumented suite + Room if writer
context changes + JVM/lint/threebuilds. Independent review and greenCI before
integration; final package DEV APK and evidence for owner physical review.
Do not declare device gatesPASS without owner/device evidence; complete code
phase, report remaining actual gate separately.

Carry-forward confirmed integration observation: generic ExpenseForm.toForm/toDraft
currently loses Expense.currency and uses EUR Money defaults, including line totals.
Preserve historical currency in form/draft (compatible defaultEUR for new existing
callers), parse/edit all amounts with contextual ISO precision, no currencyconversion
or new selector. Unsupported historical codes block actionable editing ratherthan
rewriting currency/amount. Wire documentproposal currency if it exists in current
contract; do not guess from labels. Add JPY/EUR/KWD roundtrip/update regressions and
verify payment/Expense ledger independence. This pre-existing issue must be fixed
before completeCR012, naturally within Slice4 financial Expense context boundary.

Prepared integration seams: ExpenseForm currently also omits campaignId, so
preserve an existing explicit campaign link on edits and provide explicit
campaign/day context for campaign-originated new expense forms while outside
Farm/Parcel forms remain unassigned. Do not remove a legitimate campaign link
when eliminating implicit association. Room/UI regression for both flows.
CampaignDashboard and CampaignComparison currently round using unchecked Long
multiplication (amount*1000*2). Wire the precise Slice1 RecollectionCostSummary
for canonical campaign cost/kg and safe display rounding, rather than retaining
an overflow-prone parallel formula. Confirm zero-cost with real kg yields zero;
without kg remains unavailable. Mixed currencies stay visibly separate/no FX;
unsupported codes remain unavailable and original code shown. Add focused high
amount/JPY/KWD/no-kg/zero and explicit-context tests on exposed KPI paths.

Category seam: old NotebookCosts includes FUEL/REPAIR under MACHINERY. For the
CR012 recollection cards, fuel/transport/repair belong to Otros gastos perissue.
Create a mutually exclusive ledger projection: Jornales (DAY_LABOUR or canonical
manual labour), Maquinaria (DAY_EQUIPMENT or canonical rental/MACHINERY), Otros
(the remaining explicitly-contextual posted expenses, including fuel/repair).
Reuse DayCostKind classification for compatible historical manual HARVEST labels
where relevant, without allocating unidentified historical labour to persons.
Test that all three buckets sum to total exactly once in each currency and a
manual rental collision still counts the posted manual cost, not draftcalculated.
Do not indiscriminately change unrelated annual machinery-history categorization.

Visual carry-forward from Slice3 evidence: current machine detail rows use
MoCompactListItem icon defaults, so the comb's Shears icon has a green badge
while shaker/trailer use earth. Apply machinery Earth text/tint consistently
including detail rows and sheet/context, retaining recognizable icons; same
concept same tone across Day/Campaign/detail. The old legacy text fields in
that screenshot are retained only as historical display, not new normalinputs.

Carry-forward measured modal interaction: after price editing + closing IME,
EquipmentSheet ModalBottomSheet can remain partially expanded, with internal
scroll max0 and Save below native window. Pricing/draft tests cover exact values
via enabled semantic Save; physical initial-save controls remain, but this does
not establish post-edit physical reachability. A diagnostic drag experiment was
not conclusive (Compose idle failure), no production fix has been claimed.
Within Slice4 accessibility/surface refinement inspect the actual modal anchoring
and verify reachable save after keyboard editing at360dp/largefont. Resolve any
real UX obstruction narrowly using existing modal contract; no arbitrary test
sleeps/retry clicks or new navigation. Document actual physical interaction
proof separately from semantic draft tests and leave owner-device acceptance
pending. Final review must triage this carried observation explicitly.

Include the existing labour edit modal in this post-text-entry keyboard/anchor reachability check: legacy JPY draft test also lost its physical Save callback in one full CI suite while other suites passed. Preserve JPY1000 semantics; do not infer conversion or ledger bug from a missing UI callback. Native accessible save interaction is a shared surface concern.

## Amendment 2026-10-03 — owner decision and P1 (executor Claude from `e43e3dbb`)

Owner: «Si hay una campaña de recogida activa, al pulsar Cuaderno → Gasto, el formulario debe
venir preseleccionado como "Gasto de recogida · Campaña 2026/27". El usuario puede cambiarlo
expresamente a "Gasto general de finca/parcela".» This replaces "outside Farm/Parcel forms remain
unassigned" for the Cuaderno entry only. Codex handed execution to Claude (no tokens).

- The expense form shows an explicit «Tipo de gasto» choice whenever its Farm has a running
  (ACTIVE/HARVEST) campaign or the expense already carries one: «Gasto de recogida · Campaña …»
  or «Gasto general de finca/parcela». Jornada-linked expenses keep their day's campaign.
- Cuaderno → Gasto (`expenses/farm/{farmId}` without `campaignId`) preselects the running
  campaign, visibly. The general Gastos screen and any edit never preselect or re-assign.
- Changing Farm clears the campaign; no running campaign → no choice, general expense.
- P1: a document taken on «Gastos de recogida» or Cuaderno → Gasto opens its review with the
  screen's Farm and Campaign (`document/{id}?farmId&campaignId&recollection`); documents listed
  «por revisar» reopen without context (general unless the farmer chooses).
- The writer is unchanged: campaign only from the explicit draft or the linked day; no schema
  change. Tests: `RecollectionChoiceTest` (JVM), `RecollectionChoiceUiTest` (Compose) and
  `ExpenseLedgerContractTest.onlyTheFormsExplicitChoiceCountsInCampaignCostsAndCostPerKg` (Room).
