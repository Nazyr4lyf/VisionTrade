# Build and test the backend worker before producing the runtime image.
FROM node:20-alpine AS build
WORKDIR /app
COPY backend/package.json ./
COPY backend/ ./
RUN npm test

# Minimal non-root runtime image for an always-on Render background worker.
FROM node:20-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build --chown=node:node /app /app
USER node
CMD ["npm", "start"]
