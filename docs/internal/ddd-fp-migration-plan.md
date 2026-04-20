# DDD + FP Migration Plan (Post-Book) — Agent-Ready

## Summary
Migrate remaining high-impact domains to the same pattern established in Book:  
`route DTO -> domain command (validated) -> service orchestration -> decider/policy -> persistence adapters`, with illegal states blocked at command/value-object boundaries and “one policy, one test” coverage.

Execution order is optimized for risk and dependency flow:
1) `author + series`, 2) `organization/library + settings`, 3) `user/identity + auth policy`, 4) `processing/import + staging`, 5) `opds + koreader integration`.

## Current Status
- **Phase 1 (Author + Series): Complete**
  - Typed command mapping at route boundaries for author/series update/create paths.
  - Added author/series normalization helpers.
  - Introduced author/series mutation deciders and moved title/name selection logic into decider layer.
  - Rewired author/series services to consume command + decider flow.
  - Added command/decider focused tests and API coverage for series update with nullable title.
  - Introduced `AuthorMutationRepository` and `SeriesMutationRepository` interfaces to split mutation ports from read queries.
  - Added `AuthorServiceTest` and `SeriesServiceTest` orchestration tests.

- **Phase 2 (Organization: Library + Settings): Complete**
  - Added library command mapping at route boundaries for create/update.
  - Added `LibraryTitle` value object + library normalization + mutation decider.
  - Rewired `LibraryService` create/update paths to command + decider orchestration.
  - Added settings update command mapping (`UpdateSettingsRequest -> UpdateUserSettingsCommand`) and decider pass-through.
  - Introduced `LibraryMutationRepository` interface for mutation port testability.
  - Added `LibraryServiceTest` orchestration tests (including ownership rejection).
  - Added focused tests: `LibraryCommandAndDeciderTest`, `SettingsCommandTest`, `LibraryApiTest`.

- **Phase 3 (User: Identity + Auth policy + Activity): Complete**
  - Added typed identity commands: `RegisterUserCommand`, `SetupUserCommand`, `LoginUserCommand`, `UpdateCurrentUserCommand`.
  - Added identity request→command mappers in `user/identity/domain/api.kt`.
  - Added `UserMutationDecider` for registration/update decision logic.
  - Rewired `UserService` register/setup/login/update to consume typed commands and decider output.
  - Added activity typed commands: `SaveReadingProgressCommand`, `SaveReadStatusCommand`.
  - Rewired `ActivityService.saveProgress/saveReadStatus` and book routes to consume typed commands.
  - Consolidated ad hoc `jwtAuth` in import/staging routes to `sharedCatalogRead`/`sharedCatalogMutation` wrappers.
  - Remaining `jwtAuth` usage is intentional for user-owned resources (identity, tokens, settings, library, activity) where ownership is scoped by JWT userId.
  - Added focused domain tests: `UserCommandAndDeciderTest`, `ActivityCommandTest`.

- **Phase 4 (Processing + Integrations): Complete**
  - Added import/staging typed commands: `ScanDirectoryCommand`, `UpdateStagedBookCommand`, `MergeStagedBookCommand`, `PromoteStagedBookCommand`, `StagedBatchCommand`.
  - Added request→command mapping in `processing/import/domain/commands.kt`.
  - Rewired all import/staging service boundaries to consume typed commands.
  - Added KOReader boundary command models and mappers: `KoreaderCreateUserCommand`, `KoreaderProgressReadCommand`, `KoreaderProgressUpdateCommand`.
  - All import/staging route endpoints map DTOs into commands before calling services; no remaining raw parameter entry points.
  - Extracted `StagedBookDecider` with `applyUpdate` (pure field patching) and `planPromotion` (metadata/edition assembly + identifier validation).
  - Rewired `StagedBookService.update()` and `promoteToBook()` to delegate domain decisions to decider; service retains only I/O orchestration.
  - Moved `tryIdentifier` helper from service to decider (already pure).
  - Added `StagedBookDeciderTest` with 9 unit tests covering both decider functions.

- **Phase 5 (Cross-cutting standardization + docs): Complete**
  - Updated `AGENTS.md`: added Repository layer to Architectural Layers, documented standard domain layout, added golden rule #20 for mutation repositories.
  - Updated `docs/Test-Ownership-Matrix.md`: added per-domain test mapping sections for all migrated domains.
  - Updated this migration plan with final phase statuses and key decisions.

## Implementation Changes (Decision-Complete)

### Phase 1 — Author + Series (first migration slice)
- Add/complete command mappers in `catalog/author/domain/api.kt` and `catalog/series/domain/api.kt` so all input invariants are enforced before service logic.
- Introduce domain normalization helpers (same role as Book `normalization.kt`) for canonical name handling and duplicate detection.
- Move mutation decision rules from `author/service.kt` and `series/service.kt` into explicit domain decision components (decider/policy module per domain).
- Keep services as orchestrators only: load snapshot -> decide -> persist mutation set -> emit/react to events.
- Split repository responsibilities (read snapshot vs mutation write ports) where currently mixed, while keeping persistence SQL logic minimal and non-business.
- Public/internal interface target:
  - Services expose narrow capability interfaces (`Provider`, `Modifier`) only.
  - New internal types: `*Command`, `*Mutation`, `*DomainError`, optional `*Event`.
  - Route handlers only map DTOs and call service; no policy branching.

### Phase 2 — Organization domains (Library, Settings)
- Library: formalize domain commands for create/update membership/state changes; push all invariants into command/value types.
- Settings: enforce typed settings VO boundaries (no raw primitive drift in service).
- Move authorization and ownership checks to explicit policy helpers consumed by services; remove ad hoc conditional checks from route/persistence layers.
- Ensure summaries vs aggregates follow existing rule: list endpoints return summary types; detail endpoints return aggregate/root graph.

### Phase 3 — User domain (Identity + Auth policy + Activity)
- Identity: tighten value classes and parsing boundaries for usernames/email/token identifiers; `fromRaw` only on trusted rehydration path.
- Auth: consolidate all access checks onto shared named policy helpers and wrappers; remove duplicated credential parsing/ownership checks.
- Activity: confirm progress update invariants are command-boundary validated (kind-specific constraints, percentage/time consistency) before persistence.

### Phase 4 — Processing + Integrations
- Import/Staging: define command ADTs for merge/promotion transitions; prevent illegal state transitions through typed transition commands.
- External payload mapping (Hardcover/KOReader/import scans): map untrusted data to validated domain commands at adapter boundary only.
- Keep integration services thin: transport/auth/parsing orchestration only, no domain mutation rules inline.

### Phase 5 — Cross-cutting standardization + docs
- Standardize per-domain layout:
  - `domain/{api,error,models,primitives[,commands,decider,normalization]}.kt`
  - `service.kt` (orchestrator only)
  - `persistence.kt` (typed I/O only)
- Update docs after each phase boundary: `AGENTS.md`, `README.md`, `CONTRIBUTING.md`, `docs/Test-Ownership-Matrix.md`.
- Keep telemetry low-cardinality and avoid domain IDs/user identifiers in labels/attributes.

## Parallel Work Plan (for multiple agents)
Use one phase at a time, with disjoint write ownership:

1. **Agent A (Author)**: `catalog/author/**` + author tests  
2. **Agent B (Series)**: `catalog/series/**` + series tests  
3. **Agent C (Shared Policy/Test matrix)**: auth policy helpers + `docs/Test-Ownership-Matrix.md` updates after A/B land  
4. **Agent D (Library/Settings)**: `organization/**` once A/B merged  
5. **Agent E (User/Auth/Activity)**: `user/**` after D starts  
6. **Agent F (Import/Staging/Integration)**: `processing/import/**`, `catalog/opds/**`, `integration/koreader/**` last

Merge rule: A+B must land before D/E/F to minimize rebase churn on shared domain patterns.

## Test Plan and Acceptance Criteria

### Per phase required checks
- `./gradlew spotlessApply classes`
- Targeted tests for touched domain(s), then a broader regression pass before phase completion.

### Required test structure
- **Command tests**: each invariant has isolated test (“one policy, one test”).
- **Decider tests**: mutation/event decisions by scenario (happy + reject paths).
- **Service tests**: orchestration only (load/decide/persist/event wiring).
- **API tests**: boundary contract + HTTP error mapping only.
- **Persistence tests**: typed rehydration and relation/scoping behavior, no business policy.

### Phase completion gate
A phase is done only when:
1. Business invariants are absent from routes/persistence and present in command/decider layers.
2. Domain errors map consistently to HTTP responses.
3. Test ownership matrix reflects new policy placement.
4. No repo-tracked formatting drift; build/tests green.

## Assumptions and Defaults
- No major module split (single-module Kotlin project retained).
- DB schema changes are avoided unless required to enforce invariant correctness; if needed, include explicit migration + persistence tests in that same phase.
- Existing endpoint contracts remain backward compatible unless a domain correctness issue forces a breaking change (then document and gate in API tests).
- Book domain remains current reference implementation for style and pattern reuse.

## Plan Maintenance
- Keep this document current while work is in progress.
- At the end of each completed phase, update:
  - phase status (Not Started / In Progress / Completed),
  - key decisions made,
  - deviations from this plan,
  - links to commits/tests proving completion.
- If a phase is re-scoped, update this file in the same change set as the implementation.
