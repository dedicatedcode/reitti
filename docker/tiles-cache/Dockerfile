FROM nginx:1.30.1-trixie

# Create cache directories for different tile types
RUN mkdir -p /var/cache/nginx/custom

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
