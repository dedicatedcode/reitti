
![](.github/banner.png)


Reitti is a comprehensive personal location tracking and analysis application that helps you understand your movement patterns and significant places. The name "Reitti" comes from Finnish, meaning "route" or "path".

## Features

### Core Location Analysis
- **Visit Detection**: Automatically identify places where you spend time with configurable algorithms
- **Trip Analysis**: Track your movements between locations with transport mode detection (walking, cycling, driving)
- **Significant Places**: Recognize and categorize frequently visited locations with custom naming
- **Timeline View**: Interactive daily timeline showing visits and trips with duration and distance information
- **Raw Location Tracking**: Visualize your complete movement path with detailed GPS tracks

### Data Import & Integration
- **Multiple Import Formats**: Support for GPX files, Google Takeout JSON, and GeoJSON files
- **Real-time Data Ingestion**: Live location updates via OwnTracks and GPSLogger mobile apps
- **Batch Processing**: Efficient handling of large location datasets with queue-based processing
- **API Integration**: RESTful API for programmatic data access and ingestion

### Photo Management
- **Immich Integration**: Connect with self-hosted Immich photo servers
- **Location-based Photos**: View photos taken at specific locations and dates on your timeline
- **Interactive Photo Viewer**: Full-screen photo modal with keyboard navigation
- **Photo Grid Display**: Organized photo galleries for locations with multiple images

### User Management & Security
- **Multi-user Support**: Multiple user accounts with individual data isolation
- **API Token Management**: Secure API access with token-based authentication
- **User Profile Management**: Customizable display names and secure password management

### Geocoding & Address Resolution
- **Multiple Geocoding Services**: Support for custom geocoding providers (Nominatim, etc.)
- **Automatic Address Resolution**: Convert coordinates to human-readable addresses
- **Service Management**: Configure multiple geocoding services with automatic failover

### Customization & Localization
- **Multi-language Support**: Available in English, Finnish, German, and French
- **Queue Monitoring**: Real-time job status and processing queue visibility

### Privacy & Self-hosting
- **Complete Data Control**: Your location data never leaves your server
- **Self-hosted Solution**: Deploy on your own infrastructure
- **Asynchronous Processing**: Handle large datasets efficiently with RabbitMQ-based processing

## Getting Started

### Prerequisites

- Java 24 or higher
- Maven 3.6 or higher
- Docker and Docker Compose
- PostgreSQL database with spatial extensions (PostGIS)
- RabbitMQ for message processing

### Quick Start with Docker

The easiest way to get started is using Docker Compose:

1. Clone the repository
   ```bash
   git clone https://github.com/dedicatedcode/reitti.git
   cd reitti
   ```

2. Start all services (PostgreSQL, RabbitMQ, and Reitti)
   ```bash
   docker-compose up -d
   ```

3. Access the application at `http://localhost:8080`

4. Create your first user account through the web interface

### Development Setup

For development or custom deployments:

1. Start infrastructure services
   ```bash
   docker-compose up -d postgis rabbitmq
   ```

2. Build and run the application
   ```bash
   mvn spring-boot:run
   ```

3. Access the application at `http://localhost:8080`

### Building Docker Image

```bash
# Build the application
mvn clean package

# Build the Docker image
docker build -t reitti/reitti:latest .
```

### Initial Configuration

After starting the application:

1. **Create User Account**: Set up your first user account
2. **Generate API Token**: Create an API token in Settings → API Tokens for mobile app integration
3. **Configure Geocoding**: Add geocoding services in Settings → Geocoding for address resolution
4. **Import Data**: Upload your location data via Settings → Import Data
5. **Set up Mobile Apps**: Configure OwnTracks or GPSLogger for real-time tracking

## Docker Deployment

This [repository](https://hub.docker.com/r/dedicatedcode/reitti/) contains Docker images for the Reitti application.

### Production Deployment

For production use, we recommend using the provided docker-compose configuration:

```bash
# Pull the latest image
docker pull dedicatedcode/reitti:latest

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f reitti
```

### Standalone Docker Usage

```bash
# Run standalone with environment variables
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

### Docker Compose Configuration

The included `docker-compose.yml` provides a complete setup with:
- PostgreSQL with PostGIS extensions
- RabbitMQ for message processing
- Reitti application with proper networking
- Persistent data volumes
- Health checks and restart policies

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

## Data Flow & Architecture

### Location Data Processing Pipeline

1. **Data Ingestion**: Location data enters the system via:
   - File uploads (GPX, Google Takeout, GeoJSON)
   - Real-time mobile app integration (OwnTracks, GPSLogger)
   - REST API endpoints

2. **Queue Processing**: Data is queued in RabbitMQ for asynchronous processing:
   - Raw location points are validated and stored
   - Processing jobs are distributed across workers
   - Queue status is monitored in real-time

3. **Analysis & Detection**: Processing workers analyze the data to:
   - Detect significant places where you spend time
   - Identify trips between locations
   - Determine transport modes (walking, cycling, driving)
   - Calculate distances and durations

4. **Storage & Indexing**: Results are stored in PostgreSQL with:
   - Spatial indexing for efficient geographic queries
   - Temporal indexing for timeline operations
   - User data isolation and security

5. **Visualization**: Web interface displays processed data as:
   - Interactive timeline with visits and trips
   - Map visualization with location markers
   - Photo integration showing images taken at locations
   - Statistical summaries and insights

### Mobile App Integration

Configure mobile apps for automatic location tracking:

- **OwnTracks**: Privacy-focused location sharing
- **GPSLogger**: Lightweight Android GPS logging
- **Custom Apps**: Use the REST API for custom integrations

### Photo Integration

Connect with Immich photo servers to:
- Display photos taken at specific locations
- Show images on the timeline map
- Browse photo galleries by location and date

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
