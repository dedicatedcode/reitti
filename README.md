
![](.github/banner.png)


Reitti is a comprehensive personal location tracking and analysis application that helps you understand your movement patterns and significant places. The name "Reitti" comes from Finnish, meaning "route" or "path".

## Features

### Main View

![](.github/screenshots/main.png)

### Multiple Users View

![](.github/screenshots/multiple-users.png)

### Statistics View

![](.github/screenshots/statistics.png)

### Core Location Analysis
- **Visit Detection**: Automatically identify places where you spend time
- **Trip Analysis**: Track your movements between locations with transport mode detection (walking, cycling, driving)
- **Significant Places**: Recognize and categorize frequently visited locations with custom naming
- **Timeline View**: Interactive daily timeline showing visits and trips with duration and distance information
- **Raw Location Tracking**: Visualize your complete movement path with detailed GPS tracks
- **Multi-User-View**: Visualize all your family and friends on a single map
- **Live-Mode**: Visualize incoming data automatically without having to reload the map 
- **Fullscreen-Mode**: Display the map in fullscreen. Combined with the Live-Mode you got a nice kiosk-display

### Data Import & Integration
- **Multiple Import Formats**: Support for GPX files, Google Takeout JSON, Google Timeline Exports and GeoJSON files
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
- **Unit System**: Display distances in the Imperial or Metric system
- **Queue Monitoring**: Real-time job status and processing queue visibility
- **Custom Tiles-Server**: Ability to use your own tiles-server

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
- Redis for caching

### Quick Start with Docker

The easiest way to get started is using Docker Compose:

1. Clone the repository
   ```bash
   git clone https://github.com/dedicatedcode/reitti.git
   cd reitti
   ```

2. Start all services (PostgreSQL, RabbitMQ, Redis and Reitti)
   ```bash
   docker-compose up -d
   ```

3. Access the application at `http://localhost:8080`

4. Login with admin:admin

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

Default username and password is `admin`

### Building Docker Image

```bash
# Build the application
mvn clean package

# Build the Docker image
docker build -t reitti/reitti:latest .
```

### Initial Configuration

After starting the application:

1. **Generate API Token**: Create an API token in Settings → API Tokens for mobile app integration
2. **Configure Geocoding**: Add geocoding services in Settings → Geocoding for address resolution
3. **Import Data**: Upload your location data via Settings → Import Data
4. **Set up Mobile Apps**: Configure OwnTracks or GPSLogger for real-time tracking

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
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e REDIS_USERNAME= \
  -e REDIS_PASSWORD= 
  dedicatedcode/reitti:latest
```

### Docker Compose Configuration

The included `docker-compose.yml` provides a complete setup with:
- PostgreSQL with PostGIS extensions
- RabbitMQ for message processing
- Redis for caching and session storage
- Reitti application with proper networking
- Persistent data volumes
- Health checks and restart policies

### Environment Variables

| Variable            | Description                                                                                                                                                                     | Default  |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| `POSTGIS_HOST`      | PostgreSQL database host                                                                                                                                                        | postgis  |
| `POSTGIS_PORT`      | PostgreSQL database port                                                                                                                                                        | 5432     |
| `POSTGIS_DB`        | PostgreSQL database name                                                                                                                                                        | reittidb |
| `POSTGIS_USER`      | Database username                                                                                                                                                               | reitti   |
| `POSTGIS_PASSWORD`  | Database password                                                                                                                                                               | reitti   |
| `RABBITMQ_HOST`     | RabbitMQ host                                                                                                                                                                   | rabbitmq |
| `RABBITMQ_PORT`     | RabbitMQ port                                                                                                                                                                   | 5672     |
| `RABBITMQ_USER`     | RabbitMQ username                                                                                                                                                               | reitti   |
| `RABBITMQ_PASSWORD` | RabbitMQ password                                                                                                                                                               | reitti   |
| `REDIS_HOST`        | Redis host                                                                                                                                                                      | redis    |
| `REDIS_PORT`        | Redis port                                                                                                                                                                      | 6379     |
| `REDIS_USERNAME`    | Redis username (optional)                                                                                                                                                       |          |
| `REDIS_PASSWORD`    | Redis password (optional)                                                                                                                                                       |          |
| `PHOTON_BASE_URL`   | Base URL for Photon geocoding service                                                                                                                                           |          |
| `PROCESSING_WAIT_TIME` | How many seconds to wait after the last data input before starting to process all unprocessed data. (⚠️ This needs to be lower than your integrated app reports data in Reitti) | 15 |
| `DANGEROUS_LIFE`    | Enables data management features that can reset/delete all database data (⚠️ USE WITH CAUTION)                                                                                  | false |
| `CUSTOM_TILES_SERVICE` | Custom tile service URL template (e.g., `https://tiles.example.com/{z}/{x}/{y}.png`)                                                                                        |          |
| `CUSTOM_TILES_ATTRIBUTION` | Custom attribution text for the tile service                                                                                                                                |          |
| `SERVER_PORT`       | Application server port                                                                                                                                                         | 8080     |
| `APP_UID`           | User ID to run the application as                                                                                                                                               | 1000     |
| `APP_GID`           | Group ID to run the application as                                                                                                                                              | 1000     |
| `JAVA_OPTS`         | JVM options                                                                                                                                                                     |          |

### Tags

- `develop` - **Bleeding Edge**: Built from every push to main branch. For developers and early adopters who want the newest features and don't mind potential instability.
- `latest` - **Stable Release**: Updated with each stable release. For most users who want reliable, tested functionality with new features.
- `x.y.z` - **Conservative**: Specific version releases for users who want full control over updates and prefer to manually choose when to upgrade.

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

## Reverse Geocoding Options

Reitti supports multiple approaches for reverse geocoding (converting coordinates to human-readable addresses). You can choose the option that best fits your privacy, performance, and storage requirements.

### Option 1: Self-hosted Photon (Recommended)

The included docker-compose.yml configuration provides a local Photon instance for complete privacy and optimal performance.

**Included Configuration:**
```yaml
photon:
  image: rtuszik/photon-docker:latest
  environment:
    - UPDATE_STRATEGY=PARALLEL
    - COUNTRY_CODE=de
  volumes:
    - photon-data:/photon/photon_data
  ports:
    - "2322:2322"
```

**Storage Requirements:**
- **Country-specific**: 1-10GB depending on country size
- **Global dataset**: ~200GB for the complete worldwide index
- **PARALLEL mode**: Doubles storage requirements during updates (400GB total for global)

**Configuration Options:**
- **COUNTRY_CODE**: Set to your main country code (e.g., `de`, `us`, `fr`) to save space
- **UPDATE_STRATEGY=PARALLEL**: Faster updates but requires double storage space
- **Remove COUNTRY_CODE**: Download complete global dataset for worldwide coverage

**Benefits:**
- Complete privacy - no external API calls
- Fastest response times with no rate limits
- No dependency on external service availability
- No API usage fees or quotas

### Option 2: External Geocoding Services Only

Remove the Photon service from docker-compose.yml and rely solely on configured external geocoding services.

**To disable Photon:**
1. Remove the `photon` service from docker-compose.yml
2. Remove `PHOTON_BASE_URL` environment variable from the reitti service
3. Configure external geocoding services in Settings → Geocoding

**Supported Services:**
- Nominatim (OpenStreetMap)
- Custom geocoding APIs
- Multiple services with automatic failover

**Benefits:**
- No local storage requirements
- Immediate setup without data downloads
- Access to multiple geocoding providers

### Option 3: Hybrid Approach (Default)

Use both Photon and external services for maximum reliability.

**How it works:**
1. Photon is tried first for fast local geocoding
2. External services are used as fallback if Photon returns no results
3. Automatic failover ensures continuous operation

**Configuration:**
- Keep Photon service in docker-compose.yml
- Configure additional geocoding services in Settings → Geocoding
- Services are tried in order with automatic error handling

### Choosing the Right Option

| Requirement | Photon Only | External Only | Hybrid |
|-------------|-------------|---------------|--------|
| **Privacy** | ✅ Complete | ❌ Limited | ⚠️ Partial |
| **Performance** | ✅ Fastest | ❌ Network dependent | ✅ Fast with fallback |
| **Storage** | ❌ High (1-200GB) | ✅ None | ❌ High (1-200GB) |
| **Setup Time** | ❌ Hours to days | ✅ Immediate | ❌ Hours to days |
| **Reliability** | ⚠️ Single point | ⚠️ External dependency | ✅ Multiple sources |
| **Cost** | ✅ Free | ⚠️ May have limits | ✅ Free with backup |

### Initial Setup Considerations

**For Photon:**
- Plan for significant disk space (see storage requirements above)
- Initial data download can take hours to days depending on dataset size
- Consider starting with country-specific data and expanding later
- Monitor disk space during initial setup, especially with PARALLEL mode

**For External Services:**
- Configure multiple services for redundancy
- Check rate limits and usage policies
- Consider geographic coverage of different providers

## Technologies

- **Backend**: Spring Boot, Spring Data JPA, Spring Security
- **Database**: PostgreSQL with spatial extensions
- **Message Queue**: RabbitMQ for asynchronous processing
- **Frontend**: Thymeleaf, JavaScript
- **Testing**: JUnit 5, Testcontainers
- **Containerization**: Docker

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Getting support

There are multiple ways of getting support:

- either create a [new issue](https://github.com/dedicatedcode/reitti/issues/new/choose)
- tag me on [https://discuss.tchncs.de/u/danielgraf](Lemmy)
- or join **#reitti** on [irc.dedicatedcode.com](https://irc.dedicatedcode.com)

## Support the Project

<a href='https://ko-fi.com/K3K01HDAUW' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi6.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

## License

This project is licensed under the MIT License - see the LICENSE file for details.
