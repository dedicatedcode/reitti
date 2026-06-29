class MapRenderer {
     static getCustomMapStyles() {
        return Array.isArray(window.reittiCustomMapStyles)
            ? window.reittiCustomMapStyles
            : [];
    }

    static getMapStyles() {
        return [
            ...MapRenderer.getCustomMapStyles()
        ];
    }

    static getMapStyleValue(mapStyle) {
        if (mapStyle?.mapType === 'vector') {
            if (mapStyle?.styleInputType === 'url') {
                return mapStyle?.styleUrl;
            } else if (mapStyle?.styleInputType === 'json') {
                return MapRenderer._cloneStaticStyleDefinition(mapStyle.styleInput);
            } else {
                throw new Error('Invalid vector style input type');
            }
        } else if (mapStyle?.mapType === 'raster') {
            if (mapStyle?.rasterSourceInputType === 'json-url') {
                const tileJsonUrl = mapStyle?.dataSource?.tileJsonUrl;
                if (!tileJsonUrl) {
                    throw new Error('Raster style missing tileJsonUrl');
                }
                return {
                    version: 8,
                    name: mapStyle.label || 'Raster',
                    sources: {
                        'raster-tiles': {
                            type: 'raster',
                            url: tileJsonUrl,
                            tileSize: mapStyle?.dataSource?.tileSize || 256,
                            attribution: mapStyle?.dataSource?.attribution || ''
                        }
                    },
                    layers: [{
                        id: 'raster-layer',
                        type: 'raster',
                        source: 'raster-tiles',
                        minzoom: mapStyle?.dataSource?.minzoom || 0,
                        maxzoom: mapStyle?.dataSource?.maxzoom || 22
                    }]
                };
            } else if (mapStyle?.rasterSourceInputType === 'url-template') {
                // Return a complete raster style object
                const tileUrl = mapStyle?.dataSource?.tileUrlTemplate;
                if (!tileUrl) {
                    throw new Error('Raster style missing tile URL template');
                }
                return {
                    version: 8,
                    name: mapStyle.label || 'Raster',
                    sources: {
                        'raster-tiles': {
                            type: 'raster',
                            tiles: [tileUrl],
                            tileSize: mapStyle?.dataSource?.tileSize || 256,
                            attribution: mapStyle?.dataSource?.attribution || ''
                        }
                    },
                    layers: [{
                        id: 'raster-layer',
                        type: 'raster',
                        source: 'raster-tiles',
                        minzoom: mapStyle?.dataSource?.minzoom || 0,
                        maxzoom: mapStyle?.dataSource?.maxzoom || 22
                    }]
                };
            } else {
                throw new Error('Invalid raster style input type');
            }
        } else {
            throw new Error('Invalid map type');
        }
    }

    static _cloneStaticStyleDefinition(definition) {
        if (typeof definition === 'string') {
            return JSON.parse(definition);
        } else {
            return definition;
        }
    }

    static getActiveMapStyleId() {
        const activeStyleId = window.reittiActiveMapStyleId || window.localStorage?.getItem('mapStyleId');
        return MapRenderer.getMapStyles().some(style => style.id === activeStyleId)
            ? activeStyleId
            : MapRenderer.getDefaultMapStyleId();
    }

    static setActiveMapStyleId(mapStyleId) {
        window.reittiActiveMapStyleId = mapStyleId;
        window.localStorage?.setItem('mapStyleId', mapStyleId);
    }

    static getMapStyle(styleId) {
        const styles = MapRenderer.getMapStyles();
        return styles.find(style => style.id === styleId) || styles[0];
    }

    static getDefaultMapStyleId() {
        const styles = MapRenderer.getMapStyles();
        return styles[0]?.id || 'reitti';
    }

    static ensureRTLTextPlugin() {
        if (MapRenderer.rtlTextPluginConfigured || !window.maplibregl?.setRTLTextPlugin) return;

        const pluginUrl = MapRenderer.getMapStyles().find(style => style.rtlTextPluginUrl)?.rtlTextPluginUrl;
        if (!pluginUrl) return;

        try {
            maplibregl.setRTLTextPlugin(pluginUrl, null, true);
        } catch (error) {
            console.warn('Unable to configure RTL text plugin:', error);
        } finally {
            MapRenderer.rtlTextPluginConfigured = true;
        }
    }

    constructor(element, userSettings, initialViewState, viewConfig = {}) {
        MapRenderer.ensureRTLTextPlugin();
        this._debug = false;
        this.userSettings = userSettings;
        this.transitionQueue = Promise.resolve();
        this.element = document.getElementById(element);
        this.element.classList.add('is-loading');
        this.TERRAIN_PROFILES = {
            // Mapbox/MapTiler standard: (R * 256 * 256 + G * 256 + B) * 0.1 - 10000
            MAPTILER: {
                decoder: { rScaler: 6553.6, gScaler: 25.6, bScaler: 0.1, offset: -10000 },
                scale: 1.5
            },
            // Terrarium: (R * 256 + G + B / 256) - 32768
            TERRARIUM: {
                decoder: { rScaler: 256, gScaler: 1, bScaler: 1 / 256, offset: -32768 },
                scale: 1.5
            }
        };
        const defaultViewConfig = {
            fitConfig: {
                padding: {
                    top: 50,
                    bottom: 100,
                    right: 100,
                    left: 450
                },
                maxZoom: 15,
                duration: 1000,
                essential: true
            }
        }

        this.selectedManager = null;
        this.viewConfig = {
            ...defaultViewConfig,
            ...viewConfig,
            fitConfig: {
                ...defaultViewConfig.fitConfig,
                ...(viewConfig.fitConfig || {}),
                padding: {
                    ...defaultViewConfig.fitConfig.padding,
                    ...(viewConfig.fitConfig?.padding || {})
                }
            },
            mapDataProviders: viewConfig.mapDataProviders || []
        };
        this.gpsDataManagers = []

        this.viewState = initialViewState;
        this.viewState.mapStyleId = this.viewState.mapStyleId || MapRenderer.getActiveMapStyleId();
        this.currentMapStyle = MapRenderer.getMapStyle(this.viewState.mapStyleId);
        this.satelliteHiddenPaintProperties = new Map();
        this._pitchBearingAllowed = true;

        const mapOptions = {
            interleaved: true,
            container: element,
            style: MapRenderer.getMapStyleValue(this.currentMapStyle),
            center: [userSettings.homeLongitude, userSettings.homeLatitude],
            pitch: this.viewState.is3d ? 45 : 0,
            maxPitch: 85,
            minZoom: 2,
            attributionControl: false
        };
        if (this.viewState.fixed) {
            mapOptions.interactive = false;
        }
        this.map = new maplibregl.Map(mapOptions);

        if (!(this.viewState.fixed || this.viewState.hideAttribution)) {
            this.map.addControl(new maplibregl.AttributionControl(), 'top-right');
        }
        if (this.viewState.showPhotos) {
            this.photosManager = new PhotoClusterManager(this.map, {
                clusterRadius: 80,
                iconSize: 56
            });
        }
        this.avatarMarkers = new Map(); // Store markers by user ID
        this.showAvatars = false;

        this.terrainLayer = null;
        this.deckOverlay = new deck.MapboxOverlay({
            layers: []
        });
        this.map.addControl(this.deckOverlay);
        this.currentTime = 0;

        const defaultDeckParams = {
            trips: {
                trailLength: 700,
                cometWidth: 3,
                cometOpacity: 155,
                shadowWidth: 3,
                shadowOpacity: 155,
                pathWidth: 1.5,
                pathOpacity: 25,
                staticPathWidth: 2.5,
                staticPathOpacity: 200,
            },
            visits: {
                minZoom: 12,
                polygonMinZoom: 16,
                radius: 1,
                opacity: 66,
                polygonOpacity: 66,
                lineWidth: 1
            },
            bundled: {
                precision: 0.0005,
                weight: 0.5,
                staticPathWidth: 2.5,
                staticPathOpacity: 200,
                pathWidth: 1.5,
                pathOpacity: 40,
                cometWidth: 2,
                cometOpacity: 15,
                shadowWidth: 3,
                shadowOpacity: 30,
            }
        };

        this.deckParams = {
            trips: {
                ...defaultDeckParams.trips,
                ...(this.viewConfig.deckParams?.trips || {})
            },
            visits: {
                ...defaultDeckParams.visits,
                ...(this.viewConfig.deckParams?.visits || {})
            },
            bundled: {
                ...defaultDeckParams.bundled,
                ...(this.viewConfig.deckParams?.bundled || {})
            }
        };
        this.bounds = [];

        this.highlightLayer = null;
        this._initialLoadPromise = this._initializeInitialStyle();

        this._layerInstances = [];           // Current array of deck.gl layer instances
        this._layerContextKey = null;         // Structural fingerprint of current layers
        this._terrainExtension = null;        // Cached TerrainExtension instance
        this._terrainExtensionsArray = null;  // Cached [extension] array (stable ref)
        this._emptyExtensionsArray = [];      // Stable empty array ref
        this._cachedVisitPlaces = new Map();  // managerId -> places array (stable ref)
        this._cachedFilteredPolygons = new Map(); // managerId -> filtered places (stable ref)
        this._cachedLayerData = new Map();    // cacheKey -> layerData (per-build cache)
        this._currentZoom = 0;
        this._zoomDebounceId = null;


        this._setup();
    }

    /**
     * Returns a FRESH extensions array each call, but reuses the
     * same TerrainExtension instance. deck.gl diffs extensions by
     * the extension OBJECT identity, not the array reference, so
     * this avoids re-initialization while preventing array mutation
     * conflicts between layers.
     */
    _getTerrainExtensions() {
        if (this.viewState.renderTerrain) {
            if (!this._terrainExtension) {
                this._terrainExtension = new deck._TerrainExtension({
                    elevationLayerId: 'terrain-loader'
                });
            }
            // New array each call — deck.gl mutates the array during init
            return [this._terrainExtension];
        }
        return [];
    }

    /**
     * PathLayer has no getTimestamps accessor. When the data object
     * from getLayerData() contains getTimestamps in attributes,
     * deck.gl asserts that every attribute maps to a known accessor.
     *
     * This creates a lightweight wrapper that only exposes getPath.
     * The inner Float32Array buffer retains the SAME reference, so
     * deck.gl will not re-upload to GPU despite the new wrapper object.
     *
     * Only called during structural rebuilds (_buildLayers), not during
     * animation updates (_updateAnimatedLayers), so the wrapper overhead
     * is negligible.
     */
    _stripForPathLayer(layerData) {
        if (!layerData || !layerData.attributes) return layerData;
        return {
            length: layerData.length,
            startIndices: layerData.startIndices,
            attributes: {
                getPath: layerData.attributes.getPath
            }
        };
    }

    /**
     * Returns a string that captures every structural input to the layer tree.
     * If this string hasn't changed, we skip the full rebuild and only
     * update animation/zoom-dependent props.
     */
    _getLayerContextKey() {
        const managers = this.gpsDataManagers
            .filter(m => this.selectedManager == null || m.id === this.selectedManager)
            .map(m => `${m.id}:${m.cursor}:${m.cleanedCursor}:${m.snappedVersion}:${m.loadingState}:${m.visits?.length || 0}`)
            .join('|');

        return JSON.stringify({
            managers,
            viewMode: this.viewState.viewMode,
            aggregated: this.viewState.aggregated,
            renderTerrain: this.viewState.renderTerrain,
            animating: this.viewState.animating,
            selectedManager: this.selectedManager,
            highlight: this.highlightLayer ? '1' : '0',
        });
    }

    /**
     * Within a single _buildLayers call, getLayerData is called multiple
     * times with identical arguments (once per layer type). Cache the
     * result per-build so we only call the manager once.
     */
    _getCachedLayerData(manager, mode, isAggregate) {
        const key = `${manager.id}:${mode}:${isAggregate}`;
        if (this._cachedLayerData.has(key)) {
            return this._cachedLayerData.get(key);
        }
        const data = manager.getLayerData(mode, isAggregate);
        this._cachedLayerData.set(key, data);
        return data;
    }

    /**
     * Returns STABLE references for visit data arrays.
     * `places.filter(...)` creates a new array each call, which forces
     * deck.gl to re-upload. We cache until visits change.
     */
    _getCachedVisitPlaces(manager) {
        const version = `${manager.id}:${manager.visits?.length || 0}:${manager.snappedVersion}`;
        if (this._cachedVisitPlaces.has(version)) {
            return this._cachedVisitPlaces.get(version);
        }
        // Clear old entries for this manager
        for (const key of this._cachedVisitPlaces.keys()) {
            if (key.startsWith(`${manager.id}:`)) {
                this._cachedVisitPlaces.delete(key);
            }
        }
        const places = manager.visits || [];
        const filteredPolygons = places.filter(p => p.polygon);
        this._cachedVisitPlaces.set(version, {places, filteredPolygons});
        return {places, filteredPolygons};
    }
    /**
     * Debug: Log the raw layer data from getLayerData
     */
    _debugLogLayerData(label, layerKey, layerData, bufferMode) {
        console.groupCollapsed(`%c  [DEBUG] layerData for ${label} (${layerKey})`, 'color: #888');
        console.log('  buffer mode:', bufferMode);
        console.log('  length (paths):', layerData?.length);
        console.log('  startIndices:', layerData?.startIndices);
        console.log('  startIndices.length:', layerData?.startIndices?.length);
        console.log('  startIndices[0..5]:', Array.from(layerData?.startIndices?.slice(0, 6) || []));
        console.log('  startIndices[last]:', layerData?.startIndices?.[layerData.startIndices.length - 1]);
        console.log('  attributes keys:', Object.keys(layerData?.attributes || {}));

        if (layerData?.attributes?.getPath) {
            const pa = layerData.attributes.getPath;
            console.log('  getPath:');
            console.log('    value type:', pa.value?.constructor?.name);
            console.log('    value.length:', pa.value?.length);
            console.log('    value.byteLength:', pa.value?.byteLength);
            console.log('    size:', pa.size);
            console.log('    stride (bytes):', pa.stride);
            console.log('    offset (bytes):', pa.offset);
            console.log('    stride/4 (floats):', pa.stride / 4);
            console.log('    offset/4 (floats):', pa.offset / 4);

            // Sample first 3 values
            if (pa.value && pa.value.length > 0) {
                const floatsPerVertex = pa.stride / 4;
                const firstIdx = pa.offset / 4;
                console.log('    first vertex [0]:', [pa.value[firstIdx], pa.value[firstIdx + 1], pa.value[firstIdx + 2]]);
                if (pa.value.length > floatsPerVertex) {
                    console.log('    first vertex [1]:', [pa.value[firstIdx + floatsPerVertex], pa.value[firstIdx + floatsPerVertex + 1], pa.value[firstIdx + floatsPerVertex + 2]]);
                }
            }
        }

        if (layerData?.attributes?.getTimestamps) {
            const ta = layerData.attributes.getTimestamps;
            console.log('  getTimestamps:');
            console.log('    value type:', ta.value?.constructor?.name);
            console.log('    value.length:', ta.value?.length);
            console.log('    size:', ta.size);
            console.log('    stride (bytes):', ta.stride);
            console.log('    offset (bytes):', ta.offset);
        }

        // Validate: total points should match startIndices[last]
        const lastIdx = layerData?.startIndices?.[layerData.startIndices.length - 1];
        const expectedPoints = layerData?.attributes?.getPath?.value?.length / (layerData.attributes.getPath.stride / 4);
        console.log('  validation:');
        console.log('    startIndices[last]:', lastIdx);
        console.log('    expected points (value.length / strideFloats):', expectedPoints);
        console.log('    match:', lastIdx === expectedPoints);
        console.log('    length == startIndices.length - 1:', layerData.length === layerData.startIndices.length - 1);

        console.groupEnd();
    }

    /**
     * Debug: Log the stripped data that goes to PathLayer
     */
    _debugLogStrippedData(layerKey, strippedData) {
        console.groupCollapsed(`%c  [DEBUG] strippedData for PathLayer (${layerKey})`, 'color: #888');
        console.log('  length:', strippedData?.length);
        console.log('  startIndices:', strippedData?.startIndices);
        console.log('  startIndices.length:', strippedData?.startIndices?.length);
        console.log('  attributes keys:', Object.keys(strippedData?.attributes || {}));

        const pa = strippedData?.attributes?.getPath;
        if (pa) {
            console.log('  getPath.value === original value?', pa.value != null);
            console.log('  getPath.value type:', pa.value?.constructor?.name);
            console.log('  getPath.value.length:', pa.value?.length);
            console.log('  getPath.size:', pa.size);
            console.log('  getPath.stride:', pa.stride);
            console.log('  getPath.offset:', pa.offset);
        }

        // Check for common assertion triggers
        const issues = [];
        if (strippedData.length !== strippedData.startIndices.length - 1) {
            issues.push(`length (${strippedData.length}) !== startIndices.length - 1 (${strippedData.startIndices.length - 1})`);
        }
        if (pa && pa.stride % 4 !== 0) {
            issues.push(`stride (${pa.stride}) is not divisible by 4`);
        }
        if (pa && pa.offset % 4 !== 0) {
            issues.push(`offset (${pa.offset}) is not divisible by 4`);
        }
        if (pa && pa.size !== 2 && pa.size !== 3) {
            issues.push(`size (${pa.size}) is not 2 or 3`);
        }
        if (strippedData.startIndices && strippedData.length > 0) {
            const last = strippedData.startIndices[strippedData.startIndices.length - 1];
            const strideFloats = pa.stride / 4;
            const offsetFloats = pa.offset / 4;
            const maxPossiblePoints = (pa.value.length - offsetFloats) / strideFloats;
            if (last > maxPossiblePoints) {
                issues.push(`startIndices[last] (${last}) > max possible points (${maxPossiblePoints})`);
            }
            if (last === 0 && strippedData.length === 1) {
                issues.push('SINGLE EMPTY PATH: length=1, startIndices=[0,0] — PathLayer may assert on zero-length path');
            }
        }

        if (issues.length > 0) {
            console.warn('%c  ISSUES FOUND:', 'color: #ff0000; font-weight: bold');
            issues.forEach(i => console.warn('    ⚠', i));
        } else {
            console.log('  ✓ no issues detected by heuristic checks');
        }

        console.groupEnd();
    }
    /**
     * Full layer rebuild. Called only when structural inputs change
     * (data loaded, viewMode/aggregated/terrain/animating toggled, manager set changed).
     *
     * If nothing structural changed since last call, delegates to
     * _updateAnimatedLayers() for a lightweight currentTime-only update.
     */
    _buildLayers() {
        const contextKey = this._getLayerContextKey();
        const keyChanged = contextKey !== this._layerContextKey;

        if (this._debug && keyChanged) {
            console.log('%c[_buildLayers] FULL REBUILD', 'color: #ff6600; font-weight: bold');
            console.log('  old key:', this._layerContextKey);
            console.log('  new key:', contextKey);
        } else if (this._debug && !keyChanged && this._layerInstances.length > 0) {
            console.log('%c[_buildLayers] SKIP → _updateAnimatedLayers', 'color: #00aa00');
        }

        if (!keyChanged && this._layerInstances.length > 0) {
            this._updateAnimatedLayers();
            return;
        }

        // If key changed, figure out WHY
        if (this._debug && keyChanged && this._layerContextKey !== null) {
            const oldParsed = JSON.parse(this._layerContextKey);
            const newParsed = JSON.parse(contextKey);
            const changes = [];
            for (const k of Object.keys(newParsed)) {
                if (JSON.stringify(oldParsed[k]) !== JSON.stringify(newParsed[k])) {
                    changes.push(`  ${k}: ${JSON.stringify(oldParsed[k])} → ${JSON.stringify(newParsed[k])}`);
                }
            }
            if (changes.length > 0) {
                console.log('%c[_buildLayers] CHANGED FIELDS:', 'color: #ff0000');
                changes.forEach(c => console.log(c));
            }
        }

        this._layerContextKey = contextKey;
        this._cachedLayerData.clear();

        const allLayers = [];

        if (this.viewState.renderTerrain && this.terrainLayer) {
            allLayers.push(this.terrainLayer);
        }

        const extensions = this._getTerrainExtensions();
        const terrainDrawMode = this.viewState.renderTerrain ? 'offset' : undefined;

        this.gpsDataManagers.forEach(manager => {
            if (this.selectedManager != null && manager.id !== this.selectedManager) return;

            const layerKey = `${manager.id}-${this.viewState.aggregated ? 'agg' : 'lin'}-${this.viewState.renderTerrain ? 'terrain' : 'flat'}`;
            const totalTimeSpanSec = this.viewState.aggregated ? 24 * 60 * 60 : (manager.maxTimestamp - manager.minTimestamp);
            const calculatedTrail = Math.max(1800, totalTimeSpanSec * 0.02);

            if (manager.cursor > 0) {
                if (this.viewState.viewMode === 'BUNDLED') {
                    allLayers.push(...this._buildBundleLayers(layerKey, manager, extensions, terrainDrawMode, calculatedTrail));
                } else {
                    const buffer = this.viewState.viewMode === 'LINEAR' ? 'cleaned' : 'raw';
                    const cursor = this.viewState.viewMode === 'LINEAR' ? manager.cleanedCursor : manager.cursor;
                    const layerData = this._getCachedLayerData(manager, buffer, this.viewState.aggregated);

                    if (this._debug) {
                        this._debugLogLayerData('static-fixed', layerKey, layerData, buffer);
                    }

                    const strippedData = this._stripForPathLayer(layerData);

                    if (this._debug) {
                        this._debugLogStrippedData(layerKey, strippedData);
                    }

                    try {
                        allLayers.push(new deck.PathLayer({
                            id: `paths-static-fixed-${layerKey}`,
                            data: strippedData,
                            positionFormat: 'XYZ',
                            getColor: [...manager.color, this.deckParams.trips.staticPathOpacity],
                            getWidth: this.deckParams.trips.staticPathWidth,
                            widthMinPixels: this.deckParams.trips.staticPathWidth,
                            visible: true,
                            capRounded: true,
                            jointRounded: true,
                            extensions: extensions,
                            ...(terrainDrawMode && { terrainDrawMode }),
                            parameters: {
                                depthTest: true,
                                polygonOffsetFill: true
                            },
                            updateTriggers: {
                                data: [manager.buffer?.length, buffer],
                                getPath: [cursor, buffer, this.viewState.renderTerrain],
                                getColor: [manager.color],
                            }
                        }));
                    } catch (e) {
                        console.error('%c[_buildLayers] PathLayer creation FAILED', 'color: #ff0000; font-weight: bold');
                        console.error('  layerKey:', layerKey);
                        console.error('  error:', e);
                        console.error('  strippedData:', strippedData);
                        throw e;
                    }

                    if (this.viewState.animating) {
                        allLayers.push(new deck.TripsLayer({
                            id: `trips-shadow-${layerKey}`,
                            data: layerData,
                            positionFormat: 'XYZ',
                            getColor: manager.color,
                            opacity: this.deckParams.trips.shadowOpacity,
                            widthMinPixels: this.deckParams.trips.shadowWidth,
                            trailLength: calculatedTrail,
                            visible: this.viewState.animating,
                            currentTime: this.viewState.currentTime,
                            capRounded: true,
                            jointRounded: true,
                            parameters: { depthTest: false },
                            extensions: extensions,
                            ...(terrainDrawMode && { terrainDrawMode }),
                            updateTriggers: {
                                data: [manager.buffer?.length, buffer],
                                getPath: [cursor, buffer, this.viewState.renderTerrain],
                                getTimestamps: [this.viewState.aggregated],
                            }
                        }));

                        allLayers.push(new deck.TripsLayer({
                            id: `trips-core-${layerKey}`,
                            data: layerData,
                            positionFormat: 'XYZ',
                            getColor: [255, 255, 255, 100],
                            opacity: this.deckParams.trips.cometOpacity * this.viewState.animating,
                            widthMinPixels: this.deckParams.trips.cometWidth,
                            trailLength: calculatedTrail * 1.2,
                            currentTime: this.viewState.currentTime,
                            capRounded: true,
                            jointRounded: true,
                            extensions: extensions,
                            ...(terrainDrawMode && { terrainDrawMode }),
                            updateTriggers: {
                                data: [manager.buffer?.length, buffer],
                                getPath: [cursor, buffer, this.viewState.renderTerrain],
                                currentTime: [this.viewState.currentTime],
                                getTimestamps: [this.viewState.aggregated],
                            }
                        }));
                    }
                }
            }

            allLayers.push(...this._buildVisitLayers(layerKey, manager, extensions, terrainDrawMode));


        });

        if (this.highlightLayer) {
            allLayers.push(this.highlightLayer);
        }

        if (this._debug) {
            console.log('%c[_buildLayers] LAYERS BUILT', 'color: #0066ff; font-weight: bold');
            allLayers.forEach((l, i) => {
                console.log(`  [${i}] ${l.constructor.name} id="${l.props.id}" dataLen=${l.props.data?.length ?? 'n/a'} ext=${l.props.extensions?.length ?? 0}`);
            });
        }

        this._layerInstances = allLayers;
        this.deckOverlay.setProps({ layers: allLayers });


        if (this.map && typeof this.map.triggerRepaint === 'function') {
            this.map.resize();
        }
    }
    _updateAnimatedLayers() {
        if (this._layerInstances.length === 0) {
            if (this._debug) console.warn('[_updateAnimatedLayers] NO LAYERS — was _buildLayers ever called?');
            return;
        }

        if (this._debug) {
            console.log('%c[_updateAnimatedLayers] called', 'color: #00aa00');
            console.log('  currentTime:', this.viewState.currentTime);
            console.log('  _currentZoom:', this._currentZoom);
            console.log('  layer count:', this._layerInstances.length);
        }

        const isOverview = !this.viewState.animating;
        const currentTime = this.viewState.currentTime;
        const zoom = this._currentZoom;

        let reused = 0, cloned = 0, recreated = 0;

        const updated = this._layerInstances.map(layer => {
            const id = layer.props.id;

            if (id.includes('paths-static-fixed') ||
                id.includes('bundled-paths-static') ||
                id === 'highlight-segment' ||
                id === 'terrain-loader') {
                reused++;
                return layer;
            }

            if (layer instanceof deck.TripsLayer) {
                cloned++;
                return layer.clone({ currentTime: this.viewState.currentTime });
            }

            if (id.startsWith('visit-') || id.startsWith('place-')) {
                recreated++;
                const manager = this.gpsDataManagers.find(m => id.includes(`${m.id}-`));
                if (manager) {
                    const extensions = this._getTerrainExtensions();
                    const terrainDrawMode = this.viewState.renderTerrain ? 'offset' : undefined;
                    return this._recreateVisitLayer(id, manager, extensions, terrainDrawMode, isOverview, currentTime, zoom);
                }
            }

            return layer;
        });

        if (this._debug) {
            console.log(`  reused=${reused} cloned=${cloned} recreated=${recreated}`);
        }

        this._layerInstances = updated;
        this.deckOverlay.setProps({ layers: updated });
    }

    _recreateVisitLayer(id, manager, extensions, terrainDrawMode, isOverview, currentTime, zoom) {
        const {places, filteredPolygons} = this._getCachedVisitPlaces(manager);
        const color = manager.color;
        const dp = this.deckParams.visits;

        if (id.startsWith('visit-polygons')) {
            const layerKey = id.replace('visit-polygons-', '');
            return new deck.PolygonLayer({
                id: id,
                data: filteredPolygons, // STABLE ref from cache
                getPolygon: d => {
                    if (!d.polygon || !Array.isArray(d.polygon)) return [];
                    return d.polygon.map(point => [point.longitude, point.latitude]);
                },
                getFillColor: d => [...color, dp.polygonOpacity],
                getLineColor: d => [...color, 255],
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: isOverview && zoom > dp.polygonMinZoom,
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getFillColor: [currentTime],
                    visible: [isOverview, zoom],
                    data: manager.visits?.length
                }
            });
        }

        if (id.startsWith('place-outer-circles')) {
            return new deck.ScatterplotLayer({
                id: id,
                data: places, // STABLE ref from cache
                getPosition: d => d.coordinates,
                getRadius: d => {
                    if (isOverview && zoom > dp.polygonMinZoom && d.polygon) return 0;
                    const duration = isOverview
                        ? (d.totalDurationSec || 0)
                        : this._getActiveVisitEffect(d).seconds;
                    if (duration <= 0) return 0;
                    const minRadius = isOverview ? dp.radius : (dp.radius * 1.5);
                    const maxRadius = isOverview ? 100 : 150;
                    const scalingFactor = isOverview ? 0.8 : 1.2;
                    const calculated = minRadius + (Math.sqrt(duration) * scalingFactor);
                    return Math.min(calculated, maxRadius);
                },
                getFillColor: d => [...color, isOverview ? dp.opacity : (this._getActiveVisitEffect(d).opacity * dp.opacity)],
                getLineColor: d => [...color, 255],
                stroked: true,
                getLineWidth: dp.lineWidth,
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: !isOverview || (isOverview && zoom > dp.minZoom),
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, zoom],
                    getFillColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                transitions: {
                    getRadius: {
                        type: 'spring',
                        stiffness: 0.1,
                        damping: 0.15,
                        enter: d => [0]
                    }
                }
            });
        }

        if (id.startsWith('place-inner-circles')) {
            return new deck.ScatterplotLayer({
                id: id,
                data: places, // STABLE ref from cache
                getPosition: d => d.coordinates,
                getRadius: d => {
                    if (isOverview && zoom > dp.polygonMinZoom && d.polygon) return 0;
                    return 8;
                },
                stroked: true,
                lineWidthMinPixels: dp.lineWidth,
                depthTest: false,
                getLineColor: d => {
                    const isActive = d.activeRanges.some(r => currentTime >= r.start && currentTime <= r.end);
                    const alpha = (isOverview || isActive) ? 255 : 0;
                    return [0, 0, 0, alpha];
                },
                getFillColor: d => {
                    const isActive = d.activeRanges.some(r => currentTime >= r.start && currentTime <= r.end);
                    const alpha = (isOverview || isActive) ? 255 : 0;
                    return [255, 255, 255, alpha];
                },
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, zoom],
                    getFillColor: [currentTime, isOverview],
                    getLineColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                pickable: true,
                onHover: info => this._updateTooltip(info),
            });
        }

        // Fallback: return original
        return this._layerInstances.find(l => l.props.id === id);
    }
    async _initializeInitialStyle() {
        let loaded = await this._waitForStyleLoad(this.currentMapStyle);
        if (!loaded) {
            loaded = await this._fallbackToDefaultStyle();
        }

        if (!loaded) {
            this.element.classList.remove('is-loading');
            console.warn('Map style failed to load and no fallback style was available.');
            return;
        }

        this._ensureStyleCompatibilityLayers();
        await this._switchMapBuildingLayer(this.viewState.renderBuildings && this.viewState.is3d);
        await this._switchTerrainLayer(this.viewState.renderTerrain);
        await this._switchSatelliteLayer(this.viewState.renderSatelliteView);
        await this._switchProjection(this.viewState.renderGlobe);
        this._syncPitchBearingState(false);
        this.element.classList.remove('is-loading');
        this.element.classList.add('is-loaded');
        console.log('Map style loaded successfully.');
    }

    async updateViewState(next) {
        this.transitionQueue = this.transitionQueue
            .catch(error => {
                console.error('Previous map state transition failed:', error);
            })
            .then(() => this._updateViewStateInternal(next))
            .catch(error => {
                console.error('Map state transition failed:', error);
            });
        return this.transitionQueue;
    }

    async _updateViewStateInternal(next) {
        await this._initialLoadPromise;
        const prev = { ...this.viewState };
        this.viewState = next;

        const styleChanged = prev.mapStyleId !== next.mapStyleId;
        const projectionChanged = prev.renderGlobe !== next.renderGlobe;
        const satelliteChanged  = prev.renderSatelliteView !== next.renderSatelliteView;
        const terrainChanged    = prev.renderTerrain !== next.renderTerrain;
        const buildingsChanged  = (prev.renderBuildings !== next.renderBuildings) || (prev.is3d !== next.is3d);

        if (styleChanged) {
            const styleSwitched = await this._switchMapStyle(next.mapStyleId);
            if (!styleSwitched) {
                this.viewState = {
                    ...next,
                    mapStyleId: prev.mapStyleId
                };
            }
            this._ensureStyleCompatibilityLayers();
            await this._switchMapBuildingLayer(this.viewState.renderBuildings && this.viewState.is3d);
            await this._switchTerrainLayer(this.viewState.renderTerrain);
            await this._switchSatelliteLayer(this.viewState.renderSatelliteView);
            await this._switchProjection(this.viewState.renderGlobe);
            this._syncPitchBearingState(false);
            this._rerenderOverlays();
            return;
        }

        // 1) If projection or satellite changes, temporarily turn off terrain to avoid render bugs.
        if (projectionChanged || satelliteChanged) {
            const terrainWasOn = !!this.map.getTerrain();

            if (terrainWasOn) {
                await this._switchTerrainLayer(false);
            }

            if (projectionChanged) {
                await this._switchProjection(next.renderGlobe);
            }

            if (satelliteChanged) {
                await this._switchSatelliteLayer(next.renderSatelliteView); // paint-only
            }

            // Re-enable terrain if final desired state is on
            if (next.renderTerrain) {
                await this._switchTerrainLayer(true);
            }

            // Buildings can be applied after style/projection/satellite are stable
            if (buildingsChanged) {
                this._switchMapBuildingLayer(next.renderBuildings && next.is3d);
            }

            // Sync pitch/bearing after geometry changes
            this._syncPitchBearingState();
            this._rerenderOverlays();
            return;
        }

        // 2) Else, toggle terrain alone if changed
        if (terrainChanged) {
            await this._switchTerrainLayer(next.renderTerrain);
        }

        // 3) Buildings if changed
        if (buildingsChanged) {
            this._switchMapBuildingLayer(next.renderBuildings && next.is3d);
        }

        // 4) Sync input constraints and refresh overlays
        this._syncPitchBearingState();
        this._rerenderOverlays();
    }

    _getStyleCapabilities() {
        return this.currentMapStyle?.capabilities || {};
    }

    _cloneStyleDefinition(definition) {
        return JSON.parse(JSON.stringify(definition));
    }

    _getFirstSymbolLayerId() {
        const layers = this.map.getStyle()?.layers || [];
        return layers.find(layer => layer.type === 'symbol')?.id;
    }

    _ensureSource(sourceId, sourceDefinition) {
        if (!sourceId || !sourceDefinition || this.map.getSource(sourceId)) return;
        this.map.addSource(sourceId, this._cloneStyleDefinition(sourceDefinition));
    }

    _ensureLayer(layerDefinition, beforeLayerId = this._getFirstSymbolLayerId()) {
        if (!layerDefinition || this.map.getLayer(layerDefinition.id)) return;

        const layer = this._cloneStyleDefinition(layerDefinition);
        const sourceId = layer.source;
        if (sourceId && !this.map.getSource(sourceId)) {
            console.warn(`Cannot add map layer ${layer.id}; source ${sourceId} is not available.`);
            return;
        }

        if (beforeLayerId && this.map.getLayer(beforeLayerId)) {
            this.map.addLayer(layer, beforeLayerId);
        } else {
            this.map.addLayer(layer);
        }
    }

    _ensureStyleCompatibilityLayers() {
        const capabilities = this._getStyleCapabilities();
        const buildingLayerDefinitions = capabilities.building3dLayerDefinitions || [];
        const needsBuildingSource = buildingLayerDefinitions.some(layerDefinition => layerDefinition.source === capabilities.buildingSourceId);

        this._ensureSource(capabilities.terrainSourceId, capabilities.terrainSourceDefinition);
        this._ensureSource(capabilities.satelliteSourceId, capabilities.satelliteSourceDefinition);
        if (needsBuildingSource) {
            this._ensureSource(capabilities.buildingSourceId, capabilities.buildingSourceDefinition);
        }

        this._ensureLayer(capabilities.satelliteLayerDefinition);
        this._ensureLayer(capabilities.hillshadeLayerDefinition);
        buildingLayerDefinitions.forEach(layerDefinition => this._ensureLayer(layerDefinition));
    }

    async _waitForStyleLoad(mapStyle, timeoutMs = 10000) {
        console.log('Waiting for map style to load...');
        if (this.map.isStyleLoaded()) {
            return true;
        }

        return new Promise(resolve => {
            const timeout = window.setTimeout(() => {
                cleanup();
                console.warn(`Timed out waiting for map style ${mapStyle?.id || 'unknown'} to load.`);
                resolve(false);
            }, timeoutMs);
            const cleanup = () => {
                window.clearTimeout(timeout);
                this.map.off('style.load', handleLoad);
                this.map.off('error', handleError);
            };
            const handleLoad = () => {
                console.log('Map style loaded successfully.');
                cleanup();
                resolve(true);
            };
            const handleError = (event) => {
                console.warn(`Map style ${mapStyle?.id || 'unknown'} emitted an error while loading:`, event?.error || event);
            };

            this.map.once('style.load', handleLoad);
            this.map.on('error', handleError);
        });
    }

    async _setStyleAndWait(mapStyle, timeoutMs = 10000) {
        return new Promise(resolve => {
            const timeout = window.setTimeout(() => {
                cleanup();
                console.warn(`Timed out waiting for map style ${mapStyle?.id || 'unknown'} to load.`);
                resolve(false);
            }, timeoutMs);
            const cleanup = () => {
                window.clearTimeout(timeout);
                this.map.off('style.load', handleLoad);
                this.map.off('error', handleError);
            };
            const handleLoad = () => {
                cleanup();
                resolve(true);
            };
            const handleError = (event) => {
                console.warn(`Map style ${mapStyle?.id || 'unknown'} emitted an error while loading:`, event?.error || event);
            };

            this.map.once('style.load', handleLoad);
            this.map.on('error', handleError);

            try {
                this.map.setStyle(MapRenderer.getMapStyleValue(mapStyle));
            } catch (error) {
                cleanup();
                console.warn(`Unable to apply map style ${mapStyle?.id || 'unknown'}:`, error);
                resolve(false);
            }
        });
    }

    async _fallbackToDefaultStyle() {
        const fallbackStyle = MapRenderer.getMapStyle(MapRenderer.getDefaultMapStyleId());
        if (!fallbackStyle || fallbackStyle.id === this.currentMapStyle?.id) {
            return false;
        }

        console.warn(`Falling back to map style ${fallbackStyle.id}.`);
        const loaded = await this._setStyleAndWait(fallbackStyle, 10000);
        if (loaded) {
            this.currentMapStyle = fallbackStyle;
            this.viewState.mapStyleId = fallbackStyle.id;
            this._persistMapStyleSelection(fallbackStyle.id);
        }
        return loaded;
    }

    _persistMapStyleSelection(mapStyleId) {
        window.reittiActiveMapStyleId = mapStyleId;
        window.localStorage?.setItem('mapStyleId', mapStyleId);
        const styleSelect = document.getElementById('map-style-select');
        if (styleSelect) {
            styleSelect.value = mapStyleId;
        }
        const settingsStyleSelect = document.getElementById('settings-map-style-select');
        if (settingsStyleSelect) {
            settingsStyleSelect.value = mapStyleId;
        }
    }

    async _switchMapStyle(mapStyleId) {
        const nextStyle = MapRenderer.getMapStyle(mapStyleId);
        if (!nextStyle || nextStyle.id === this.currentMapStyle?.id) return true;

        const previousStyle = this.currentMapStyle;
        this.terrainLayer = null;
        this.satelliteHiddenPaintProperties.clear();
        this.element.classList.add('is-loading');

        let loaded = await this._setStyleAndWait(nextStyle);
        if (loaded) {
            this.currentMapStyle = nextStyle;
            this.element.classList.remove('is-loading');
            return true;
        }

        if (previousStyle) {
            console.warn(`Restoring previous map style ${previousStyle.id}.`);
            loaded = await this._setStyleAndWait(previousStyle, 10000);
            if (loaded) {
                this.currentMapStyle = previousStyle;
                this._persistMapStyleSelection(previousStyle.id);
            }
        }

        this.element.classList.remove('is-loading');
        return false;
    }

    setGpsDataManagers(managers) {
        // Deduplicate by manager.id, preserving order
        const seen = new Set();
        const unique = [];
        for (const m of managers) {
            if (!seen.has(m.id)) {
                seen.add(m.id);
                unique.push(m);
            }
        }
        this.gpsDataManagers = unique.reverse();
        this.bounds = [];
        this._layerContextKey = null;
        this._cachedVisitPlaces.clear();
        this._cachedFilteredPolygons.clear();
    }

    fitMapToBounds(bounds) {
        this.map.stop();
        const containerWidth = this.map.getContainer().clientWidth;
        const config = { ...this.viewConfig.fitConfig };
        if (containerWidth < 600) {
            config.padding = { top: 20, bottom: 20, right: 20, left: 20 };
        }
        this.map.fitBounds(bounds, config);
    }

    flyTo(config) {
        this.map.stop();
        this.map.flyTo(config);
    }

    finishedLoading() {
        console.log('Finished loading map data');

        const performFit = async () => {
            try {
                await this._initialLoadPromise;
                this._refitBounds();
                this.element.classList.remove('is-loading');
                this.element.classList.add('is-loaded');
            } catch (error) {
                console.error("Error during performFit:", error);
            }
        };

        return performFit();
    }

    _refitBounds() {
        if (this.bounds.length === 0) return;

        const bounds = [
            Math.min(...this.bounds.map(b => b[0])),
            Math.min(...this.bounds.map(b => b[1])),
            Math.max(...this.bounds.map(b => b[0])),
            Math.max(...this.bounds.map(b => b[1]))
        ];

        // Check if globe projection is active but not yet initialized
        const projection = this.map.getProjection?.();
        const isGlobe = projection?.type === 'globe';

        if (isGlobe && !this._globeReady) {
            // Globe projection is set but camera helper hasn't switched yet.
            // Wait for the next 'idle' event (style fully loaded + rendered),
            // then re-fit with the correct globe camera math.
            console.log('[_refitBounds] Globe projection not ready, deferring fitBounds...');
            this.map.once('idle', () => {
                this._globeReady = true;
                this._doFitBounds(bounds);
                // After re-fitting, rebuild layers so deck.gl picks up the corrected viewport
                this._buildLayers();
            });
        } else {
            this._globeReady = true;
            this._doFitBounds(bounds);
        }
    }

    _doFitBounds(bounds) {
        this.map.fitBounds(bounds, {
            padding: 60,
            duration: 0  // instant, no animation — important for deck.gl sync
        });
    }

    reset() {
        this.highlightLayer = null;
        this._layerInstances = [];
        this._layerContextKey = null;
        this.deckOverlay.setProps({layers: []});
    }
    setBearing(number) {
        this.map.easeTo({
            bearing: number,
            duration: 500, // Mapbox uses 'duration' in ms
            essential: true
        });
    }

    setPhotos(photos) {
        this.photosManager.setPhotos(photos);
    }

    enableAvatars(todayStart = null, todayEnd = null) {
        this.showAvatars = true;
        this.gpsDataManagers.forEach(manager => {
            const userConfig = manager.config;
            const latestLocation = manager.lastLocation;
            if (todayStart !== null && todayEnd !== null) {
                if (!latestLocation) return;
                const locMs = new Date(latestLocation.timestamp).getTime();
                if (locMs < todayStart || locMs > todayEnd) {
                    return; // we skip the avatar marker since it is not today
                }
            }

            if (latestLocation && userConfig?.showAvatar && this.avatarMarkers.get(manager.id) == null) {
                this.addAvatarMarker(
                    manager.id, // Add user ID
                    latestLocation.latitude, 
                    latestLocation.longitude, 
                    {
                        avatarUrl: window.contextPath + userConfig.avatarUrl,
                        avatarFallback: userConfig.avatarFallback,
                        displayName: userConfig.displayName,
                        timestamp: latestLocation.timestamp
                    }
                );
            }
        });
    }

    disableAvatars() {
        this.showAvatars = false;
        this.removeAvatarMarkers();
    }

    destroy() {
        this.map.remove();
        this.gpsDataManagers.forEach(manager => manager.destroy());
    }

    /**
     * Provides a way for external controllers to listen to map events
     * without accessing the internal map object directly.
     * @param {string} type - The event type (e.g., 'dragstart', 'zoomstart').
     * @param {function} listener - The callback function.
     */
    on(type, listener) {
        this.map.on(type, listener);
    }

    /**
     * Returns the current camera state of the map.
     * Useful for saving a view to return to later.
     */
    getCameraState() {
        return {
            center: this.map.getCenter(),
            zoom: this.map.getZoom(),
            pitch: this.map.getPitch(),
            bearing: this.map.getBearing()
        };
    }

    _shouldAllowPitchAndBearing() {
        return this.viewState.is3d && !(this.viewState.renderGlobe && this.map.getZoom() < 12);
    }

    _syncPitchBearingState(animate = true) {
        if (!this.map) return;

        const shouldAllow = this._shouldAllowPitchAndBearing();
        const wasAllowed = this._pitchBearingAllowed;

        if (shouldAllow === wasAllowed) return;

        this._pitchBearingAllowed = shouldAllow;

        if (shouldAllow) {
            this.map.dragRotate.enable();
            this.map.touchZoomRotate.enableRotation();
            this.map.easeTo({ pitch: 45, bearing: 0, duration: 500, essential: true });
        } else {
            this.map.dragRotate.disable();
            this.map.touchZoomRotate.disableRotation();
            if (this.map.getPitch() !== 0 || this.map.getBearing() !== 0) {
                if (animate) {
                    this.map.easeTo({ pitch: 0, bearing: 0, duration: 500, essential: true });
                } else {
                    this.map.jumpTo({ pitch: 0, bearing: 0 });
                }
            }
        }
    }

    _updateLayers() {

        const allLayers = [];

        if (this.viewState.renderTerrain) {
            allLayers.push(this.terrainLayer);
        }

        this.gpsDataManagers.forEach(manager => {
            const layerKey = `${manager.id}-${this.viewState.aggregated ? 'agg' : 'lin'}-${this.viewState.renderTerrain ? 'terrain' : 'flat'}`;
            const totalTimeSpanSec = this.viewState.aggregated ? 24 * 60 * 60 : manager.maxTimestamp - manager.minTimestamp;
            const calculatedTrail = Math.max(1800, totalTimeSpanSec * 0.02);
            if (manager.cursor > 0) {
                if (this.viewState.viewMode === 'BUNDLED') {
                    allLayers.push(...this._getBundleLayers(layerKey, manager));
                } else {
                    const buffer = this.viewState.viewMode === 'LINEAR' ? 'cleaned' : 'raw';
                    const cursor = this.viewState.viewMode === 'LINEAR' ? manager.cleanedCursor : manager.cursor;
                    const extensions = this.viewState.renderTerrain ? [new deck._TerrainExtension({elevationLayerId: 'terrain-loader'})] : [];
                    const layerData = manager.getLayerData(buffer, this.viewState.aggregated);
                    allLayers.push(new deck.PathLayer({
                        id: `paths-static-fixed-${layerKey}`,
                        data: layerData,
                        positionFormat: 'XYZ',
                        getColor: [...manager.color, this.deckParams.trips.staticPathOpacity],
                        getWidth: this.deckParams.trips.staticPathWidth,
                        widthMinPixels: this.deckParams.trips.staticPathWidth,
                        visibility: true,
                        capRounded: true,
                        jointRounded: true,
                        extensions: extensions,
                        terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                        parameters: {
                            depthTest: true,
                            polygonOffsetFill: true
                        },
                        updateTriggers: {
                            data: [manager.buffer?.length, buffer],
                            getPath: [cursor, buffer, this.viewState.renderTerrain],
                            extensions: [this.viewState.renderTerrain],
                            getTimestamps: [this.viewState.aggregated],
                            getColor: [manager.color],
                        }
                    }));
                    if (this.viewState.animating) {
                        // SHADOW TRIP (Animated)
                        allLayers.push(new deck.TripsLayer({
                            id: `trips-shadow-${layerKey}`,
                            data: layerData,
                            positionFormat: 'XYZ',
                            getColor: manager.color,
                            opacity: this.deckParams.trips.shadowOpacity,
                            widthMinPixels: this.deckParams.trips.shadowWidth,
                            trailLength: calculatedTrail,
                            visibility: this.viewState.animating,
                            currentTime: this.viewState.currentTime,
                            capRounded: true,
                            jointRounded: true,
                            parameters: {depthTest: false},
                            extensions: extensions,
                            terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                            updateTriggers: {
                                data: [manager.buffer?.length, buffer],
                                getPath: [cursor, buffer, this.viewState.renderTerrain],
                                extensions: [this.viewState.renderTerrain],
                                getTimestamps: [this.viewState.aggregated],
                                visibility: [this.viewState.animating],
                            }
                        }));

                        allLayers.push(new deck.TripsLayer({
                            id: `trips-core-${layerKey}`,
                            data: layerData,
                            positionFormat: 'XYZ',
                            getColor: [255, 255, 255, 100],
                            opacity: this.deckParams.trips.cometOpacity * this.viewState.animating,
                            widthMinPixels: this.deckParams.trips.cometWidth,
                            trailLength: calculatedTrail * 1.2,
                            currentTime: this.viewState.currentTime,
                            capRounded: true,
                            jointRounded: true,
                            extensions: extensions,
                            terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                            updateTriggers: {
                                data: [manager.buffer?.length, buffer],
                                extensions: [this.viewState.renderTerrain],
                                getPath: [cursor, buffer, this.viewState.renderTerrain],
                                currentTime: [this.viewState.currentTime],
                                getTimestamps: [this.viewState.aggregated],
                            }
                        }));
                    }
                }
            }
            allLayers.push(...this._getVisitLayers(layerKey, manager));
        })

        if (this.highlightLayer) {
            allLayers.push(this.highlightLayer);
        }
        this.deckOverlay.setProps({
            layers: allLayers
        })
    }

    _buildBundleLayers(layerKey, manager, extensions, terrainDrawMode, calculatedTrail) {
        if (manager.loadingState !== 'complete' || !manager.snappedBuffer || manager.snappedBuffer.length === 0) {
            return [];
        }

        // Single cached call per build
        const layerData = this._getCachedLayerData(manager, 'bundled', this.viewState.aggregated);
        const color = manager.color;
        const dp = this.deckParams.bundled;
        const isAnimating = this.viewState.animating;
        const currentTime = this.viewState.currentTime;

        const layers = [];

        // Static bundled path — millions of points, only built on structural change
        layers.push(new deck.PathLayer({
            id: `bundled-paths-static-${layerKey}`,
            data: this._stripForPathLayer(layerData),          // ← strip getTimestamps
            positionFormat: 'XY',
            getColor: [...color, dp.staticPathOpacity],
            widthMinPixels: dp.staticPathWidth,
            capRounded: true,
            jointRounded: true,
            depthTest: false,
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion, this.viewState.aggregated],
                getPath: [manager.snappedVersion, this.viewState.aggregated],
                getColor: [color],
            }
        }));



        // Semi-transparent overlay path
        layers.push(new deck.PathLayer({
            id: `bundled-path-${layerKey}`,
            data: this._stripForPathLayer(layerData),          // ← strip getTimestamps
            positionFormat: 'XY',
            widthMinPixels: dp.pathWidth,
            getColor: [...color, dp.pathOpacity],
            parameters: {
                blendFunc: [770, 1],
                blendEquation: 32774,
                depthTest: false
            },
            capRounded: true,
            jointRounded: true,
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
            }
        }));

        // Shadow trip (animated)
        layers.push(new deck.TripsLayer({
            id: `bundled-path-shadow-${layerKey}`,
            data: layerData,                                  // ← keep getTimestamps
            positionFormat: 'XY',
            getColor: [255, 255, 255],
            opacity: dp.shadowOpacity,
            visible: isAnimating,
            widthMinPixels: dp.shadowWidth,
            trailLength: calculatedTrail * 1.2,
            currentTime: currentTime,
            capRounded: true,
            jointRounded: true,
            parameters: { depthTest: false },
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [currentTime],
                // Removed: nothing was invalid here, already clean
            }
        }));

// Core trip (animated)
        layers.push(new deck.TripsLayer({
            id: `bundled-path-core-${layerKey}`,
            data: layerData,                                  // ← keep getTimestamps
            positionFormat: 'XY',
            getColor: color,
            opacity: dp.cometOpacity,
            visible: isAnimating,
            widthMinPixels: dp.cometWidth,
            trailLength: calculatedTrail,
            currentTime: currentTime,
            capRounded: true,
            jointRounded: true,
            depthTest: false,
            blendFunc: [770, 771],
            blendEquation: 32774,
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [currentTime],
            }
        }));

        return layers;
    }

    _buildVisitLayers(layerKey, manager, extensions, terrainDrawMode) {
        if (manager.visits === undefined) return [];

        const {places, filteredPolygons} = this._getCachedVisitPlaces(manager);
        const isOverview = !this.viewState.animating;
        const currentTime = this.viewState.currentTime;
        const zoom = this._currentZoom;
        const color = manager.color;
        const dp = this.deckParams.visits;

        return [
            new deck.PolygonLayer({
                id: `visit-polygons-${layerKey}`,
                data: filteredPolygons, // STABLE cached ref
                getPolygon: d => {
                    if (!d.polygon || !Array.isArray(d.polygon)) return [];
                    return d.polygon.map(point => [point.longitude, point.latitude]);
                },
                getFillColor: d => [...color, dp.polygonOpacity],
                getLineColor: d => [...color, 255],
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: isOverview && zoom > dp.polygonMinZoom,
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getFillColor: [currentTime],
                    visible: [isOverview, zoom],
                    data: manager.visits?.length
                }
            }),
            new deck.ScatterplotLayer({
                id: `place-outer-circles-${layerKey}`,
                data: places, // STABLE cached ref
                getPosition: d => d.coordinates,
                getRadius: d => {
                    if (isOverview && zoom > dp.polygonMinZoom && d.polygon) return 0;
                    const duration = isOverview
                        ? (d.totalDurationSec || 0)
                        : this._getActiveVisitEffect(d).seconds;
                    if (duration <= 0) return 0;
                    const minRadius = isOverview ? dp.radius : (dp.radius * 1.5);
                    const maxRadius = isOverview ? 100 : 150;
                    const scalingFactor = isOverview ? 0.8 : 1.2;
                    return Math.min(minRadius + (Math.sqrt(duration) * scalingFactor), maxRadius);
                },
                getFillColor: d => [...color, isOverview ? dp.opacity : (this._getActiveVisitEffect(d).opacity * dp.opacity)],
                getLineColor: d => [...color, 255],
                stroked: true,
                getLineWidth: dp.lineWidth,
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: !isOverview || (isOverview && zoom > dp.minZoom),
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, zoom],
                    getFillColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                transitions: {
                    getRadius: {
                        type: 'spring',
                        stiffness: 0.1,
                        damping: 0.15,
                        enter: d => [0]
                    }
                }
            }),
            new deck.ScatterplotLayer({
                id: `place-inner-circles-${layerKey}`,
                data: places, // STABLE cached ref
                getPosition: d => d.coordinates,
                getRadius: d => {
                    if (isOverview && zoom > dp.polygonMinZoom && d.polygon) return 0;
                    return 8;
                },
                stroked: true,
                lineWidthMinPixels: dp.lineWidth,
                depthTest: false,
                getLineColor: d => {
                    const isActive = d.activeRanges.some(r => currentTime >= r.start && currentTime <= r.end);
                    const alpha = (isOverview || isActive) ? 255 : 0;
                    return [0, 0, 0, alpha];
                },
                getFillColor: d => {
                    const isActive = d.activeRanges.some(r => currentTime >= r.start && currentTime <= r.end);
                    const alpha = (isOverview || isActive) ? 255 : 0;
                    return [255, 255, 255, alpha];
                },
                extensions: extensions,
                terrainDrawMode: terrainDrawMode,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, zoom],
                    getFillColor: [currentTime, isOverview],
                    getLineColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                pickable: true,
                onHover: info => this._updateTooltip(info),
            })
        ];
    }

    /**
     * Called by the external time control (replay slider) to advance the track.
     * This is a LIGHTWEIGHT update — only TripsLayer `currentTime` props
     * and visit layer accessors are re-evaluated. Static path layers
     * (millions of points) are not touched at all.
     *
     * @param {number} time - The new currentTime value
     */
    setCurrentTime(time) {
        if (this._debug) {
            console.log(`%c[setCurrentTime] ${time}`, 'color: #9933cc');
        }
        this.viewState.currentTime = time;
        this._updateAnimatedLayers();
    }
    async _waitForIdle() {
        if (this.map.loaded() && !this.map.isMoving()) {
            return; // Already idle, resolve immediately
        }
        return new Promise(resolve => this.map.once('idle', resolve));
    }

    _rerenderOverlays() {
        if (this._debug) console.log('%c[_rerenderOverlays] → _buildLayers', 'color: #336699');
        this._buildLayers();
        if (this.showAvatars) this.updateAvatarPositions();
        this.viewConfig.mapDataProviders.forEach(provider => provider.render(this.map));
    }

    _setup = () => {
        // Track current zoom for visit layer visibility decisions
        this._currentZoom = this.map.getZoom();

        if (this._debug) {
            console.log('%c[_setup] move listener removed, zoom listeners added', 'color: #336699; font-weight: bold');
        }

        // REMOVED: this.map.on('move', () => this._updateLayers())
        //
        // Pan/zoom do NOT require layer rebuilds. deck.gl handles viewport
        // uniforms internally. The only zoom-dependent thing is visit polygon
        // visibility, handled by the debounced handler below.

        this.map.on('zoom', () => {
            this._currentZoom = this.map.getZoom();

            // Debounce: only update visit layer visibility after zoom settles.
            // During continuous zoom, visit circles/polygons can be slightly
            // stale — this is an acceptable visual tradeoff for 60fps panning.
            if (this._zoomDebounceId !== null) {
                clearTimeout(this._zoomDebounceId);
            }
            this._zoomDebounceId = setTimeout(() => {
                this._zoomDebounceId = null;
                // Only update if we're not in a structural rebuild scenario
                this._updateAnimatedLayers();
            }, 100);
        });

        this.map.on('zoomend', () => {
            this._currentZoom = this.map.getZoom();
            this._syncPitchBearingState();
            // Final visit layer visibility update
            if (this._zoomDebounceId !== null) {
                clearTimeout(this._zoomDebounceId);
                this._zoomDebounceId = null;
            }
            this._updateAnimatedLayers();
        });

        this.map.on('zoom', (e) => {
            if (e.originalEvent && !this._pitchBearingAllowed) {
                if (this.map.getPitch() !== 0 || this.map.getBearing() !== 0) {
                    this.map.jumpTo({ pitch: 0, bearing: 0 });
                }
            }
        });
    };
    _getActiveVisitEffect(visit) {
        const exitFadeDuration = 3000;
        let accumulated = 0;
        let multiplier = 0;

        for (const range of visit.activeRanges) {
            const start = this.viewState.aggregated ? range.startAggregate : range.start;
            const end = this.viewState.aggregated ? range.endAggregate : range.end;
            if (this.viewState.currentTime < start) {
                // Future visit: do nothing
                break;
            } else if (this.viewState.currentTime >= start && this.viewState.currentTime <= end) {
                // Currently visiting: Full size
                accumulated += (this.viewState.currentTime - start);
                multiplier = 1.0;
            } else if (this.viewState.currentTime > end) {
                // Past visit: Add full duration
                accumulated += (end - start);

                // Calculate decay: how recently did we leave?
                const timeSinceLeft = this.viewState.currentTime - end;
                const decay = 1.0 - (timeSinceLeft / exitFadeDuration);

                // We take the "freshest" decay. If we just left, multiplier is near 1.
                // If we left a long time ago, multiplier is 0.
                multiplier = Math.max(multiplier, Math.min(1, decay));
            }
        }

        return {seconds: accumulated, opacity: multiplier};
    }

    _updateTooltip({x, y, object}) {
        const tooltip = document.getElementById('tooltip');
        if (object) {
            const visitListHtml = object.originalVisits.map(v => `
            <div style="border-top: 1px solid #444; margin-top: 5px; padding-top: 5px;">
                <b>${t('map.popup.labels.from')}</b> ${formatDateTime(v.startTime)}<br>
                <b>${t('map.popup.labels.to')}</b> ${formatDateTime(v.endTime)}
            </div>
        `).join('');

            tooltip.innerHTML = `
            <div style="font-size: 14px; font-weight: bold; margin-bottom: 5px;">${object.name == null ? t('place.unknown.label') : object.name}</div>
            <div><b>${t('map.popup.labels.total_duration')}</b> ${humanizeDuration(object.totalDurationSec * 1000)}</div>
            <div style="max-height: 150px; overflow-y: auto; margin-top: 10px;">
                ${visitListHtml}
            </div>
        `;
            tooltip.style.display = 'block';
            tooltip.style.left = `${x + 15}px`;
            tooltip.style.top = `${y + 15}px`;
        } else {
            tooltip.style.display = 'none';
        }
    }

    _extendBounds(newBounds) {
        if (newBounds) {
            if (!this.bounds || this.bounds.length === 0) {
                this.bounds = [...newBounds];
                return;
            }

            // 2. Otherwise, compare current vs new to find the extremes
            // this.bounds = [minLng, minLat, maxLng, maxLat]
            this.bounds[0] = Math.min(this.bounds[0], newBounds[0]); // Lowest Lng
            this.bounds[1] = Math.min(this.bounds[1], newBounds[1]); // Lowest Lat
            this.bounds[2] = Math.max(this.bounds[2], newBounds[2]); // Highest Lng
            this.bounds[3] = Math.max(this.bounds[3], newBounds[3]); // Highest Lat
        }
    }

    _flyToHomeLocation() {
        this.map.stop();
        this.map.flyTo({
            center: [window.userSettings.homeLongitude, window.userSettings.homeLatitude],
            zoom: 15
        });
    }

    async _switchProjection(renderGlobe) {
        const targetProjection = renderGlobe ? 'globe' : 'mercator';
        this.map.setProjection({
            type: targetProjection
        });
    }

    async _switchSatelliteLayer(enable) {
        this._ensureStyleCompatibilityLayers();
        this._applySatellitePaintProperties(enable);
        await this._waitForIdle();
    }

    _applySatellitePaintProperties(enable) {
        if (!this.map || !this.map.getStyle) return;

        const satelliteLayerId = this._getStyleCapabilities().satelliteLayerId;
        if (!satelliteLayerId || !this.map.getLayer || !this.map.getLayer(satelliteLayerId)) return;

        this.map.setLayoutProperty(satelliteLayerId, 'visibility', enable ? 'visible' : 'none');
        this.map.setPaintProperty(satelliteLayerId, 'raster-opacity', enable ? 1 : 0);

        const style = this.map.getStyle();
        if (!style || !Array.isArray(style.layers)) return;

        const targetTypes = ['fill', 'background', 'fill-extrusion', 'line'];
        const protectedLayers = new Set([
            satelliteLayerId,
            'sky',
            ...(this._getStyleCapabilities().building3dLayerIds || [])
        ]);

        style.layers.forEach(layer => {
            if (targetTypes.includes(layer.type) && !protectedLayers.has(layer.id)) {
                let opacityProp = '';
                if (layer.type === 'line') opacityProp = 'line-opacity';
                if (layer.type === 'fill') opacityProp = 'fill-opacity';
                if (layer.type === 'fill-extrusion') opacityProp = 'fill-extrusion-opacity';
                if (layer.type === 'background') opacityProp = 'background-opacity';

                try {
                    const cacheKey = `${layer.id}:${opacityProp}`;
                    if (enable) {
                        if (!this.satelliteHiddenPaintProperties.has(cacheKey)) {
                            this.satelliteHiddenPaintProperties.set(cacheKey, this.map.getPaintProperty(layer.id, opacityProp));
                        }
                        this.map.setPaintProperty(layer.id, opacityProp, 0);
                    } else if (this.satelliteHiddenPaintProperties.has(cacheKey)) {
                        this.map.setPaintProperty(layer.id, opacityProp, this.satelliteHiddenPaintProperties.get(cacheKey));
                        this.satelliteHiddenPaintProperties.delete(cacheKey);
                    }
                } catch (_) {
                    // Layer might not be present yet; ignore
                }
            }
        });

        // Buildings: keep a faint extrusion over satellite if desired
        (this._getStyleCapabilities().building3dLayerIds || []).forEach(layerId => {
            if (!this.map.getLayer || !this.map.getLayer(layerId)) return;
            try {
                const opacityProp = 'fill-extrusion-opacity';
                const cacheKey = `${layerId}:${opacityProp}`;
                if (enable) {
                    if (!this.satelliteHiddenPaintProperties.has(cacheKey)) {
                        this.satelliteHiddenPaintProperties.set(cacheKey, this.map.getPaintProperty(layerId, opacityProp));
                    }
                    this.map.setPaintProperty(layerId, opacityProp, 0.6);
                } else if (this.satelliteHiddenPaintProperties.has(cacheKey)) {
                    this.map.setPaintProperty(layerId, opacityProp, this.satelliteHiddenPaintProperties.get(cacheKey));
                    this.satelliteHiddenPaintProperties.delete(cacheKey);
                }
            } catch (_) {}
        });
    }

    _extractTerrainUrl() {
        const terrainSourceId = this._getStyleCapabilities().terrainSourceId;
        if (!terrainSourceId) return null;

        const style = this.map.getStyle();
        if (style?.sources?.[terrainSourceId]) {
            const sourceDef = style.sources[terrainSourceId];
            const profile = this._detectProfile(sourceDef);

            if (sourceDef.tiles && sourceDef.tiles.length > 0) {
                return { type: 'template', value: sourceDef.tiles[0], profile: profile };
            }

            if (sourceDef.url) {
                return { type: 'manifest', value: sourceDef.url, profile: profile };
            }
        }
        return null;
    }

    _detectProfile(sourceDef) {
        if (sourceDef.encoding === 'terrarium') return this.TERRAIN_PROFILES.TERRARIUM;

        // Fallback based on attribution/name
        const attr = (sourceDef.attribution || "").toLowerCase();
        if (attr.includes("maptiler")) return this.TERRAIN_PROFILES.MAPTILER;

        return this.TERRAIN_PROFILES.TERRARIUM;
    }


    async _switchTerrainLayer(enable) {
        this._ensureStyleCompatibilityLayers();
        const capabilities = this._getStyleCapabilities();
        const terrainSourceId = capabilities.terrainSourceId;
        const hillshadeLayerId = capabilities.hillshadeLayerId;
        const hasHillshading = !!hillshadeLayerId && !!this.map.getLayer && this.map.getLayer(hillshadeLayerId);

        if (!terrainSourceId || !this.map.getSource(terrainSourceId)) {
            this.terrainLayer = null;
            this.map.setTerrain(null);
            return;
        }

        if (enable) {
            // Get URL safely
            let terrainData = this._extractTerrainUrl();

            if (!terrainData) {
                console.warn("Terrain source definition not found in style.");
                return;
            }

            let finalTileUrl;
            let elevationScale = 1.5;

            // Handle the two types detected
            if (terrainData.type === 'manifest') {
                const response = await fetch(terrainData.value);
                const tileJson = await response.json();
                terrainData.profile = this._detectProfile(tileJson);
                finalTileUrl = tileJson.tiles[0];
                // Dynamically set scale if provided in TileJSON
                elevationScale = tileJson.scale ? parseFloat(tileJson.scale) : 1.5;
            } else {
                finalTileUrl = terrainData.value;
            }

            if (hasHillshading) {
                this.map.setLayoutProperty(hillshadeLayerId, 'visibility', 'visible');
            }

            this.terrainLayer = new deck.TerrainLayer({
                id: 'terrain-loader',
                elevationData: finalTileUrl,
                elevationDecoder: terrainData.profile.decoder,
                minZoom: 0,
                maxZoom: 14,
                elevationScale: elevationScale,
                operation: 'terrain',
                loadOptions: {
                    terrain: {
                        maxRequests: 6
                    },
                    fetch: { priority: 'high' } }
            });

            // Set MapLibre terrain
            this.map.setTerrain({
                source: terrainSourceId,
                exaggeration: 1
            });

        } else {
            this.terrainLayer = null;
            if (hasHillshading) {
                this.map.setLayoutProperty(hillshadeLayerId, 'visibility', 'none');
            }

            this.map.setTerrain(null);
        }
        this._terrainExtension = null;
        this._terrainExtensionsArray = null;
        this._layerContextKey = null; // Force rebuild

    }

    _switchMapBuildingLayer(is3d) {
        this._ensureStyleCompatibilityLayers();
        (this._getStyleCapabilities().building3dLayerIds || []).forEach(layerId => {
            if (!this.map.getLayer || !this.map.getLayer(layerId)) return;
            this.map.setLayoutProperty(layerId, 'visibility', is3d ? 'visible' : 'none');
        });
    }

    setHighlight({ managerId, startTime, endTime }) {
        const manager = this.gpsDataManagers.find(m => m.id === managerId);
        if (!manager) {
            console.warn(`Manager with id ${managerId} not found.`);
            return null;
        }

        // Determine which data buffer to use. 'cleaned' is usually best for trips.
        const tripData = manager.getLayerData('cleaned', this.viewState.aggregated);
        if (!tripData || !tripData.attributes || !tripData.attributes.getPath || !tripData.attributes.getTimestamps) {
            console.warn("Trip data is missing the required binary attributes for highlighting.");
            this.clearHighlight();
            return null;
        }

        const pathAttribute = tripData.attributes.getPath;
        const timeAttribute = tripData.attributes.getTimestamps;
        const bufferValue = pathAttribute.value; // The giant Float32Array

        // Convert byte-based stride and offsets to float-based indices
        const strideInFloats = pathAttribute.stride / 4; // e.g., 24 bytes / 4 = 6 floats
        const pathOffsetInFloats = pathAttribute.offset / 4; // e.g., 0 bytes / 4 = 0
        const timeOffsetInFloats = timeAttribute.offset / 4; // e.g., 12 bytes / 4 = 3

        const segmentPath = [];
        const numVertices = bufferValue.length / strideInFloats;

        for (let i = 0; i < numVertices; i++) {
            // Calculate the starting index for this vertex in the flat buffer
            const baseIndex = i * strideInFloats;

            // Extract the timestamp
            const timestamp = bufferValue[baseIndex + timeOffsetInFloats];

            // Check if the point is within the desired time range
            if (timestamp >= startTime && timestamp <= endTime) {
                // If it is, extract the position [lng, lat, alt]
                const point = [
                    bufferValue[baseIndex + pathOffsetInFloats + 0], // Lng
                    bufferValue[baseIndex + pathOffsetInFloats + 1], // Lat
                    bufferValue[baseIndex + pathOffsetInFloats + 2]  // Alt (or Z)
                ];
                segmentPath.push(point);
            }
        }

        if (segmentPath.length === 0) {
            console.warn("No data points found for the given time range.");
            this.clearHighlight(); // Clear any existing highlight
            return null;
        }

        // Create the new highlight layer
        this.highlightLayer = new deck.PathLayer({
            id: 'highlight-segment',
            data: [{ path: segmentPath }], // Data is an array with one object that has a 'path' property
            widthMinPixels: 6, // Make it thick and obvious
            getColor: [255, 255, 0, 255], // Bright yellow for high visibility
            capRounded: true,
            jointRounded: true,
            // Add terrain extension if needed
            extensions: this.viewState.renderTerrain ? [new deck._TerrainExtension()] : [],
            terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
        });

        this._rerenderOverlays();

        // Calculate and return the bounds
        const bounds = new maplibregl.LngLatBounds();
        segmentPath.forEach(point => {
            bounds.extend([point[0], point[1]]); // Extend bounds for each point in the segment
        });
        return bounds;
    }

    /**
     * Removes the highlight layer from the map.
     */
    clearHighlight() {
        if (this.highlightLayer) {
            this.highlightLayer = null;
            this._rerenderOverlays(); // Trigger a re-render to remove the layer
        }
    }

    /**
     * Add an avatar marker at the specified coordinates
     */
    addAvatarMarker(userId, lat, lng, userData) {
        const SIZE = 40;

        // Calculate initial opacity
        const opacity = this._calculateAvatarOpacity(userData.timestamp);

        // Outer container - NO position:relative, MapLibre controls this
        const container = document.createElement('div');
        container.style.cssText =
            'width:' + SIZE + 'px;height:' + SIZE + 'px;cursor:pointer;z-index:1000;';

        // Inner wrapper for proper positioning
        const inner = document.createElement('div');
        inner.style.cssText =
            'width:' + SIZE + 'px;height:' + SIZE + 'px;position:relative;';

        const circle = document.createElement('div');
        circle.className = 'avatar-marker';

        const img = document.createElement('img');
        img.src = userData.avatarUrl;
        img.alt = userData.avatarFallback;
        img.className = 'avatar-marker-img';

        // Apply opacity to the image
        img.style.opacity = opacity;
        img.style.transition = 'opacity 0.5s ease-in-out';

        circle.appendChild(img);
        inner.appendChild(circle);
        container.appendChild(inner);

        // Create MapLibre GL marker
        const marker = new maplibregl.Marker({
            element: container,
            anchor: 'center',
            offset: [-5, 0]
        }).setLngLat([lng, lat]).addTo(this.map);

        // Store additional data with the marker for updates
        marker._avatarData = {
            userId: userId,
            userData: userData,
            color: userData.color,
            imgElement: img
        };

        const popupContent = ``;

        // Add popup with detailed user info
        const popup = new maplibregl.Popup({
            offset: 25,
            maxWidth: '500px',
            closeButton: false
        }).setHTML(popupContent);

        container.addEventListener('pointerenter', (e) => {
            e.stopPropagation();
            e.stopImmediatePropagation();
            popup.setLngLat([lng, lat]).addTo(this.map);
        }, { capture: true });

        container.addEventListener('pointerleave', (e) => {
            e.stopPropagation();
            e.stopImmediatePropagation();
            popup.remove();
        }, { capture: true });

        marker.setPopup(popup);
        this.avatarMarkers.set(userId, marker);
    }

    /**
     * Update avatar positions to latest known locations
     */
    updateAvatarPositions() {
        const activeUserIds = new Set();
        // Update existing markers or create new ones
        this.gpsDataManagers.forEach(manager => {
            const userConfig = manager.config;
            const latestLocation = manager.lastLocation;

            if (latestLocation && userConfig) {
                activeUserIds.add(manager.id);

                const existingMarker = this.avatarMarkers.get(manager.id);
                if (existingMarker) {
                    // Move existing marker to new position
                    existingMarker.setLngLat([latestLocation.longitude, latestLocation.latitude]);

                    // Update user data with new timestamp
                    existingMarker._avatarData.userData = {
                        avatarUrl: window.contextPath + userConfig.avatarUrl,
                        avatarFallback: userConfig.avatarFallback,
                        displayName: userConfig.displayName,
                        timestamp: latestLocation.timestamp
                    };

                    // Update opacity based on new timestamp
                    const opacity = this._calculateAvatarOpacity(latestLocation.timestamp);
                    if (existingMarker._avatarData.imgElement) {
                        existingMarker._avatarData.imgElement.style.opacity = opacity;
                    }

                    // Update popup content with new timestamp
                    this.updateMarkerPopup(existingMarker, latestLocation.latitude, latestLocation.longitude, {
                        avatarUrl: window.contextPath + userConfig.avatarUrl,
                        avatarFallback: userConfig.avatarFallback,
                        displayName: userConfig.displayName,
                        timestamp: latestLocation.timestamp,
                        color: userConfig.color
                    });
                } else {
                    this.addAvatarMarker(
                        manager.id,
                        latestLocation.latitude,
                        latestLocation.longitude,
                        {
                            avatarUrl: window.contextPath + userConfig.avatarUrl,
                            avatarFallback: userConfig.avatarFallback,
                            displayName: userConfig.displayName,
                            timestamp: latestLocation.timestamp
                        }
                    );
                }
            }
        });

        // Remove markers for users that no longer have location data
        for (const [userId, marker] of this.avatarMarkers) {
            if (!activeUserIds.has(userId)) {
                marker.remove();
                this.avatarMarkers.delete(userId);
            }
        }
    }
    /**
     * Update marker popup content
     */
    updateMarkerPopup(marker, lat, lng, userData) {
        const formatTimestamp = (timestamp) => {
            if (!timestamp) return t('common.unknown') || 'Unknown';
            const date = new Date(timestamp);
            return date.toLocaleString();
        };

        const formatCoordinates = (lat, lng) => {
            return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        };

        const popupContent = `
        <div class="floating map-popup">
            <div class="sel-info-head">
                <div class="sel-info-title">
                    ${t('map.auto-update.latest-location')}
                </div>
            </div>
            <div class="sel-info-row">
                <span class="k">${t('common.user')}:</span>
                <span class="v sel-info-tag">
                    <span class="swatch" style="background:${userData.color}"></span>${userData.displayName}
                </span>
            </div>
            <div class="sel-info-row">
                <span class="k">${t('common.position')}:</span>
                <span class="v mono">${formatTimestamp(userData.timestamp)}</span>
            </div>
           <div class="sel-info-row">
                <span class="k">${t('common.time')}:</span>
                <span class="v mono">${formatCoordinates(lat, lng)}</span>
            </div>
            <div class="sel-info-row">
                <span class="k">${t('common.last-updated')}:</span>
                <span class="v mono">${this._formatTimeAgo(userData.timestamp)}</span>
            </div>
        </div>
    `;

        marker.getPopup().setHTML(popupContent);
    }
    /**
     * Remove all avatar markers from the map
     */
    removeAvatarMarkers() {
        this.avatarMarkers.forEach(marker => {
            marker.remove();
        });
        this.avatarMarkers.clear();
    }

    _calculateAvatarOpacity(timestamp) {
        if (!timestamp) return 1.0;

        const now = Date.now();
        const ageMs = now - new Date(timestamp);
        const ageMinutes = ageMs / (1000 * 60);

        if (ageMinutes <= 10) {
            return 1.0;
        }

        if (ageMinutes <= 180) {
            const progress = (ageMinutes - 10) / (180 - 10);
            return 1.0 - (0.9 * progress);
        }
        return 0.1;
    }

    _formatTimeAgo(timestamp) {
        if (!timestamp) return t('common.unknown') || 'Unknown';

        const now = Date.now();
        const diffMs = now - new Date(timestamp);
        const diffMinutes = Math.floor(diffMs / (1000 * 60));
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));

        if (diffMinutes < 1) {
            return t('common.just-now') || 'Just now';
        } else if (diffMinutes < 60) {
            return `${t('common.minutes-ago', [diffMinutes]) || 'minutes ago'}`;
        } else if (diffHours < 24) {
            return `${t('common.hours-ago', [diffHours]) || 'hours ago'}`;
        } else {
            const diffDays = Math.floor(diffHours / 24);
            return `${t('common.days-ago', [diffDays]) || 'days ago'}`;
        }
    }

    setSelectedManager(managerId) {
        console.log("setting selected manager to", managerId);
        this.selectedManager = managerId;
        // Invalidate context so next _buildLayers does a full rebuild
        this._layerContextKey = null;
        if (this.element.classList.contains('is-loaded') && this.gpsDataManagers.length > 0) {
            this._refitBounds();
        }
    }
}

MapRenderer.rtlTextPluginConfigured = false;
