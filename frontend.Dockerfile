# Bun on Alpine/musl is unstable for this Vite build path under linux/amd64 images.
# Use the glibc-based image for both build and runtime.
FROM oven/bun:1.3.12 AS builder

WORKDIR /app

# Copy dependency files
COPY ui/package.json ui/bun.lock ./

# Install dependencies
RUN bun install --frozen-lockfile

# Copy source code
COPY ui/ .

# Build the application
RUN bun run build

# Production stage
FROM oven/bun:1.3.12

WORKDIR /app

# Copy built application from builder
COPY --from=builder /app/build ./build
COPY --from=builder /app/package.json ./

# Expose the application port
EXPOSE 3000

# Start the application
ENV PORT=3000
CMD ["bun", "build/index.js"]
