# Quickstart

Shelf is designed to be easily self-hosted using Docker. This guide walks you through getting Shelf running on your own server.

## Prerequisites

- **Docker** and **Docker Compose** installed.
- A domain name (optional, but recommended for HTTPS).

## Docker Compose Setup

1. **Clone the repository:**

    ```bash
    git clone https://github.com/tarantini-io/shelf.git
    cd shelf
    ```

2. **Configure environment variables:**

    Copy the example environment file and edit it with your settings.

    ```bash
    cp example.env .env
    ```

    !!! danger "Security Warning"
        You **must** change `JWT_SECRET` and `POSTGRES_PASSWORD` before deploying to a public-facing server. Do not use the default values from `example.env` in production.

    See the [Configuration Reference](configuration.md) for all available environment variables.

3. **Start the containers:**

    ```bash
    docker-compose up -d
    ```

Shelf is now accessible at `http://localhost:3000`.

## Storage Configuration

Shelf expects your media files (EPUBs, MP3s, M4Bs) to be located in a directory accessible to the container. By default, `docker-compose.yaml` mounts `./storage` into the backend container.

You can change this by modifying `STORAGE_PATH`, `IMPORT_SCAN_ROOTS`, and the backend volume mapping in `docker-compose.yaml`.

## Reverse Proxy and HTTPS

We recommend running Shelf behind a reverse proxy like **Nginx**, **Caddy**, or **Traefik** to handle SSL/TLS termination. Proxy to the frontend on port `3000`; it forwards `/api/*` requests to the backend.

### Example Nginx Config

```nginx
server {
    listen 443 ssl;
    server_name shelf.example.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Updating Shelf

To update to the latest version, pull the latest images and restart:

```bash
docker-compose pull
docker-compose up -d
```
