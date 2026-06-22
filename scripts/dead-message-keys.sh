#!/bin/bash

# Define paths
PROPERTIES_FILE="src/main/resources/messages.properties"
SOURCE_DIR="src/main"

echo "Scanning for unused keys..."
echo "------------------------------------------------"

# Extract all keys, ignoring comments and empty lines
keys=$(grep -v '^\s*[#!]' "$PROPERTIES_FILE" | grep -v '^\s*$' | cut -d'=' -f1 | sed 's/^[ \t]*//;s/[ \t]*$//')

for key in $keys; do
    # Check if the key is a frontend JS key
    if [[ "$key" == js.* ]]; then
        # Strip the "js." prefix
        search_term="${key#js.}"

        # Search for t('key') or t("key") using Extended Regex (-E)
        # We escape the parentheses and quotes for the regex engine
        if ! grep -rqE "\bt\(['\"]${search_term}['\"]" "$SOURCE_DIR"; then
            echo "[Frontend] Unused JS key : $key"
        fi
    else
        # It's a backend/Thymeleaf key.
        # Search for the literal key (e.g., inside #{my.key} or Java code)
        if ! grep -rq "$key" "$SOURCE_DIR"; then
            echo "[Backend]  Unused key    : $key"
        fi
    fi
done

echo "------------------------------------------------"
echo "Scan complete. Please manually verify before deleting!"