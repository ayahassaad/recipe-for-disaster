# Recipe for Disaster

A single-player Android restaurant-management simulation. You run a
restaurant and try to keep it alive for as many in-game days as possible,
against a simulation of staff, customers, inventory, equipment, and events
that's meant to produce its own chaos rather than lean on scripted moments.

This repository is at the **project foundation** stage: the module
structure, build configuration, and baseline architecture exist; the actual
simulation, persistence, and UI are being built out phase by phase (see
`docs/architecture.md` for the full plan).

## Status

- [x] Phase 0 — Product definition
- [x] Phase 1 — Technical architecture
- [x] Phase 2 — Project foundation
- [x] Phase 3 — Simulation engine
- [x] Phase 4 — Persistence
- [x] Phase 5 — Core UI
- [x] Phase 6 — Events and emergent systems (25-event library, player decisions, balance pass)
- [ ] Phase 7 — Accessibility
- [ ] Phase 8 — Security review
- [ ] Phase 9 — Device testing
- [ ] Phase 10 — Release preparation

## Module structure

```
:domain   Pure Kotlin/JVM — the simulation engine. No Android or AndroidX
          imports, ever. Unit-testable on the plain JVM.
:data     Android library — Room persistence, repositories. Depends on
          :domain only.
:app      Android application — Jetpack Compose UI, Material 3. Depends on
          :domain and :data.
```

See `docs/architecture.md` for the reasoning behind this split, the state
model, and the event-engine design.

## Requirements

- Android Studio (current stable)
- JDK 21
- minSdk 28 (Android 9) / targetSdk 36 (Android 16)

## Building

```
./gradlew build
```

Build output goes to each module's `build.nosync/` folder rather than
`build/`. The project lives on an iCloud-synced Desktop, and iCloud skips
folders ending in `.nosync`; without this, builds hung reading back their
own compiled classes (see the note in `build.gradle.kts`).

## Testing

- `:domain` — `./gradlew :domain:test` (plain JUnit, no emulator needed).
  Add `-DbalanceReport=true` to print the balance simulation's survival
  stats per play style (`-DbalanceMaxDays=400` to look further out).
- `:data` — `./gradlew :data:connectedAndroidTest` (needs a device/emulator; Room in-memory DB tests)
- `:app` — `./gradlew :app:test` for unit tests, `./gradlew :app:connectedAndroidTest` for Compose UI tests

## License / ownership

All code, design decisions, and commits in this repository are authored and
owned by the project's human developer. AI assistance was used for parts of
the implementation under the developer's direction and review.
