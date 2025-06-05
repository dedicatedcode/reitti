# Reitti

Reitti is a personal location tracking and analysis application that helps you understand your movement patterns and significant places. The name "Reitti" comes from Finnish, meaning "route" or "path".

## Features

- **Location Tracking**: Import and process GPS data from various sources including GPX files
- **Visit Detection**: Automatically identify places where you spend time
- **Trip Analysis**: Track your movements between locations
- **Significant Places**: Recognize and categorize frequently visited locations
- **Timeline View**: See your day organized as visits and trips
- **Privacy-Focused**: Self-hosted solution that keeps your location data private
- **Asynchronous Processing**: Handle large datasets efficiently with RabbitMQ-based processing

## Getting Started

### Prerequisites

- Java 24 or higher
- Maven 3.6 or higher
- Docker and Docker Compose
- PostgreSQL database with spatial extensions
- RabbitMQ for message processing

### Running the Application

1. Clone the repository
   ```bash
   git clone https://github.com/dedicatedcode/reitti.git
   cd reitti
   ```

2. Start the infrastructure services
   ```bash
   docker-compose up -d
   ```

3. Build and run the application
   ```bash
   mvn spring-boot:run
   ```

4. Access the application at `http://localhost:8080`

### Building Docker Image

```bash
# Build the application
mvn clean package

# Build the Docker image
docker build -t reitti/reitti:latest .
```

## Docker

This [repository](https://hub.docker.com/r/dedicatedcode/reitti/)  contains Docker images for the Reitti application.

### Usage

```bash
# Pull the image
docker pull reitti/reitti:latest

# Run with PostgreSQL and RabbitMQ using docker-compose
docker-compose up -d

# Or run standalone with environment variables
docker run -p 8080:8080 \
  -e POSTGIS_HOST=postgres \
  -e POSTGIS_PORT=5432 \
  -e POSTGIS_DB=reittidb \
  -e POSTGIS_USER=reitti \
  -e POSTGIS_PASSWORD=reitti \
  -e RABBITMQ_HOST=rabbitmq \
  reitti/reitti:latest
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGIS_HOST` | PostgreSQL database host | postgis |
| `POSTGIS_PORT` | PostgreSQL database port | 5432 |
| `POSTGIS_DB` | PostgreSQL database name | reittidb |
| `POSTGIS_USER` | Database username | reitti |
| `POSTGIS_PASSWORD` | Database password | reitti |
| `RABBITMQ_HOST` | RabbitMQ host | rabbitmq |
| `RABBITMQ_PORT` | RabbitMQ port | 5672 |
| `RABBITMQ_USER` | RabbitMQ username | reitti |
| `RABBITMQ_PASSWORD` | RabbitMQ password | reitti |
| `SERVER_PORT` | Application server port | 8080 |
| `APP_UID` | User ID to run the application as | 1000 |
| `APP_GID` | Group ID to run the application as | 1000 |
| `JAVA_OPTS` | JVM options | |

### Tags

- `latest` - Latest stable release
- `x.y.z` - Specific version releases

## Data Flow

1. Location data is imported via API or web interface
2. Data is queued in RabbitMQ for asynchronous processing
3. Processing workers analyze the data to detect visits and trips
4. Results are stored in PostgreSQL database
5. Web interface displays the processed data as timeline and maps

## Technologies

- **Backend**: Spring Boot, Spring Data JPA, Spring Security
- **Database**: PostgreSQL with spatial extensions
- **Message Queue**: RabbitMQ for asynchronous processing
- **Frontend**: Thymeleaf, JavaScript
- **Testing**: JUnit 5, Testcontainers
- **Containerization**: Docker

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
