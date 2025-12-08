/**
 * RawLocationLoader - Handles loading and displaying raw location data on the map
 */
class RawLocationLoader {
    constructor(map, userSettings, fitToBoundsConfig = null) {
        this.map = map;
        this.userSettings = userSettings;
        this.rawPointPaths = [];
        this.selectedRangePaths = [];
        this.pulsatingMarkers = [];
        this.currentZoomLevel = null;
        this.userConfigs = [];
        this.isFittingBounds = false;
        this.allSegments = []; // Store all loaded segments
        this.selectedStartTime = null;
        this.selectedEndTime = null;
        // Configuration for map bounds fitting
        this.fitToBoundsConfig = fitToBoundsConfig || {};
        // Listen for map events
        this.setupMapEventListeners();
    }
    
    /**
     * Initialize with user configurations
     * @param {Array} userConfigs - Array of user configuration objects
     * Each config should contain: { url, color, avatarUrl, avatarFallback, displayName }
     */
    init(userConfigs) {
        this.userConfigs = userConfigs || [];
    }
    
    setupMapEventListeners() {
        // Listen for zoom end events to reload raw location points
        this.map.on('zoomend', () => {
            if (this.isFittingBounds) return;
            const newZoomLevel = Math.round(this.map.getZoom());
            
            // Only reload if zoom level actually changed
            if (this.currentZoomLevel !== null && this.currentZoomLevel !== newZoomLevel) {
                console.log('Zoom level changed from', this.currentZoomLevel, 'to', newZoomLevel, '- reloading raw location points');
                this.currentZoomLevel = newZoomLevel;
                this.reloadForCurrentView();
            } else if (this.currentZoomLevel === null) {
                this.currentZoomLevel = newZoomLevel;
            }
        });
        
        // Listen for move end events to reload raw location points
        this.map.on('moveend', () => {
            if (this.isFittingBounds) return;
            this.reloadForCurrentView();
        });
    }
    
    /**
     * Get bounding box parameters for the current map view with buffer
     */
    getBoundingBoxParams() {
        const bounds = this.map.getBounds();
        const southWest = bounds.getSouthWest();
        const northEast = bounds.getNorthEast();
        
        // Calculate buffer as 20% of the current viewport size
        const latBuffer = (northEast.lat - southWest.lat) * 0.2;
        const lngBuffer = (northEast.lng - southWest.lng) * 0.2;
        
        return {
            minLat: southWest.lat - latBuffer,
            minLng: southWest.lng - lngBuffer,
            maxLat: northEast.lat + latBuffer,
            maxLng: northEast.lng + lngBuffer
        };
    }
    
    /**
     * Load raw location data for a specific date range
     */
    loadForDateRange(autoUpdateMode = false, withBounds = true) {
        // Remove pulsating markers when loading new data
        this.removePulsatingMarkers();
        
        // Clear existing paths
        this.clearPaths();
        
        let bounds = L.latLngBounds();
        const fetchPromises = [];

        for (let i = 0; i < this.userConfigs.length; i++) {
            const config = this.userConfigs[i];
            if (config.url) {
                // Get current zoom level
                const currentZoom = Math.round(this.map.getZoom());
                
                // Get bounding box parameters
                const bbox = this.getBoundingBoxParams();
                
                // Build URL with zoom and bounding box parameters
                const separator = config.url.includes('?') ? '&' : '?';
                let urlWithParams = config.url + separator +
                    'zoom=' + currentZoom;
                if (config.respectBounds && withBounds) {
                    urlWithParams +=
                        '&minLat=' + bbox.minLat +
                        '&minLng=' + bbox.minLng +
                        '&maxLat=' + bbox.maxLat +
                        '&maxLng=' + bbox.maxLng;
                }
                
                // Create fetch promise for raw location points with index to maintain order
                const fetchPromise = fetch(urlWithParams).then(response => {
                    if (!response.ok) {
                        console.warn('Could not fetch raw location points');
                        return { points: [], index: i, config: config };
                    }
                    return response.json();
                }).then(rawPointsData => {
                    return { ...rawPointsData, index: i, config: config };
                }).catch(error => {
                    console.warn('Error fetching raw location points:', error);
                    return { points: [], index: i, config: config };
                });
                
                fetchPromises.push(fetchPromise);
            }
        }

        // Wait for all fetch operations to complete, then update map in correct order
        Promise.all(fetchPromises).then(results => {
            // Sort results by original index to maintain order
            results.sort((a, b) => a.index - b.index);
            
            // Process results in order
            results.forEach(result => {
                const fetchBounds = this.updateMapWithRawPoints(result, result.config.color, autoUpdateMode);
                if (fetchBounds.isValid()) {
                    bounds.extend(fetchBounds);
                }
            });
            
            // Update map bounds after all fetch operations are complete
            if (bounds.isValid()) {
                window.originalBounds = bounds;

                this.isFittingBounds = true;
                this.map.fitBounds(bounds, this.fitToBoundsConfig);
                this.isFittingBounds = false;
            }
        });
    }
    
    /**
     * Reload raw location points for the current map view
     */
    reloadForCurrentView(withBounds = true) {
        let bounds = L.latLngBounds();
        const fetchPromises = [];
        const color = this.userConfigs[0].color;
        for (let i = 0; i < this.userConfigs.length; i++) {
            const config = this.userConfigs[i];
            if (config.url) {
                // Get current zoom level
                const currentZoom = Math.round(this.map.getZoom());
                
                // Get bounding box parameters
                const bbox = this.getBoundingBoxParams();
                
                // Build URL with zoom and bounding box parameters
                const separator = config.url.includes('?') ? '&' : '?';
                const urlWithParams = config.url + separator + 
                    'zoom=' + currentZoom + (config.respectBounds && withBounds ? ('&minLat=' + bbox.minLat +'&minLng=' + bbox.minLng +'&maxLat=' + bbox.maxLat +'&maxLng=' + bbox.maxLng) : '');
                
                // Create fetch promise for raw location points with index to maintain order
                const fetchPromise = fetch(urlWithParams).then(response => {
                    if (!response.ok) {
                        console.warn('Could not fetch raw location points');
                        return { points: [], index: i, config: config };
                    }
                    return response.json();
                }).then(rawPointsData => {
                    return { ...rawPointsData, index: i, config: config };
                }).catch(error => {
                    console.warn('Error fetching raw location points:', error);
                    return { points: [], index: i, config: config };
                });
                
                fetchPromises.push(fetchPromise);
            }
        }

        // Wait for all fetch operations to complete, then update map in correct order
        Promise.all(fetchPromises).then(results => {
            this.clearPaths();

            results.sort((a, b) => a.index - b.index);
            
            // Process results in order
            results.forEach(result => {
                const fetchBounds = this.updateMapWithRawPoints(result, result.config.color);
                if (fetchBounds.isValid()) {
                    bounds.extend(fetchBounds);
                }
            });
            
            // Re-render selected range if it exists
            if (this.selectedStartTime && this.selectedEndTime) {
                this.renderSelectedRange(color);
            }
        });
    }
    
    /**
     * Update map with raw location points data
     */
    updateMapWithRawPoints(rawPointsData, color, autoUpdateMode = false) {
        const bounds = L.latLngBounds();

        if (rawPointsData && rawPointsData.segments && rawPointsData.segments.length > 0) {
            // Store segments with metadata for later filtering
            for (const segment of rawPointsData.segments) {
                const segmentWithMetadata = {
                    ...segment,
                    userConfig: rawPointsData.config,
                    color: color == null ? '#f1ba63' : color
                };
                this.allSegments.push(segmentWithMetadata);
                
                const rawPointsPath = L.geodesic([], {
                    color: color == null ? '#f1ba63' : color,
                    weight: 6,
                    opacity: 0.9,
                    lineJoin: 'round',
                    lineCap: 'round',
                    steps: 2
                });
                const rawPointsCoords = segment.points.map(point => [point.latitude, point.longitude]);
                bounds.extend(rawPointsCoords)
                rawPointsPath.setLatLngs(rawPointsCoords);
                rawPointsPath.addTo(this.map);
                this.rawPointPaths.push(rawPointsPath)
            }
        }

        // Add avatar marker for the latest point if in auto-update mode and today is selected
        if (autoUpdateMode && this.isSelectedDateToday() && rawPointsData.latest) {
            const latestPoint = rawPointsData.latest;
            const config = rawPointsData.config;
            if (config) {
                const userData = {
                    avatarUrl: config.avatarUrl,
                    avatarFallback: config.avatarFallback,
                    displayName: config.displayName
                };
                this.addAvatarMarker(latestPoint.latitude, latestPoint.longitude, userData);
            }
        }

        // Re-render selected range if it exists
        if (this.selectedStartTime && this.selectedEndTime) {
            this.renderSelectedRange(color);
        }

        return bounds;
    }
    
    /**
     * Set selected time range for highlighting specific segments
     */
    setSelectedTimeRange(startTime, endTime) {
        this.selectedStartTime = startTime;
        this.selectedEndTime = endTime;
        
        // Reload data without bounds to get the complete path for the selected range
        return this.reloadForSelectedRange();
    }
    
    /**
     * Reload raw location points specifically for selected range (without bounds)
     */
    reloadForSelectedRange() {
        let bounds = L.latLngBounds();
        const fetchPromises = [];
        const mainColor = this.userConfigs[0].color;
        for (let i = 0; i < this.userConfigs.length; i++) {

            const config = this.userConfigs[i];
            if (config.url) {
                // Get current zoom level
                const currentZoom = Math.round(this.map.getZoom());
                
                // Build URL without bounding box parameters to get complete path
                const separator = config.url.includes('?') ? '&' : '?';
                const urlWithParams = config.url + separator + 'zoom=' + currentZoom;
                
                // Create fetch promise for raw location points with index to maintain order
                const fetchPromise = fetch(urlWithParams).then(response => {
                    if (!response.ok) {
                        console.warn('Could not fetch raw location points');
                        return { points: [], index: i, config: config };
                    }
                    return response.json();
                }).then(rawPointsData => {
                    return { ...rawPointsData, index: i, config: config };
                }).catch(error => {
                    console.warn('Error fetching raw location points:', error);
                    return { points: [], index: i, config: config };
                });
                
                fetchPromises.push(fetchPromise);
            }
        }

        // Wait for all fetch operations to complete, then update map in correct order
        return Promise.all(fetchPromises).then(results => {
            this.clearPaths();

            results.sort((a, b) => a.index - b.index);
            
            // Process results in order
            results.forEach(result => {
                const fetchBounds = this.updateMapWithRawPoints(result, result.config.color);
                if (fetchBounds.isValid()) {
                    bounds.extend(fetchBounds);
                }
            });
            
            // Render selected range and return its bounds
            const selectedRangeBounds = this.renderSelectedRange(mainColor);
            if (selectedRangeBounds && selectedRangeBounds.isValid()) {
                return selectedRangeBounds;
            }
            
            return bounds;
        });
    }
    
    /**
     * Clear selected time range
     */
    clearSelectedTimeRange() {
        this.selectedStartTime = null;
        this.selectedEndTime = null;
        this.clearSelectedRangePaths();
        // Reload with bounds to return to normal view
        this.reloadForCurrentView(true);
    }
    
    /**
     * Render segments within the selected time range with different color
     */
    renderSelectedRange(color) {
        // Clear existing selected range paths
        this.clearSelectedRangePaths();
        
        if (!this.selectedStartTime || !this.selectedEndTime) {
            return L.latLngBounds();
        }
        
        const bounds = L.latLngBounds();
        // Extend the time range by 2 minutes (120,000 milliseconds) on each side
        const startTimeUtc = new Date(this.selectedStartTime).getTime() - 150000;
        const endTimeUtc = new Date(this.selectedEndTime).getTime() + 150000;
        
        // Filter segments that fall within the selected time range
        for (const segment of this.allSegments) {
            if (segment.points && segment.points.length > 0) {
                // Filter points within the time range using UTC timestamps
                const filteredPoints = segment.points.filter(point => {
                    const pointTimeUtc = new Date(point.timestamp).getTime();
                    return pointTimeUtc >= startTimeUtc && pointTimeUtc <= endTimeUtc;
                });
                
                // Only render if we have points within the time range
                if (filteredPoints.length > 0) {
                    const selectedPath = L.geodesic([], {
                        color: lightenHexColor(color, 100), // Orange color for selected range
                        weight: 8,
                        opacity: 1,
                        lineJoin: 'round',
                        lineCap: 'round',
                        steps: 2
                    });
                    
                    const coords = filteredPoints.map(point => [point.latitude, point.longitude]);
                    bounds.extend(coords);
                    selectedPath.setLatLngs(coords);
                    selectedPath.addTo(this.map);
                    this.selectedRangePaths.push(selectedPath);
                }
            }
        }
        
        return bounds;
    }
    
    /**
     * Clear selected range paths from the map
     */
    clearSelectedRangePaths() {
        for (const path of this.selectedRangePaths) {
            path.remove();
        }
        this.selectedRangePaths.length = 0;
    }
    
    /**
     * Clear all raw location paths from the map
     */
    clearPaths() {
        for (const path of this.rawPointPaths) {
            path.remove();
        }
        this.rawPointPaths.length = 0;
        this.allSegments.length = 0; // Clear stored segments
    }
    
    /**
     * Add an avatar marker at the specified coordinates
     */
    addAvatarMarker(lat, lng, userData) {
        // Create avatar marker with user's avatar
        const avatarHtml = `
            <div class="avatar-marker">
                <img src="${userData.avatarUrl}" 
                     alt="${userData.avatarFallback}" 
                     class="avatar-marker-img">
            </div>
        `;
        
        const avatarIcon = L.divIcon({
            className: 'avatar-marker-icon',
            html: avatarHtml,
            iconSize: [40, 40],
            iconAnchor: [20, 20]
        });

        const avatarMarker = L.marker([lat, lng], {icon: avatarIcon}).addTo(this.map);

        // Add tooltip
        avatarMarker.bindTooltip(`${window.locale.autoupdate.latestLocation} - ${userData.displayName}`, {
            permanent: false,
            direction: 'top'
        });

        // Store the marker for cleanup later
        this.pulsatingMarkers.push(avatarMarker);
    }
    
    /**
     * Remove all pulsating markers from the map
     */
    removePulsatingMarkers() {
        this.pulsatingMarkers.forEach(marker => {
            if (marker) {
                this.map.removeLayer(marker);
            }
        });
        this.pulsatingMarkers = [];
    }
    
    /**
     * Check if the selected date is today
     */
    isSelectedDateToday() {
        const today = getCurrentLocalDate();
        const selectedDate = this.getSelectedDate();
        return selectedDate === today;
    }
    
    /**
     * Get the currently selected date
     */
    getSelectedDate() {
        debugger
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.has('date')) {
            return urlParams.get('date');
        } else {
            return getCurrentLocalDate();
        }
    }
}
