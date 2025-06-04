# Reitti - Personal Location Tracking & Analysis

Reitti is a self-hosted application for tracking, analyzing, and visualizing your location data over time. It helps you understand your movement patterns and significant places while keeping your location data private.

## Key Features

- **Location Data Processing**: Import and process GPS data from various sources
- **Visit & Trip Detection**: Automatically identify places where you spend time and trips between them
- **Significant Places**: Recognize and categorize frequently visited locations
- **Privacy-First**: Your data stays on your server, under your control
- **Asynchronous Processing**: Efficient handling of large location datasets

## Quick Start

```bash
docker pull reitti/reitti:latest
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/reitti \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  reitti/reitti:latest
```

For production use, we recommend using the provided docker-compose file that includes PostgreSQL and RabbitMQ.

## Environment Variables

- `SPRING_DATASOURCE_URL` - JDBC URL for PostgreSQL database
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `SPRING_RABBITMQ_HOST` - RabbitMQ host
- `SPRING_RABBITMQ_PORT` - RabbitMQ port
- `SPRING_RABBITMQ_USERNAME` - RabbitMQ username
- `SPRING_RABBITMQ_PASSWORD` - RabbitMQ password
- `APP_UID` - User ID to run the application as (default: 1000)
- `APP_GID` - Group ID to run the application as (default: 1000)
- `JAVA_OPTS` - JVM options for the application

## Tags

- `latest` - Latest stable release
- `x.y.z` - Specific version releases

## Source Code

The source code for this project is available on GitHub: [https://github.com/dedicatedcode/reitti](https://github.com/dedicatedcode/reitti)

## License

This project is licensed under the MIT License.
