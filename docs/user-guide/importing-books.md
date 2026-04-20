# Importing Books

Shelf uses a **directory scanning** approach to importing books.

## Supported Formats

- **EPUB** — Electronic publications
- **MP3 / M4B** — Audiobook files

## Import Workflow

1. **Upload files** — Place your EPUB or audiobook files into your configured storage directory.
2. **Scan** — Shelf recursively scans the directory for supported media files.
3. **Metadata extraction** — Shelf reads metadata (title, author, cover art) directly from the files.

## Configuring Scan Roots

Set the `IMPORT_SCAN_ROOTS` environment variable to a comma-separated list of directories that Shelf is allowed to scan. This is an administrative operation limited to configured paths.

```bash
IMPORT_SCAN_ROOTS=/media/books,/media/audiobooks
```

See the [Configuration Reference](../getting-started/configuration.md) for all storage-related settings.

## Storage Layout

Shelf manages its own internal storage layout. Media files are served through API endpoints rather than direct filesystem access, keeping your library private by default.

The `STORAGE_PATH` variable controls where Shelf stores processed files (covers, converted assets). This is separate from your import scan roots.
