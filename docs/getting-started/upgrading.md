# Upgrading

## Docker Compose

Pull the latest images and restart:

```bash
docker-compose pull
docker-compose up -d
```

Database migrations run automatically on startup. No manual migration steps are required.

## From Source

Pull the latest changes and rebuild:

```bash
git pull
docker-compose up -d --build
```

## Breaking Changes

Breaking changes are documented in the [CHANGELOG](https://github.com/tarantini-io/shelf/blob/main/CHANGELOG.md) and flagged in release notes. Check the changelog before upgrading across major versions.
