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

## Development

To run the GPX generator locally for development or testing, you can use a simple HTTP server.

### Using Python (easiest, works on most systems)

If you have Python installed (Python 3 is recommended), navigate to the project directory and run:

```bash
# For Python 3
python3 -m http.server 8080

# Or for Python 2
python -m SimpleHTTPServer 8080
```

Then open http://localhost:8080 in your browser.

### Using Node.js

If you have Node.js installed, you can use the `http-server` package:

```bash
# Install http-server globally (if not already installed)
npm install -g http-server

# Run from the project directory
http-server -p 8080
```

Then open http://localhost:8080 in your browser.

### Using PHP

If you have PHP installed:

```bash
php -S localhost:8080
```

Then open http://localhost:8080 in your browser.

## Docker Usage

### Run the container:
```bash
docker run -p 8080:80 dedicatedcode/reitti-gpx-generator
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

