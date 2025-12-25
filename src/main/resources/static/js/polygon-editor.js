/**
 * Polygon Editor for SignificantPlaces
 */
class PolygonEditor {
    constructor(map, centerLat, centerLng, placeName) {
        this.map = map;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.placeName = placeName;
        
        this.polygonPoints = [];
        this.polygonMarkers = [];
        this.polygonLayer = null;
        this.previewLine = null;
        this.isDragging = false;
        
        this.init();
    }
    
    init() {
        // Add center marker for the place
        this.centerMarker = L.marker([this.centerLat, this.centerLng], {
            icon: L.divIcon({
                className: 'center-marker',
                html: '<div style="background: var(--color-highlight); width: 12px; height: 12px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>',
                iconSize: [16, 16],
                iconAnchor: [8, 8]
            })
        }).addTo(this.map);

        this.centerMarker.bindTooltip(this.placeName + ' (center)', {
            permanent: false,
            direction: 'top'
        });

        // Add click handler for adding polygon points
        this.map.on('click', (e) => {
            // Don't add point if we're dragging
            if (!this.isDragging) {
                this.addPolygonPoint(e.latlng);
            }
        });
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.clearPolygon();
            } else if (e.key === 'Enter' && e.ctrlKey) {
                this.savePolygon();
            } else if (e.key === 'z' && e.ctrlKey) {
                e.preventDefault();
                this.undoLastPoint();
            }
        });
    }
    
    addPolygonPoint(latlng) {
        this.polygonPoints.push(latlng);
        
        // Add marker for the point
        const marker = L.marker(latlng, {
            draggable: true,
            icon: L.divIcon({
                className: 'polygon-point-marker',
                html: '<div style="background: var(--color-highlight); width: 8px; height: 8px; border-radius: 50%; border: 2px solid #daa520; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>',
                iconSize: [12, 12],
                iconAnchor: [6, 6]
            })
        }).addTo(this.map);
        
        marker.bindTooltip(`Point ${this.polygonPoints.length}`, {
            permanent: false,
            direction: 'top'
        });
        
        // Add click handler to remove point
        marker.on('click', (e) => {
            L.DomEvent.stopPropagation(e);
            const index = this.polygonMarkers.indexOf(marker);
            this.removePolygonPoint(index);
        });
        
        // Add drag handlers to update polygon when point is moved
        marker.on('dragstart', (e) => {
            this.isDragging = true;
        });
        
        marker.on('drag', (e) => {
            const index = this.polygonMarkers.indexOf(marker);
            if (index >= 0) {
                this.polygonPoints[index] = e.target.getLatLng();
                this.updatePolygonDisplay();
            }
        });
        
        marker.on('dragend', (e) => {
            this.isDragging = false;
            const index = this.polygonMarkers.indexOf(marker);
            if (index >= 0) {
                this.polygonPoints[index] = e.target.getLatLng();
                this.updatePolygonDisplay();
            }
        });
        
        this.polygonMarkers.push(marker);
        this.updatePolygonDisplay();
    }
    
    removePolygonPoint(index) {
        if (index >= 0 && index < this.polygonPoints.length) {
            this.polygonPoints.splice(index, 1);
            this.map.removeLayer(this.polygonMarkers[index]);
            this.polygonMarkers.splice(index, 1);
            
            // Update tooltips
            this.polygonMarkers.forEach((marker, i) => {
                marker.setTooltipContent(`Point ${i + 1}`);
            });
            
            this.updatePolygonDisplay();
        }
    }
    
    undoLastPoint() {
        if (this.polygonPoints.length > 0) {
            this.removePolygonPoint(this.polygonPoints.length - 1);
        }
    }
    
    updatePolygonDisplay() {
        // Remove existing polygon
        if (this.polygonLayer) {
            this.map.removeLayer(this.polygonLayer);
            this.polygonLayer = null;
        }
        
        // Remove preview line
        if (this.previewLine) {
            this.map.removeLayer(this.previewLine);
            this.previewLine = null;
        }
        
        if (this.polygonPoints.length >= 3) {
            // Create polygon
            this.polygonLayer = L.polygon(this.polygonPoints, {
                color: 'var(--color-highlight)',
                weight: 2,
                fillColor: 'var(--color-highlight)',
                fillOpacity: 0.3
            }).addTo(this.map);
        } else if (this.polygonPoints.length === 2) {
            // Show preview line to first point
            const previewPoints = [...this.polygonPoints, this.polygonPoints[0]];
            this.previewLine = L.polyline(previewPoints, {
                color: '#6b7280',
                weight: 2,
                dashArray: '5, 5'
            }).addTo(this.map);
        }
        
        this.updateSaveButton();
    }
    
    updateSaveButton() {
        const saveBtn = document.getElementById('save-btn');
        const polygonDataInput = document.getElementById('polygonData');
        const saveStatusElement = document.getElementById('save-status');
        
        if (this.polygonPoints.length === 0) {
            // No polygon - this is valid, allow saving
            saveBtn.disabled = false;
            polygonDataInput.value = '';
            if (saveStatusElement) {
                saveStatusElement.textContent = '';
                saveStatusElement.style.display = 'none';
            }
        } else if (this.polygonPoints.length >= 3) {
            // Valid polygon - allow saving
            saveBtn.disabled = false;
            const polygonData = this.polygonPoints.map(point => ({
                lat: point.lat,
                lng: point.lng
            }));
            polygonDataInput.value = JSON.stringify(polygonData);
            if (saveStatusElement) {
                saveStatusElement.textContent = '';
                saveStatusElement.style.display = 'none';
            }
        } else {
            // Invalid polygon (1-2 points) - disable saving with explanation
            saveBtn.disabled = true;
            polygonDataInput.value = '';
            if (saveStatusElement) {
                saveStatusElement.textContent = `Polygon needs at least 3 points (currently ${this.polygonPoints.length})`;
                saveStatusElement.style.display = 'block';
            }
        }
    }
    
    clearPolygon() {
        this.polygonPoints = [];
        this.polygonMarkers.forEach(marker => this.map.removeLayer(marker));
        this.polygonMarkers = [];
        
        if (this.polygonLayer) {
            this.map.removeLayer(this.polygonLayer);
            this.polygonLayer = null;
        }
        
        if (this.previewLine) {
            this.map.removeLayer(this.previewLine);
            this.previewLine = null;
        }
        
        this.updateSaveButton();
    }
    
    savePolygon() {
        if (!document.getElementById('save-btn').disabled) {
            document.getElementById('polygon-form').submit();
        }
    }
    
    loadExistingPolygon(polygonData) {
        if (polygonData && polygonData.length >= 3) {
            polygonData.forEach(point => {
                const lat = point.latitude || point.lat;
                const lng = point.longitude || point.lng;
                this.addPolygonPoint(L.latLng(lat, lng));
            });
        }
    }
    
    loadNearbyPlaces(nearbyPlaces) {
        nearbyPlaces.forEach(place => {
            if (place.id !== this.placeId) {
                const marker = L.circleMarker([place.lat, place.lng], {
                    radius: 6,
                    fillColor: '#ffcccb',
                    color: '#ff6b6b',
                    weight: 1,
                    fillOpacity: 0.7
                }).addTo(this.map);
                
                marker.bindTooltip(place.name, {
                    permanent: false,
                    direction: 'top'
                });
            }
        });
    }
}
