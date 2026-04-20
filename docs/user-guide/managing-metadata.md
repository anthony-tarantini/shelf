# Managing Metadata

Once books are imported, you can view and edit their metadata through the web UI.

## Editing Metadata

Select any book to view its details, then edit fields like title, author, series, publisher, genre, and more. Changes are validated at the boundary and persisted as atomic domain operations.

## Metadata Providers

Shelf can fetch missing details from external services to fill in gaps from your imported files.

- **Hardcover** — Fetch book metadata from the [Hardcover](https://hardcover.app) API. Requires an API key configured via `HARDCOVER_API_KEY`.

Additional providers (OpenLibrary, Google Books) are on the roadmap.

## Series and Collections

Group books into series and specify their reading order. Series identity is scoped — the same series title can exist under different authors without collision.

## Tags

Add custom tags to organize your library by genre, mood, reading status, or any other criteria.
