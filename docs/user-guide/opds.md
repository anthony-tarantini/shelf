# OPDS Feed

Shelf provides an **OPDS (Open Publication Distribution System)** feed that is compatible with most modern e-reader apps.

## Connecting Your App

Point your app to your Shelf URL followed by `/opds`:

```
https://shelf.example.com/opds
```

You will be prompted for your Shelf credentials (OPDS uses HTTP Basic authentication).

## Compatible Apps

| App | Platform | Notes |
|-----|----------|-------|
| **KOReader** | Linux/Android/Kindle | Full integration including [progress sync](koreader.md) |
| **KyBook 3** | iOS | OPDS catalog browsing and download |
| **MapleRead** | iOS | OPDS catalog browsing and download |
| **Moon+ Reader** | Android | OPDS catalog browsing and download |

## What the Feed Provides

The OPDS feed exposes your library catalog, allowing connected apps to:

- Browse your book collection
- Search by title or author
- Download books directly to the reading app
