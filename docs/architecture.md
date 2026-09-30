# Architecture

Status: approved (Phase 1), scaffolded (Phase 2), simulation engine
implemented (Phase 3), persistence implemented (Phase 4). This document
records the architecture as agreed, so later phases build on a written
decision rather than institutional memory.

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
gameplay example. `EventEngine.selectNext` is real, working selection
logic; it just has nothing to select yet, since the actual event rule
library is Phase 6 content.

## Event engine

`EventRule` (in `domain/event/Event.kt`) describes an event as data: a
prerequisite predicate over `GameState`, a weight function, severity,
cooldown, a uniqueness flag, and a resolve function that produces an
`EventOutcome`. `EventOutcome` can name follow-up rule IDs, which is the
intended mechanism for chain reactions — ordinary rules whose prerequisites
reference each other's consequences, rather than a hardcoded event script.

`EventEngine.selectNext(...)` is implemented (Phase 3): it filters eligible
rules by prerequisite/cooldown/uniqueness, does a weighted-random pick, and
centrally stamps the resulting cooldown/uniqueness bookkeeping onto
`GameState` so individual rules only need to describe their own
consequences. The initial rule library itself (15–25 events for MVP, per
the product brief) is still Phase 6 work — an `EventEngine` built with an
empty rule list is valid and simply never fires, which is the correct
state until that content exists.

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

## Testing

| Module    | Test type                          | Runs on            |
|-----------|-------------------------------------|---------------------|
| `:domain` | JUnit, plain Kotlin                 | JVM, no emulator     |
| `:data`   | Room in-memory DB (instrumented)    | Device/emulator      |
| `:app`    | JUnit (unit) + Compose UI (instrumented) | JVM / device/emulator |

## Key decisions log

- **No DI framework for MVP.** Plain constructor injection. Revisit if the
  wiring actually becomes unwieldy — not before.
- **`minSdk` = 31 (Android 12), `targetSdk` = 36 (Android 16).** Chosen to
  match "phones from roughly the last 5 years," which as of the decision
  date (September 2026) covers on the order of ~79% of active Android
  devices. `targetSdk` 36 is required for new Google Play submissions as of
  the same date.
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
