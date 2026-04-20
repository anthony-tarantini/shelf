<!-- Derived from README.md and CONTRIBUTING.md — keep in sync -->

# Releases

Shelf uses **Conventional Commits** and **release-please** to automate releases and changelogs.

## Commit Message Format

| Prefix | Purpose | Version bump |
|--------|---------|-------------|
| `feat:` | New feature | MINOR |
| `fix:` | Bug fix | PATCH |
| `chore:` | Maintenance | None |
| `docs:` | Documentation | None |
| `BREAKING CHANGE:` | Breaking change (in footer or body) | MAJOR |

## How Releases Work

1. Contributors merge PRs with Conventional Commit messages.
2. `release-please` automatically opens and updates a release PR that accumulates changes.
3. When the release PR is merged, a GitHub Release is created with auto-generated release notes.

## CI Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | Push to `main`, PRs | Backend checks, backend tests, frontend unit tests, image publishing |
| `release-please.yml` | Push to `main` | Maintains release PR, creates GitHub releases |
| `release-images.yml` | GitHub release | Publishes container images to GHCR |

## Release Metadata Files

- `version.txt` — Current version number
- `CHANGELOG.md` — Auto-generated changelog
- `release-please-config.json` — release-please configuration
- `.release-please-manifest.json` — Version manifest

## Container Images

Images are published to GitHub Container Registry (GHCR) on pushes to `main` and on releases.

!!! note "CI Token Configuration"
    If you want CI to run on release PRs opened by `release-please`, configure a fine-grained GitHub token and swap it in for `secrets.GITHUB_TOKEN` in the workflow. GitHub does not trigger follow-up workflows from PRs or tags created by the default repository token.

## Branch Naming

Use descriptive branch names:

- `feature/add-series-support`
- `fix/author-slug-validation`
