/**
 * RawLocationLoader - Handles loading and displaying raw location data on the map
 */
class RawLocationLoader {
    constructor(map, userSettings) {
        this.map = map;
        this.userSettings = userSettings;
        this.rawPointPaths = [];
        this.pulsatingMarkers = [];
        this.currentZoomLevel = null;
        
        // Configuration for map bounds fitting
        this.fitToBoundsConfig = {
            paddingTopLeft: [100,0],
            paddingBottomRight: [100, 300],
            zoomSnap: 0.1
        };
        
        // Listen for map events
        this.setupMapEventListeners();
    }
    
    setupMapEventListeners() {
        // Listen for zoom end events to reload raw location points
        this.map.on('zoomend', () => {
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
    loadForDateRange(startDate, endDate, autoUpdateMode = false) {
        // Remove pulsating markers when loading new data
        this.removePulsatingMarkers();
        
        // Clear existing paths
        this.clearPaths();
        
        // Get raw location points URL from timeline container
        const timelineContainer = document.querySelectorAll('.user-timeline-section');
        let bounds = L.latLngBounds();
        const fetchPromises = [];

        for (let i = 0; i < timelineContainer.length; i++) {
            const element = timelineContainer[i];
            const rawLocationPointsUrl = element?.dataset.rawLocationPointsUrl;
            const color = element?.dataset.baseColor;
            if (rawLocationPointsUrl) {
                // Get current zoom level
                const currentZoom = Math.round(this.map.getZoom());
                
                // Get bounding box parameters
                const bbox = this.getBoundingBoxParams();
                
                // Build URL with zoom and bounding box parameters
                const separator = rawLocationPointsUrl.includes('?') ? '&' : '?';
                const urlWithParams = rawLocationPointsUrl + separator + 
                    'zoom=' + currentZoom +
                    '&minLat=' + bbox.minLat +
                    '&minLng=' + bbox.minLng +
                    '&maxLat=' + bbox.maxLat +
                    '&maxLng=' + bbox.maxLng;
                
                // Create fetch promise for raw location points with index to maintain order
                const fetchPromise = fetch(urlWithParams).then(response => {
                    if (!response.ok) {
                        console.warn('Could not fetch raw location points');
                        return { points: [], index: i, color: color };
                    }
                    return response.json();
                }).then(rawPointsData => {
                    return { ...rawPointsData, index: i, color: color };
                }).catch(error => {
                    console.warn('Error fetching raw location points:', error);
                    return { points: [], index: i, color: color };
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
                const fetchBounds = this.updateMapWithRawPoints(result, result.color, autoUpdateMode);
                if (fetchBounds.isValid()) {
                    bounds.extend(fetchBounds);
                }
            });
            
            // Update map bounds after all fetch operations are complete
            if (bounds.isValid()) {
                window.originalBounds = bounds;
                this.map.fitBounds(bounds, this.fitToBoundsConfig);
            }
        });
    }
    
    /**
     * Reload raw location points for the current map view
     */
    reloadForCurrentView() {
        // Reload raw location points with new zoom level
        const timelineContainer = document.querySelectorAll('.user-timeline-section');
        let bounds = L.latLngBounds();
        const fetchPromises = [];

        for (let i = 0; i < timelineContainer.length; i++) {
            const element = timelineContainer[i];
            const rawLocationPointsUrl = element?.dataset.rawLocationPointsUrl;
            const color = element?.dataset.baseColor;
            if (rawLocationPointsUrl) {
                // Get current zoom level
                const currentZoom = Math.round(this.map.getZoom());
                
                // Get bounding box parameters
                const bbox = this.getBoundingBoxParams();
                
                // Build URL with zoom and bounding box parameters
                const separator = rawLocationPointsUrl.includes('?') ? '&' : '?';
                const urlWithParams = rawLocationPointsUrl + separator + 
                    'zoom=' + currentZoom +
                    '&minLat=' + bbox.minLat +
                    '&minLng=' + bbox.minLng +
                    '&maxLat=' + bbox.maxLat +
                    '&maxLng=' + bbox.maxLng;
                
                // Create fetch promise for raw location points with index to maintain order
                const fetchPromise = fetch(urlWithParams).then(response => {
                    if (!response.ok) {
                        console.warn('Could not fetch raw location points');
                        return { points: [], index: i, color: color };
                    }
                    return response.json();
                }).then(rawPointsData => {
                    return { ...rawPointsData, index: i, color: color };
                }).catch(error => {
                    console.warn('Error fetching raw location points:', error);
                    return { points: [], index: i, color: color };
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
                const fetchBounds = this.updateMapWithRawPoints(result, result.color);
                if (fetchBounds.isValid()) {
                    bounds.extend(fetchBounds);
                }
            });
        });
    }
    
    /**
     * Update map with raw location points data
     */
    updateMapWithRawPoints(rawPointsData, color, autoUpdateMode = false) {
        const bounds = L.latLngBounds();

        if (rawPointsData && rawPointsData.segments && rawPointsData.segments.length > 0) {
            for (const segment of rawPointsData.segments) {
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
            // Find the corresponding timeline section to get user data
            const timelineContainers = document.querySelectorAll('.user-timeline-section');
            const timelineSection = timelineContainers[rawPointsData.index];
            if (timelineSection) {
                const userData = {
                    avatarUrl: timelineSection.dataset.userAvatarUrl,
                    avatarFallback: timelineSection.dataset.avatarFallback,
                    displayName: timelineSection.dataset.displayName
                };
                this.addAvatarMarker(latestPoint.latitude, latestPoint.longitude, userData);
            }
        }

        return bounds;
    }
    
    /**
     * Clear all raw location paths from the map
     */
    clearPaths() {
        for (const path of this.rawPointPaths) {
            path.remove();
        }
        this.rawPointPaths.length = 0;
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
        const today = new Date().toISOString().split('T')[0];
        const selectedDate = this.getSelectedDate();
        return selectedDate === today;
    }
    
    /**
     * Get the currently selected date
     */
    getSelectedDate() {
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.has('date')) {
            return urlParams.get('date');
        } else if (window.userSettings.newestData) {
            return window.userSettings.newestData.split('T')[0];
        } else {
            return new Date().toISOString().split('T')[0];
        }
    }
}
