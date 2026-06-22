FROM nginx:1.30.1-trixie

RUN mkdir -p /var/cache/nginx/custom

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=5 \
  CMD curl -f -s -o /dev/null -H "X-Reitti-Upstream-Url: https://tiles.dedicatedcode.com" http://localhost/custom/planet/latest/0/0/0.pbf || exit 1
