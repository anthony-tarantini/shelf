# KOReader Sync

Shelf provides full reading progress synchronization with [KOReader](https://koreader.rocks/), the popular open-source e-reader application.

## Setting Up Sync

1. Open KOReader on your device.
2. Go to **Tools > Cloud storage > KOReader sync server**.
3. Configure the server URL:

    ```
    https://shelf.example.com
    ```

4. Enter your Shelf credentials (username and password).

KOReader uses its own authentication flow with dedicated sync tokens, handled automatically through Shelf's auth system.

## What Syncs

- **Reading progress** — Page position and percentage syncs between KOReader and Shelf's web reader.
- **Document matching** — Sync is matched by edition file hash, so the same file on multiple devices stays in sync.

## Public URL Configuration

If your Shelf instance is behind a reverse proxy or uses a different public URL for KOReader access, set:

```bash
PUBLIC_KOREADER_BASE_URL=https://shelf.example.com
```

Leave this empty to use the default `PUBLIC_ROOT_URL`.

## Smoke Testing

For development, use the local smoke test script to verify sync without physical hardware:

```bash
./scripts/koreader-sync-smoke.sh
```

Add `--document-hash <hash>` to test progress sync for a specific edition:

```bash
./scripts/koreader-sync-smoke.sh --document-hash <edition-file-hash>
```

Without `--document-hash`, the script still verifies token creation and KOReader auth headers end to end.
