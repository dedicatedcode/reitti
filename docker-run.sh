#!/bin/bash

# Build the Docker image if needed
# mvn clean package
# docker build -t reitti/reitti:test .

# Run the container with custom UID/GID
docker run -p 8080:8080 \
  -e APP_UID=1001 \
  -e APP_GID=1001 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/reitti \
  reitti/reitti:test

# To verify the user ID inside the container, run:
# docker exec -it $(docker ps -q --filter ancestor=reitti/reitti:test) sh -c "id"
