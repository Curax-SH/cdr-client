FROM nginx:alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy custom nginx configuration to enable directory listings
COPY nginx.conf /etc/nginx/conf.d/default.conf
