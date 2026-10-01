# Task 3 — equipment use cost, CR012

Read AGENTS/CURRENT-STATE, Issue309 complete and integrated Slice2 financial guard.
Only machinery simple cost and its tests/UI. Do not rewrite navigation or payroll.
No subagents. Preserve data and established canonical EquipmentRepository/ledger.

## Required implementation

EquipmentLine gains optional immutable snapshot of agreed UNIT day/use price,
currency and agreement date. EquipmentDraftLine gains optional snapshot override
at end. New line preloads usual farm price by type; user can edit before confirm.
Quantity multiplication once using checked arithmetic; existing snapshot is kept
on quantity edit unless price explicitly changed. Currency stays historical;
future usual rate changes do not rewrite equipment historical costs (including
open campaigns). Missing price !=known zero. Keep legacy unknown null without
fake backfill; preserve old ledger while any unknown remains, explain/confirm
missing prices explicitly rather than replacing with priced subtotal.

Room additive migration v21→22 for nullable snapshot fields on harvest_equipment;
schema exported by compiler, upgrade tests preserve oldrows. DayCostLedger
equipment path reads per-line snapshots, posts/updates only one DAY_EQUIPMENT;
manual collision handling remains Expense POSTED sole-source and common paid
labour guard from Slice2 must still protect collateral ledger mutations.

EquipmentSheet uses existing machine/type selection, quantity, unit cost of day/
use and preview line total; usual editable prefill, no €/hour amortization matrix.
Cost per unit and total distinguished clearly; no double multiplication. When
replacing form, unchanged legacy lines not silently assigned latest default.
Costs missing are visible; explicit editing records snapshot. Totals from Expense
only, no second money from operational EquipmentLine in campaign total.
Closed campaign retains history and rejects line/cost edits.

## Acceptance tests and evidence

-1 vibradora70+1peine20+1remolque30=120, unique ledger; 2vibradoras70=140 once.
-change habitual farm price does not rewrite saved equipment snapshot/ledger;
 quantity change preserves actual unit price and correctly updates unique expense.
-null price remains unknown, zero accepted as explicit zero, wrongcurrency/mixing
rejected, Longoverflow rollback; manual collision not double money.
-Room upgrade/reopen persists agreement and legacy, outbox no duplicate expense.
-Compose type/machine quantity/price/default/override/edit and error/closed states.
-emulator capture sheet+day resources. Full JVM, lint, 3builds+androidTest, CI,
independent review. Commit bounded files only, root handles PR/integration/docs.

Carry-forward from Slice2 review: legacy/new lines on an existing day resolve
historical denomination from the unique authoritative POSTED DAY_EQUIPMENT ledger
and compatible confirmed snapshots before usual currency/defaults. Ambiguous
contexts block with an actionable explanation; never reinterpret old minor units
as another currency. Money helper now supports currency minor units. Add focused
JPY historical/EUR usual and partly confirmed legacy cases. Quantity/price
overflow must show an actionable error while Save is disabled, not silent null.

Same legacy ruling as Slice2: never append a new priced equipment line to a day
with unresolved historical prices while keeping an old ledger that omits the new
cost. Reject the priced append with actionable missing-price confirmation until
the legacy set is explicit/complete; transaction rolls back rows/outbox. Partial
confirmation of existing legacy snapshots may preserve the old ledger until all
are known, visible as awaiting confirmation. Add a Room regression for this path.

Integration seam discovered during preparation: existing DayCostContractTest
expects changing usual equipment rates to recalculate saved days. Update those
expectations to the new historical contract, retaining unique-ledger/manual
collision assertions; explicit price confirmation must replace implicit repricing.
EquipmentRules.key uses machine/type/named-other identity: preserve canonical
ids and snapshots when replacing unchanged lines. Registered-machine quantity
remains one. Review nullable snapshot propagation in every entity→domain mapper
(including DayCostLedger), and preferCalculated rejects incomplete equipment
prices before demoting manual costs. Known zero equipment must retain a posted
zero Expense instead of removing it. Use scoped tests to cover these seams.
