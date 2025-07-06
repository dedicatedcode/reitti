# Reitti - Personal Location Tracking & Analysis

Reitti is a comprehensive self-hosted application for tracking, analyzing, and visualizing your location data over time. It helps you understand your movement patterns and significant places while keeping your location data completely private and under your control.

## Key Features

### Core Functionality
- **Advanced Location Analysis**: Automatic visit and trip detection with transport mode recognition
- **Interactive Timeline**: Daily timeline view with visits, trips, durations, and distances
- **Significant Places**: Smart recognition and categorization of frequently visited locations
- **Real-time Tracking**: Live location updates via OwnTracks and GPSLogger mobile apps
- **Multi-format Import**: Support for GPX, Google Takeout JSON, and GeoJSON files

### Photo Integration
- **Immich Integration**: Connect with self-hosted Immich photo servers
- **Location-based Photos**: View photos taken at specific locations on your timeline
- **Interactive Photo Viewer**: Full-screen photo galleries with keyboard navigation

### User Management & Security
- **Multi-user Support**: Individual user accounts with data isolation
- **Multi-language Support**: Available in English, Finnish, German, and French
- **Geocoding Services**: Configurable address resolution with multiple provider support

### Privacy & Performance
- **Complete Privacy**: Your data never leaves your server - no cloud dependencies
- **Asynchronous Processing**: Efficient handling of large location datasets with RabbitMQ
- **Real-time Monitoring**: Queue status and job processing visibility
- **Self-hosted**: Deploy on your own infrastructure with full control

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Clone the repository for docker-compose.yml
git clone https://github.com/dedicatedcode/reitti.git
cd reitti

# Start all services (PostgreSQL, RabbitMQ, Reitti)
docker-compose up -d

# Access at http://localhost:8080
```

### Standalone Docker

```bash
docker pull dedicatedcode/reitti:latest
docker run -p 8080:8080 \
  -e POSTGIS_HOST=postgres \
  -e POSTGIS_PORT=5432 \
  -e POSTGIS_DB=reittidb \
  -e POSTGIS_USER=reitti \
  -e POSTGIS_PASSWORD=reitti \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_PORT=5672 \
  -e RABBITMQ_USER=reitti \
  -e RABBITMQ_PASSWORD=reitti \
  dedicatedcode/reitti:latest
```

**Note**: Standalone mode requires separate PostgreSQL (with PostGIS) and RabbitMQ instances.

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
| `SERVER_PORT` | Application server port | 8080 |
| `APP_UID` | User ID to run the application as | 1000 |
| `APP_GID` | Group ID to run the application as | 1000 |
| `JAVA_OPTS` | JVM options for the application | |

## Getting Started

1. **Deploy**: Use docker-compose for a complete setup with all dependencies
2. **Create Account**: Set up your first user account via the web interface
3. **Generate API Token**: Create tokens for mobile app integration
4. **Import Data**: Upload existing location data (GPX, Google Takeout, GeoJSON)
5. **Configure Mobile Apps**: Set up OwnTracks or GPSLogger for real-time tracking
6. **Add Geocoding**: Configure address resolution services
7. **Connect Photos**: Integrate with Immich for location-based photo viewing

## Mobile App Integration

Configure real-time location tracking with:
- **OwnTracks** (iOS/Android): Privacy-focused location sharing
- **GPSLogger** (Android): Lightweight GPS logging with custom URL support
- **Custom Apps**: Use the REST API for custom integrations

## Tags

- `develop` - **Bleeding Edge**: Built from every push to main branch. For developers and early adopters who want the newest features and don't mind potential instability.
- `latest` - **Stable Release**: Updated with each stable release. For most users who want reliable, tested functionality with new features.
- `x.y.z` - **Conservative**: Specific version releases for users who want full control over updates and prefer to manually choose when to upgrade.

## Source Code

The source code for this project is available on GitHub: [https://github.com/dedicatedcode/reitti](https://github.com/dedicatedcode/reitti)

## License

This project is licensed under the MIT License.
