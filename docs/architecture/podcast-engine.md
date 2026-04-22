# Podcast & Ad-Free Ingestion Engine Architecture

This document outlines the technical design and edge-case handling for the "Ad-Free Ingestion Engine" within the Shelf ecosystem. It maps the provided user stories onto the existing Kotlin/SQLDelight/SvelteKit architecture. 

**Architectural Invariant:** The implementation will strictly adhere to the established Domain-Driven Design (DDD) and Functional Programming (FP) patterns used throughout the `io.tarantini.shelf` codebase. This includes:
- **Strict Domain Separation:** Enforcing boundaries between `app` (routes/env), `catalog` (core entities), `integration` (external fetching), and `processing` (sanitization/files).
- **FP Error Handling:** Utilizing Arrow's `Raise` context and `either` blocks for all fallible operations, completely avoiding exceptions for expected domain errors.
- **Domain Primitives:** Modeling all new IDs, external tokens, and configurations using strongly typed Kotlin `value classes`.
- **Pure Persistence:** Keeping SQLDelight queries free of business logic and authorization checks.

## 1. Automated Retrieval (The Fetcher)
**Goal:** Automatically pull premium content from external walled gardens (e.g., Audible, Wondery) and convert to DRM-free lossless/high-bitrate formats.

**Implementation Details:**
- **Job Scheduling:** Introduce a lightweight background job runner (e.g., Kotlin Coroutines `ticker` or a DB-backed queue) within the `processing` or `integration` domain.
- **External CLI Integration:** Create an adapter in `integration/audible` to wrap the execution of tools like `Libation` or `ffmpeg`/`audible-cli`.
- **Format Conversion:** Download `.aax`/`.aaxc` and execute a local `ffmpeg` process to decrypt (using acquired auth/activation bytes) and transcode to `.m4b`.

**Technical Edge Cases:**
- **State & Auth Refresh:** Walled gardens frequently expire session cookies or tokens. The ingestion engine must support headless re-authentication flows or alert the user via the UI to re-auth.
- **Resilient Downloads:** Handle rate limiting, HTTP 429s, and network drops by utilizing byte-range resuming for massive audiobook/podcast files.
- **Multi-part Assembly:** Some platforms split large files. The engine must identify multi-part downloads and merge them into a single `m4b` *before* sanitization.

## 2. Content Sanitization ("The Commercial Crusher")
**Goal:** Identify and non-destructively remove dynamic or baked-in ads using heuristics and AI.

**Implementation Details:**
- **Feed Configuration:** A new `podcast_subscriptions` (or similar) table linked to the `Series` entity will hold configuration settings for the feed, including an `auto_sanitize` boolean toggle. This allows users to bypass the pipeline for ad-free premium feeds or podcasts they prefer to leave untouched.
- **Processing Pipeline (Async):** Sanitization is compute-heavy. Files marked for sanitization must enter a `PROCESSING` state in the staging area (`ui/src/lib/components/StagedBookDisplay.svelte` adaptation).
- **Phase 1 (Heuristics):** Use FFmpeg's `silencedetect` or a bundled Python script (using `pydub`) to find silence thresholds commonly bordering ads.
- **Phase 2 (AI/Transcription):** 
  - Extract a low-bitrate audio track.
  - Pipe to a local Whisper model (e.g., `whisper.cpp` via JNI/CLI or an HTTP call to a local Ollama/MCP service).
  - Feed the timestamped VTT/SRT to a local LLM to flag "sponsor-read" segments based on linguistic markers ("use promo code", "support the show").
- **Phase 3 (Editing):** Use FFmpeg's `concat` demuxer to piece the audio back together, excluding the flagged timestamps.

**Technical Edge Cases:**
- **"Seamless" Transitions (Wondery-style):** Baked-in ads without silence are difficult for Phase 1. The pipeline must heavily weigh Phase 2 LLM context over silence detection for specific publishers.
- **Chapter Timestamp Drift:** Removing a 2-minute ad shifts all subsequent chapter markers backwards by 2 minutes. The engine MUST parse chapters from the original file, mathematically adjust the start/end times based on the removed segments, and write the new chapters to the SQLDelight `chapters` table and re-embed them in the final `m4b`.
- **Non-Destructive Safety:** Keep the original unsanitized file until the user confirms the "Commercial Crusher" didn't clip narrative audio (maybe provide a toggle in the UI to swap between raw/sanitized).

## 3. Metadata & Library Integrity
**Goal:** Tag ingested media with correct season/episode data and persist in Shelf's catalog so it feels like official media.

**Implementation Details:**
- **Data Model Mapping:** 
  - A Podcast feed maps to the existing `Series` entity (`src/main/sqldelight/io/tarantini/shelf/catalog/series/persistence/series.sq`).
  - An Episode maps to a `Book` entity with a new or existing format (`BookFormat.AUDIOBOOK`).
  - `series_books` link handles the Season/Episode numbering (using the `index` float field, e.g., Season 2 Ep 4 = 2.04).
- **Metadata Fetching:** Create providers in `integration/applepodcasts` or `integration/listennotes` to fetch canonical metadata, cover art, and descriptions.

**Technical Edge Cases:**
- **Chapter Extraction:** Extract existing metadata from `.aax`/`.m4b` via FFmpeg probe or `AtomicParsley` to ensure native narration chapters remain intact.
- **Fallback Matching:** If an external API fails, fallback to parsing the ID3 tags or Libation's generated JSON.

## 4. Personal Distribution (The "Pocket Casts" Bridge)
**Goal:** Serve the sanitized files via a private RSS feed compatible with modern podcast players.

**Implementation Details:**
- **RSS Generation:** Create an HTTP route (`routes.kt`) under `catalog/rss` that queries a `Series` and its associated `Books` to build an XML feed.
- **Podcast 2.0 Namespace:** Ensure the XML includes `<podcast:chapter>` links (served from the Shelf DB if not embedded), `<podcast:transcript>` (since we already generated it in Phase 2), and `<itunes:image>`.
- **Authentication:** Standard JWT auth won't work for Pocket Casts. Use URL-based Secret Tokens (e.g., `/rss/podcasts/123?token=abc-123`). We can leverage the existing `tokens.sq` schema.

**Technical Edge Cases:**
- **Byte-Range Streaming (CRITICAL):** Podcast players do not download the whole file before playing. The HTTP server (Ktor) must properly handle `Range` requests and respond with `206 Partial Content`.
- **Caching & Proxying:** To prevent DB hammering on feed refreshes, the XML response should be heavily cached (ETag or Cache-Control), invalidated only when a new episode is ingested.

## 5. Security & Credential Management
**Goal:** Safely store reversible external credentials (e.g., Audible activation bytes, premium feed HTTP Basic auth passwords, or walled-garden session cookies) required by the Fetcher.

**Implementation Details:**
- **Reversible Encryption:** Unlike user passwords or API tokens which Shelf currently *hashes* (via PBKDF2 or SHA-256), external credentials must be recoverable by the Fetcher. They cannot be hashed.
- **Encryption at Rest:** 
  - Introduce a new environment variable `ENCRYPTION_SECRET` (similar to the existing `JWT_SECRET`).
  - Use symmetric encryption (e.g., AES-256-GCM) within the `integration` domain to encrypt credentials before writing them to the `podcast_subscriptions` or a dedicated `integration_credentials` SQLDelight table.
  - The Fetcher will decrypt these on-the-fly in memory just before passing them to the external CLI tool (like Libation/FFmpeg).
- **Zero-Logging Policy:** Ensure the `integration` service adapters sanitize command-line arguments and standard output when executing external tools so that keys/IVs are never written to the local logback logs.
