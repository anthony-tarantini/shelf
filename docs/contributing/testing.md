<!-- Derived from docs/Test-Ownership-Matrix.md — keep in sync -->

# Testing

Shelf uses [Kotest](https://kotest.io/) for its test suite. Run all tests with:

```bash
./gradlew test
```

After Kotlin changes, always run:

```bash
./gradlew spotlessApply classes
```

## Test Ownership Matrix

Each kind of rule is tested in **one primary layer** to avoid duplicate assertions.

| Concern | Primary Test Layer | Example |
|---|---|---|
| Request field validation, normalization, parsing | Domain command mapping tests | `UpdateBookMetadataRequest.toCommand()` rejects invalid year/URL and maps author/series intents |
| Mutation decision rules and defaults | Domain decider/aggregate tests | `DefaultBookMetadataDecider.decide(...)` replacement and event rules |
| Service orchestration sequence | Service unit tests with mocked ports | load -> decide -> persist -> domain event handling |
| Repository SQL persistence behavior | Persistence tests | metadata identifiers and relink writes in transaction |
| Endpoint auth/serialization/http codes | API integration tests | `/api/books/{id}/metadata` status + response envelope |

### Anti-Patterns to Avoid

- Re-testing command validation rules in decider tests.
- Re-testing decider decisions in service orchestration tests.
- Re-testing detailed domain invariants in API integration tests.

## Per-Domain Test Mapping

### Book

- `BookMetadataCommandTest`: command input validation + mapping policy.
- `BookMetadataDeciderTest`: mutation decision logic only.
- `BookMetadataUpdateServiceTest`: orchestration (load -> settings -> decide -> persist -> event).
- `BookAuthorResolverTest`: author resolution policy only.
- `BookSeriesResolverTest`: series resolution policy only.
- `BookMetadataRepositoryTest`: persistence rehydration + relation writes.
- `BookPersistenceTest`: base persistence operations.
- `BookApiTest`: endpoint contract/auth behavior + metadata mutation wiring.

### Author

- `AuthorCommandTest`: `AuthorName` value object validation (blank rejection, trimming).
- `AuthorServiceTest`: orchestration (decide -> persist for create; load -> decide -> persist for update; canonicalization preservation).
- `AuthorPersistenceTest`: persistence operations.
- `CatalogApiTest`: author endpoint contract coverage.

### Series

- `SeriesCommandAndDeciderTest`: `SeriesTitle` validation + `SeriesMutationDecider` decision logic (create, update, null-title preservation, canonicalization).
- `SeriesServiceTest`: orchestration (decide -> persist for create; load -> decide -> persist for update; null-title preservation).
- `SeriesPersistenceTest`: persistence operations.
- `CatalogApiTest`: series endpoint contract coverage.

### Library

- `LibraryCommandAndDeciderTest`: `LibraryTitle` validation + `LibraryMutationDecider` decision logic.
- `LibraryServiceTest`: orchestration (decide -> persist for create; ownership -> load -> decide -> persist for update; non-owner rejection).
- `LibraryDomainTest` / `LibraryRootTest` / `PrimitivesTest`: domain model invariants.
- `LibraryPersistenceTest`: persistence operations.
- `LibraryApiTest`: endpoint contract + update with null title.

### User Identity

- `UserCommandAndDeciderTest`: identity command validation + `UserMutationDecider` decision logic.
- `UserPersistenceTest`: persistence operations.
- `UserApiTest`: endpoint contract/auth behavior.
- `TokenApiTest`: token management endpoint contract.

### Activity

- `ActivityCommandTest`: `SaveReadingProgressCommand` / `SaveReadStatusCommand` validation + mapping.

### Settings

- `SettingsCommandTest`: `UpdateUserSettingsCommand` mapping validation.
- `SettingsPersistenceTest`: persistence operations.

### Metadata

- `MetadataDeciderTest`: logic for planning metadata aggregates from processed files.
- `MetadataPersistenceTest`: persistence operations for metadata roots, editions, and chapters.

### Import / Staging

- `StagedBookDeciderTest`: pure update application + promotion assembly decision logic.
- `ImportServiceProgressTest`: integration-level scan progress orchestration.
- `ImportApiTest`: import endpoint contract.
- `MergeIntegrationTest`: staged book merge flow.
- `PromotionIntegrationTest`: staged book promotion flow.
- `StagedBatchTest`: batch operation flow.
