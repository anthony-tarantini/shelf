<!-- Derived from docs/Book-Metadata-Update-Flow.md — keep in sync -->

# Metadata Update Flow

This document explains the metadata update path and how Shelf maps API requests into validated domain commands.

## Goal

Move validation and parsing of complex update payloads out of service internals and into an explicit boundary mapping step:

- Route receives API DTO
- Route maps DTO to validated domain command
- Service orchestrates persistence and side effects using typed domain data

## High-Level Before vs After

### Before

```mermaid
flowchart LR
  A[HTTP PATCH /books/{id}/metadata] --> B[Route receives UpdateBookMetadataRequest DTO]
  B --> C[Service updateBookMetadata(request DTO)]
  C --> D[Service parses/validates raw strings inline]
  D --> E[DB transaction + storage + relinks + job enqueue]
```

### After

```mermaid
flowchart LR
  A[HTTP PATCH /books/{id}/metadata] --> B[Route receives UpdateBookMetadataRequest DTO]
  B --> C[toCommand() boundary mapper]
  C --> D[UpdateBookMetadataCommand + value objects]
  D --> E[Service updateBookMetadata(command)]
  E --> F[DB transaction + storage + relinks + job enqueue]
```

## Runtime Sequence

```mermaid
sequenceDiagram
  participant Client
  participant Route as bookRoutes.kt
  participant Mapper as UpdateBookMetadataRequest.toCommand
  participant Service as BookMetadataUpdateService
  participant Decider as BookMetadataDecider
  participant DB as Book/Author/Series/Metadata Queries
  participant Storage as StorageService
  participant Settings as SettingsService
  participant Events as BookDomainEventHandler

  Client->>Route: PATCH /api/books/{id}/metadata
  Route->>Mapper: req.toCommand()
  Mapper-->>Route: UpdateBookMetadataCommand
  Route->>Service: updateBookMetadata(userId, bookId, command)

  alt coverUrl provided
    Service->>Storage: fetchRemoteImage + save(books/{id}/cover.ext)
  end

  Service->>DB: loadMetadataSnapshot(bookId)
  Service->>Settings: getUserSettings(userId)
  Service->>Decider: decide(snapshot, command, resolvedCoverPath, syncSetting)
  Decider-->>Service: mutation + domain events
  Service->>DB: applyMetadataMutation(mutation)

  alt decision contains events
    Service->>Events: handle(event)
  end

  Service-->>Route: success or AppError
  Route-->>Client: 200 OK or mapped error
```

## Validation Boundary

```mermaid
flowchart TD
  A[UpdateBookMetadataRequest] --> B[toCommand()]
  B --> C[Domain value objects]
  C --> D[BookTitle / AuthorName / SeriesName / PublisherName]
  C --> E[PublishYear / Genre / Mood / CoverSourceUrl]
  C --> F[EditionIdentifiersCommand]
  D --> G[BookValidationError]
  E --> G
  F --> G
```

## Domain Types Involved

- `UpdateBookMetadataCommand`
- `AuthorRelinkIntent` (`UseExisting` / `UpsertByName`)
- `SeriesRelinkIntent.AuthorScopedUpsertByName`
- `EditionIdentifiersCommand`
- `BookMetadataDecider` + `BookMetadataMutation`
- `BookTitle`, `AuthorName`, `SeriesName`, `PublisherName`
- `PublishYear`, `Genre`, `Mood`, `CoverSourceUrl`

## Notes

- API contract for `PATCH /api/books/{id}/metadata` is unchanged.
- The route is now responsible for converting untrusted request data into validated domain command objects.
- Service logic remains behavior-compatible, but now consumes typed domain inputs.
- Series matching during metadata relink is author-scoped; identical series titles may exist across different author scopes.
