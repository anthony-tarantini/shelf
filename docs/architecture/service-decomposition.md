<!-- Derived from docs/Book-Service-Capability-Split.md — keep in sync -->

# Service Decomposition

This page documents the internal structure of the Book service after decomposition into capability-focused components.

## Intent

The previous `BookService` implementation had many unrelated responsibilities in one large class.
The new shape keeps the same public `BookService` umbrella interface but composes it from focused internal components:

- Read/query assembly
- Write/mutation operations
- Asset/edition resolution
- Metadata update orchestration

## Component View

```mermaid
flowchart TD
  Facade[BookService facade]

  Read[BookReadService]
  Write[BookWriteService]
  Asset[BookAssetService]
  Meta[BookMetadataUpdateService]
  Decider[BookMetadataDecider]

  Facade --> Read
  Facade --> Write
  Facade --> Asset
  Facade --> Meta

  Read --> ReadRepo[BookReadRepository]
  Read --> AP[BookAuthorProvider]
  Read --> SP[BookSeriesProvider]
  Read --> MP[MetadataProvider]

  ReadRepo --> BQ[BookQueries]
  ReadRepo --> AQ[AuthorQueries]
  ReadRepo --> SQ[SeriesQueries]

  Write --> BQ
  Write --> AQ
  Write --> SQ

  Asset --> Read
  Asset --> MP
  Asset --> SS[StorageService]

  Meta --> WriteRepo[BookRepository]
  Meta --> Decider
  Meta --> SS
  Meta --> Settings[SettingsService]
  Meta --> Events[BookDomainEventHandler]

  WriteRepo --> MW[BookMetadataWriter]
  WriteRepo --> AR[BookAuthorResolver]
  WriteRepo --> SR[BookSeriesResolver]

  WriteRepo --> BQ
  WriteRepo --> AQ
  WriteRepo --> SQ
  WriteRepo --> MQ[MetadataQueries]
```

## Capability Interfaces

The facade still exposes `BookService`, which combines:

- `BookProvider`
- `BookAggregateProvider`
- `BookPagingProvider`
- `AuthorBookProvider`
- `SeriesBookProvider`
- `LibraryBookProvider`
- `BookModifier`
- `BookAssetProvider`
- `BookMetadataModifier`

Consumers can depend on narrower interfaces instead of full `BookService`.

## Dependency Narrowing in Consumers

```mermaid
flowchart LR
  BR[bookRoutes] --> BP[BookProvider]
  BR --> BAG[BookAggregateProvider]
  BR --> BPG[BookPagingProvider]
  BR --> BM[BookModifier]
  BR --> BAS[BookAssetProvider]
  BR --> BMM[BookMetadataModifier]

  AR[authorRoutes] --> BPG
  LR[libraryRoutes] --> LBP[LibraryBookProvider]
  OPDS[opdsService] --> BPG
  OPDS --> BAG
  Worker[SyncMetadataWorker] --> BAG
```

## Why This Matters

- **Reduces coupling:** each caller depends only on capabilities it actually uses.
- **Improves maintainability:** read/write/asset/metadata concerns evolve independently.
- **Preserves compatibility:** `BookService` remains available as the composed umbrella type.
- **Repository boundaries:** both query and mutation paths sit behind repository interfaces (`BookReadRepository` and `BookRepository`).

## Current Limits

- The metadata updater uses a domain decider for mutation and event decisions; further DDD work can push more invariants into richer aggregate behaviors.
- The metadata repository composes focused internal write ports (metadata writer + author/series resolvers), but these remain in one package boundary.
- The split is package-level (single module), not multi-module.
