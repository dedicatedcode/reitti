#!/bin/bash

# Script to generate contributors.json and translators.json for the acknowledgments page
# This script should be run before building the application

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOURCES_DIR="$SCRIPT_DIR/../src/main/resources"

# GitHub API settings
GITHUB_REPO="${GITHUB_REPOSITORY:-dedicatedcode/reitti}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"


echo "Generating acknowledgments data..."

# Function to fetch GitHub contributors
fetch_contributors() {
    echo "Fetching contributors from GitHub..."
    
    local api_url="https://api.github.com/repos/$GITHUB_REPO/contributors"
    local headers=""
    
    if [ -n "$GITHUB_TOKEN" ]; then
        headers="-H \"Authorization: token $GITHUB_TOKEN\""
    fi
    
    # Fetch contributors and format as JSON
    local contributors_data=$(eval "curl -s $headers \"$api_url\"" | jq '[
        .[] | 
        select(.type == "User") | 
        select(.login != "dependabot[bot]") |
        select(.login != "github-actions[bot]") |
        select(.login != "weblate") |
        select(.login != "dgraf-gh") |
        {
            name: (.name // .login),
            role: "Contributor",
            avatar: .avatar_url,
            github: .login,
            contributions: ["Code", "Documentation"]
        }
    ][:10]')  # Limit to top 10 contributors
    
    # Create contributors.json
    echo "{\"contributors\": $contributors_data}" > "$RESOURCES_DIR/contributors.json"
    echo "✓ Contributors data saved to contributors.json"
}


# Function to create projects.json with open source dependencies
create_projects_data() {
    echo "Creating projects acknowledgments..."
    
    local projects_json='[
        {
            "name": "Spring Boot",
            "description": "Java-based framework for building production-ready applications",
            "url": "https://spring.io/projects/spring-boot",
            "license": "Apache 2.0",
            "category": "Framework"
        },
        {
            "name": "PostgreSQL",
            "description": "Advanced open source relational database",
            "url": "https://www.postgresql.org/",
            "license": "PostgreSQL License",
            "category": "Database"
        },
        {
            "name": "Leaflet",
            "description": "Open-source JavaScript library for mobile-friendly interactive maps",
            "url": "https://leafletjs.com/",
            "license": "BSD-2-Clause",
            "category": "Frontend"
        },
        {
            "name": "HTMX",
            "description": "High power tools for HTML",
            "url": "https://htmx.org/",
            "license": "BSD-2-Clause",
            "category": "Frontend"
        },
        {
            "name": "Thymeleaf",
            "description": "Modern server-side Java template engine",
            "url": "https://www.thymeleaf.org/",
            "license": "Apache 2.0",
            "category": "Template Engine"
        },
        {
            "name": "Chart.js",
            "description": "Simple yet flexible JavaScript charting library",
            "url": "https://www.chartjs.org/",
            "license": "MIT",
            "category": "Frontend"
        }
    ]'
    
    echo "{\"projects\": $projects_json}" > "$RESOURCES_DIR/projects.json"
    echo "✓ Projects data saved to projects.json"
}

# Main execution
main() {
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        echo "Error: jq is required but not installed."
        exit 1
    fi
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        echo "Error: curl is required but not installed."
        exit 1
    fi
    
    # Create resources directory if it doesn't exist
    mkdir -p "$RESOURCES_DIR"
    
    # Generate all acknowledgment files
    fetch_contributors
    create_projects_data
    
    echo "✅ Acknowledgments data generation completed!"
    echo "Generated files:"
    echo "  - $RESOURCES_DIR/contributors.json"
    echo "  - $RESOURCES_DIR/projects.json"
}

# Run main function
main "$@"
