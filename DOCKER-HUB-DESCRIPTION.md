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
  -e POSTGIS_HOST=postgres \
  -e POSTGIS_PORT=5432 \
  -e POSTGIS_DB=reittidb \
  -e POSTGIS_USER=reitti \
  -e POSTGIS_PASSWORD=reitti \
  -e RABBITMQ_HOST=rabbitmq \
  reitti/reitti:latest
```

For production use, we recommend using the provided docker-compose file that includes PostgreSQL and RabbitMQ.

## Environment Variables

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
| `APP_UID` | User ID to run the application as | 1000 |
| `APP_GID` | Group ID to run the application as | 1000 |
| `JAVA_OPTS` | JVM options for the application | |

## Tags

- `latest` - Latest stable release
- `x.y.z` - Specific version releases

## Source Code

The source code for this project is available on GitHub: [https://github.com/dedicatedcode/reitti](https://github.com/dedicatedcode/reitti)

## License

This project is licensed under the MIT License.
