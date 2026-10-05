# Architecture

Status: approved (Phase 1), scaffolded (Phase 2), simulation engine
implemented (Phase 3), persistence implemented (Phase 4), core UI and a
playable loop implemented (Phase 5), events, player decisions and a
balance pass implemented (Phase 6). This document records the architecture
as agreed, so later phases build on a written decision rather than
institutional memory.

## Module graph

```
:app  ──depends on──▶  :domain
  │                        ▲
  └──depends on──▶  :data ─┘
```

`:domain` has zero dependencies on `:app` or `:data`, enforced by module
boundaries (and, longer term, a Gradle dependency-verification check). This
is what makes the simulation engine testable independent of the Android UI
— a hard requirement from the product brief, not a nice-to-have.

## State model

`GameState` (in `domain/simulation/GameState.kt`) is a single immutable
snapshot of the entire run: the restaurant, its employees, the customers
currently present, inventory, menu, equipment, and financial ledger, plus
the current day number and a running log.

A day advances via one pure function:

```kotlin
fun interface DayTickEngine {
    fun advanceDay(state: GameState, decisions: PlayerDecisions, rng: RandomSource): DayResult
}
```

No shared mutable state anywhere in `:domain`. Every tick takes a snapshot
in and produces a new one out. Combined with `RandomSource` being the only
permitted entry point for randomness, this is what makes a seeded run
reproducible: same seed, same decisions, same state in ⇒ same result, every
time.

`DayTickEngine`'s actual rules are now implemented in
`DefaultDayTickEngine` (Phase 3): customer arrivals scale with reputation
(`CustomerFlow`), service quality falls out of employee skill/morale/stress
versus staffing ratio (`ServiceSimulator`), inventory is consumed and
spoils (`InventoryOperations`), equipment wears and can fail
(`EquipmentOperations`), the day's revenue/expenses roll up
(`DailyFinancialsCalculator`), and average satisfaction feeds back into
reputation (`ReputationModel`) — which feeds back into tomorrow's demand,
closing the feedback loop described in the product brief's emergent-
gameplay example.

Phase 6 put real constraints on that loop. The kitchen can only cook so
many meals (`KitchenModel`, from who is cooking and what is broken), every
meal comes out of real stock, cleanliness drifts down with every service
(`CleanlinessModel`), stress can recover as well as climb, and price
relative to a dish's `referencePrice` changes both what customers order and
how happy they are. A day now runs: morning decisions (`DecisionApplier`),
service, closing (spoilage, staff, equipment, books, reputation), then one
overnight event.

## Event engine

`EventRule` (in `domain/event/Event.kt`) describes an event as data: a
prerequisite predicate over `GameState`, a weight function, severity,
cooldown, a uniqueness flag, and a resolve function that produces an
`EventOutcome`. `EventOutcome` can name follow-up rule IDs, which is the
intended mechanism for chain reactions — ordinary rules whose prerequisites
reference each other's consequences, rather than a hardcoded event script.

`EventEngine.selectNext(...)` filters eligible rules by
prerequisite/cooldown/uniqueness, does a weighted-random pick, and
centrally stamps the resulting cooldown/uniqueness bookkeeping onto
`GameState` so individual rules only need to describe their own
consequences. Phase 6 added two things to it:

- **Quiet days.** `quietDayWeight` is the weight of "nothing happens".
  Rule weights rise as things go wrong, so a struggling restaurant gets
  events most nights and a calm one roughly every other night.
- **Follow-ups.** An outcome's `followUpRuleIds` are stored in
  `GameState.scheduledFollowUps` and fire first the next night, if their
  prerequisite still holds. A rule with zero weight only ever fires this
  way, which is how a consequence is expressed (fire → fire inspection,
  rat → health inspection).

**The rule library (Phase 6)** is `EventLibrary`: 25 rules, the top of the
brief's 15–25, in four groups: `StaffEvents`, `CustomerEvents`,
`KitchenEvents` and `SupplyEvents`. None is tied to a day number. Event
money is booked onto the closed day's `DailyFinancials.eventCashDelta`, so
ledger totals always match cash on hand.

## Persistence

`:data` is an Android library depending only on `:domain`. `GameDatabase`
(Room) is versioned from `version = 1` with schema export turned on from
the start, so future migrations have real prior schemas to test against
instead of retrofitting versioning after the fact.

**Save format (Phase 4):** a full `GameState` is serialized to JSON
(`kotlinx.serialization`, defined in `:domain` on `GameState` itself via
`@Serializable`) and stored as a single blob in one `SaveEntity` row —
always `id = 0`, matching the single-save-slot decision from Phase 0. This
was a deliberate choice against a fully relational schema: the game never
needs to SQL-query into save data, so normalizing every domain type into
its own table would have bought nothing but migration surface. The
trade-off is an explicit one, not an oversight — see the decisions log
below.

Several domain maps are keyed by a value class rather than a plain String
(e.g. `Employee.relationships: Map<EmployeeId, RelationshipScore>`), so the
shared `GameStateJson` config (in `domain/simulation/GameState.kt`) turns on
`allowStructuredMapKeys`, which encodes those as flat key/value arrays
instead of JSON objects. `ignoreUnknownKeys` is also on, and every field
added to `GameState` since Phase 2 carries a default value — together
that's the whole forward-compatibility story: an older save missing a
newer field decodes fine and just gets that field's default, so "schema
migration" for this save format is mostly about not removing fields or
making a previously-optional one required, rather than SQL `ALTER TABLE`
statements.

`GameRepository` is the interface `:domain`-facing code depends on;
`RoomGameRepository` is the Room-backed implementation. `load()` returns a
`SaveLoadResult` (`Success`, `NoSaveFound`, or `Corrupted`) rather than
throwing or returning a nullable `GameState` — a save can fail for two very
different reasons (nothing was ever saved, versus something was saved but
can't be trusted), and callers need to tell those apart. Three things can
mark a load `Corrupted`: the JSON fails to parse, the stored schema version
doesn't match what this build expects, or the JSON parses fine but
`GameStateValidator` (in `:domain`) finds values that don't make sense —
reputation outside 0-100, negative inventory, and so on. That validator is
deliberately in `:domain`, not `:data`: what counts as a *valid* GameState
is a business rule, so `:data` only ever decides "did the bytes parse,"
never "does this number make sense."

## UI

`:app` is Compose + Material 3, single-activity, unidirectional data flow
(Composable ⇄ ViewModel ⇄ repository/domain). No DI framework — dependencies
are wired by hand via plain constructors, revisited only if that becomes
genuinely unwieldy. No business logic in Composables or ViewModels.

**Core loop (Phase 5):** two navigation destinations. `StartScreen` offers
New Game and Continue (Continue only enabled once `GameRepository.
hasExistingSave()` says so — never implied as safe before that check
returns). `GameScreen` renders the day-to-day dashboard (cash, reputation,
cleanliness, staff, running log) or the game-over summary, depending on
whether the loaded `GameState.restaurant.status` is still `OPEN`/`CLOSED`
or has become `BANKRUPT`/`CONDEMNED` — one route covers both rather than a
separate game-over destination, so a `BANKRUPT` result can't leave the back
stack in a state where pressing "back" returns to a mid-run dashboard.

**Player decisions (Phase 6):** `PlayerDecisions` carries purchases,
price changes, menu availability, recipe-book additions, hires, firings,
days off, repairs and a deep clean. `DecisionApplier` carries them out in
a fixed priority order and skips (and logs) anything the cash on hand
can't cover. It's pure, so `GameViewModel` runs it on the draft plan to
preview costs, and the day-tick runs the same function for real. The
preview and the outcome can't disagree.

**The day loop on screen: an animated 2D restaurant (October 2026).**
Playtesting showed that text dashboards, even simple ones, didn't feel like
a game. The restaurant is now drawn and animated (`ui/scene/`):

- **Morning:** a top-down view of the dining room and kitchen
  (`RestaurantScene`, drawn in `SceneDrawing.kt` from the floor plan in
  `SceneLayout`). Staff stand at their stations with faces that follow
  morale and sweat when stressed. Problems are visible: a broken oven
  smokes, shelves empty out, the floor stains (and a rat appears) as
  cleanliness drops, and a sign says what kind of help is wanted
  (`MorningAdvisor.staffNeed`). Anything flagged by `MorningAdvisor` gets a
  pulsing "!". Tapping an object opens a small popup (`Sheets.kt`) with one
  or two choices, which show in the scene straight away and can be undone
  until service. Under the hood this is still a `PlayerDecisions` plan
  applied by `DecisionApplier` to the morning's starting state.
- **Service, played by you:** `ServiceNight` (in `:domain`, `service/`)
  is a small real-time game. Guests arrive in parties and sit at six
  numbered tables. You are a waiter: tap a table with a "?" to take its
  order, tap the counter to hand the ticket to the kitchen, tap it again
  when the plate is up, and tap the table to serve it. You can carry two
  plates. Each hired server looks after two tables on their own (shown in
  their colour); you always keep at least two. Cooks set how many dishes
  cook at once and how fast; a broken oven slows them; stock is checked
  when a dish starts cooking. Parties lose patience while they wait, and
  if it runs out they storm off. `ServiceNight` is immutable and
  deterministic: everything random (party sizes, arrival times, who orders
  what) is rolled once in `ServiceNight.open`, so a night depends only on
  the player's taps. Its `result()` has the same shape the automatic
  `ServiceSimulator` produces, so the engine is split into
  `openService` / `closeService` and closes a played night exactly like a
  simulated one. `advanceDay` still simulates the night automatically,
  which the balance tests rely on. `ServiceFloor` is the shared floor
  plan (table positions, aisles, walking speed); the app draws from it,
  so what you see and what the timing assumes can't disagree.
- **End of night:** a receipt slides up over the room with the profit,
  guests fed and the overnight event (some events also show in the room,
  like a rat or an oven fire). The itemised bill is one tap away.

Everything is drawn with Compose shapes; there are no image assets and no
emoji, because older Android versions (the test phone runs Android 9) can't
draw newer emoji. Invisible, labelled tap areas over each object make the
scene usable with a screen reader. Forecasts and advice come from
`:domain` (`OutlookCalculator`, `MorningAdvisor`), so Composables don't
reimplement rules.

`GameViewModel` derives each day's `RandomSource` from `GameState.seed`
and `GameState.day` (`seed * <constant> + day`) rather than keeping one
continuous seeded `Random` alive in memory for the whole run. This makes
any single day reproducible from the state it started from, but is a known
partial answer to full-run determinism: replaying an entire run from day 1
across an app restart mid-run isn't guaranteed byte-identical to an
uninterrupted run, since RNG stream position itself isn't persisted — only
the base seed. Documented here as a deliberate, small trade-off rather than
a hidden gap; revisit if full-run replay ever becomes a real requirement
(e.g. for a "replay" or seed-sharing feature).

`AppContainer` (constructed once, in `RecipeForDisasterApplication`) is the
hand-wired object graph — the Room database, `GameRepository`,
`EventEngine` (`EventLibrary.engine()`), and `DayTickEngine`. Each ViewModel gets a small `ViewModelProvider.Factory`
rather than a DI framework, per the no-DI decision below.

The Phase 2 `PlaceholderScreen` (and its accompanying placeholder tests)
are removed as of Phase 5 — its own doc comment said it existed only to
prove module wiring until the real dashboard arrived, and that's now here.

## Testing

| Module    | Test type                          | Runs on            |
|-----------|-------------------------------------|---------------------|
| `:domain` | JUnit, plain Kotlin                 | JVM, no emulator     |
| `:data`   | Room in-memory DB (instrumented)    | Device/emulator      |
| `:app`    | JUnit (unit) + Compose UI (instrumented) | JVM / device/emulator |

`BalanceSimulationTest` stands in for playtesting. It plays 40 seeded runs
with scripted policies and asserts that decisions matter. As of Phase 6:
doing nothing lasts about 12 days, a careless owner who only restocks about
20, and a sensible player usually 120+ (median about 167 with no cap, with
rent increases eventually ending every run). It also asserts that every
event in the library fires somewhere across those runs.

## Key decisions log

- **No DI framework for MVP.** Plain constructor injection. Revisit if the
  wiring actually becomes unwieldy — not before.
- **`minSdk` = 28 (Android 9), `targetSdk` = 36 (Android 16).** Originally
  31 (Android 12), chosen to match "phones from roughly the last 5 years"
  (~79% of active Android devices as of September 2026). Lowered to 28 in
  October 2026 so the game runs on the developer's own test phone, a
  Samsung Galaxy S8 (last updated to Android 9). Nothing in the app needs
  an API above 28, and Lint's `NewApi` check guards against one sneaking in;
  the trade-off is testing on older Android versions in Phase 9. `targetSdk`
  36 is required for new Google Play submissions as of the same date.
- **Android Lint only, no ktlint/detekt.** Zero new dependencies for
  baseline static analysis. Revisit as a deliberate, approved decision if
  style enforcement becomes a real pain point.
- **`:domain` is a plain Kotlin/JVM module, not an Android library.** Non-
  negotiable per the product brief's "testable independent of Android UI"
  requirement.
- **Save format is one JSON blob per save, not a relational schema
  (Phase 4).** Approved trade-off: simpler and faster to evolve, at the
  cost of not being able to SQL-query into save data — acceptable since
  nothing in the game needs to.
- **Phase 5 ships with a single player action ("Open for the day").**
  `PlayerDecisions` stays the Phase 2 placeholder; real decisions are
  deferred until there's event/economy content (Phase 6+) for them to
  matter against, rather than building decision UI with nothing yet to
  decide about.
- **Phase 6 rebalance.** Fixed costs used to exceed the most the restaurant
  could earn, so every run went bankrupt on schedule whatever the player
  did. Starting numbers, prices (and customer budgets, which were in a
  different unit), stress recovery, the equipment failure curve and the
  reputation model were all retuned, checked by `BalanceSimulationTest`.
  These are still first-pass numbers pending real playtesting.
- **No event choices yet.** Events resolve on their own. Letting the
  player pick a response (pay the fine or argue, etc.) would need a
  "pending decision" in `GameState`; deferred until the decisions above
  have been playtested.
- **Build output in `build.nosync/`.** The project sits on an iCloud-synced
  Desktop, which offloaded build outputs mid-build and hung Gradle.
- **Per-day RNG seed derived from (base seed, day number), not one
  continuous stream (Phase 5).** Simpler, and sufficient for reproducing
  any single day from its starting state; the trade-off (documented above,
  under UI) is that full-run replay across an app restart isn't guaranteed
  byte-identical.
- **Two small `androidx.lifecycle` artifacts added in Phase 5:**
  `lifecycle-viewmodel-compose` (the `viewModel()` composable) and
  `lifecycle-runtime-compose` (`collectAsStateWithLifecycle()`). Same
  dependency family and version already pinned for `lifecycle-runtime-ktx`
  since Phase 2 — needed to give each screen its own ViewModel, which was
  always part of the agreed Compose + ViewModel architecture, not a new
  category of dependency.
