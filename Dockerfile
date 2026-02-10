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
    chown -R reitti:reitti $APP_HOME

WORKDIR $APP_HOME

# Copy the application jar
COPY --chown=reitti:reitti target/*.jar $APP_HOME/app.jar

# Create a script to start the application with configurable UID/GID
RUN cat <<'EOF' > /entrypoint.sh
#!/bin/sh
if [ -n "$APP_UID" ] && [ -n "$APP_GID" ]; then
  echo "Changing reitti user/group to UID:$APP_UID / GID:$APP_GID"
  apk add --no-cache shadow
  groupmod -g $APP_GID reitti
  usermod -u $APP_UID reitti
  chown -R reitti:reitti $APP_HOME
fi

mkdir -p $DATA_DIR
chown -R reitti:reitti $DATA_DIR

# Execute
exec su-exec reitti java $JAVA_OPTS -jar $APP_HOME/app.jar -Dspring.profiles.active=docker "$@"
EOF

RUN chmod +x /entrypoint.sh
# Expose the application port
EXPOSE 8080

# Add healthcheck
HEALTHCHECK --interval=5s --timeout=3s --start-period=1s --retries=20 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Install su-exec for proper user switching and wget for healthcheck
RUN apk add --no-cache su-exec wget attr
RUN setfattr -n user.pax.flags -v "mr" /opt/java/openjdk/bin/java

# Run as root initially to allow UID/GID changes
USER root

ENTRYPOINT ["/entrypoint.sh"]
