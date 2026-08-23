# Reitti – Personal Location Tracking & Analysis

**Reitti** is a self-hosted personal location tracking and analysis application that helps you understand your movement
patterns and significant places. The name comes from Finnish, meaning *"route"* or *"path"*.

Your location data never leaves your server. Reitti is fully self-hosted, multi-user capable and supports OIDC /
single sign-on.

## Features

- **Visit & Trip Detection** – automatically identifies places you spend time at and tracks movements between them,
  including transport-mode detection (walking, cycling, driving)
- **Interactive Timeline & Map** – daily timeline showing visits and trips with duration and distance info
- **Significant Places** – recognizes and names the locations you visit frequently
- **Devices & Workbench** – track multiple devices per user; drag misplaced GPS points to their correct location or
  delete outliers directly on the map
- **Live Location Sharing** – follow family and friends on a single map, across instances (federation) or via revocable
  magic links
- **Custom Map Styles** – upload your own style files or link to remote ones (e.g. MapTiler, Stadia Maps or your own
  tile server)
- **Photos** – Immich integration; photos appear on your timeline at the locations where they were taken
- **Statistics** – distance charts, top places and transport-mode breakdowns

## Quick Start

The fastest way to try Reitti is with Docker Compose:

```bash
mkdir reitti && cd reitti
wget https://raw.githubusercontent.com/dedicatedcode/reitti/refs/heads/main/docker-compose.yml
docker compose up -d
```

Then open **http://localhost:8080**. On first login you'll be prompted to set an admin password. A default API token is
created automatically, so you can jump straight into connecting your devices.

The stack consists of Reitti, PostgreSQL with PostGIS, Redis and an optional tile cache
(`dedicatedcode/reitti-tile-cache`). If you don't want the tile cache, remove that service from the compose file and set
the environment variable `TILES_CACHE` to an empty value on the Reitti service.

> **ARM64 users** (Apple Silicon, etc.):
> Until [postgis/docker-postgis#216](https://github.com/postgis/docker-postgis/issues/216) is resolved, change the
> PostGIS image in the compose file to `imresamu/postgis:17-3.5-alpine`.

## Image Tags

| Tag      | Stability     | Use case                                                                                                                                                    |
|----------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `latest` | Stable        | Points to the most recent stable release.                                                                                                                    |
| `5`      | Stable        | Latest release within a major version – bug fixes and minor features without the risk of a major version jump. **Recommended for most self-hosters.**        |
| `x.y.z`  | Stable        | Pinned specific release (e.g. `5.0.2`) for reproducible deployments and full control over upgrades.                                                          |
| `develop`| Bleeding edge | Rebuilt on every push to `main`. For testing upcoming changes and contributing – expect occasional rough edges.                                              |
| `next`   | Alpha         | Next major version in development. **Do not use in production** – database schema may change in incompatible ways.                                           |

## Data Import & Integrations

Reitti imports GPX files, Google Takeout / Timeline exports and GeoJSON, and ingests real-time locations from OwnTracks,
GPSLogger, Overland and Home Assistant.

- File import: [Data Import Guide](https://www.dedicatedcode.com/projects/reitti/latest/usage/import-data/)
- Real-time tracking: [Mobile App Guide](https://www.dedicatedcode.com/projects/reitti/latest/integrations/mobile-apps/)
- Custom integrations: [Ingest API](https://www.dedicatedcode.com/projects/reitti/latest/api/ingest/)

## Configuration

Reitti works out of the box with the provided compose file. All further configuration happens via environment variables.
For the full list of variables, OIDC setup, reverse proxy and other deployment options, see the
[Infrastructure Documentation](https://www.dedicatedcode.com/projects/reitti/latest/infrastructure/docker-config/).

## Backup

Back up the PostGIS database **and** the Reitti storage volume regularly. Redis and other stateless services do not need
to be backed up. See the [Backup Guide](https://www.dedicatedcode.com/projects/reitti/latest/backup/) for details.

## More

- Source code: [github.com/dedicatedcode/reitti](https://github.com/dedicatedcode/reitti)
- Documentation: [dedicatedcode.com/projects/reitti](https://www.dedicatedcode.com/projects/reitti/)
- Support: [open an issue](https://github.com/dedicatedcode/reitti/issues/new/choose)

## License

This project is licensed under the **GNU Affero General Public License v3.0** (AGPL-3.0).
