# E2E Test Failure Fixes

## Context

12 of 28 e2e tests failing on `tests/e2e` branch. Backend, db, valkey, fixtures containers all healthy. Failures are test↔UI drift, not backend regressions. No `data-testid` attributes anywhere in `src/` — every selector relies on text/role and breaks when copy or markup shifts.

## Failure Categories

### A. Strict-mode (selector resolves to >1 node)
- `auth.spec.ts:24` — `.text-destructive, .bg-destructive` matches StatusBanner wrapper + inner eyebrow `<p>`
- `import.spec.ts:15` — `/scan|start scan/i` matches "Scan now" (disabled) + "Scan Directory"
- `koreader.spec.ts:8` — `/sync server url/i` matches `<span>` label + instructions `<li>`

### B. OPDS XML copy mismatch
- `opds.spec.ts:35` — assert `Latest Books`, feed emits `All Books`
- `opds.spec.ts:41` — assert `Search Results`, feed emits `Search results for: …`

### C. Selectors don't match real UI
- `catalog.spec.ts:9` — no element matches `/search/i` placeholder or `searchbox` role
- `stats.spec.ts:7` — no heading matches `/statistics|overview/i`
- `mobile-shell.spec.ts:20` — no `[role=dialog]` / `.drawer` after menu click
- `library.spec.ts:13`, `metadata.spec.ts:11` — no `article, .book-card, .book-item`
- `podcasts.spec.ts:8` — copy "no podcast subscriptions yet" + `article, .podcast-card` absent

### D. Auth flow blocker (root cause for several Cs)
- `auth.spec.ts:15` — after valid login, URL stays `/login`. Login page does `api.setToken(...)` then `goto('/')`. Need to confirm whether (a) token not persisted, (b) `/` redirect bounces back because layout guard fires before `auth.setUser`, or (c) login POST itself errors. If auth broken in browser, every authenticated test that touches UI sees logged-out state → empty lists → Category C cascade.

## Approach

Two-layer fix:
1. **Stabilize selectors** — add `data-testid` on key surfaces (book card, podcast card, search input, stats heading, mobile drawer, sync URL value, scan-now button). Cheaper + durable than chasing copy.
2. **Repair test logic** — narrow strict-mode selectors, correct OPDS assertions, gate selector-cascade tests behind auth fix.

## File Changes

### Investigate first (Task 1)
- `src/routes/login/+page.svelte`, `src/lib/api/client.ts`, `src/lib/auth.svelte.ts`, root `+page.svelte` / layout guard — trace why authed redirect doesn't land. Likely `auth.setUser` runs after `goto` evaluation, or `shelf_token` write to localStorage races with subsequent page-load fetch.

### Modify tests
- `ui/tests/auth.spec.ts`
  - `:15` — wait for URL change with `waitForURL` then assert; loosen regex to `^http.*\/(admin\/.*|)$`
  - `:24` — scope to `.first()` or use `getByRole('alert')` once banner gets `role="alert"`
- `ui/tests/import.spec.ts:15` — use exact name `'Scan Directory'` (the submit button)
- `ui/tests/koreader.spec.ts:8` — `getByText('Sync Server URL', { exact: true })` or testid
- `ui/tests/opds.spec.ts`
  - `:35` — assert `All Books` (or whatever current title is) + `<feed` + acquisition link
  - `:41` — assert `Search results for: test`
- `ui/tests/catalog.spec.ts:9` — switch to `[data-testid="library-search"]`
- `ui/tests/stats.spec.ts:7` — switch to `[data-testid="stats-heading"]`
- `ui/tests/mobile-shell.spec.ts:20` — `[data-testid="mobile-drawer"]` (or whatever actual menu impl uses; verify via tracing existing markup)
- `ui/tests/library.spec.ts`, `ui/tests/metadata.spec.ts` — `[data-testid="book-card"]`
- `ui/tests/podcasts.spec.ts:8` — `[data-testid="podcast-card"]` + correct empty-state copy

### Modify UI (add testids)
Minimal, additive. No behavior change.
- `src/lib/components/ui/StatusBanner.svelte` — `role="alert"` on wrapper
- `src/routes/(admin)/admin/library/+page.svelte` (or catalog list component) — `data-testid="library-search"` on input, `data-testid="book-card"` on item
- `src/routes/stats/+page.svelte` — `data-testid="stats-heading"` on main heading
- Mobile shell drawer component — `data-testid="mobile-drawer"` on open dialog
- `src/routes/podcasts/+page.svelte` — `data-testid="podcast-card"` on item, `data-testid="podcasts-empty"` on empty-state
- Import page — `data-testid="scan-now-button"` / `scan-directory-submit` to disambiguate
- KOReader settings — `data-testid="sync-server-url"` on URL value

## Implementation Steps

### Task 1 — Diagnose auth redirect (BLOCKING)
- Run single test with `--trace=on --headed`: `npx playwright test auth.spec.ts:6 --headed --trace=on`
- Inspect `goto('/')` vs guard order. Likely fix: `await auth.setUser(...)` before `goto`, or have root `+page.svelte` await auth hydration. Confirm `shelf_token` survives reload.
- Validate by running `auth.spec.ts` to green.

### Task 2 — Fix trivially-wrong assertions
- OPDS string asserts (`opds.spec.ts:35,41`)
- Strict-mode `.first()` / exact-name fixes (auth, import, koreader)
- Re-run those files only.

### Task 3 — Add `data-testid` hooks
- Touch each component listed above with one attribute. Spotless apply not needed (frontend).

### Task 4 — Switch flaky tests to testids
- Library, metadata, podcasts, stats, catalog, mobile-shell.

### Task 5 — Seed verification
- Library/metadata/podcasts depend on `apiHelper.seedBook()` / `seedPodcast()`. Confirm seeding actually runs in those specs (grep `beforeEach`/`beforeAll`). If missing, add explicit seed step.

### Task 6 — Full suite green
- `npm run test:e2e` → 0 failures across chromium + Mobile Chrome.
- Tear down + bring up fresh containers once to confirm clean-state run.

## Out of scope
- Backend changes. All failures explainable from frontend / test layer.
- CI workflow wiring (separate plan).
- Replacing remaining text selectors that currently pass.
