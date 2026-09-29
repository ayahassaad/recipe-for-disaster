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
- [x] Phase 2 — Project foundation (this commit)
- [ ] Phase 3 — Simulation engine
- [ ] Phase 4 — Persistence
- [ ] Phase 5 — Core UI
- [ ] Phase 6 — Events and emergent systems
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
- minSdk 31 (Android 12) / targetSdk 36 (Android 16)

## Building

```
./gradlew build
```

**Note:** this scaffold was generated in a sandboxed environment with no
Android SDK installed and no access to Maven Central, so the build has
*not* been verified to compile or run yet. Open it in Android Studio to let
it sync, resolve dependencies, and surface anything that needs fixing —
dependency versions in `gradle/libs.versions.toml` were checked against
current release pages as of the date they were added, but haven't been
build-tested end to end.

## Testing

- `:domain` — `./gradlew :domain:test` (plain JUnit, no emulator needed)
- `:data` — `./gradlew :data:connectedAndroidTest` (needs a device/emulator; Room in-memory DB tests)
- `:app` — `./gradlew :app:test` for unit tests, `./gradlew :app:connectedAndroidTest` for Compose UI tests

## License / ownership

All code, design decisions, and commits in this repository are authored and
owned by the project's human developer. AI assistance was used for parts of
the implementation under the developer's direction and review.
