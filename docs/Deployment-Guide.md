# Deployment Guide 🚀

Shelf is designed to be easily self-hosted using Docker. This guide will walk you through the process of setting up Shelf on your own server.

## 📋 Prerequisites

- **Docker** and **Docker Compose** installed.
- A domain name (optional, but recommended for HTTPS).
- Basic familiarity with the command line.

## 🐳 Docker Compose Setup

The easiest way to run Shelf is using the provided `docker-compose.yaml`.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/tarantini-io/shelf.git
   cd shelf
   ```

2. **Configure environment variables:**
   Copy the `example.env` to `.env` and edit it with your settings.
   ```bash
   cp example.env .env
   ```

   > **⚠️ CRITICAL SECURITY WARNING:** You MUST change `JWT_SECRET` and `POSTGRES_PASSWORD`. Do not use the default values from `example.env` in production, as this will leave your server vulnerable to unauthorized access.

3. **Start the containers:**
   ```bash
   docker-compose up -d
   ```

Shelf will now be accessible at `http://localhost:3000`.

## 📁 Storage Configuration

Shelf expects your media files (EPUBs, MP3s) to be located in a directory that is accessible to the container. By default, the `docker-compose.yaml` mounts the `./storage` directory into the backend container.

You can change this by modifying `STORAGE_PATH`, `IMPORT_SCAN_ROOTS`, and the backend volume mapping in `docker-compose.yaml`.

## 🔒 Reverse Proxy & HTTPS

We recommend running Shelf behind a reverse proxy like **Nginx**, **Caddy**, or **Traefik** to handle SSL/TLS termination. When using the bundled frontend container, proxy to the frontend on port `3000`; it forwards `/api/*` requests to the backend.

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

## 🔄 Updating Shelf

To update to the latest version, pull the latest changes and restart your containers:

```bash
git pull
docker-compose up -d --build
```
