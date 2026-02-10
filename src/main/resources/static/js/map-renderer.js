class MapRenderer {
    constructor(element, userSettings, initialViewState) {
        this.userSettings = userSettings;
        this.gpsDataManagers = []

        this.viewState = initialViewState
        this.map = new maplibregl.Map({
            interleaved: true,
            container: 'new-map',
            style: '/map/reitti.json',
            center: [userSettings.homeLongitude, userSettings.homeLatitude],
            pitch: this.viewState.is3d ? 45 : 0,
            maxPitch: 85,
            minZoom: 2,
        });

        this.photosManager = new PhotoClusterManager(this.map,  {
            clusterRadius: 80,
            iconSize: 56
        });

        this.map.on('load', () => {
            this.map.setSourceTileLodParams(1, 10); //effectively disabling LOD


        });

        this.terrainLayer = new deck.TerrainLayer({
            id: 'terrain-loader',
            // Use the same Mapterhorn/MapLibre source
            elevationData: 'https://tiles.mapterhorn.com/{z}/{x}/{y}.webp',
            elevationDecoder: {
                rScaler: 256,
                gScaler: 1,
                bScaler: 1 / 256,
                offset: -32768
            },
            minZoom: 0,
            maxZoom: 14,
            elevationScale: 1.5,
            bounds: [-180, -90, 180, 90], // Global bounds help with alignment
            operation: 'terrain',
            loadOptions: {
                fetch: {
                    priority: 'high'
                }
            }
        });


        this.deckOverlay = new deck.MapboxOverlay({
            layers: []
        });
        this.map.addControl(this.deckOverlay);

        this.currentTime = 0;

        this.deckParams = {
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

        this.bounds = [];

        this.map.once('style.load', () => {
            this._switchMapBuildingLayer(this.viewState.renderBuildings && this.viewState.is3d);
            this._switchTerrainLayer(this.viewState.renderTerrain);
            this._switchSatelliteLayer(this.viewState.renderSatelliteView);
            this._switchProjection(this.viewState.renderGlobe);

        });
        if (!this.viewState.is3d) {
            this.map.dragRotate.disable();
            this.map.touchZoomRotate.disableRotation();
        }

        this._setup();
    }

    updateViewState(viewState) {
        let switchTo3D = false;
        let switchTo2D = false;
        let switchTerrainOn = false;
        let switchTerrainOff = false;
        let switchBuildingsOn = false;
        let switchBuildingsOff = false;
        let switchSatelliteOn = false;
        let switchSatelliteOff = false;
        let toggleGlobeMode = false;

        if (this.viewState.is3d && !viewState.is3d) {
            switchTo2D = true;
        } else if (!this.viewState.is3d && viewState.is3d) {
            switchTo3D = true;
        }

        if (this.viewState.renderTerrain && !viewState.renderTerrain) {
            switchTerrainOff = true;
        } else if (!this.viewState.renderTerrain && viewState.renderTerrain) {
            switchTerrainOn = true;
        }

        if (this.viewState.renderBuildings && (!viewState.renderBuildings || !viewState.is3d)) {
            switchBuildingsOff = true;
        } else if (!this.viewState.renderBuildings && viewState.renderBuildings && viewState.is3d) {
            switchBuildingsOn = true;
        }

        if (this.viewState.renderSatelliteView && !viewState.renderSatelliteView) {
            switchSatelliteOff = true;
        } else if (!this.viewState.renderSatelliteView && viewState.renderSatelliteView) {
            switchSatelliteOn = true;
        }

        if ((this.viewState.renderGlobe && !viewState.renderGlobe) || (!this.viewState.renderGlobe && viewState.renderGlobe)) {
            toggleGlobeMode = true;
        }

        this.viewState = viewState;
        if (switchBuildingsOn || switchBuildingsOff) {
            this._awaitStyleLoaded(() => {
                this._switchMapBuildingLayer(switchBuildingsOn && this.viewState.is3d);
            });
        }
        if (switchTo3D || switchTo2D) {
            this._awaitStyleLoaded(() => {
                this._switchMapBuildingLayer(switchTo3D && this.viewState.renderBuildings);
            });

            if (this.map) {
                if (switchTo2D) {
                    this.map.dragRotate.disable();
                    this.map.touchZoomRotate.disableRotation();
                } else {
                    this.map.dragRotate.enable();
                    this.map.touchZoomRotate.enableRotation();
                }
                this.map.easeTo({
                    pitch: switchTo3D ? 45 : 0,
                    bearing: switchTo3D ? this.map.getBearing() : 0,
                    duration: 500, // Mapbox uses 'duration' in ms
                    essential: true
                });
            }
        }

        if (switchTerrainOn || switchTerrainOff) {
            this._awaitStyleLoaded(() => {
                this._switchTerrainLayer(switchTerrainOn)
            });
        }

        if (switchSatelliteOn || switchSatelliteOff) {
            this._awaitStyleLoaded(() => {
                this._switchSatelliteLayer(switchSatelliteOn && !switchTerrainOn);
            })
        }

        if (toggleGlobeMode) {
            this._switchProjection(this.viewState.renderGlobe);
        }

        this.gpsDataManagers.forEach(manager => {
            this._updateLayers(manager)
        })
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

    setGpsDataManagers(managers) {
        this.gpsDataManagers = managers;
        this.bounds = [];
    }

    fitMapToBounds(bounds) {
        this.map.fitBounds(bounds, {
            padding: {
                top: 50,
                bottom: 100,
                right: 100,
                left: 450
            },
            maxZoom: 15,
            duration: 1000,
            essential: true
        });
    }

    flyTo(config) {
        this.map.flyTo(config);
    }

    finishedLoading() {
        this.gpsDataManagers.forEach(manager => this._extendBounds(manager.bounds));
        if (this.bounds.length === 0) {
            this._flyToHomeLocation();
        } else {
            this.fitMapToBounds(this.bounds);
        }
    }

    reset() {
        this.deckOverlay.setProps([]);
    }

    _updateLayers(manager) {
        const layers = [];

        const layerKey = `${manager.id}-${this.viewState.aggregated ? 'agg' : 'lin'}-${this.viewState.renderTerrain ? 'terrain' : 'flat'}`;
        const totalTimeSpanSec = this.viewState.aggregated ? 24 * 60 * 60 : manager.maxTimestamp - manager.minTimestamp;
        const calculatedTrail = Math.max(1800, totalTimeSpanSec * 0.02);

        if (this.viewState.renderTerrain) {
            layers.push(this.terrainLayer)
        }
        if (manager.cursor > 0) {
            // --- BASE VISUALIZATION ---
            if (this.viewState.viewMode === 'BUNDLED') {
                layers.push(...this._getBundleLayers(layerKey, manager));
            } else {
                const buffer = this.viewState.viewMode === 'LINEAR' ? 'cleaned' : 'raw';
                const cursor = this.viewState.viewMode === 'LINEAR' ? manager.cleanedCursor : manager.cursor;
                const extensions = this.viewState.renderTerrain ? [new deck._TerrainExtension()] : [];
                layers.push(new deck.PathLayer({
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
                layers.push(new deck.TripsLayer({
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
                layers.push(new deck.TripsLayer({
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
        layers.push(...this._getVisitLayers(layerKey, manager));

        this.deckOverlay.setProps({
            layers: layers
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
                getPolygon: d => d.polygon.coordinates,
                getFillColor: d => [...manager.color, this.deckParams.visits.polygonOpacity],
                getLineColor: d => [...manager.color, 255],
                pickable: true,
                depthTest: false,
                onHover: info => this._updateTooltip(info),
                visible: isOverview && this.map.getZoom() > this.deckParams.visits.minZoom,
                extensions: extensions,
                terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                updateTriggers: {
                    getFillColor: [currentTime],
                    visible: [isOverview, this.map.zoom],
                    data: manager.visits?.length
                }
            }),
            new deck.ScatterplotLayer({
                id: `place-outer-circles-${layerKey}`,
                data: places,
                getPosition: d => d.coordinates,
                getRadius: d => {
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
                depthTest: false,
                onHover: info => this._updateTooltip(info),
                visible: !isOverview || this.map.getZoom() > this.deckParams.visits.minZoom,
                extensions: extensions,
                terrainDrawMode: this.viewState.renderTerrain ? 'offset' : undefined,
                updateTriggers: {
                    getRadius: [currentTime, isOverview],
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
                getRadius: 8,
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
                    getFillColor: [currentTime, isOverview],
                    getLineColor: [currentTime, isOverview],
                    data: manager.visits?.length
                },
                pickable: true,
                onHover: info => this._updateTooltip(info),
            })
        ];
    }

    _setup = () => {
        this.map.on('move', () => {
            const currentState = {
                longitude: this.map.getCenter().lng,
                latitude: this.map.getCenter().lat,
                zoom: this.map.getZoom()
            };

            gpsDataManagers.forEach(manager => {
                this._updateLayers(manager)
            })
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
                <b>${locale.map.popup.labels.from}</b> ${formatDateTime(v.startTime)}<br>
                <b>${locale.map.popup.labels.to}</b> ${formatDateTime(v.endTime)}
            </div>
        `).join('');

            tooltip.innerHTML = `
            <div style="font-size: 14px; font-weight: bold; margin-bottom: 5px;">${object.name}</div>
            <div><b>${window.locale.map.popup.labels.totalDuration}</b> ${humanizeDuration(object.totalDurationSec * 1000)}</div>
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

    setBearing(number) {
        this.map.easeTo({
            bearing: number,
            duration: 500, // Mapbox uses 'duration' in ms
            essential: true
        });
    }

    _switchProjection(renderGlobe) {
        this.map.setProjection({
            type: renderGlobe ? 'globe' : 'mercator'
        });
    }

    _switchSatelliteLayer(enable) {
        if (this.map.getLayer('satellite-layer')) {
            this.map.setPaintProperty(
                'satellite-layer',
                'raster-opacity',
                enable ? 1 : 0
            );
        }
        //hide all layers which are not desired
        const style = this.map.getStyle();
        if (!style || !style.layers) return;

        // Types of layers that usually look like "ground" or "cartoons"
        const targetTypes = ['fill', 'background', 'fill-extrusion', 'line'];

        // Layers we NEVER want to hide (like the satellite itself or transparent overlays)
        const protectedLayers = ['satellite-layer', 'sky', 'building-3d'];

        style.layers.forEach(layer => {
            if (targetTypes.includes(layer.type) && !protectedLayers.includes(layer.id)) {

                // Determine the correct property name based on layer type
                let opacityProp = '';
                if (layer.type === 'line') opacityProp = 'line-opacity';
                if (layer.type === 'fill') opacityProp = 'fill-opacity';
                if (layer.type === 'fill-extrusion') opacityProp = 'fill-extrusion-opacity';
                if (layer.type === 'background') opacityProp = 'background-opacity';

                // Apply the Toggle
                // If showing satellite -> Opacity 0
                // If hiding satellite -> Opacity null (Resets to style.json default)
                this.map.setPaintProperty(
                    layer.id,
                    opacityProp,
                    enable ? 0 : null
                );
            }
        });
        this.map.setPaintProperty('building-3d', 'fill-extrusion-opacity', enable ? 0.6 : null)
    }

    _switchTerrainLayer(enable) {
        if (enable) {
            this.map.setLayoutProperty('hillshading', 'visibility', 'visible');
            this.terrainLayer = new deck.TerrainLayer({
                id: 'terrain-loader',
                elevationData: 'https://tiles.mapterhorn.com/{z}/{x}/{y}.webp',
                elevationDecoder: {
                    rScaler: 256,
                    gScaler: 1,
                    bScaler: 1 / 256,
                    offset: -32768
                },
                minZoom: 0,
                maxZoom: 14,
                elevationScale: 1.5,
                bounds: [-180, -90, 180, 90], // Global bounds help with alignment
                operation: 'terrain',
                loadOptions: {
                    fetch: {
                        priority: 'high'
                    }
                }
            });
            this.map.setTerrain({
                source: 'terrain-source',
                exaggeration: 1
            });
        } else {
            this.map.setLayoutProperty('hillshading', 'visibility', 'none');
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

    setPhotos(photos) {
        this.photosManager.setPhotos(photos);
    }
}