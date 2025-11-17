# GitHub Actions - Build and Push Docker Images

## Overview

The GitHub Actions workflow automatically builds Docker images for all three services and pushes them to GitHub Container Registry (ghcr.io).

## Workflow Triggers

The workflow runs on:
- **Push** to `main` or `master` branch
- **Pull Request** to `main` or `master` branch
- **Manual trigger** via workflow_dispatch

## What It Does

1. Builds Docker images for:
   - `eureka-server`
   - `user-service`
   - `order-service`

2. Pushes images to GitHub Container Registry with tags:
   - `latest` (for main/master branch)
   - `<branch-name>` (for branch pushes)
   - `<branch>-<sha>` (commit SHA)
   - `pr-<number>` (for pull requests)

## Setup Instructions

### 1. Enable GitHub Container Registry

No additional setup needed - the workflow uses `GITHUB_TOKEN` which is automatically provided.

### 2. Push to GitHub

```bash
cd eureka-demo
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/<repo-name>.git
git push -u origin main
```

### 3. Verify Images

After the workflow completes, images will be available at:
```
ghcr.io/<your-username>/eureka-server:latest
ghcr.io/<your-username>/user-service:latest
ghcr.io/<your-username>/order-service:latest
```

## Using the Images

### Pull from GitHub Container Registry

```bash
# Login (if repository is private)
echo $GITHUB_TOKEN | docker login ghcr.io -u <your-username> --password-stdin

# Pull images
docker pull ghcr.io/<your-username>/eureka-server:latest
docker pull ghcr.io/<your-username>/user-service:latest
docker pull ghcr.io/<your-username>/order-service:latest
```

### Update docker-compose.yml

Replace the `build` directives with `image`:

```yaml
version: '3.8'

services:
  eureka-server:
    image: ghcr.io/<your-username>/eureka-server:latest
    ports:
      - "8761:8761"
    networks:
      - eureka-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  user-service:
    image: ghcr.io/<your-username>/user-service:latest
    ports:
      - "8081:8081"
    environment:
      - EUREKA_SERVER=http://eureka-server:8761
    depends_on:
      eureka-server:
        condition: service_healthy
    networks:
      - eureka-network

  order-service:
    image: ghcr.io/<your-username>/order-service:latest
    ports:
      - "8082:8082"
    environment:
      - EUREKA_SERVER=http://eureka-server:8761
    depends_on:
      eureka-server:
        condition: service_healthy
    networks:
      - eureka-network

networks:
  eureka-network:
    driver: bridge
```

## Making Images Public

By default, images are private. To make them public:

1. Go to `https://github.com/<your-username>?tab=packages`
2. Click on the package (e.g., `eureka-server`)
3. Click "Package settings"
4. Scroll to "Danger Zone"
5. Click "Change visibility" → "Public"

## Workflow Features

- **Matrix Strategy**: Builds all three services in parallel
- **Docker Buildx**: Uses BuildKit for faster builds
- **Layer Caching**: Uses GitHub Actions cache to speed up builds
- **Automatic Tagging**: Creates multiple tags for different use cases
- **Permissions**: Minimal required permissions (read contents, write packages)

## Monitoring Builds

View workflow runs at:
```
https://github.com/<your-username>/<repo-name>/actions
```
