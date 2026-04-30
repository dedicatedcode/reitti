class MapRenderer {
    static getBundledMapStyles() {
        const contextPath = window.contextPath || '';
        return [
            {
                id: 'reitti',
                label: 'Reitti',
                styleUrl: `${contextPath}/map/reitti.json?ts=${Date.now()}`,
                capabilities: {
                    terrainSourceId: 'terrain-source',
                    hillshadeLayerId: 'hillshading',
                    satelliteLayerId: 'satellite-layer',
                    building3dLayerIds: ['building-3d']
                }
            }
        ];
    }

    static getCustomMapStyles() {
        return Array.isArray(window.reittiCustomMapStyles)
            ? window.reittiCustomMapStyles
            : [];
    }

    static getMapStyles() {
        const bundledStyles = MapRenderer.getBundledMapStyles();
        const configuredStyles = Array.isArray(window.reittiMapStyles)
            ? window.reittiMapStyles.filter(style => !bundledStyles.some(bundledStyle => bundledStyle.id === style.id))
            : [];
        return [
            ...bundledStyles,
            ...configuredStyles,
            ...MapRenderer.getCustomMapStyles()
        ];
    }

    static dispatchMapStylesChanged(activeStyleId = null) {
        document.dispatchEvent(new CustomEvent('mapStylesChanged', {
            detail: {
                activeStyleId,
                styles: MapRenderer.getMapStyles()
            }
        }));
    }

    static getMapStyleValue(mapStyle) {
        if (mapStyle?.styleJson) {
            return MapRenderer._cloneStaticStyleDefinition(mapStyle.styleJson);
        }
        return mapStyle?.styleUrl;
    }






    static _cloneStaticStyleDefinition(definition) {
        return JSON.parse(JSON.stringify(definition));
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
        return fetch(`${window.contextPath || ''}/settings/map-styles/api/active`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({activeStyleId: mapStyleId})
        }).catch(error => {
            console.warn('Unable to persist active map style:', error);
        });
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

        this.userSettings = userSettings;
        this.transitionQueue = Promise.resolve();
        this.element = document.getElementById(element);
        this.element.classList.add('is-loading');
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

        this._setup();
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
        next.mapStyleId = next.mapStyleId || prev.mapStyleId || MapRenderer.getDefaultMapStyleId();
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
        const explicitCapabilities = this.currentMapStyle?.capabilities || {};
        if (Object.keys(explicitCapabilities).length) {
            return explicitCapabilities;
        }

        return this._buildGenericStyleCapabilities();
    }

    _buildGenericStyleCapabilities() {
        const terrainSourceId = 'reitti-terrain-source';
        const satelliteSourceId = 'reitti-satellite-source';
        const buildingSourceId = 'reitti-building-source';
        const buildingCapabilities = this._detectBuildingCapabilities(buildingSourceId);

        return {
            terrainSourceId,
            terrainSourceDefinition: {
                type: 'raster-dem',
                tiles: ['https://tiles.mapterhorn.com/{z}/{x}/{y}.webp'],
                tileSize: 256,
                encoding: 'terrarium',
                maxzoom: 14,
                attribution: "© <a href='https://mapterhorn.com' target='_blank'>Mapterhorn</a>"
            },
            hillshadeLayerId: 'reitti-terrain-hillshade',
            hillshadeLayerDefinition: {
                id: 'reitti-terrain-hillshade',
                type: 'hillshade',
                source: terrainSourceId,
                layout: {
                    visibility: 'none'
                },
                paint: {
                    'hillshade-exaggeration': 0.35
                }
            },
            satelliteSourceId,
            satelliteSourceDefinition: {
                type: 'raster',
                tiles: [
                    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
                ],
                tileSize: 256,
                maxzoom: 18,
                attribution: "Powered by <a href='https://www.esri.com' target='_blank'>Esri</a> | Sources: Esri, Maxar, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community"
            },
            satelliteLayerId: 'reitti-satellite-layer',
            satelliteLayerDefinition: {
                id: 'reitti-satellite-layer',
                type: 'raster',
                source: satelliteSourceId,
                paint: {
                    'raster-opacity': 0
                }
            },
            buildingSourceId,
            buildingSourceDefinition: {
                type: 'vector',
                url: 'https://tiles.dedicatedcode.com/planet',
                minzoom: 0,
                maxzoom: 14,
                attribution: "© <a href='https://openfreemap.org' target='_blank'>OpenFreeMap</a> © <a href='https://www.openstreetmap.org/copyright' target='_blank'>OSM</a>"
            },
            ...buildingCapabilities
        };
    }

    _detectBuildingCapabilities(runtimeBuildingSourceId = '') {
        const style = this.map?.getStyle?.();
        const layers = style?.layers || [];
        const sources = style?.sources || {};

        const existingExtrusionIds = layers
            .filter(layer => layer.type === 'fill-extrusion' && this._looksLikeBuildingLayer(layer))
            .map(layer => layer.id);
        if (existingExtrusionIds.length) {
            return {
                building3dLayerIds: existingExtrusionIds,
                building3dLayerDefinitions: []
            };
        }

        const buildingFillLayer = layers.find(layer => layer.type === 'fill' && this._looksLikeBuildingLayer(layer));
        let sourceId = buildingFillLayer?.source;
        let sourceLayer = buildingFillLayer?.['source-layer'];
        if (!sourceId || !sourceLayer) {
            sourceId = runtimeBuildingSourceId || this._getPreferredVectorSourceId(sources);
            sourceLayer = this._getPreferredBuildingSourceLayer(sourceId, sources, runtimeBuildingSourceId);
        }
        if (!sourceId || !sourceLayer) {
            return {
                building3dLayerIds: [],
                building3dLayerDefinitions: []
            };
        }

        return {
            building3dLayerIds: ['reitti-building-3d'],
            building3dLayerDefinitions: [
                {
                    id: 'reitti-building-3d',
                    type: 'fill-extrusion',
                    source: sourceId,
                    'source-layer': sourceLayer,
                    minzoom: 15,
                    layout: {
                        visibility: 'none'
                    },
                    paint: {
                        'fill-extrusion-color': '#d2dde2',
                        'fill-extrusion-height': [
                            'case',
                            ['has', 'height'],
                            ['to-number', ['get', 'height'], 8],
                            ['has', 'render_height'],
                            ['to-number', ['get', 'render_height'], 8],
                            ['has', 'levels'],
                            ['*', ['to-number', ['get', 'levels'], 3], 3],
                            8
                        ],
                        'fill-extrusion-base': [
                            'case',
                            ['has', 'min_height'],
                            ['to-number', ['get', 'min_height'], 0],
                            ['has', 'render_min_height'],
                            ['to-number', ['get', 'render_min_height'], 0],
                            0
                        ],
                        'fill-extrusion-opacity': 0.75,
                        'fill-extrusion-vertical-gradient': true
                    }
                }
            ]
        };
    }

    _looksLikeBuildingLayer(layer) {
        const id = String(layer?.id || '').toLowerCase();
        const sourceLayer = String(layer?.['source-layer'] || '').toLowerCase();
        return id.includes('building') || sourceLayer.includes('building');
    }

    _getPreferredVectorSourceId(sources) {
        const configuredSourceId = this.currentMapStyle?.dataSource?.sourceId;
        if (configuredSourceId && sources[configuredSourceId]?.type === 'vector') {
            return configuredSourceId;
        }

        const entry = Object.entries(sources).find(([, source]) => source?.type === 'vector');
        return entry?.[0] || '';
    }

    _getPreferredBuildingSourceLayer(sourceId, sources, runtimeBuildingSourceId = '') {
        if (!sourceId) return '';
        if (sourceId === runtimeBuildingSourceId) return 'building';
        if (sources[sourceId]?.type !== 'vector') return '';
        return 'building';
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
        this.gpsDataManagers = [...managers].reverse();
        this.bounds = [];
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
                this.bounds = [];
                console.log('Attempting to fit bounds...');
                this.gpsDataManagers.forEach(manager => this._extendBounds(manager.bounds));
                console.log('Bounds calculated:', this.bounds);
                if (this.bounds.length === 0) {
                    this._flyToHomeLocation();
                } else {
                    this.fitMapToBounds(this.bounds);
                }
                this.element.classList.remove('is-loading');
                this.element.classList.add('is-loaded');
            } catch (error) {
                console.error("Error during performFit:", error);
            }
        };

        return performFit();
    }

    reset() {
        this.highlightLayer = null;
        this.deckOverlay.setProps([]);
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

    enableAvatars() {
        this.showAvatars = true;
        this.gpsDataManagers.forEach(manager => {
            const userConfig = manager.config;
            const latestLocation = manager.lastLocation;
            
            if (latestLocation && userConfig) {
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

    _awaitStyleLoaded(func) {
        if (this.map.isStyleLoaded()) {
            func();
        } else {
            this.map.once('style.load', () => {
                func()
            });
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
                    const extensions = this.viewState.renderTerrain ? [new deck._TerrainExtension()] : [];
                    allLayers.push(new deck.PathLayer({
                        id: `paths-static-fixed-${layerKey}`,
                        data: manager.getLayerData(buffer, this.viewState.aggregated),
                        positionFormat: 'XYZ',
                        getColor: [...manager.color, this.deckParams.trips.staticPathOpacity],
                        getWidth: this.deckParams.trips.staticPathWidth,
                        widthMinPixels: this.deckParams.trips.staticPathWidth,
                        visibility: !this.viewState.animating,
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
                            visibility: [this.viewState.animating],
                            getColor: [manager.color],
                        }
                    }));
                    if (this.viewState.animating) {
                        // SHADOW TRIP (Animated)
                        allLayers.push(new deck.TripsLayer({
                            id: `trips-shadow-${layerKey}`,
                            data: manager.getLayerData(buffer, this.viewState.aggregated),
                            positionFormat: 'XYZ',
                            getColor: [255, 255, 255],
                            opacity: this.deckParams.trips.shadowOpacity,
                            widthMinPixels: this.deckParams.trips.shadowWidth,
                            trailLength: calculatedTrail * 1.2,
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

                        // CORE TRIP (Animated)
                        allLayers.push(new deck.TripsLayer({
                            id: `trips-core-${layerKey}`,
                            data: manager.getLayerData(buffer, this.viewState.aggregated),
                            positionFormat: 'XYZ',
                            getColor: manager.color,
                            opacity: this.deckParams.trips.cometOpacity * this.viewState.animating,
                            widthMinPixels: this.deckParams.trips.cometWidth,
                            trailLength: calculatedTrail,
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

    _getBundleLayers(layerKey, manager) {
        const timeSpan = this.viewState.aggregated ? 86400 : (manager.maxTimestamp - manager.minTimestamp);
        const calculatedTrail = Math.max(1800, timeSpan * 0.02);

        // Check if bundling is complete
        if (manager.loadingState !== 'complete' || !manager.snappedBuffer || manager.snappedBuffer.length === 0) {
            return [];
        }
        const layers = [];
        const extensions = this.viewState.renderTerrain ? [new deck._TerrainExtension()] : [];
        layers.push(new deck.PathLayer({
            id: `bundled-paths-static-${layerKey}`,
            data: manager.getLayerData('bundled', this.viewState.aggregated),
            positionFormat: 'XY',
            getColor: [...manager.color, this.deckParams.bundled.staticPathOpacity],
            widthMinPixels: this.deckParams.bundled.staticPathWidth,
            capRounded: true,
            jointRounded: true,
            depthTest: false,
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [
                    manager.snappedVersion,
                    this.viewState.aggregated
                ],
                getPath: [
                    manager.snappedVersion,
                    this.viewState.aggregated
                ],
                getColor: [
                    manager.color,
                ],
            }
        }));


        layers.push(new deck.PathLayer({
            id: `bundled-path-${layerKey}`,
            data: manager.getLayerData('bundled', this.viewState.aggregated),
            positionFormat: 'XY',
            widthMinPixels: this.deckParams.bundled.pathWidth,
            getColor: [...manager.color, this.deckParams.bundled.pathOpacity],
            parameters: {
                blendFunc: [770, 1], // GL_SRC_ALPHA, GL_ONE
                blendEquation: 32774, // GL_FUNC_ADD
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

        layers.push(new deck.TripsLayer({
            id: `bundled-path-shadow-${layerKey}`,
            data: manager.getLayerData('bundled', this.viewState.aggregated),
            positionFormat: 'XY',
            getColor: [255, 255, 255],
            opacity: this.deckParams.bundled.shadowOpacity,
            visible: this.viewState.animating,
            widthMinPixels: this.deckParams.bundled.shadowWidth,
            trailLength: calculatedTrail * 1.2,
            currentTime: this.viewState.currentTime,
            capRounded: true,
            jointRounded: true,
            parameters: {depthTest: false},
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [this.viewState.currentTime],
            }
        }));

        // CORE TRIP (Animated)
        layers.push(new deck.TripsLayer({
            id: `bundled-path-core-${layerKey}`,
            data: manager.getLayerData('bundled', this.viewState.aggregated),
            positionFormat: 'XY',
            getColor: manager.color,
            opacity: this.deckParams.bundled.cometOpacity,
            visible: this.viewState.animating,
            widthMinPixels: this.deckParams.bundled.cometWidth,
            trailLength: calculatedTrail,
            currentTime: this.viewState.currentTime,
            capRounded: true,
            jointRounded: true,
            depthTest: false,
            blendFunc: [770, 771], // Standard Alpha Blending
            blendEquation: 32774,
            extensions: extensions,
            terrainDrawMode: 'offset',
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [this.viewState.currentTime],
            }
        }));

        return layers;
    }

    _getVisitLayers(layerKey, manager) {
        const isOverview = !this.viewState.animating;
        const currentTime = this.viewState.currentTime;

        const places = manager.visits;
        if (places === undefined) {
            return [];
        }
        const extensions = this.viewState.renderTerrain ? [new deck._TerrainExtension()] : [];
        return [
            // 1. Polygon Layer
            new deck.PolygonLayer({
                id: `visit-polygons-${layerKey}`,
                data: places.filter(p => p.polygon),
                getPolygon: d => {
                    if (!d.polygon || !Array.isArray(d.polygon)) return [];
                    return d.polygon.map(point => [point.longitude, point.latitude]);
                },
                getFillColor: d => [...manager.color, this.deckParams.visits.polygonOpacity],
                getLineColor: d => [...manager.color, 255],
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: isOverview && this.map.getZoom() > this.deckParams.visits.polygonMinZoom,
                extensions: extensions,
                terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                updateTriggers: {
                    getFillColor: [currentTime],
                    visible: [isOverview, this.map.getZoom()],
                    data: manager.visits?.length
                }
            }),
            new deck.ScatterplotLayer({
                id: `place-outer-circles-${layerKey}`,
                data: places,
                getPosition: d => d.coordinates,
                getRadius: d => {
                    if (isOverview && this.map.getZoom() > this.deckParams.visits.polygonMinZoom && d.polygon) {
                        return 0;
                    }

                    const duration = isOverview
                        ? (d.totalDurationSec || 0)
                        : this._getActiveVisitEffect(d).seconds;

                    if (duration <= 0) {
                        return 0;
                    }

                    const minRadius = isOverview ? this.deckParams.visits.radius : (this.deckParams.visits.radius * 1.5);
                    const maxRadius = isOverview ? 100 : 150;

                    // This treats the duration as the "Area" of the circle
                    // Adjusted so 1 day (86400s) hits a reasonable size
                    const scalingFactor = isOverview ? 0.8 : 1.2;

                    const calculated = minRadius + (Math.sqrt(duration) * scalingFactor);

                    return Math.min(calculated, maxRadius);
                },
                getFillColor: d => [...manager.color, isOverview ? this.deckParams.visits.opacity : (this._getActiveVisitEffect(d).opacity * this.deckParams.visits.opacity)],
                getLineColor: d => [...manager.color, 255],
                stroked: true,
                getLineWidth: this.deckParams.visits.lineWidth,
                pickable: true,
                depthTest: true,
                onHover: info => this._updateTooltip(info),
                visible: !isOverview || (isOverview && this.map.getZoom() > this.deckParams.visits.minZoom),
                extensions: extensions,
                terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, this.map.getZoom()],
                    getFillColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                // Optional: Smooth the appearance/disappearance
                transitions: {
                    getRadius: {
                        type: 'spring',
                        stiffness: 0.1,
                        damping: 0.15,
                        enter: d => [0] // Starts at 0 radius when appearing
                    }
                }
            }),

            new deck.ScatterplotLayer({
                id: `place-inner-circles-${layerKey}`,
                data: places,
                getPosition: d => d.coordinates,
                getRadius: d =>{
                    if (isOverview && this.map.getZoom() > this.deckParams.visits.polygonMinZoom && d.polygon) {
                        return 0;
                    } else {
                        return 8;
                    }
                },
                stroked: true,
                lineWidthMinPixels: this.deckParams.visits.lineWidth,
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
                terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                updateTriggers: {
                    getRadius: [currentTime, isOverview, this.map.getZoom()],
                    getFillColor: [currentTime, isOverview],
                    getLineColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                pickable: true,
                onHover: info => this._updateTooltip(info),
            })
        ];
    }

    async _waitForIdle() {
        if (this.map.loaded() && !this.map.isMoving()) {
            return; // Already idle, resolve immediately
        }
        return new Promise(resolve => this.map.once('idle', resolve));
    }

    _rerenderOverlays() {
        this._updateLayers();
        if (this.showAvatars) this.updateAvatarPositions();
        this.viewConfig.mapDataProviders.forEach(provider => provider.render(this.map));
    }

    _setup = () => {
        this.map.on('move', () => {
            this._updateLayers()
        });

        this.map.on('zoomend', () => {
            this._syncPitchBearingState();
        });

        this.map.on('zoom', (e) => {
            if (e.originalEvent && !this._pitchBearingAllowed) {
                if (this.map.getPitch() !== 0 || this.map.getBearing() !== 0) {
                    this.map.jumpTo({ pitch: 0, bearing: 0 });
                }
            }

        });

    }

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

        // 1. Try reading directly from the loaded Style JSON (Instant, no waiting)
        const style = this.map.getStyle();
        if (style && style.sources && style.sources[terrainSourceId]) {
            const sourceDef = style.sources[terrainSourceId];
            if (sourceDef.tiles && sourceDef.tiles.length > 0) {
                return sourceDef.tiles[0];
            }
            if (sourceDef.url) {
                return sourceDef.url;
            }
        }

        const source = this.map.getSource(terrainSourceId);
        if (source && source.tiles && source.tiles.length > 0) {
            return source.tiles[0];
        }

        return null; // Return null safely if nothing found
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
            const terrainUrl = this._extractTerrainUrl();

            if (!terrainUrl) {
                console.warn("Terrain source definition not found in style.");
                return;
            }

            if (hasHillshading) {
                this.map.setLayoutProperty(hillshadeLayerId, 'visibility', 'visible');
            }

            // Create DeckGL layer
            this.terrainLayer = new deck.TerrainLayer({
                id: 'terrain-loader',
                elevationData: terrainUrl,
                elevationDecoder: {
                    rScaler: 256,
                    gScaler: 1,
                    bScaler: 1 / 256,
                    offset: -32768
                },
                minZoom: 0,
                maxZoom: 14,
                elevationScale: 1.5,
                operation: 'terrain',
                loadOptions: { fetch: { priority: 'high' } }
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

        // We must trigger a re-render of the deck.gl overlay
        this._rerenderOverlays(); // We'll modify _updateLayers to include the highlight

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
            anchor: 'center'
        })
            .setLngLat([lng, lat])
            .addTo(this.map);

        // Store additional data with the marker for updates
        marker._avatarData = {
            userId: userId,
            userData: userData,
            imgElement: img
        };

        // Create detailed popup content
        const formatTimestamp = (timestamp) => {
            if (!timestamp) return t('common.unknown') || 'Unknown';
            const date = new Date(timestamp);
            return date.toLocaleString();
        };

        const formatCoordinates = (lat, lng) => {
            return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        };

        const popupContent = `
        <div style="font-family: var(--sans-font); min-width: 200px;">
            <div style="font-weight: bold; margin-bottom: 8px; color: var(--color-primary);">
                ${t('map.auto-update.latest-location')}
            </div>
            <div style="margin-bottom: 6px;">
                <strong>${t('common.user')}:</strong> ${userData.displayName}
            </div>
            <div style="margin-bottom: 6px;">
                <strong>${t('common.time')}:</strong> ${formatTimestamp(userData.timestamp)}
            </div>
            <div style="margin-bottom: 4px;">
                <strong>${t('common.position')}:</strong> ${formatCoordinates(lat, lng)}
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: #666;">
                <strong>${t('common.last-updated')}:</strong> ${this._formatTimeAgo(userData.timestamp)}
            </div>
        </div>
    `;

        // Add popup with detailed user info
        const popup = new maplibregl.Popup({
            offset: 25,
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



        // Store the marker by user ID for updates
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
                        timestamp: latestLocation.timestamp
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
        <div style="font-family: var(--sans-font); min-width: 200px;">
            <div style="font-weight: bold; margin-bottom: 8px; color: var(--color-primary);">
                ${t('map.auto-update.latest-location')}
            </div>
            <div style="margin-bottom: 6px;">
                <strong>${t('common.user')}:</strong> ${userData.displayName}
            </div>
            <div style="margin-bottom: 6px;">
                <strong>${t('common.time')}:</strong> ${formatTimestamp(userData.timestamp)}
            </div>
            <div style="margin-bottom: 4px;">
                <strong>${t('common.position')}:</strong> ${formatCoordinates(lat, lng)}
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: #666;">
                <strong>${t('common.last-updated')}:</strong> ${this._formatTimeAgo(userData.timestamp)}
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


}

MapRenderer.rtlTextPluginConfigured = false;
