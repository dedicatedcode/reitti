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

This repository contains Docker images for the Reitti application.

### Usage

```bash
# Pull the image
docker pull reitti/reitti:latest

# Run with PostgreSQL and RabbitMQ using docker-compose
docker-compose up -d

# Or run standalone with environment variables
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/reitti \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  reitti/reitti:latest
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL for PostgreSQL database | jdbc:postgresql://localhost:5432/reitti |
| `SPRING_DATASOURCE_USERNAME` | Database username | postgres |
| `SPRING_DATASOURCE_PASSWORD` | Database password | postgres |
| `SPRING_RABBITMQ_HOST` | RabbitMQ host | localhost |
| `SPRING_RABBITMQ_PORT` | RabbitMQ port | 5672 |
| `SPRING_RABBITMQ_USERNAME` | RabbitMQ username | guest |
| `SPRING_RABBITMQ_PASSWORD` | RabbitMQ password | guest |
| `SERVER_PORT` | Application server port | 8080 |
| `APP_UID` | User ID to run the application as | 1000 |
| `APP_GID` | Group ID to run the application as | 1000 |
| `JAVA_OPTS` | JVM options | |

### Tags

- `latest` - Latest stable release
- `x.y.z` - Specific version releases

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/import/gpx` | POST | Import GPX data files |
| `/api/v1/queue-stats` | GET | Get processing queue statistics |
| `/settings/import/gpx` | POST | Web interface for GPX import |
| `/api/v1/timeline` | GET | Get timeline data |

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
- **Containerization**: Docker, Spring Boot Docker plugin

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
