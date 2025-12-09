# GPX Test Data Generator

An interactive web-based tool for creating and editing GPX tracks for testing location-based applications.

## Features

- **Interactive Map Interface**: Click to add points, right-click to remove
- **View/Edit Modes**: Start in view mode, toggle to edit mode when needed
- **Realistic GPS Simulation**: Configurable speed, elevation, and GPS accuracy
- **Multiple Track Support**: Create and manage multiple tracks
- **Paint Mode**: Draw continuous tracks by dragging
- **Import/Export**: Load existing GPX files and export your creations
- **Responsive Design**: Works on desktop and mobile devices

## Docker Usage

### Build the container:
```bash
docker build -t gpx-generator .
```

### Run the container:
```bash
docker run -p 8080:80 gpx-generator
```

Then open http://localhost:8080 in your browser.

### Production deployment:
```bash
docker run -d --name gpx-generator -p 80:80 --restart unless-stopped gpx-generator
```

## Use Cases

- Testing location-based mobile applications
- Creating sample data for GPS analytics
- Simulating user movement patterns
- Generating test routes for navigation systems
- Educational purposes for GPS and mapping concepts

## Part of Reitti Project

This tool is part of the [Reitti project](https://github.com/dedicatedcode/reitti) - an open-source location tracking and analytics platform.

