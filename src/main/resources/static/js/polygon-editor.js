/**
 * Polygon Editor for SignificantPlaces (MapLibre GL JS).
 * Click on the map to add points, drag a vertex to move it,
 * click a vertex without dragging to remove it.
 */
class PolygonEditor {
    constructor(map) {
        this.map = map;
        this.placeId = null;
        this.polygonPoints = [];
        this.nearbyPlaces = [];
        this.nearbyMarkers = [];
        this.centerMarker = null;
        this.isDragging = false;
        this.dragIndex = -1;
        this.dragMoved = false;
        this.dragStartPoint = null;
        this.onPolygonChange = null;
        this.onNearbyPlaceSelect = null;
        this.suspended = false;
        this._layersReady = false;

        this._initLayers();
        this._bindEvents();
    }

    _initLayers() {
        if (this.map.isStyleLoaded()) {
            this._addLayers();
        } else {
            this.map.once('load', () => this._addLayers());
        }
    }

    _addLayers() {
        this.map.addSource('polygon-editor', {type: 'geojson', data: this._emptyData()});
        this.map.addLayer({
            id: 'polygon-editor-fill',
            type: 'fill',
            source: 'polygon-editor',
            filter: ['==', ['geometry-type'], 'Polygon'],
            paint: {'fill-color': '#F5DEB3', 'fill-opacity': 0.3}
        });
        this.map.addLayer({
            id: 'polygon-editor-outline',
            type: 'line',
            source: 'polygon-editor',
            filter: ['all', ['==', ['geometry-type'], 'LineString'], ['==', ['get', 'kind'], 'outline']],
            paint: {'line-color': '#F5DEB3', 'line-width': 2}
        });
        this.map.addLayer({
            id: 'polygon-editor-preview',
            type: 'line',
            source: 'polygon-editor',
            filter: ['all', ['==', ['geometry-type'], 'LineString'], ['==', ['get', 'kind'], 'preview']],
            paint: {'line-color': '#6b7280', 'line-width': 2, 'line-dasharray': [2, 2]}
        });
        this.map.addLayer({
            id: 'polygon-editor-vertices',
            type: 'circle',
            source: 'polygon-editor',
            filter: ['==', ['geometry-type'], 'Point'],
            paint: {'circle-radius': 6, 'circle-color': '#F5DEB3', 'circle-stroke-width': 2, 'circle-stroke-color': '#ffffff'}
        });

        this.map.on('mousedown', 'polygon-editor-vertices', (e) => this._startDrag(e));
        this.map.on('touchstart', 'polygon-editor-vertices', (e) => this._startDrag(e));
        this.map.on('mouseenter', 'polygon-editor-vertices', () => {
            this.map.getCanvas().style.cursor = 'pointer';
        });
        this.map.on('mouseleave', 'polygon-editor-vertices', () => {
            this.map.getCanvas().style.cursor = '';
        });

        this._layersReady = true;
        this._redraw();
    }

    _bindEvents() {
        this.map.on('click', (e) => {
            if (this.isDragging || this.suspended) return;
            if (this._layersReady) {
                const hits = this.map.queryRenderedFeatures(
                    [[e.point.x - 8, e.point.y - 8], [e.point.x + 8, e.point.y + 8]],
                    {layers: ['polygon-editor-vertices']});
                if (hits.length > 0) return;
            }
            this.addPolygonPoint({lat: e.lngLat.lat, lng: e.lngLat.lng});
        });

        this.map.on('mousemove', (e) => this._moveDrag(e));
        this.map.on('mouseup', () => this._endDrag());
        this.map.on('touchmove', (e) => this._moveDrag(e));
        this.map.on('touchend', () => this._endDrag());
        document.addEventListener('mouseup', () => this._endDrag());
        document.addEventListener('touchend', () => this._endDrag());

        document.addEventListener('keydown', (e) => {
            if (e.target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(e.target.tagName)) return;
            if (e.key === 'Enter' && e.ctrlKey) {
                this.savePolygon();
            } else if (e.key === 'z' && e.ctrlKey) {
                e.preventDefault();
                this.undoLastPoint();
            }
        });
    }

    _startDrag(e) {
        if (!e.features || e.features.length === 0) return;
        this.isDragging = true;
        this.dragMoved = false;
        this.dragStartPoint = e.point;
        this.dragIndex = e.features[0].properties.idx;
        this.map.dragPan.disable();
        e.preventDefault();
    }

    _endDrag() {
        if (!this.isDragging) return;
        this.isDragging = false;
        this.map.dragPan.enable();
        const idx = this.dragIndex;
        this.dragIndex = -1;
        if (!this.dragMoved && idx >= 0 && idx < this.polygonPoints.length) {
            this.removePolygonPoint(idx);
        } else {
            this._redraw();
        }
    }

    _moveDrag(e) {
        if (!this.isDragging || this.dragIndex < 0) return;
        const dx = e.point.x - this.dragStartPoint.x;
        const dy = e.point.y - this.dragStartPoint.y;
        if (Math.hypot(dx, dy) > 3) {
            this.dragMoved = true;
        }
        this.polygonPoints[this.dragIndex] = {lat: e.lngLat.lat, lng: e.lngLat.lng};
        this._redraw();
    }

    suspend() {
        this.suspended = true;
    }

    resume() {
        this.suspended = false;
    }

    setPlace(placeData, animate = false) {
        this.placeId = placeData.id;
        this.clearPolygon();
        this._updateCenterMarker(placeData.lat, placeData.lng, placeData.name);
        if (placeData.polygon && placeData.polygon.length >= 3) {
            this.loadExistingPolygon(placeData.polygon);
        }
        this._renderNearby();
        const center = [placeData.lng, placeData.lat];
        if (animate) {
            this.map.flyTo({center, zoom: 19, duration: 800});
        } else {
            this.map.jumpTo({center, zoom: 19});
        }
    }

    addPolygonPoint(point) {
        if (typeof point.lat !== 'number' || typeof point.lng !== 'number' ||
            isNaN(point.lat) || isNaN(point.lng)) {
            return;
        }
        this.polygonPoints.push({lat: point.lat, lng: point.lng});
        this._redraw();
    }

    removePolygonPoint(index) {
        if (index >= 0 && index < this.polygonPoints.length) {
            this.polygonPoints.splice(index, 1);
            this._redraw();
        }
    }

    undoLastPoint() {
        if (this.polygonPoints.length > 0) {
            this.removePolygonPoint(this.polygonPoints.length - 1);
        }
    }

    clearPolygon() {
        this.polygonPoints = [];
        this._redraw();
    }

    getPolygonPoints() {
        return this.polygonPoints.map(p => ({lat: p.lat, lng: p.lng}));
    }

    savePolygon() {
        const saveBtn = document.getElementById('save-btn');
        if (!saveBtn.disabled || saveBtn.classList.contains('btn-loading')) {
            document.getElementById('polygon-form').submit();
        }
    }

    loadExistingPolygon(polygonData) {
        if (polygonData && polygonData.length >= 3) {
            polygonData.forEach(point => {
                const lat = point.latitude ?? point.lat;
                const lng = point.longitude ?? point.lng;
                this.addPolygonPoint({lat, lng});
            });
        }
    }

    loadNearbyPlaces(nearbyPlaces) {
        this.nearbyPlaces = nearbyPlaces || [];
        this._renderNearby();
    }

    _renderNearby() {
        this.nearbyMarkers.forEach(marker => marker.remove());
        this.nearbyMarkers = [];
        this.nearbyPlaces
            .filter(place => place.id !== this.placeId)
            .forEach(place => {
                const el = document.createElement('div');
                el.className = 'nearby-place-marker';
                el.title = place.name || 'Unnamed Place';
                el.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (this.onNearbyPlaceSelect) {
                        this.onNearbyPlaceSelect(place.id);
                    }
                });
                const marker = new maplibregl.Marker({element: el})
                    .setLngLat([place.lng, place.lat])
                    .addTo(this.map);
                this.nearbyMarkers.push(marker);
            });
    }

    _updateCenterMarker(lat, lng, name) {
        if (!this.centerMarker) {
            const el = document.createElement('div');
            el.className = 'place-centroid-marker';
            this.centerMarker = new maplibregl.Marker({element: el})
                .setLngLat([lng, lat])
                .addTo(this.map);
        } else {
            this.centerMarker.setLngLat([lng, lat]);
        }
        this.centerMarker.getElement().title = (name || 'Place') + ' (center)';
    }

    _redraw() {
        if (this._layersReady) {
            const features = [];
            this.polygonPoints.forEach((p, idx) => {
                features.push({
                    type: 'Feature',
                    geometry: {type: 'Point', coordinates: [p.lng, p.lat]},
                    properties: {idx}
                });
            });
            if (this.polygonPoints.length >= 3) {
                const ring = this.polygonPoints.map(p => [p.lng, p.lat]);
                ring.push([this.polygonPoints[0].lng, this.polygonPoints[0].lat]);
                features.push({
                    type: 'Feature',
                    geometry: {type: 'Polygon', coordinates: [ring]},
                    properties: {}
                });
            } else if (this.polygonPoints.length === 2) {
                const first = this.polygonPoints[0];
                const last = this.polygonPoints[1];
                features.push({
                    type: 'Feature',
                    geometry: {
                        type: 'LineString',
                        coordinates: [[first.lng, first.lat], [last.lng, last.lat], [first.lng, first.lat]]
                    },
                    properties: {kind: 'preview'}
                });
            }
            this.map.getSource('polygon-editor').setData({type: 'FeatureCollection', features});
        }
        this.updateSaveButton();
        if (typeof this.onPolygonChange === 'function') {
            this.onPolygonChange(this.getPolygonPoints());
        }
    }

    _emptyData() {
        return {type: 'FeatureCollection', features: []};
    }

    updateSaveButton() {
        const saveBtn = document.getElementById('save-btn');
        const polygonDataInput = document.getElementById('polygonData');
        const saveStatusElement = document.getElementById('save-status');

        if (!saveBtn || !polygonDataInput) return;

        if (this.polygonPoints.length === 0) {
            saveBtn.disabled = false;
            polygonDataInput.value = '';
            if (saveStatusElement) {
                saveStatusElement.textContent = '';
                saveStatusElement.style.display = 'none';
            }
        } else if (this.polygonPoints.length >= 3) {
            saveBtn.disabled = false;
            polygonDataInput.value = JSON.stringify(this.getPolygonPoints());
            if (saveStatusElement) {
                saveStatusElement.textContent = '';
                saveStatusElement.style.display = 'none';
            }
        } else {
            saveBtn.disabled = true;
            polygonDataInput.value = '';
            if (saveStatusElement) {
                saveStatusElement.textContent = `Polygon needs at least 3 points (currently ${this.polygonPoints.length})`;
                saveStatusElement.style.display = 'block';
            }
        }
    }
}
