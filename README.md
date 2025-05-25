# Reitti

Reitti is a Spring Boot application for tracking and visualizing location data over time.

## Features

- Store location data with timestamps
- View timeline of locations
- REST API for location data

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Running the Application

1. Clone the repository
2. Navigate to the project directory
3. Run `mvn spring-boot:run`
4. Access the application at `http://localhost:8080`

## API Endpoints

- `GET /api/timeline` - Get all timeline locations

## Technologies

- Spring Boot
- Spring Data JPA
- H2 Database
- Lombok
