# GPX Sender Tool

A CLI utility to send GPX track data to a Reitti instance via the Owntracks ingest API.

## Building

```bash
cd docs/tools/gpx-sender
mvn clean package
```

## Usage

```bash
java -jar target/gpx-sender-1.0.0.jar <gpx-file> --url <reitti-url> --token <api-token> [--interval <seconds>]
```

### Parameters

- `gpx-file`: Path to the GPX file containing track points (positional parameter)
- `--url`: Base URL of the Reitti instance (e.g., `http://localhost:8080`)
- `--token`: API token for authentication
- `--interval`: Optional interval between sending points (default: 15 seconds)

### Example

```bash
java -jar target/gpx-sender-1.0.0.jar my-track.gpx --url http://localhost:8080 --token your-api-token --interval 10
```

## How it works

1. Parses the GPX file to extract track points with coordinates and timestamps
2. Starts sending from the current time, with each subsequent point sent at the specified interval
3. Converts each point to Owntracks format and sends via HTTP POST to `/api/v1/ingest/owntracks`
4. Waits the specified interval between each point transmission

## Requirements

- Java 17 or higher
- Valid API token for the Reitti instance
- GPX file with track points
