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
        
        this.init();
    }
    
    init() {
        // Add center marker for the place
        this.centerMarker = L.marker([this.centerLat, this.centerLng], {
            icon: L.divIcon({
                className: 'center-marker',
                html: '<div style="background: #3b82f6; width: 12px; height: 12px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>',
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
            this.addPolygonPoint(e.latlng);
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
        const marker = L.circleMarker(latlng, {
            radius: 6,
            fillColor: '#ef4444',
            color: '#dc2626',
            weight: 2,
            fillOpacity: 0.8
        }).addTo(this.map);
        
        marker.bindTooltip(`Point ${this.polygonPoints.length}`, {
            permanent: false,
            direction: 'top'
        });
        
        // Add click handler to remove point
        marker.on('click', (e) => {
            L.DomEvent.stopPropagation(e);
            this.removePolygonPoint(this.polygonPoints.indexOf(latlng));
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
                color: '#3b82f6',
                weight: 2,
                fillColor: '#3b82f6',
                fillOpacity: 0.2
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
        
        if (this.polygonPoints.length >= 3) {
            saveBtn.disabled = false;
            // Convert points to JSON
            const polygonData = this.polygonPoints.map(point => ({
                lat: point.lat,
                lng: point.lng
            }));
            polygonDataInput.value = JSON.stringify(polygonData);
        } else {
            saveBtn.disabled = true;
            polygonDataInput.value = '';
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
                this.addPolygonPoint(L.latLng(point.lat, point.lng));
            });
        }
    }
    
    loadNearbyPlaces(nearbyPlaces) {
        nearbyPlaces.forEach(place => {
            if (place.id !== this.placeId) {
                const marker = L.circleMarker([place.lat, place.lng], {
                    radius: 4,
                    fillColor: '#6b7280',
                    color: '#4b5563',
                    weight: 1,
                    fillOpacity: 0.6
                }).addTo(this.map);
                
                marker.bindTooltip(place.name, {
                    permanent: false,
                    direction: 'top'
                });
            }
        });
    }
}
