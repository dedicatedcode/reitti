(function () {
    'use strict';

    const FILL_COLOR = '#F0C05A';
    const LINE_COLOR = '#E8A93B';
    const CASING_COLOR = '#2b2416';

    let currentMap = null;
    let currentOsmId = null;
    let renderToken = 0;
    let resizeObserver = null;

    async function resolveMapStyle() {
        const styles = Array.isArray(window.reittiCustomMapStyles) ? window.reittiCustomMapStyles : [];
        if (styles.length === 0) {
            return null;
        }
        const activeId = window.reittiActiveMapStyleId || window.localStorage?.getItem('mapStyleId');
        const style = styles.find(candidate => candidate.id === activeId) || styles[0];
        if (!style) {
            return null;
        }
        if (style.mapType === 'raster') {
            return style.styleUrl;
        }
        if (style.mapType !== 'vector') {
            return null;
        }

        let definition;
        if (style.styleInputType === 'url') {
            try {
                const response = await fetch(style.styleUrl, {credentials: 'same-origin'});
                if (!response.ok) {
                    return null;
                }
                definition = await response.json();
            } catch (error) {
                console.warn('Unable to load map style', error);
                return null;
            }
        } else if (typeof style.styleInput === 'string') {
            try {
                definition = JSON.parse(style.styleInput);
            } catch (error) {
                console.warn('Unable to parse map style', error);
                return null;
            }
        } else {
            definition = style.styleInput;
        }

        // The details map is a small 2D preview: no terrain, no hillshade and no
        // 3D buildings. Those only add raster-dem/extrusion work that can delay
        // (or block) the first render.
        if (definition && typeof definition === 'object') {
            delete definition.terrain;
            if (Array.isArray(definition.layers)) {
                definition.layers = definition.layers.filter(layer =>
                    layer.type !== 'hillshade'
                    && layer.type !== 'fill-extrusion'
                    && layer.id !== 'building-3d');
            }
        }
        return definition;
    }

    function configureWorker() {
        if (!window.maplibregl?.setWorkerUrl) {
            return;
        }
        const contextPath = (window.contextPath || '').replace(/\/$/, '');
        window.maplibregl.setWorkerUrl(`${window.location.origin}${contextPath}/js/vendor/maplibre-gl-worker.6.16.0.mjs`);
    }

    function destroyMap() {
        if (resizeObserver) {
            resizeObserver.disconnect();
            resizeObserver = null;
        }
        if (currentMap) {
            try {
                currentMap.remove();
            } catch (error) {
                console.warn('Could not dispose coverage details map', error);
            }
            currentMap = null;
        }
        currentOsmId = null;
    }

    function nextFrame() {
        return new Promise(resolve => requestAnimationFrame(resolve));
    }

    async function waitForSize(container, token) {
        for (let attempt = 0; attempt < 20; attempt++) {
            if (token !== renderToken || !container.isConnected) {
                return false;
            }
            if (container.clientWidth > 0 && container.clientHeight > 0) {
                return true;
            }
            await nextFrame();
        }
        return container.clientWidth > 0 && container.clientHeight > 0;
    }

    function boundsFromGeoJson(geometry) {
        const bounds = new maplibregl.LngLatBounds();
        const visit = (coordinates) => {
            if (typeof coordinates[0] === 'number') {
                bounds.extend(coordinates);
                return;
            }
            coordinates.forEach(visit);
        };
        const visitGeometry = (g) => {
            if (g && g.coordinates) {
                visit(g.coordinates);
            }
        };
        if (!geometry) {
            return null;
        }
        if (geometry.type === 'GeometryCollection') {
            (geometry.geometries || []).forEach(visitGeometry);
        } else {
            visitGeometry(geometry);
        }
        return bounds.isEmpty() ? null : bounds;
    }

    async function render() {
        const token = ++renderToken;
        const container = document.getElementById('coverage-details-map');
        if (!container) {
            destroyMap();
            return;
        }
        const osmId = container.dataset.osmId;
        if (!osmId) {
            destroyMap();
            return;
        }
        if (currentMap && currentOsmId === osmId && container.contains(currentMap.getCanvas())) {
            return;
        }
        destroyMap();

        const style = await resolveMapStyle();
        if (!style || !window.maplibregl) {
            return;
        }
        configureWorker();

        const contextPath = (window.contextPath || '').replace(/\/$/, '');
        let payload;
        try {
            const response = await fetch(`${contextPath}/api/v2/coverage/boundary/${osmId}`, {
                headers: {Accept: 'application/json'},
                credentials: 'same-origin'
            });
            if (!response.ok) {
                return;
            }
            payload = await response.json();
        } catch (error) {
            console.warn('Unable to load boundary for', osmId, error);
            return;
        }
        if (token !== renderToken || !container.isConnected || !payload || !payload.geojson) {
            return;
        }
        let geometry;
        try {
            geometry = typeof payload.geojson === 'string' ? JSON.parse(payload.geojson) : payload.geojson;
        } catch (error) {
            console.warn('Unable to parse boundary geojson', error);
            return;
        }

        if (!await waitForSize(container, token)) {
            return;
        }

        const map = new maplibregl.Map({
            container,
            style,
            interactive: false,
            attributionControl: false,
            renderWorldCopies: false,
            dragRotate: false
        });
        currentMap = map;
        currentOsmId = osmId;

        resizeObserver = new ResizeObserver(() => {
            if (currentMap === map) {
                map.resize();
            }
        });
        resizeObserver.observe(container);

        map.on('error', event => console.warn('Coverage details map error', event?.error?.message || event));
        map.on('load', () => {
            if (currentMap !== map) {
                return;
            }
            map.addSource('region', {
                type: 'geojson',
                data: {type: 'Feature', properties: {}, geometry}
            });
            map.addLayer({
                id: 'region-fill',
                type: 'fill',
                source: 'region',
                paint: {'fill-color': FILL_COLOR, 'fill-opacity': 0.32}
            });
            map.addLayer({
                id: 'region-casing',
                type: 'line',
                source: 'region',
                paint: {'line-color': CASING_COLOR, 'line-width': 4.5, 'line-opacity': 0.45}
            });
            map.addLayer({
                id: 'region-outline',
                type: 'line',
                source: 'region',
                paint: {'line-color': LINE_COLOR, 'line-width': 2.4, 'line-opacity': 1}
            });

            let bounds = boundsFromGeoJson(geometry);
            if (!bounds && payload.bbox) {
                bounds = new maplibregl.LngLatBounds(
                    [payload.bbox.minLon, payload.bbox.minLat],
                    [payload.bbox.maxLon, payload.bbox.maxLat]
                );
            }
            if (bounds) {
                map.fitBounds(bounds, {padding: 14, duration: 0, maxZoom: 9});
            }
            map.resize();
            container.classList.add('is-loaded');
        });
    }

    document.addEventListener('DOMContentLoaded', render);
    document.body.addEventListener('htmx:afterSwap', render);
})();
