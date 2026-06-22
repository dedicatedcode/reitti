FROM eclipse-temurin:25-jre-alpine-3.22

LABEL maintainer="dedicatedcode"
LABEL org.opencontainers.image.source="https://github.com/dedicatedcode/reitti"
LABEL org.opencontainers.image.description="Reitti - Personal Location Tracking & Analysis"
LABEL org.opencontainers.image.licenses="MIT"

# Create a non-root user and group
RUN addgroup -S reitti -g 1000 && adduser -S reitti -u 1000 -G reitti

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV APP_HOME=/app
ENV DATA_DIR=/data

# Create application directory
RUN mkdir -p $APP_HOME && \
    chown -R reitti:reitti $APP_HOME && \
    chmod 755 $APP_HOME

WORKDIR $APP_HOME

# Copy the application jar
COPY --chown=reitti:reitti target/*.jar $APP_HOME/app.jar
RUN chmod 644 $APP_HOME/app.jar

# Create a script to start the application with configurable UID/GID
RUN cat <<'EOF' > /entrypoint.sh
#!/bin/sh

# 1. Only attempt UID/GID modification if we are running as root
if [ "$(id -u)" = '0' ]; then
  if [ -n "$APP_UID" ] && [ -n "$APP_GID" ]; then
    echo "Changing reitti user/group to UID:$APP_UID / GID:$APP_GID"
    apk add --no-cache shadow
    groupmod -g "$APP_GID" reitti
    usermod -u "$APP_UID" reitti
    # Ensure the home directory is owned by the new UID/GID
    chown -R reitti:reitti "$APP_HOME"
  fi

  # 2. Ensure DATA_DIR exists
  mkdir -p "$DATA_DIR"
  chmod 755 "$DATA_DIR"

  # 3. Only chown DATA_DIR if the reitti user cannot write to it
  # We use su-exec to "test" writability as the reitti user
  if ! su-exec reitti [ -w "$DATA_DIR" ]; then
    echo "DATA_DIR ($DATA_DIR) is not writable by reitti. Adjusting permissions..."
    chown -R reitti:reitti "$DATA_DIR"
  else
    echo "DATA_DIR ($DATA_DIR) is already writable. Skipping chown."
  fi

  # Execute as the reitti user
  exec su-exec reitti java $JAVA_OPTS -jar "$APP_HOME/app.jar" -Dspring.profiles.active=docker "$@"
else
  echo "Warning: Container is running as UID $(id -u), not root."
  echo "Environment variables APP_UID/APP_GID will be ignored."
  echo "Ensure your volumes have the correct permissions on the host."
  exec java $JAVA_OPTS -jar "$APP_HOME/app.jar" -Dspring.profiles.active=docker "$@"
fi
EOF

RUN chmod 755 /entrypoint.sh
# Expose the application port
EXPOSE 8080

# Add healthcheck
HEALTHCHECK --interval=5s --timeout=3s --start-period=1s --retries=5 \
  CMD sh -c 'curl -f --max-time 2 "http://localhost:8080${BASE_PATH}/actuator/health" || (echo "Health check failed" && exit 1)'

# Install su-exec for proper user switching and wget for healthcheck
RUN apk add --no-cache su-exec curl attr
RUN setfattr -n user.pax.flags -v "mr" /opt/java/openjdk/bin/java

# Run as root initially to allow UID/GID changes
USER root

ENTRYPOINT ["/entrypoint.sh"]
