# E2E Test Suite with Playwright — Real Backend

## Context

Iteration has broken features because there's no e2e safety net. Existing 7 test files all use `page.route()` mocks — they can't catch backend contract drift, API shape changes, or integration bugs. This plan replaces the mocked approach with real-backend e2e tests using docker-compose, covering auth, library, import, podcasts, KOReader sync/stats, and OPDS. Also adds a CI job to run them on every PR.

## Goal

Build a Playwright e2e test suite that runs against the real backend (database + valkey + backend container), with CI integration via GitHub Actions.

## Approach

Use a dedicated `e2e.docker-compose.yaml` that spins up database, valkey, and backend with deterministic test config. Playwright tests hit the real API through the Vite dev server proxy. Each test suite resets state via API calls (setup user, seed data, teardown). Tests grouped by feature domain with shared fixtures for auth.

**Why real backend over mocks:** Contract drift is the pain point. Mocked tests gave false confidence — real backend catches serialization changes, validation rule updates, and DB migration issues.

## File Changes

### Create

- **`ui/e2e.docker-compose.yaml`** — Minimal compose: database, valkey, backend only. Fixed ports (5433, 6380, 8081) to avoid conflicts with dev. Deterministic env vars. No volumes for data persistence (ephemeral).

- **`ui/tests/fixtures.ts`** — Shared Playwright fixtures:
  - `authenticatedPage` — Page with auth token pre-set in localStorage
  - `adminPage` — Page authenticated as admin user
  - API helpers: `seedBook()`, `seedPodcast()`, `createApiToken()`, `uploadKoreaderStats()`
  - `opdsRequest()` helper wrapping fetch with Basic Auth for OPDS XML endpoints
  - Base URL configuration pointing to Vite dev server (which proxies to backend)

- **`ui/tests/global-setup.ts`** — Waits for backend health, creates initial admin user, stores auth state

- **`ui/tests/global-teardown.ts`** — Cleanup (optional, compose down handled externally)

- **`ui/tests/koreader.spec.ts`** — KOReader sync settings: token CRUD, connection URLs, setup instructions

- **`ui/tests/stats.spec.ts`** — Stats pages: daily overview, books list, edition detail, unmatched books

- **`ui/tests/opds.spec.ts`** — OPDS feed via API testing: auth, navigation/acquisition feeds, search, pagination

### Modify

- **`ui/playwright.config.ts`** — globalSetup, timeouts, trace/screenshot on failure, mobile viewport project
- **`ui/tests/auth.spec.ts`** — Rewrite: real login, setup, session, logout
- **`ui/tests/library.spec.ts`** — Rewrite: real book listing and details
- **`ui/tests/import.spec.ts`** — Rewrite: real scan → stage → promote
- **`ui/tests/catalog.spec.ts`** — Rewrite: real search and catalog navigation
- **`ui/tests/podcasts.spec.ts`** — Rewrite: real subscription and management
- **`ui/tests/mobile-shell.spec.ts`** — Rewrite: real mobile navigation
- **`ui/tests/metadata.spec.ts`** — Rewrite: real metadata editing
- **`ui/package.json`** — Add e2e lifecycle scripts
- **`.github/workflows/ci.yml`** — Add e2e-test job

## Implementation Steps

### Task 1: E2E Infrastructure
1. Create `ui/e2e.docker-compose.yaml` — database:5433, valkey:6380, backend:8081. Fixed env: `JWT_SECRET=e2e-test-secret`. Health check on `/readiness`.
2. Update `ui/playwright.config.ts` — globalSetup, timeouts (30s/10s), trace on failure, `BACKEND_URL=http://localhost:8081`
3. Create `ui/tests/fixtures.ts` — authenticatedPage fixture, API helpers, auth state reader
4. Create `ui/tests/global-setup.ts` — poll readiness, POST `/api/setup`, store token
5. Add npm scripts: `test:e2e:up`, `test:e2e:down`, `test:e2e`, `test:e2e:full`

### Task 2: Auth Tests
- Setup page shown on fresh DB
- Complete setup flow → admin → redirect
- Login valid/invalid credentials
- Unauthenticated redirect to login
- Logout clears session

### Task 3: Library & Book Tests
- Library page shows imported books
- Book details page renders correctly
- Authors/series catalog pages
- Search returns results

### Task 4: Import Tests
- Mount test fixtures dir in e2e compose
- Scan finds files → staged books appear → promote to library

### Task 5: Podcast Tests
- Subscribe, list, detail, settings edit

### Task 6: KOReader Sync Tests
- Settings page shows sync/WebDAV URLs
- Setup instructions visible
- Create API token → displayed once
- Token in list with date
- Delete token → removed
- Connection URLs match expected format

### Task 7: Stats Tests
- Seed via WebDAV PUT of `statistics.sqlite` + matching book import
- Overview: metrics cards, daily chart, calendar heatmap
- Books page: table with title/pages/sessions/duration
- Click row → edition detail with sessions chart
- Unmatched books section

### Task 8: OPDS Tests
- Uses Playwright `request` context (API testing), no browser UI
- Root catalog with Basic Auth → 200 Atom XML
- Without auth → 401
- Books acquisition feed with entries
- Authors/series navigation feeds
- Search results
- Pagination links
- Download links with correct MIME types

### Task 9: Mobile & Metadata Tests
- Mobile nav drawer, bottom nav, search sheet (390x844)
- Edit book metadata, verify persistence on reload

### Task 10: CI Integration
- `e2e-test` job in GitHub Actions
- Build backend image, spin up compose, run Playwright, tear down
- Upload report artifact on failure
- Gate `publish-images` on e2e passing

## Acceptance Criteria

- `bun run test:e2e:full` starts infra, runs all tests, tears down — zero manual steps
- Auth tests verify real login/logout/setup
- Library tests verify books after import, details render
- Import tests verify scan → stage → promote flow
- Podcast tests verify subscribe → list → detail → settings
- Mobile tests verify responsive navigation at 390x844
- KOReader tests verify token CRUD and connection URL display
- Stats tests verify overview metrics, charts render, books table, edition detail
- OPDS tests verify feed auth, navigation/acquisition feeds, search, pagination via direct API
- CI job runs on every PR, blocks merge on failure
- All tests complete in under 5 minutes in CI
- Playwright report uploaded as artifact on failure

## Verification Steps

1. Locally: `cd ui && bun run test:e2e:up` → `bun run test:e2e` → all pass → `bun run test:e2e:down`
2. CI: push branch, verify e2e-test job passes
3. Break API intentionally → tests catch it
4. `npx playwright show-report` for debugging

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Backend build time slows CI | Use GHCR cached images from main |
| Flaky timing | Playwright auto-waiting + explicit `waitForResponse()` |
| Data pollution between suites | Unique identifiers per suite; DB reset in global setup |
| Port conflicts with dev stack | Non-standard ports: 5433, 6380, 8081 |
| Import needs real files | Commit minimal test epub fixture |
| HARDCOVER_API_KEY required | Dummy value in e2e compose |
| Stats need KOReader sqlite data | Include test `statistics.sqlite` fixture or generate via WebDAV PUT |
| OPDS XML parsing in tests | Lightweight string assertions on known elements, not full schema validation |