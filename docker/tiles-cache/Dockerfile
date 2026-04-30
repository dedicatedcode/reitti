FROM nginx:alpine

# Create cache directories for different tile types
RUN mkdir -p /var/cache/nginx/osm /var/cache/nginx/vector /var/cache/nginx/custom /var/cache/nginx/terrain /var/cache/nginx/satellite

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
