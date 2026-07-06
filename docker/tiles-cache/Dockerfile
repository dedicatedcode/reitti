FROM nginx:1.30.1-trixie

RUN mkdir -p /var/cache/nginx/custom

COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY entrypoint.sh /custom-entrypoint.sh
RUN chmod +x /custom-entrypoint.sh

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=5 \
  CMD curl -f -s -o /dev/null -H "X-Reitti-Upstream-Url: https://tiles.dedicatedcode.com" http://localhost/custom/planet/latest/0/0/0.pbf || exit 1

ENTRYPOINT ["/custom-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]