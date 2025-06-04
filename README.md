# Reitti

Reitti is a personal location tracking and analysis application that helps you understand your movement patterns and significant places.

## Features

- **Location Tracking**: Import and process GPS data from various sources including GPX files
- **Visit Detection**: Automatically identify places where you spend time
- **Trip Analysis**: Track your movements between locations
- **Significant Places**: Recognize and categorize frequently visited locations
- **Timeline View**: See your day organized as visits and trips
- **Privacy-Focused**: Self-hosted solution that keeps your location data private

## Getting Started

### Prerequisites

- Java 24 or higher
- Maven 3.6 or higher
- Docker and Docker Compose

### Running the Application

1. Clone the repository
2. Navigate to the project directory
3. Start the infrastructure services with `docker-compose up -d`
4. Run `mvn spring-boot:run`
5. Access the application at `http://localhost:8080`

## Docker

This repository contains Docker images for the Reitti application.

### Usage

```bash
docker pull reitti/reitti:latest

# Run with PostgreSQL and RabbitMQ
docker-compose up -d
```

### Environment Variables

- `SPRING_DATASOURCE_URL` - JDBC URL for PostgreSQL database
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `SPRING_RABBITMQ_HOST` - RabbitMQ host
- `SPRING_RABBITMQ_PORT` - RabbitMQ port
- `SPRING_RABBITMQ_USERNAME` - RabbitMQ username
- `SPRING_RABBITMQ_PASSWORD` - RabbitMQ password

### Tags

- `latest` - Latest stable release
- `x.y.z` - Specific version releases

## API Endpoints

- `POST /api/v1/import/gpx` - Import GPX data
- `GET /api/v1/queue-stats` - Get processing queue statistics

## Technologies

- Spring Boot
- Spring Data JPA
- PostgreSQL with spatial extensions
- RabbitMQ for asynchronous processing
- Spring Security for authentication

## License

This project is licensed under the MIT License - see the LICENSE file for details.
