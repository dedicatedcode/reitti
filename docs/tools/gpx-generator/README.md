# GPX Test Data Generator

A web-based tool for generating GPX test data for location tracking applications. This tool allows you to create realistic GPS tracks by clicking on a map, with features for simulating GPS accuracy, elevation changes, and realistic movement patterns.

## Features

- **Interactive Map**: Click to add GPS points with real-time preview
- **Auto Mode**: Automatically interpolates points to maintain realistic speeds
- **Paint Mode**: Draw continuous tracks by moving the mouse
- **GPS Simulation**: Add realistic GPS noise and accuracy simulation
- **Elevation Control**: Set base elevation with optional variation
- **Time Management**: Configure timestamps and intervals between points
- **Speed Validation**: Visual indicators for realistic vs unrealistic speeds
- **Export**: Generate standard GPX files for testing

## Running the Tool

### Option 1: Direct File Access
Simply open `index.html` in your web browser. Note that some features may be limited due to browser security restrictions when accessing files directly.

### Option 2: Python HTTP Server (Recommended)
For full functionality, serve the files through a local HTTP server:

```bash
# Navigate to the gpx-generator directory
cd docs/tools/gpx-generator

# Python 3
python -m http.server 8000

# Python 2 (if needed)
python -m SimpleHTTPServer 8000
```

Then open your browser and navigate to:
```
http://localhost:8000
```

### Option 3: Other HTTP Servers
You can use any other HTTP server:

```bash
# Node.js (if you have npx)
npx serve .
```
```bash

# PHP (if available)
php -S localhost:8000
```
```bash

# Ruby (if available)
ruby -run -e httpd . -p 8000
```

## Usage

1. **Set Parameters**: Configure start date/time, intervals, and GPS settings
2. **Add Points**: Click on the map to add GPS points
3. **Use Auto Mode**: Enable for automatic speed-limited interpolation
4. **Use Paint Mode**: Enable to draw tracks by moving the mouse
5. **Adjust Settings**: Modify elevation, GPS accuracy, and speed limits
6. **Export**: Generate GPX file for testing your application

## Controls

- **Left Click**: Add point (or start/stop painting in paint mode)
- **Right Click**: Remove point
- **Mouse Hover**: Preview next point with distance and speed info
- **Auto Mode**: Automatically adds intermediate points to maintain realistic speeds
- **Paint Mode**: Continuous point addition while moving mouse

## GPX Output

The generated GPX files include:
- Accurate latitude/longitude coordinates
- Elevation data with optional variation
- Realistic timestamps based on your settings
- Standard GPX 1.1 format compatible with most applications

## Browser Compatibility

- Chrome/Chromium (recommended)
- Firefox
- Safari
- Edge

Requires JavaScript enabled and modern browser features (ES6+).

