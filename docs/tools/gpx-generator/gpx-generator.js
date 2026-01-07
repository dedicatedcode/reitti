// GPX Generator JavaScript
let map;
let tracks = []; // Array of track objects
let currentTrackIndex = 0;
let polylines = [];
let markers = []; // Keep for compatibility, but will use canvas
let markerCanvas;
let markerCanvasLayer;
let hoverTooltip;
let previewLine;
let paintMode = false;
let paintActive = false; // Whether painting is currently active
let lastPaintTime = 0;
let paintThrottleMs = 100; // Minimum time between paint points
let paintInterval = null; // Interval for automatic painting
let lastMousePosition = null; // Store last mouse position for painting
let currentMapLayer = 'street'; // 'street' or 'satellite'
let streetLayer, satelliteLayer;
let stopProbability = 0.05; // 5% chance of adding a stop per point when auto-stops enabled

// Google JSON Import State
let pendingJsonData = null;
let pendingJsonFilename = "";
let selectedDates = new Set();
let lastClickedDate = null;

// Track colors for visual distinction
const TRACK_COLORS = [
    '#3498db', '#e74c3c', '#2ecc71', '#f39c12', '#9b59b6',
    '#1abc9c', '#34495e', '#e67e22', '#95a5a6', '#d35400'
];

// Global edit mode state
let editModeEnabled = false;

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    initializeTheme();
    initializeMap();
    initializeDateTime();
    initializeControls();
    initializeKeyboardShortcuts();
    createNewTrack(); // Create the first track
    updateStatus();
    
    // Start in view mode (edit disabled)
    editModeEnabled = false;
});

// Functions to control edit mode from HTML
window.enableEditMode = function() {
    editModeEnabled = true;
    updateStatus(); // Update status when edit mode changes
};

window.disableEditMode = function() {
    editModeEnabled = false;
    // Stop paint mode when disabling edit mode
    if (paintMode) {
        paintMode = false;
        paintActive = false;
        stopAutoPainting();
        updatePaintModeButton();
        if (map) {
            map.getContainer().style.cursor = '';
        }
    }
    updateStatus(); // Update status when edit mode changes
};

function initializeTheme() {
    // Check for saved theme preference or default to light mode
    const savedTheme = localStorage.getItem('gpx-generator-theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
    updateThemeButton();
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('gpx-generator-theme', newTheme);
    updateThemeButton();
}

function updateThemeButton() {
    const button = document.getElementById('themeToggle');
    const currentTheme = document.documentElement.getAttribute('data-theme');
    button.textContent = currentTheme === 'dark' ? '☀️' : '🌙';
}

function toggleMapLayer() {
    if (currentMapLayer === 'street') {
        map.removeLayer(streetLayer);
        map.addLayer(satelliteLayer);
        currentMapLayer = 'satellite';
        document.getElementById('layerToggle').textContent = 'Street';
    } else {
        map.removeLayer(satelliteLayer);
        map.addLayer(streetLayer);
        currentMapLayer = 'street';
        document.getElementById('layerToggle').textContent = 'Satellite';
    }
}

function initializeMap() {
    // Initialize Leaflet map
    map = L.map('map').setView([60.1699, 24.9384], 10); // Helsinki as default
    
    // Create map layers
    streetLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    });

    satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: '© Esri, Maxar, Earthstar Geographics'
    });

    // Add default layer
    streetLayer.addTo(map);
    
    // Initialize canvas marker layer
    initializeCanvasMarkers();
    
    // Add event listeners
    map.on('click', onMapClick);
    map.on('mousemove', onMapMouseMove);
    map.on('mouseout', onMapMouseOut);
    map.on('zoom', redrawMarkers);
    map.on('move', redrawMarkers);
    
    // Polylines will be created per track
    polylines = [];
    
    // Initialize preview line
    previewLine = L.polyline([], {
        color: '#95a5a6',
        weight: 2,
        opacity: 0.5,
        dashArray: '5, 5'
    }).addTo(map);
    
    // Create hover tooltip
    hoverTooltip = document.createElement('div');
    hoverTooltip.className = 'hover-tooltip';
    hoverTooltip.style.display = 'none';
    document.body.appendChild(hoverTooltip);
}

function initializeDateTime() {
    // Set default datetime to current time in user's locale
    const now = new Date();
    // Format for datetime-local input (YYYY-MM-DDTHH:mm:ss)
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    
    const datetimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
    document.getElementById('startDateTime').value = datetimeString;
}

function initializeControls() {
    // Update accuracy value display when slider changes
    const accuracySlider = document.getElementById('accuracySlider');
    const accuracyValue = document.getElementById('accuracyValue');
    
    accuracySlider.addEventListener('input', function() {
        accuracyValue.textContent = this.value;
    });
}

function initializeKeyboardShortcuts() {
    document.addEventListener('keydown', function(e) {
        // Only handle shortcuts when paint mode is active
        if (!paintMode) return;
        
        // Prevent default behavior for + and - keys to avoid Leaflet zoom conflicts
        if (e.key === '+' || e.key === '=' || e.key === '-' || e.key === '_') {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            
            const maxSpeedInput = document.getElementById('maxSpeed');
            let currentSpeed = parseFloat(maxSpeedInput.value);
            let newSpeed = currentSpeed;
            let increment = 5;
            
            // Use larger increment with Shift key
            if (e.shiftKey) {
                increment = 10;
            }
            
            if (e.key === '+' || e.key === '=') {
                newSpeed = Math.min(currentSpeed + increment, 300); // Max 300 km/h
            } else if (e.key === '-' || e.key === '_') {
                newSpeed = Math.max(currentSpeed - increment, 1); // Min 1 km/h to prevent browser issues
            }
            
            if (newSpeed !== currentSpeed) {
                maxSpeedInput.value = newSpeed;
                showSpeedChangeNotification(newSpeed);
            }
            
            return false;
        }
    }, true); // Use capture phase to intercept before Leaflet
}

function showSpeedChangeNotification(speed) {
    // Remove existing notification if present
    const existingNotification = document.querySelector('.speed-notification');
    if (existingNotification) {
        existingNotification.remove();
    }
    
    // Create notification element
    const notification = document.createElement('div');
    notification.className = 'speed-notification';
    notification.textContent = `Max Speed: ${speed} km/h`;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: rgba(26, 32, 44, 0.95);
        color: white;
        padding: 12px 16px;
        border-radius: 8px;
        font-size: 14px;
        font-weight: 500;
        z-index: 10000;
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.1);
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
        transition: opacity 0.3s ease;
    `;
    
    document.body.appendChild(notification);
    
    // Auto-remove after 2 seconds
    setTimeout(() => {
        notification.style.opacity = '0';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.remove();
            }
        }, 300);
    }, 2000);
}

function createNewTrack(name = null, startTime = null) {
    const trackIndex = tracks.length;
    const color = TRACK_COLORS[trackIndex % TRACK_COLORS.length];
    
    // Collapse all existing tracks
    tracks.forEach(track => {
        track.collapsed = true;
    });
    
    // Set start time based on last point of previous track or current time
    let trackStartTime = startTime || new Date();
    if (!startTime && tracks.length > 0) {
        const lastTrack = tracks[tracks.length - 1];
        if (lastTrack.points.length > 0) {
            const lastPoint = lastTrack.points[lastTrack.points.length - 1];
            const timeInterval = parseInt(document.getElementById('timeInterval').value);
            trackStartTime = new Date(lastPoint.timestamp.getTime() + (timeInterval * 1000));
        }
    }
    
    const track = {
        id: trackIndex,
        name: name || `Track ${trackIndex + 1}`,
        points: [],
        color: color,
        collapsed: false,
        startTime: trackStartTime
    };
    
    tracks.push(track);
    currentTrackIndex = trackIndex;
    
    // Create polyline for this track
    const polyline = L.polyline([], {
        color: color,
        weight: 3,
        opacity: 0.7
    }).addTo(map);
    polylines.push(polyline);
    
    updatePointsList();
    updateStatus();
    return track;
}

function newTrack() {
    createNewTrack();
}

function onMapClick(e) {
    // Prevent point addition if edit mode is disabled
    if (!editModeEnabled) {
        return;
    }
    
    if (paintMode) {
        // Toggle paint active state
        paintActive = !paintActive;
        lastMousePosition = e.latlng; // Store initial position

        if (paintActive) {
            // Add first point immediately
            const currentTrack = tracks[currentTrackIndex];
            if (currentTrack && currentTrack.points.length > 0) {
                addPointWithInterpolation(lastMousePosition.lat, lastMousePosition.lng);
            } else {
                addPoint(lastMousePosition.lat, lastMousePosition.lng);
            }
            // Start automatic painting
            startAutoPainting();
        } else {
            // Stop automatic painting
            stopAutoPainting();
        }

        updatePaintModeButton();
        return;
    }
    
    const currentTrack = tracks[currentTrackIndex];
    if (currentTrack && currentTrack.points.length > 0) {
        addPointWithInterpolation(e.latlng.lat, e.latlng.lng);
    } else {
        addPoint(e.latlng.lat, e.latlng.lng);
    }
}

function addPoint(lat, lng, options = {}) {
    if (tracks.length === 0) {
        createNewTrack();
    }
    
    const currentTrack = tracks[currentTrackIndex];
    const pointIndex = currentTrack.points.length;
    
    // Use provided timestamp or current datetime input value
    let timestamp = options.timestamp;
    if (!timestamp) {
        const startDateTimeValue = document.getElementById('startDateTime').value;
        timestamp = new Date(startDateTimeValue);
    }
    
    // Check if we need to create a new track due to day change
    if (!options.skipDayChangeCheck && shouldCreateNewTrackForDayChange(timestamp, currentTrack)) {
        createNewTrack();
        return addPoint(lat, lng, options); // Recursively add to new track
    }
    
    // Check for realistic stops
    if (!options.skipStops && document.getElementById('autoStops').checked && shouldAddStop(currentTrack)) {
        timestamp = addRealisticStop(timestamp);
    }

    // Apply GPS accuracy simulation
    const accuracy = options.accuracy !== undefined ? options.accuracy : parseFloat(document.getElementById('accuracySlider').value);
    const adjustedCoords = options.skipNoise ? {lat, lng} : applyGPSNoise(lat, lng, accuracy);
    
    // Calculate elevation with variation
    let elevation = options.elevation;
    if (elevation === undefined) {
        const baseElevation = parseFloat(document.getElementById('elevation').value);
        const elevationVariation = parseFloat(document.getElementById('elevationVariation').value);
        elevation = baseElevation + (Math.random() - 0.5) * 2 * elevationVariation;
    }
    
    const point = {
        id: pointIndex,
        trackId: currentTrackIndex,
        lat: adjustedCoords.lat,
        lng: adjustedCoords.lng,
        originalLat: lat,
        originalLng: lng,
        timestamp: timestamp,
        elevation: elevation,
        accuracy: accuracy
    };
    
    currentTrack.points.push(point);
    
    // Store marker data for canvas rendering
    const markerData = {
        lat: adjustedCoords.lat,
        lng: adjustedCoords.lng,
        color: currentTrack.color,
        trackIndex: currentTrackIndex,
        pointIndex: pointIndex,
        title: `${currentTrack.name} - Point ${pointIndex + 1}`
    };
    
    markers.push(markerData);
    
    // Update polyline for current track
    updatePolyline(currentTrackIndex);
    
    // Redraw markers to include the new point
    redrawMarkers();
    
    // Update datetime input to next interval for next point
    if (!options.skipTimeUpdate) {
        const timeInterval = parseInt(document.getElementById('timeInterval').value);
        const nextTimestamp = new Date(timestamp.getTime() + (timeInterval * 1000));
        
        // Format for datetime-local input (YYYY-MM-DDTHH:mm:ss)
        const year = nextTimestamp.getFullYear();
        const month = String(nextTimestamp.getMonth() + 1).padStart(2, '0');
        const day = String(nextTimestamp.getDate()).padStart(2, '0');
        const hours = String(nextTimestamp.getHours()).padStart(2, '0');
        const minutes = String(nextTimestamp.getMinutes()).padStart(2, '0');
        const seconds = String(nextTimestamp.getSeconds()).padStart(2, '0');
        
        const datetimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
        document.getElementById('startDateTime').value = datetimeString;
    }
    
    // Update UI
    if (!options.skipUIUpdate) {
        updatePointsList();
        updateStatus();
    }
    
    // Clear preview line
    previewLine.setLatLngs([]);
}

function removePoint(trackIndex, pointIndex) {
    const track = tracks[trackIndex];
    if (pointIndex < 0 || pointIndex >= track.points.length) return;
    
    // Find the marker to remove
    const point = track.points[pointIndex];
    const markerIndex = markers.findIndex(marker => {
        return marker.trackIndex === trackIndex && marker.pointIndex === pointIndex;
    });
    
    // Remove marker from array
    if (markerIndex >= 0) {
        markers.splice(markerIndex, 1);
    }
    
    // Remove from track
    track.points.splice(pointIndex, 1);
    
    // Reassign IDs to remaining points in track and update marker data
    for (let i = pointIndex; i < track.points.length; i++) {
        track.points[i].id = i;
    }
    
    // Update marker indices for remaining points in this track
    markers.forEach(marker => {
        if (marker.trackIndex === trackIndex && marker.pointIndex > pointIndex) {
            marker.pointIndex--;
        }
    });
    
    // Update polyline for this track
    updatePolyline(trackIndex);
    
    // Redraw markers
    redrawMarkers();
    
    // Update UI
    updatePointsList();
    updateStatus();
}


function updatePolyline(trackIndex) {
    if (trackIndex !== undefined) {
        // Update specific track
        const track = tracks[trackIndex];
        const latLngs = track.points.map(point => [point.lat, point.lng]);
        polylines[trackIndex].setLatLngs(latLngs);
    } else {
        // Update all tracks
        tracks.forEach((track, index) => {
            const latLngs = track.points.map(point => [point.lat, point.lng]);
            polylines[index].setLatLngs(latLngs);
        });
    }
}

function updatePointsList() {
    const pointsList = document.getElementById('pointsList');
    const pointCount = document.getElementById('pointCount');
    
    const totalPoints = tracks.reduce((sum, track) => sum + track.points.length, 0);
    pointCount.textContent = totalPoints;
    
    updateSpeedLegend();
    
    if (totalPoints === 0) {
        pointsList.innerHTML = '<div style="padding: 20px; text-align: center; color: #7f8c8d;">Click on the map to add points</div>';
        return;
    }
    
    let html = '';
    let lastAddedPoint = null;
    
    tracks.forEach((track, trackIndex) => {
        const isCollapsed = track.collapsed;
        const pointCount = track.points.length;
        const isCurrentTrack = trackIndex === currentTrackIndex;
        
        html += `
            <div class="track-header ${isCollapsed ? 'collapsed' : ''} ${isCurrentTrack ? 'active' : ''}" onclick="selectTrackFromHeader(${trackIndex})">
                <div class="track-info">
                    <div class="track-color" style="background-color: ${track.color}"></div>
                    <span>${track.name} (${pointCount} points)</span>
                </div>
                <div class="track-controls">
                    <button class="track-export-btn" onclick="event.stopPropagation(); exportTrackGPX(${trackIndex})">Export</button>
                    <span class="collapse-icon">▼</span>
                </div>
            </div>
            <div class="track-points ${isCollapsed ? 'collapsed' : ''}">
        `;
        
        track.points.forEach((point, pointIndex) => {
            const speedInfo = pointIndex > 0 ? calculateSpeedInfo(track.points[pointIndex - 1], point) : null;
            const speedColor = speedInfo ? getSpeedColor(speedInfo.speed) : '#e2e8f0';
            const speedText = speedInfo ? `${speedInfo.speed.toFixed(1)}` : '-';
            
            const pointId = `point-${trackIndex}-${pointIndex}`;
            
            // Format accuracy for display
            const accuracyText = point.accuracy !== undefined ? `${point.accuracy.toFixed(0)}m` : '';
            
            html += `
                <div class="point-item" id="${pointId}" onclick="selectPoint(${trackIndex}, ${pointIndex})" style="border-left-color: ${speedColor}">
                    <div class="point-content">
                        <div class="point-coords">${point.lat.toFixed(4)}, ${point.lng.toFixed(4)}</div>
                        <div class="point-time">${formatCompactTimestamp(point.timestamp)}</div>
                        <div class="point-speed" style="color: ${speedColor}">${speedText}</div>
                        <div class="point-accuracy">${accuracyText}</div>
                    </div>
                    <button class="point-delete" onclick="event.stopPropagation(); removePoint(${trackIndex}, ${pointIndex})">×</button>
                </div>
            `;
            
            // Track the last added point for scrolling
            if (trackIndex === currentTrackIndex && pointIndex === track.points.length - 1) {
                lastAddedPoint = pointId;
            }
        });
        
        html += '</div>';
    });
    
    pointsList.innerHTML = html;
    
    // Auto-scroll to latest point in paint mode
    if (paintMode && paintActive && lastAddedPoint) {
        setTimeout(() => {
            const element = document.getElementById(lastAddedPoint);
            if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }, 50);
    }
}

function toggleTrack(trackIndex) {
    tracks[trackIndex].collapsed = !tracks[trackIndex].collapsed;
    updatePointsList();
}

function selectTrackFromHeader(trackIndex) {
    // If clicking on current track, just toggle collapse
    if (trackIndex === currentTrackIndex) {
        toggleTrack(trackIndex);
    } else {
        // Select new track and ensure it's expanded
        currentTrackIndex = trackIndex;
        tracks[trackIndex].collapsed = false;
        updatePointsList();
        updateStatus();
    }
}

function selectPoint(trackIndex, pointIndex) {
    const track = tracks[trackIndex];
    if (pointIndex < 0 || pointIndex >= track.points.length) return;
    
    const point = track.points[pointIndex];
    map.setView([point.lat, point.lng], map.getZoom());
    
    // Highlight selected point in list
    document.querySelectorAll('.point-item').forEach(item => {
        item.classList.remove('selected');
    });
    
    // Find and highlight the correct point item
    const trackElements = document.querySelectorAll('.track-points');
    if (trackElements[trackIndex]) {
        const pointItems = trackElements[trackIndex].querySelectorAll('.point-item');
        if (pointItems[pointIndex]) {
            pointItems[pointIndex].classList.add('selected');
        }
    }
}

function formatTimestamp(timestamp) {
    return timestamp.toLocaleString();
}

function formatCompactTimestamp(timestamp) {
    const today = new Date();
    const pointDate = new Date(timestamp);
    
    // Check if it's the same date as today
    const isToday = pointDate.toDateString() === today.toDateString();
    
    if (isToday) {
        return pointDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } else {
        // Include date for different days
        const dateStr = pointDate.toLocaleDateString([], { month: '2-digit', day: '2-digit' });
        const timeStr = pointDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        return `${dateStr} ${timeStr}`;
    }
}

function updateSpeedLegend() {
    const speedLegendInline = document.getElementById('speedLegendInline');
    const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
    
    const speedRanges = [
        { min: 0, max: 5, label: '0-5' },
        { min: 5, max: 10, label: '5-10' },
        { min: 10, max: 15, label: '10-15' },
        { min: 15, max: 20, label: '15-20' },
        { min: 20, max: 25, label: '20-25' },
        { min: 25, max: 30, label: '25-30' },
        { min: 30, max: 50, label: '30-50' },
        { min: 50, max: 100, label: '50+' }
    ];
    
    let html = '';
    speedRanges.forEach(range => {
        const color = getSpeedColor((range.min + range.max) / 2);
        html += `
            <div class="speed-legend-item">
                <div class="speed-color-indicator" style="background-color: ${color}"></div>
                <span>${range.label}</span>
            </div>
        `;
    });
    
    speedLegendInline.innerHTML = html;
}

function getSpeedColor(speed) {
    // Color gradient based on speed ranges (5km/h intervals)
    if (speed < 5) return '#48bb78';      // Green - very slow
    if (speed < 10) return '#68d391';     // Light green - slow
    if (speed < 15) return '#9ae6b4';     // Lighter green - walking/cycling
    if (speed < 20) return '#fbb040';     // Orange - moderate cycling
    if (speed < 25) return '#ed8936';     // Dark orange - fast cycling
    if (speed < 30) return '#f56565';     // Red - very fast cycling/slow car
    if (speed < 50) return '#e53e3e';     // Dark red - car speed
    return '#c53030';                     // Very dark red - high speed
}

function updateStatus() {
    const statusText = document.getElementById('statusText');
    const pointsSummary = document.getElementById('pointsSummary');
    
    let paintStatus = 'OFF';
    if (paintMode && paintActive) {
        paintStatus = 'PAINTING';
    } else if (paintMode) {
        paintStatus = 'READY';
    }
    
    const totalPoints = tracks.reduce((sum, track) => sum + track.points.length, 0);
    const maxSpeed = document.getElementById('maxSpeed').value;
    const modeText = `Paint: ${paintStatus} • Speed: ${maxSpeed}km/h • Current: ${tracks[currentTrackIndex]?.name || 'None'}`;
    
    if (totalPoints === 0) {
        if (!editModeEnabled) {
            statusText.textContent = 'View mode - switch to edit mode to add points';
            pointsSummary.textContent = 'Switch to edit mode to start creating tracks';
        } else if (paintMode && paintActive) {
            statusText.textContent = 'Painting active - move mouse to add points (±: adjust speed)';
            pointsSummary.textContent = 'Move mouse over the map to paint points';
        } else if (paintMode) {
            statusText.textContent = 'Paint mode ready - click map to start painting (±: adjust speed)';
            pointsSummary.textContent = 'Click on the map to start painting points';
        } else {
            statusText.textContent = 'Ready to create GPX tracks';
            pointsSummary.textContent = 'Click on the map to add points';
        }
    } else if (totalPoints === 1) {
        if (!editModeEnabled) {
            statusText.textContent = '1 point - switch to edit mode to add more';
        } else {
            statusText.textContent = '1 point added - click to add more points';
        }
        pointsSummary.innerHTML = `<strong>1 point</strong> in ${tracks.length} track(s) • ${modeText}`;
    } else {
        const duration = calculateTotalDuration();
        const totalDistance = calculateTotalDistance();
        if (!editModeEnabled) {
            statusText.textContent = `${totalPoints} points in ${tracks.length} track(s) - viewing mode`;
        } else {
            statusText.textContent = `${totalPoints} points in ${tracks.length} track(s) - Total duration: ${formatDuration(duration)}`;
        }
        pointsSummary.innerHTML = `<strong>${totalPoints} points</strong> • ${formatDistance(totalDistance)} • ${formatDuration(duration)} • ${modeText}`;
    }
}

function calculateTotalDuration() {
    if (tracks.length === 0) return 0;
    
    let firstTimestamp = null;
    let lastTimestamp = null;
    
    tracks.forEach(track => {
        if (track.points.length > 0) {
            const trackFirst = track.points[0].timestamp;
            const trackLast = track.points[track.points.length - 1].timestamp;
            
            if (!firstTimestamp || trackFirst < firstTimestamp) {
                firstTimestamp = trackFirst;
            }
            if (!lastTimestamp || trackLast > lastTimestamp) {
                lastTimestamp = trackLast;
            }
        }
    });
    
    if (!firstTimestamp || !lastTimestamp) return 0;
    
    return Math.floor((lastTimestamp - firstTimestamp) / 1000); // seconds
}

function formatDuration(seconds) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
        return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
        return `${minutes}m ${secs}s`;
    } else {
        return `${secs}s`;
    }
}

function clearAll() {
    const totalPoints = tracks.reduce((sum, track) => sum + track.points.length, 0);
    if (totalPoints === 0) return;
    
    if (confirm('Are you sure you want to clear all tracks and points?')) {
        // Clear tracks array
        tracks = [];
        
        // Clear markers array
        markers = [];
        
        // Remove all polylines
        polylines.forEach(polyline => map.removeLayer(polyline));
        polylines = [];
        
        // Clear canvas
        redrawMarkers();
        
        // Create first track
        createNewTrack();
        
        // Update UI
        updatePointsList();
        updateStatus();
    }
}

function shouldCreateNewTrackForDayChange(newTimestamp, currentTrack) {
    const autoNewTrack = document.getElementById('autoNewTrack').checked;
    if (!autoNewTrack || currentTrack.points.length === 0) {
        return false;
    }
    
    const lastPoint = currentTrack.points[currentTrack.points.length - 1];
    const lastDate = new Date(lastPoint.timestamp).toDateString();
    const newDate = new Date(newTimestamp).toDateString();
    
    return lastDate !== newDate;
}


function exportTrackGPX(trackIndex) {
    const track = tracks[trackIndex];
    if (track.points.length === 0) {
        alert('This track has no points to export.');
        return;
    }
    
    const gpxContent = generateTrackGPX(track);
    const filename = generateTrackFilename(track);
    
    downloadFile(gpxContent, filename, 'application/gpx+xml');
}

function exportAllGPX() {
    const tracksWithPoints = tracks.filter(track => track.points.length > 0);
    if (tracksWithPoints.length === 0) {
        alert('Please add some points before exporting.');
        return;
    }
    
    tracksWithPoints.forEach((track, index) => {
        setTimeout(() => {
            const gpxContent = generateTrackGPX(track);
            const filename = generateTrackFilename(track);
            downloadFile(gpxContent, filename, 'application/gpx+xml');
        }, index * 100); // Small delay between downloads
    });
}

function generateTrackGPX(track) {
    const startDateTimeValue = document.getElementById('startDateTime').value;
    const startDateStr = startDateTimeValue ? new Date(startDateTimeValue).toISOString().split('T')[0] : '';
    
    let gpx = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPX Test Data Generator" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>${track.name} ${startDateStr}</name>
    <desc>Generated test track for Reitti</desc>
    <time>${new Date().toISOString()}</time>
  </metadata>
  <trk>
    <name>${track.name}</name>
    <trkseg>
`;

    track.points.forEach(point => {
        const isoTimestamp = point.timestamp.toISOString();
        gpx += `      <trkpt lat="${point.lat}" lon="${point.lng}">
        <ele>${point.elevation.toFixed(1)}</ele>
        <time>${isoTimestamp}</time>
      </trkpt>
`;
    });

    gpx += `    </trkseg>
  </trk>
</gpx>`;

    return gpx;
}

function generateTrackFilename(track) {
    const startDateTimeValue = document.getElementById('startDateTime').value;
    
    let filename = track.name.toLowerCase().replace(/\s+/g, '_');
    
    if (startDateTimeValue) {
        const dateTime = new Date(startDateTimeValue);
        const dateStr = dateTime.toISOString().split('T')[0];
        const timeStr = dateTime.toTimeString().split(' ')[0].replace(/:/g, '');
        filename += `_${dateStr}_${timeStr}`;
    } else if (track.points.length > 0) {
        const firstPoint = track.points[0];
        const dateStr = firstPoint.timestamp.toISOString().split('T')[0];
        const timeStr = firstPoint.timestamp.toTimeString().split(' ')[0].replace(/:/g, '');
        filename += `_${dateStr}_${timeStr}`;
    }
    
    return `${filename}.gpx`;
}

function downloadFile(content, filename, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    
    URL.revokeObjectURL(url);
}

// GPX file handling functions
function handleGPXFiles(event) {
    const files = Array.from(event.target.files);
    if (files.length === 0) return;
    
    files.forEach((file, index) => {
        const reader = new FileReader();
        reader.onload = function(e) {
            if (file.name.toLowerCase().endsWith('.json')) {
                handleGoogleJson(e.target.result, file.name);
            } else {
                try {
                    const result = parseAndImportGPX(e.target.result, file.name, index === 0);
                } catch (error) {
                    alert(`Error parsing GPX file "${file.name}": ${error.message}`);
                }
            }
        };
        reader.readAsText(file);
    });
    
    // Reset the input so the same files can be selected again
    event.target.value = '';
}

function handleGoogleJson(content, filename) {
    try {
        const data = JSON.parse(content);
        const locations = data.locations || [];
        if (locations.length === 0) {
            alert("No location data found in JSON.");
            return;
        }

        // Group points by date
        const days = {};
        locations.forEach(loc => {
            const timestamp = loc.timestamp || loc.timestampMs;
            if (!timestamp) return;
            
            const date = new Date(timestamp);
            const dateStr = date.toISOString().split('T')[0];
            
            if (!days[dateStr]) days[dateStr] = [];
            days[dateStr].push({
                lat: loc.latitudeE7 / 1e7,
                lng: loc.longitudeE7 / 1e7,
                timestamp: date,
                elevation: loc.altitude || 0,
                accuracy: loc.accuracy || 0
            });
        });

        pendingJsonData = days;
        pendingJsonFilename = filename.replace('.json', '');
        showJsonDatePicker();
    } catch (e) {
        alert("Error parsing Google Records JSON: " + e.message);
    }
}

function showJsonDatePicker() {
    const modal = document.getElementById('jsonDatePicker');
    const grid = document.getElementById('dateGrid');
    grid.innerHTML = '';
    selectedDates.clear();
    lastClickedDate = null;

    const sortedDates = Object.keys(pendingJsonData).sort();
    sortedDates.forEach(date => {
        const div = document.createElement('div');
        div.className = 'date-item';
        div.textContent = date;
        div.dataset.date = date;
        div.onclick = (e) => handleDateClick(date, e.shiftKey);
        grid.appendChild(div);
    });

    modal.classList.add('open');
}

function handleDateClick(date, isShift) {
    const sortedDates = Object.keys(pendingJsonData).sort();
    
    if (isShift && lastClickedDate) {
        const startIdx = sortedDates.indexOf(lastClickedDate);
        const endIdx = sortedDates.indexOf(date);
        const [low, high] = [Math.min(startIdx, endIdx), Math.max(startIdx, endIdx)];
        
        selectedDates.clear();
        for (let i = low; i <= high; i++) {
            selectedDates.add(sortedDates[i]);
        }
    } else {
        if (selectedDates.has(date)) {
            selectedDates.delete(date);
        } else {
            selectedDates.add(date);
        }
        lastClickedDate = date;
    }
    updateDateGridUI();
}

function updateDateGridUI() {
    const items = document.querySelectorAll('.date-item');
    items.forEach(item => {
        if (selectedDates.has(item.dataset.date)) {
            item.classList.add('selected');
        } else {
            item.classList.remove('selected');
        }
    });
}

function closeJsonPicker() {
    document.getElementById('jsonDatePicker').classList.remove('open');
    pendingJsonData = null;
}

function importSelectedJsonDates() {
    if (selectedDates.size === 0) {
        alert("Please select at least one date.");
        return;
    }

    const sortedSelected = Array.from(selectedDates).sort();
    let totalPoints = 0;
    const bounds = L.latLngBounds();

    // Collapse all existing tracks first
    tracks.forEach(track => {
        track.collapsed = true;
    });

    sortedSelected.forEach((dateStr, index) => {
        const dayPoints = pendingJsonData[dateStr];
        // Sort points within the day
        dayPoints.sort((a, b) => a.timestamp - b.timestamp);

        const trackName = `${pendingJsonFilename} - ${dateStr}`;
        const track = createNewTrack(trackName, dayPoints[0].timestamp);
        
        // Ensure the newly created track is expanded if it's part of the batch
        track.collapsed = false;

        dayPoints.forEach(p => {
            addPoint(p.lat, p.lng, {
                timestamp: p.timestamp,
                elevation: p.elevation,
                accuracy: p.accuracy,
                skipNoise: true,
                skipDayChangeCheck: true,
                skipStops: true,
                skipTimeUpdate: true,
                skipUIUpdate: true // Skip UI updates during batch
            });
            bounds.extend([p.lat, p.lng]);
            totalPoints++;
        });
    });

    if (totalPoints > 0) {
        map.fitBounds(bounds.pad(0.1));
    }

    closeJsonPicker();
    updatePointsList();
    updateStatus();
    redrawMarkers();
}

function parseAndImportGPX(gpxContent, filename, isFirstFile = true) {
    const parser = new DOMParser();
    const gpxDoc = parser.parseFromString(gpxContent, 'text/xml');
    
    // Check for parsing errors
    const parserError = gpxDoc.querySelector('parsererror');
    if (parserError) {
        throw new Error('Invalid GPX file format');
    }
    
    // Extract track points from all tracks and track segments
    const trackPoints = [];
    const gpxTracks = gpxDoc.querySelectorAll('trk');
    
    gpxTracks.forEach(track => {
        const trackSegments = track.querySelectorAll('trkseg');
        trackSegments.forEach(segment => {
            const points = segment.querySelectorAll('trkpt');
            points.forEach(point => {
                const lat = parseFloat(point.getAttribute('lat'));
                const lng = parseFloat(point.getAttribute('lon'));
                
                if (isNaN(lat) || isNaN(lng)) return;
                
                const timeElement = point.querySelector('time');
                const elevationElement = point.querySelector('ele');
                
                let timestamp = new Date();
                if (timeElement && timeElement.textContent) {
                    timestamp = new Date(timeElement.textContent);
                    if (isNaN(timestamp.getTime())) {
                        timestamp = new Date();
                    }
                }
                
                let elevation = parseFloat(document.getElementById('elevation').value);
                if (elevationElement && elevationElement.textContent) {
                    const parsedElevation = parseFloat(elevationElement.textContent);
                    if (!isNaN(parsedElevation)) {
                        elevation = parsedElevation;
                    }
                }
                
                trackPoints.push({
                    lat: lat,
                    lng: lng,
                    timestamp: timestamp,
                    elevation: elevation
                });
            });
        });
    });
    
    if (trackPoints.length === 0) {
        alert('No valid track points found in the GPX file.');
        return;
    }
    
    // Sort points by timestamp
    trackPoints.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    
    // Group points by local timezone day
    const pointsByDay = groupPointsByLocalDay(trackPoints);
    
    // Create tracks for each day
    let importedTracksCount = 0;
    let isFirstImportedTrack = isFirstFile;

    Object.keys(pointsByDay).sort().forEach(dayKey => {
        const dayPoints = pointsByDay[dayKey];
        if (dayPoints.length === 0) return;
        
        let track;
        let trackIndex;
        let color;

        // Check if we can reuse the first empty track
        if (isFirstImportedTrack && tracks.length > 0 && tracks[0].points.length === 0) {
            // Reuse the existing empty track
            track = tracks[0];
            trackIndex = 0;
            color = track.color;

            // Update track properties
            track.name = `${filename.replace('.gpx', '')} - ${dayKey}`;
            track.startTime = dayPoints[0].timestamp;
            track.collapsed = false;

            // Collapse all other existing tracks
            tracks.forEach((t, index) => {
                if (index !== 0) {
                    t.collapsed = true;
                }
            });
        } else {
            // Collapse all existing tracks
            tracks.forEach(track => {
                track.collapsed = true;
            });

            // Create new track for this day
            trackIndex = tracks.length;
            color = TRACK_COLORS[trackIndex % TRACK_COLORS.length];
            const trackName = `${filename.replace('.gpx', '')} - ${dayKey}`;

            track = {
                id: trackIndex,
                name: trackName,
                points: [],
                color: color,
                collapsed: false,
                startTime: dayPoints[0].timestamp
            };

            tracks.push(track);

            // Create polyline for this track
            const polyline = L.polyline([], {
                color: color,
                weight: 3,
                opacity: 0.7
            }).addTo(map);
            polylines.push(polyline);
        }

        isFirstImportedTrack = false;
        
        // Add points to the track
        dayPoints.forEach((point, pointIndex) => {
            const trackPoint = {
                id: pointIndex,
                trackId: trackIndex,
                lat: point.lat,
                lng: point.lng,
                originalLat: point.lat,
                originalLng: point.lng,
                timestamp: point.timestamp,
                elevation: point.elevation,
                accuracy: 0 // Imported points have no accuracy simulation
            };
            
            track.points.push(trackPoint);
            
            // Store marker data for canvas rendering
            const markerData = {
                lat: point.lat,
                lng: point.lng,
                color: color,
                trackIndex: trackIndex,
                pointIndex: pointIndex,
                title: `${track.name} - Point ${pointIndex + 1}`
            };
            
            markers.push(markerData);
        });
        
        // Update polyline for this track
        updatePolyline(trackIndex);
        importedTracksCount++;
    });
    
    // Set current track to the last imported track
    if (importedTracksCount > 0) {
        currentTrackIndex = tracks.length - 1;
        
        // Fit map to show all imported points
        if (trackPoints.length > 0) {
            const bounds = L.latLngBounds();
            trackPoints.forEach(point => {
                bounds.extend([point.lat, point.lng]);
            });
            map.fitBounds(bounds.pad(0.1));
        }
    }
    
    // Update UI
    updatePointsList();
    
    // Redraw all markers
    redrawMarkers();
    
    updateStatus();
    
    // Return import statistics for summary
    return {
        pointsCount: trackPoints.length,
        tracksCount: importedTracksCount
    };
}

function groupPointsByLocalDay(points) {
    const pointsByDay = {};
    
    points.forEach(point => {
        // Get local date string (YYYY-MM-DD) using the browser's timezone
        const localDate = new Date(point.timestamp.getTime() - (point.timestamp.getTimezoneOffset() * 60000));
        const localDateString = localDate.toISOString().split('T')[0];
        
        if (!pointsByDay[localDateString]) {
            pointsByDay[localDateString] = [];
        }
        
        pointsByDay[localDateString].push(point);
    });
    
    return pointsByDay;
}

// New functions for Phase 2 features

function onMapMouseMove(e) {
    // Check if we are hovering over a marker first
    const containerPoint = map.mouseEventToContainerPoint(e.originalEvent);
    const tolerance = 10; // pixels
    let hoveredMarker = null;

    for (let i = 0; i < markers.length; i++) {
        const markerData = markers[i];
        const markerPoint = map.latLngToContainerPoint([markerData.lat, markerData.lng]);
        
        const dist = Math.sqrt(
            Math.pow(containerPoint.x - markerPoint.x, 2) + 
            Math.pow(containerPoint.y - markerPoint.y, 2)
        );
        
        if (dist <= tolerance) {
            hoveredMarker = markerData;
            break;
        }
    }

    if (hoveredMarker) {
        showMarkerTooltip(e.originalEvent, hoveredMarker);
        previewLine.setLatLngs([]);
        return;
    }

    // Don't show preview or allow painting if edit mode is disabled
    if (!editModeEnabled) {
        hideHoverTooltip();
        previewLine.setLatLngs([]);
        return;
    }
    
    // Update mouse position for paint mode
    if (paintMode) {
        lastMousePosition = e.latlng;
        if (paintActive) {
            // Add point immediately when mouse moves during painting
            const now = Date.now();
            if (now - lastPaintTime >= paintThrottleMs) {
                const currentTrack = tracks[currentTrackIndex];
                if (currentTrack && currentTrack.points.length > 0) {
                    addPointWithInterpolation(lastMousePosition.lat, lastMousePosition.lng);
                } else {
                    addPoint(lastMousePosition.lat, lastMousePosition.lng);
                }
                lastPaintTime = now;

                // Reset the auto-paint interval since we just added a point
                resetAutoPaintInterval();
            }
            return; // Don't show preview when actively painting
        }
    }
    
    const currentTrack = tracks[currentTrackIndex];
    if (!currentTrack || currentTrack.points.length === 0) {
        hideHoverTooltip();
        previewLine.setLatLngs([]);
        return;
    }
    
    const lastPoint = currentTrack.points[currentTrack.points.length - 1];
    const mouseLatLng = e.latlng;
    
    // Update preview line with current track color
    previewLine.setStyle({ color: currentTrack.color });
    previewLine.setLatLngs([[lastPoint.lat, lastPoint.lng], [mouseLatLng.lat, mouseLatLng.lng]]);
    
    // Calculate preview information
    const distance = calculateDistance(lastPoint.lat, lastPoint.lng, mouseLatLng.lat, mouseLatLng.lng);
    const timeInterval = parseInt(document.getElementById('timeInterval').value);
    const speed = (distance / 1000) / (timeInterval / 3600); // km/h
    const speedClass = getSpeedClass(speed);
    
    // Show hover tooltip
    showHoverTooltip(e.originalEvent, mouseLatLng, distance, speed, speedClass);
}

function onMapMouseOut(e) {
    hideHoverTooltip();
    previewLine.setLatLngs([]);
}

function showMarkerTooltip(mouseEvent, markerData) {
    const track = tracks[markerData.trackIndex];
    const point = track.points[markerData.pointIndex];
    const tooltip = hoverTooltip;
    
    let speedText = '-';
    let speedClass = 'speed-ok';
    
    if (markerData.pointIndex > 0) {
        const prevPoint = track.points[markerData.pointIndex - 1];
        const speedInfo = calculateSpeedInfo(prevPoint, point);
        speedText = `${speedInfo.speed.toFixed(1)} km/h`;
        speedClass = getSpeedClass(speedInfo.speed);
    }

    // Format accuracy for tooltip
    const accuracyText = point.accuracy !== undefined ? `${point.accuracy.toFixed(0)}m` : 'N/A';

    tooltip.innerHTML = `
        <div style="font-weight: 600; margin-bottom: 4px; color: ${markerData.color}">${track.name} - Point ${markerData.pointIndex + 1}</div>
        <div>Time: ${formatTimestamp(point.timestamp)}</div>
        <div class="${speedClass}">Speed: ${speedText}</div>
        <div>Elevation: ${point.elevation.toFixed(1)}m</div>
        <div>Accuracy: ${accuracyText}</div>
    `;
    
    tooltip.style.left = (mouseEvent.pageX + 10) + 'px';
    tooltip.style.top = (mouseEvent.pageY - 10) + 'px';
    tooltip.style.display = 'block';
}

function showHoverTooltip(mouseEvent, latLng, distance, speed, speedClass) {
    const tooltip = hoverTooltip;
    const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
    const timeInterval = parseInt(document.getElementById('timeInterval').value);
    const maxDistance = (maxSpeed * 1000 / 3600) * timeInterval; // meters
    
    let interpolationInfo = '';
    if (distance > maxDistance) {
        const numSegments = Math.ceil(distance / maxDistance);
        interpolationInfo = `<div style="color: #ed8936;">Will add ${numSegments} points</div>`;
    }
    
    // Show current accuracy setting in hover tooltip
    const currentAccuracy = document.getElementById('accuracySlider').value;

    tooltip.innerHTML = `
        <div>Lat: ${latLng.lat.toFixed(6)}</div>
        <div>Lng: ${latLng.lng.toFixed(6)}</div>
        <div>Distance: ${distance.toFixed(0)}m</div>
        <div class="${speedClass}">Speed: ${speed.toFixed(1)} km/h</div>
        <div>Accuracy: ${currentAccuracy}m</div>
        ${interpolationInfo}
    `;
    
    tooltip.style.left = (mouseEvent.pageX + 10) + 'px';
    tooltip.style.top = (mouseEvent.pageY - 10) + 'px';
    tooltip.style.display = 'block';
}

function hideHoverTooltip() {
    hoverTooltip.style.display = 'none';
}

function calculateDistance(lat1, lng1, lat2, lng2) {
    const R = 6371000; // Earth's radius in meters
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLng/2) * Math.sin(dLng/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
}

function calculateSpeedInfo(point1, point2) {
    const distance = calculateDistance(point1.lat, point1.lng, point2.lat, point2.lng);
    const timeDiff = (point2.timestamp - point1.timestamp) / 1000; // seconds
    const speed = (distance / 1000) / (timeDiff / 3600); // km/h
    
    return { distance, speed };
}

function getSpeedClass(speed) {
    const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
    
    if (speed <= maxSpeed) {
        return 'speed-ok';
    } else if (speed <= maxSpeed * 2) {
        return 'speed-fast';
    } else {
        return 'speed-unrealistic';
    }
}

function applyGPSNoise(lat, lng, accuracyMeters) {
    if (accuracyMeters === 0) {
        return { lat, lng };
    }
    
    // Convert accuracy to degrees (approximate)
    const latOffset = (Math.random() - 0.5) * 2 * (accuracyMeters / 111000);
    const lngOffset = (Math.random() - 0.5) * 2 * (accuracyMeters / (111000 * Math.cos(lat * Math.PI / 180)));
    
    return {
        lat: lat + latOffset,
        lng: lng + lngOffset
    };
}

function addPointWithInterpolation(targetLat, targetLng) {
    const currentTrack = tracks[currentTrackIndex];
    if (!currentTrack || currentTrack.points.length === 0) {
        addPoint(targetLat, targetLng);
        return;
    }
    
    const lastPoint = currentTrack.points[currentTrack.points.length - 1];
    const distance = calculateDistance(lastPoint.lat, lastPoint.lng, targetLat, targetLng);
    const timeInterval = parseInt(document.getElementById('timeInterval').value);
    const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
    
    // Calculate max distance for given time interval and speed
    const maxDistance = (maxSpeed * 1000 / 3600) * timeInterval; // meters
    
    if (distance <= maxDistance) {
        // Direct point addition - speed is acceptable
        addPoint(targetLat, targetLng);
    } else {
        // Need interpolation - calculate intermediate points
        const numSegments = Math.ceil(distance / maxDistance);
        const latStep = (targetLat - lastPoint.lat) / numSegments;
        const lngStep = (targetLng - lastPoint.lng) / numSegments;
        
        // Add intermediate points
        for (let i = 1; i <= numSegments; i++) {
            const interpolatedLat = lastPoint.lat + (latStep * i);
            const interpolatedLng = lastPoint.lng + (lngStep * i);
            addPoint(interpolatedLat, interpolatedLng);
        }
    }
}

function calculateTotalDistance() {
    let totalDistance = 0;
    
    tracks.forEach(track => {
        if (track.points.length < 2) return;
        
        for (let i = 1; i < track.points.length; i++) {
            totalDistance += calculateDistance(
                track.points[i-1].lat, track.points[i-1].lng,
                track.points[i].lat, track.points[i].lng
            );
        }
    });
    
    return totalDistance;
}

function formatDistance(meters) {
    if (meters < 1000) {
        return `${meters.toFixed(0)}m`;
    } else {
        return `${(meters / 1000).toFixed(1)}km`;
    }
}

// Paint mode functions
function togglePaintMode() {
    // Don't allow paint mode if edit mode is disabled
    if (!editModeEnabled) {
        return;
    }
    
    paintMode = !paintMode;
    paintActive = false; // Reset paint active state when toggling mode

    // Stop any active painting when toggling mode off
    if (!paintMode) {
        stopAutoPainting();
        lastMousePosition = null;
    }

    updatePaintModeButton();
    
    // Change cursor style when paint mode is active
    if (paintMode) {
        map.getContainer().style.cursor = 'crosshair';
    } else {
        map.getContainer().style.cursor = '';
    }
    
    updateStatus();
}

function updatePaintModeButton() {
    const button = document.getElementById('paintModeToggle');
    
    if (!paintMode) {
        button.textContent = 'Paint Mode: OFF';
        button.className = 'control-button';
    } else if (paintActive) {
        button.textContent = 'Paint Mode: PAINTING';
        button.className = 'control-button paint-active';
    } else {
        button.textContent = 'Paint Mode: READY';
        button.className = 'control-button paint-ready';
    }
}

function startAutoPainting() {
    if (paintInterval) {
        clearInterval(paintInterval);
    }

    paintInterval = setInterval(() => {
        if (paintActive && lastMousePosition) {
            const currentTrack = tracks[currentTrackIndex];
            if (currentTrack && currentTrack.points.length > 0) {
                addPointWithInterpolation(lastMousePosition.lat, lastMousePosition.lng);
            } else {
                addPoint(lastMousePosition.lat, lastMousePosition.lng);
            }
            lastPaintTime = Date.now();
        }
    }, 500); // Add point every 500ms
}

function resetAutoPaintInterval() {
    if (paintActive && paintInterval) {
        // Restart the interval to prevent double-adding points
        clearInterval(paintInterval);
        paintInterval = setInterval(() => {
            if (paintActive && lastMousePosition) {
                const currentTrack = tracks[currentTrackIndex];
                if (currentTrack && currentTrack.points.length > 0) {
                    addPointWithInterpolation(lastMousePosition.lat, lastMousePosition.lng);
                } else {
                    addPoint(lastMousePosition.lat, lastMousePosition.lng);
                }
                lastPaintTime = Date.now();
            }
        }, 500);
    }
}

function stopAutoPainting() {
    if (paintInterval) {
        clearInterval(paintInterval);
        paintInterval = null;
    }
}

// Realistic stops functions
function shouldAddStop(track) {
    if (track.points.length < 5) return false; // Need some points before considering stops
    return Math.random() < stopProbability;
}

function addRealisticStop(baseTimestamp) {
    // Add a random stop duration between 30 seconds and 10 minutes
    const stopDuration = Math.random() * (10 * 60 - 30) + 30; // 30s to 10min in seconds
    return new Date(baseTimestamp.getTime() + (stopDuration * 1000));
}

// Canvas marker implementation for better performance
function initializeCanvasMarkers() {
    // Create canvas element
    markerCanvas = document.createElement('canvas');
    markerCanvas.style.position = 'absolute';
    markerCanvas.style.top = '0';
    markerCanvas.style.left = '0';
    markerCanvas.style.pointerEvents = 'none';
    markerCanvas.style.zIndex = '400'; // Above map tiles but below controls
    
    // Create custom Leaflet layer for canvas
    markerCanvasLayer = L.Layer.extend({
        onAdd: function(map) {
            this._map = map;
            map.getPanes().overlayPane.appendChild(markerCanvas);
            this._reset();
            map.on('viewreset', this._reset, this);
            map.on('zoom', this._reset, this);
            map.on('move', this._reset, this);
        },
        
        onRemove: function(map) {
            map.getPanes().overlayPane.removeChild(markerCanvas);
            map.off('viewreset', this._reset, this);
            map.off('zoom', this._reset, this);
            map.off('move', this._reset, this);
        },
        
        _reset: function() {
            const size = this._map.getSize();
            const topLeft = this._map.containerPointToLayerPoint([0, 0]);
            
            markerCanvas.style.left = topLeft.x + 'px';
            markerCanvas.style.top = topLeft.y + 'px';
            markerCanvas.width = size.x;
            markerCanvas.height = size.y;
            
            redrawMarkers();
        }
    });
    
    // Add canvas layer to map
    new markerCanvasLayer().addTo(map);
    
    // Add click handler for marker interaction
    map.getContainer().addEventListener('contextmenu', handleMarkerContextMenu);
}

function redrawMarkers() {
    if (!markerCanvas) return;
    
    const ctx = markerCanvas.getContext('2d');
    ctx.clearRect(0, 0, markerCanvas.width, markerCanvas.height);
    
    // Draw all markers
    markers.forEach(markerData => {
        const point = map.latLngToContainerPoint([markerData.lat, markerData.lng]);
        drawMarker(ctx, point.x, point.y, markerData.color);
    });
}

function drawMarker(ctx, x, y, color) {
    const radius = 4;
    const borderWidth = 2;
    
    // Draw white border
    ctx.beginPath();
    ctx.arc(x, y, radius + borderWidth, 0, 2 * Math.PI);
    ctx.fillStyle = 'white';
    ctx.fill();
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.3)';
    ctx.lineWidth = 1;
    ctx.stroke();
    
    // Draw colored center
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, 2 * Math.PI);
    ctx.fillStyle = color;
    ctx.fill();
}

function handleMarkerContextMenu(e) {
    if (!editModeEnabled) return;
    
    e.preventDefault();
    
    const containerPoint = map.mouseEventToContainerPoint(e);
    const tolerance = 10; // pixels
    
    // Find marker near click point
    for (let i = 0; i < markers.length; i++) {
        const markerData = markers[i];
        const markerPoint = map.latLngToContainerPoint([markerData.lat, markerData.lng]);
        
        const distance = Math.sqrt(
            Math.pow(containerPoint.x - markerPoint.x, 2) + 
            Math.pow(containerPoint.y - markerPoint.y, 2)
        );
        
        if (distance <= tolerance) {
            removePoint(markerData.trackIndex, markerData.pointIndex);
            break;
        }
    }
}

// Time shifting functions
function shiftTrackTime(amount, unit) {
    if (tracks.length === 0 || currentTrackIndex >= tracks.length) {
        alert('No track selected to shift time.');
        return;
    }
    
    const currentTrack = tracks[currentTrackIndex];
    if (currentTrack.points.length === 0) {
        alert('Current track has no points to shift.');
        return;
    }
    
    // Calculate milliseconds to shift
    let shiftMs = 0;
    if (unit === 'hour') {
        shiftMs = amount * 60 * 60 * 1000; // hours to milliseconds
    } else if (unit === 'day') {
        shiftMs = amount * 24 * 60 * 60 * 1000; // days to milliseconds
    }
    
    // Shift all points in the current track
    currentTrack.points.forEach(point => {
        point.timestamp = new Date(point.timestamp.getTime() + shiftMs);
    });
    
    // Update track start time
    if (currentTrack.points.length > 0) {
        currentTrack.startTime = new Date(currentTrack.points[0].timestamp);
    }
    
    // Update the datetime input to reflect the new time of the last point
    if (currentTrack.points.length > 0) {
        const lastPoint = currentTrack.points[currentTrack.points.length - 1];
        const timeInterval = parseInt(document.getElementById('timeInterval').value);
        const nextTimestamp = new Date(lastPoint.timestamp.getTime() + (timeInterval * 1000));
        
        // Format for datetime-local input
        const year = nextTimestamp.getFullYear();
        const month = String(nextTimestamp.getMonth() + 1).padStart(2, '0');
        const day = String(nextTimestamp.getDate()).padStart(2, '0');
        const hours = String(nextTimestamp.getHours()).padStart(2, '0');
        const minutes = String(nextTimestamp.getMinutes()).padStart(2, '0');
        const seconds = String(nextTimestamp.getSeconds()).padStart(2, '0');
        
        const datetimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
        document.getElementById('startDateTime').value = datetimeString;
    }
    
    // Update UI
    updatePointsList();
    updateStatus();
    
    // Show confirmation
    const unitText = unit === 'hour' ? 'hour' : 'day';
    const direction = amount > 0 ? 'forward' : 'backward';
    const absAmount = Math.abs(amount);
}
