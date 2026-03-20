class MapRenderer {
    constructor(element, userSettings, initialViewState, viewConfig = {}) {
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
        this._pitchBearingAllowed = true;

        const mapOptions = {
            interleaved: true,
            container: element,
            style: window.contextPath + '/map/reitti.json?ts=' + new Date().getTime(),
            center: [userSettings.homeLongitude, userSettings.homeLatitude],
            pitch: this.viewState.is3d ? 45 : 0,
            maxPitch: 85,
            minZoom: 2
        };
        if (this.viewState.fixed) {
            mapOptions.interactive = false;
        }
        if (this.viewState.fixed || this.viewState.hideAttribution) {
            mapOptions.attributionControl = false;
        }
        this.map = new maplibregl.Map(mapOptions);

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
                pathWidth: 2,
                pathOpacity: 35,
                staticPathWidth: 4,
                staticPathOpacity: 200,
            },
            visits: {
                minZoom: 12,
                polygonMinZoom: 16,
                radius: 1,
                opacity: 140,
                polygonOpacity: 140,
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

        this._initialLoadPromise = new Promise(resolve => {
            this.map.once('style.load', async () => {
                console.log('Initial style loaded!'); // For debugging
                await this._switchMapBuildingLayer(this.viewState.renderBuildings && this.viewState.is3d);
                await this._switchTerrainLayer(this.viewState.renderTerrain);
                await this._switchSatelliteLayer(this.viewState.renderSatelliteView);
                await this._switchProjection(this.viewState.renderGlobe);
                this._syncPitchBearingState(false);
                this.element.classList.remove('is-loading');
                this.element.classList.add('is-loaded');
                resolve();
            });
        });

        this._setup();
    }


    async updateViewState(next) {
        this.transitionQueue = this.transitionQueue.then(() => this._updateViewStateInternal(next));
        return this.transitionQueue;
    }

    async _updateViewStateInternal(next) {
        await this._initialLoadPromise;
        const prev = { ...this.viewState };
        this.viewState = next;

        const projectionChanged = prev.renderGlobe !== next.renderGlobe;
        const satelliteChanged  = prev.renderSatelliteView !== next.renderSatelliteView;
        const terrainChanged    = prev.renderTerrain !== next.renderTerrain;
        const buildingsChanged  = (prev.renderBuildings !== next.renderBuildings) || (prev.is3d !== next.is3d);

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
    setGpsDataManagers(managers) {
        this.gpsDataManagers = managers;
        this.bounds = [];
    }

    fitMapToBounds(bounds) {
        this.map.fitBounds(bounds, this.viewConfig.fitConfig);
    }

    flyTo(config) {
        this.map.flyTo(config);
    }

    finishedLoading() {
        console.log('Finished loading map data');

        const performFit = async () => {
            try {
                await this._initialLoadPromise;
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

        // Check if style is already loaded
        if (this.map.isStyleLoaded()) {
            console.log('Style already loaded, fitting immediately.');
            performFit();
        } else {
            console.log('Style not loaded yet, waiting...');
            this.map.once('load', performFit);
        }
    }

    reset() {
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
                        avatarUrl: userConfig.avatarUrl,
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
            allLayers.push(...this._getVisitLayers(layerKey, manager));
        })

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
                    const maxRadius = isOverview ? 500 : 600;

                    // This treats the duration as the "Area" of the circle
                    // Adjusted so 1 day (86400s) hits a reasonable size
                    const scalingFactor = isOverview ? 0.8 : 1.2;

                    const calculated = minRadius + (Math.sqrt(duration) * scalingFactor);

                    return Math.min(calculated, maxRadius);
                },
                getFillColor: d => [...manager.color, isOverview ? this.deckParams.visits.opacity : (this._getActiveVisitEffect(d).opacity * this.deckParams.visits.opacity)],
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

        this.map.on('zoom', () => {
            if (!this._pitchBearingAllowed) {
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
            <div style="font-size: 14px; font-weight: bold; margin-bottom: 5px;">${object.name}</div>
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
        this._applySatellitePaintProperties(enable);
        await this._waitForIdle();
    }

    _applySatellitePaintProperties(enable) {
        if (!this.map || !this.map.getStyle) return;

        if (this.map.getLayer && this.map.getLayer('satellite-layer')) {
            this.map.setPaintProperty('satellite-layer', 'raster-opacity', enable ? 1 : 0);
        }

        const style = this.map.getStyle();
        if (!style || !Array.isArray(style.layers)) return;

        const targetTypes = ['fill', 'background', 'fill-extrusion', 'line'];
        const protectedLayers = ['satellite-layer', 'sky', 'building-3d'];

        style.layers.forEach(layer => {
            if (targetTypes.includes(layer.type) && !protectedLayers.includes(layer.id)) {
                let opacityProp = '';
                if (layer.type === 'line') opacityProp = 'line-opacity';
                if (layer.type === 'fill') opacityProp = 'fill-opacity';
                if (layer.type === 'fill-extrusion') opacityProp = 'fill-extrusion-opacity';
                if (layer.type === 'background') opacityProp = 'background-opacity';

                try {
                    this.map.setPaintProperty(layer.id, opacityProp, enable ? 0 : null);
                } catch (_) {
                    // Layer might not be present yet; ignore
                }
            }
        });

        // Buildings: keep a faint extrusion over satellite if desired
        if (this.map.getLayer && this.map.getLayer('building-3d')) {
            try {
                this.map.setPaintProperty('building-3d', 'fill-extrusion-opacity', enable ? 0.6 : null);
            } catch (_) {}
        }
    }

    _extractTerrainUrl() {
        // 1. Try reading directly from the loaded Style JSON (Instant, no waiting)
        const style = this.map.getStyle();
        if (style && style.sources && style.sources['terrain-source']) {
            const sourceDef = style.sources['terrain-source'];
            if (sourceDef.tiles && sourceDef.tiles.length > 0) {
                return sourceDef.tiles[0];
            }
            if (sourceDef.url) {
                return sourceDef.url;
            }
        }

        const source = this.map.getSource('terrain-source');
        if (source && source.tiles && source.tiles.length > 0) {
            return source.tiles[0];
        }

        return null; // Return null safely if nothing found
    }

    async _switchTerrainLayer(enable) {
        const hasHillshading = !!this.map.getLayer && this.map.getLayer('hillshading');

        if (enable) {
            // Get URL safely
            const terrainUrl = this._extractTerrainUrl();

            if (!terrainUrl) {
                console.warn("Terrain source definition not found in style.");
                return;
            }

            if (hasHillshading) {
                this.map.setLayoutProperty('hillshading', 'visibility', 'visible');
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
                source: 'terrain-source',
                exaggeration: 1
            });

        } else {
            this.terrainLayer = null;
            if (hasHillshading) {
                this.map.setLayoutProperty('hillshading', 'visibility', 'none');
            }
            this.map.setTerrain(null);
        }
    }

    _switchMapBuildingLayer(is3d) {
        if (is3d) {
            this.map.setLayoutProperty('building-3d', 'visibility', 'visible');
        } else {
            this.map.setLayoutProperty('building-3d', 'visibility', 'none');
        }
    }

    /**
     * Add an avatar marker at the specified coordinates
     */
    addAvatarMarker(userId, lat, lng, userData) {
        const SIZE = 40;
        
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
            </div>
        `;

        // Add popup with detailed user info
        const popup = new maplibregl.Popup({
            offset: 25,
            closeButton: false
        }).setHTML(popupContent);

        marker.setPopup(popup);

        // Use MapLibre's built-in hover events for better reliability
        marker.getElement().addEventListener('mouseenter', () => {
            marker.getPopup().addTo(this.map);
        });

        marker.getElement().addEventListener('mouseleave', () => {
            marker.getPopup().remove();
        });

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

                    // Update popup content with new timestamp
                    this.updateMarkerPopup(existingMarker, latestLocation.latitude, latestLocation.longitude, {
                        avatarUrl: userConfig.avatarUrl,
                        avatarFallback: userConfig.avatarFallback,
                        displayName: userConfig.displayName,
                        timestamp: latestLocation.timestamp
                    });
                } else {
                    debugger
                    // Create new marker for this user
                    this.addAvatarMarker(
                        manager.id,
                        latestLocation.latitude, 
                        latestLocation.longitude, 
                        {
                            avatarUrl: userConfig.avatarUrl,
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

}
