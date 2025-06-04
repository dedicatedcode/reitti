FROM eclipse-temurin:24-jre-alpine

LABEL maintainer="dedicatedcode"
LABEL org.opencontainers.image.source="https://github.com/dedicatedcode/reitti"
LABEL org.opencontainers.image.description="Reitti - Personal Location Tracking & Analysis"
LABEL org.opencontainers.image.licenses="MIT"

# Create a non-root user and group
RUN addgroup -S reitti -g 1000 && adduser -S reitti -u 1000 -G reitti

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV APP_HOME=/app

# Create application directory
RUN mkdir -p $APP_HOME && \
    chown -R reitti:reitti $APP_HOME

WORKDIR $APP_HOME

# Copy the application jar
COPY --chown=reitti:reitti target/*.jar $APP_HOME/app.jar

# Create a script to start the application with configurable UID/GID
RUN echo '#!/bin/sh' > /entrypoint.sh && \
    echo 'if [ -n "$APP_UID" ] && [ -n "$APP_GID" ]; then' >> /entrypoint.sh && \
    echo '  echo "Changing reitti user/group to UID:$APP_UID / GID:$APP_GID"' >> /entrypoint.sh && \
    echo '  # Add shadow package for usermod and groupmod commands' >> /entrypoint.sh && \
    echo '  apk add --no-cache shadow' >> /entrypoint.sh && \
    echo '  # Change GID first' >> /entrypoint.sh && \
    echo '  groupmod -g $APP_GID reitti' >> /entrypoint.sh && \
    echo '  # Then change UID' >> /entrypoint.sh && \
    echo '  usermod -u $APP_UID reitti' >> /entrypoint.sh && \
    echo '  # Fix ownership of all files' >> /entrypoint.sh && \
    echo '  chown -R reitti:reitti $APP_HOME' >> /entrypoint.sh && \
    echo 'fi' >> /entrypoint.sh && \
    echo 'exec su-exec reitti java $JAVA_OPTS -jar $APP_HOME/app.jar "$@"' >> /entrypoint.sh && \
    chmod +x /entrypoint.sh

# Expose the application port
EXPOSE 8080

# Install su-exec for proper user switching
RUN apk add --no-cache su-exec

# Run as root initially to allow UID/GID changes
USER root

ENTRYPOINT ["/entrypoint.sh"]
