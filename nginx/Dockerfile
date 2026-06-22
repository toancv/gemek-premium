# =============================================================================
# Gemek Premium — nginx + Frontend
# Build context: project root (docker-compose uses context: .)
# Stage 1: Install pnpm workspace deps
# Stage 2: Build admin app  (served on port 80)
# Stage 3: Build resident app (served on port 81)
# Stage 4: nginx runtime with both builds
# =============================================================================

FROM node:24-alpine AS base
ENV CI=true
RUN corepack enable

# --- Dependency install --------------------------------------------------
FROM base AS deps
WORKDIR /app
# Copy manifests first to leverage Docker layer caching.
COPY frontend/package.json frontend/pnpm-workspace.yaml ./
COPY frontend/packages/ui/package.json ./packages/ui/
COPY frontend/apps/admin/package.json ./apps/admin/
COPY frontend/apps/resident/package.json ./apps/resident/
RUN pnpm install

# --- Admin build ---------------------------------------------------------
FROM deps AS admin-builder
# Copy full workspace source on top of installed node_modules.
COPY frontend/ .
RUN pnpm --filter admin build

# --- Resident build -------------------------------------------------------
FROM deps AS resident-builder
COPY frontend/ .
RUN pnpm --filter resident build

# --- nginx runtime --------------------------------------------------------
FROM nginx:1.28.3-alpine AS runtime
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=admin-builder    /app/apps/admin/dist    /usr/share/nginx/html/admin
COPY --from=resident-builder /app/apps/resident/dist /usr/share/nginx/html/resident
EXPOSE 80 81
