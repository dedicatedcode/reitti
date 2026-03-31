FROM postgis/postgis@sha256:1cd5da788ab0deddabefb607a51fcfcbcaf6ebc44ab917452ed9f8a529fc8e24

LABEL maintainer="dedicatedcode" \
      org.opencontainers.image.source="https://github.com/dedicatedcode/reitti" \
      org.opencontainers.image.description="Postgis with h3"

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-18-h3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN echo "CREATE EXTENSION IF NOT EXISTS h3;" > /docker-entrypoint-initdb.d/01-init-h3.sql \
    && echo "CREATE EXTENSION IF NOT EXISTS h3_postgis CASCADE;" >> /docker-entrypoint-initdb.d/01-init-h3.sql

EXPOSE 5432

VOLUME ["/var/lib/postgresql"]

STOPSIGNAL SIGINT

USER postgres
