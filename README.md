# Reitti

Reitti is a Spring Boot application for tracking and visualizing location data over time.

## Features

- Store location data with timestamps
- View timeline of locations
- REST API for location data

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

## API Endpoints

- `GET /api/v1/timeline` - Get all timeline locations

## Technologies

- Spring Boot
- Spring Data JPA
- PostgreSQL with PostGIS and TimescaleDB extensions
- Redis for caching
- RabbitMQ for message queuing
- Lombok
