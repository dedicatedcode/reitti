class MapRenderer {
    constructor(element, userSettings, photoClient) {
        this.userSettings = userSettings;
        this.photoClient = photoClient;
        this.gpsDataManagers = [];

        this.map = new maplibregl.Map({
            container: 'new-map',
            style: '/map/reitti.json',
            center: [userSettings.homeLongitude, userSettings.homeLatitude],
            zoom: 3,
            pitch: 0,
        });

        this.deckOverlay = new deck.MapboxOverlay({
            layers: []
        });
        this.map.addControl(this.deckOverlay);
        this.viewState = {
            aggregated: false,
            viewMode: 'LINEAR',
            currentTime: 0,
            animating: false,
            highlightTimes: []
        }
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
                staticPathWidth: 2,
                staticPathOpacity: 150,

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
        this._setup();
    }

    updateViewState(viewState) {
        this.viewState = viewState;
        this.gpsDataManagers.forEach(manager => {
            this._updateLayers(manager)
        })
    }

    update(time) {
        this.viewState.currentTime = time;
        this.gpsDataManagers.forEach(manager => {
            this._updateLayers(manager)
        })
    }

    setGpsDataManagers(managers) {
        this.gpsDataManagers = managers;
        this.bounds = [];
    }

    _flyToHomeLocation() {
        this.map.flyTo({
            center: [window.userSettings.homeLongitude, window.userSettings.homeLatitude],
            zoom: 15,
            pitch: 45
        });
    }

    fitMapToBounds(bounds) {
        this.map.fitBounds(bounds, {
            padding: {
                top: 50,
                bottom: 100,
                right: 100,
                left: 450
            },
            duration: 2000,
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

    _updateLayers(manager) {
        const layers = [];

        if (manager.buffer.length === 0) {
            this.deckOverlay.setProps({layers})
            return;
        }
        const totalTimeSpanSec = this.viewState.aggregated ? 24 * 60 * 60 : manager.maxTimestamp - manager.minTimestamp;
        const calculatedTrail = Math.max(1800, totalTimeSpanSec * 0.02);

        const mode = this.viewState.aggregated ? 'agg' : 'lin';

        // --- BASE VISUALIZATION ---
        if (this.viewState.viewMode === 'BUNDLED') {
            layers.push(...this._getBundleLayers(manager));
        } else {
            const buffer = this.viewState.viewMode === 'LINEAR' ? 'cleaned' : 'raw';
            const cursor = this.viewState.viewMode === 'LINEAR' ? manager.cleanedCursor : manager.cursor;
            layers.push(new deck.PathLayer({
                id: 'paths-static-fixed',
                data: manager.getLayerData(buffer, this.viewState.aggregated),
                positionFormat: 'XYZ',
                getColor: [...manager.color, this.deckParams.trips.staticPathOpacity],
                getWidth: this.deckParams.trips.staticPathWidth,
                widthMinPixels: this.deckParams.trips.staticPathWidth,
                visibility: !this.viewState.animating,
                capRounded: true,
                jointRounded: true,
                updateTriggers: {
                    data: [manager.buffer?.length, buffer],
                    getPath: [cursor, buffer],
                    getTimestamps: [this.viewState.aggregated],
                    visibility: [this.viewState.animating],
                    getColor: [manager.color],
                }
            }));

            // SHADOW TRIP (Animated)
            layers.push(new deck.TripsLayer({
                id: `trips-shadow-${mode}`,
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

                updateTriggers: {
                    data: [manager.buffer?.length, buffer],
                    visibility: [this.viewState.animating],
                    getPath: [cursor, buffer],
                    currentTime: [this.viewState.currentTime],
                    getTimestamps: [this.viewState.aggregated],
                }
            }));

            // CORE TRIP (Animated)
            layers.push(new deck.TripsLayer({
                id: `trips-core-${mode}`,
                data: manager.getLayerData(buffer, this.viewState.aggregated),
                positionFormat: 'XYZ',
                getColor: manager.color,
                opacity: this.deckParams.trips.cometOpacity * this.viewState.animating,
                widthMinPixels: this.deckParams.trips.cometWidth,
                trailLength: calculatedTrail,
                currentTime: this.viewState.currentTime,
                capRounded: true,
                jointRounded: true,
                updateTriggers: {
                    data: [manager.buffer?.length, buffer],
                    getPath: [cursor, buffer],
                    currentTime: [this.viewState.currentTime],
                    getTimestamps: [this.viewState.aggregated],
                }
            }));
        }

        layers.push(...this._getVisitLayers(manager));
        layers.push(...photoClient.getLayers(this.viewState.currentTime, {
            longitude: this.map.getCenter().lng,
            latitude: this.map.getCenter().lat,
            zoom: this.map.getZoom(),
            animating: this.viewState.animating
        }));

        this.deckOverlay.setProps({layers})
    }

    _getBundleLayers(manager) {
        const timeSpan = this.viewState.aggregated ? 86400 : (manager.maxTimestamp - manager.minTimestamp);
        const calculatedTrail = Math.max(1800, timeSpan * 0.02);

        // Check if bundling is complete
        if (manager.loadingState !== 'complete' || !manager.snappedBuffer || manager.snappedBuffer.length === 0) {
            return [];
        }
        const layers = [];
        layers.push(new deck.PathLayer({
            id: `bundled-paths-static-${manager.id}`,
            data: manager.getLayerData('bundled', this.viewState.aggregated),
            positionFormat: 'XY',
            getColor: [...manager.color, this.deckParams.bundled.staticPathOpacity],
            widthMinPixels: this.deckParams.bundled.staticPathWidth,
            capRounded: true,
            jointRounded: true,
            depthTest: false,
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
            id: `bundled-path-${manager.id}`,
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
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
            }

        }));

        layers.push(new deck.TripsLayer({
            id: `bundled-path-shadow-${manager.id}`,
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
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [this.viewState.currentTime],
            }
        }));

        // CORE TRIP (Animated)
        layers.push(new deck.TripsLayer({
            id: `bundled-path-core-${manager.id}`,
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
            updateTriggers: {
                data: [manager.snappedVersion],
                getPath: [manager.snappedVersion],
                currentTime: [this.viewState.currentTime],
            }
        }));

        return layers;
    }

    _getVisitLayers(manager) {
        const isOverview = !this.viewState.animating;
        const currentTime = this.viewState.currentTime;

        const places = manager.visits;
        if (places === undefined) {
            return [];
        }
        return [
            // 1. Polygon Layer
            new deck.PolygonLayer({
                id: 'visit-polygons',
                data: places.filter(p => p.polygon),
                getPolygon: d => d.polygon.coordinates,
                getFillColor: d => [...manager.color, this.deckParams.visits.polygonOpacity],
                getLineColor: d => [...manager.color, 255],
                pickable: true,
                depthTest: false,
                onHover: info => this._updateTooltip(info),
                visible: isOverview && this.map.getZoom() > this.deckParams.visits.minZoom,
                updateTriggers: {
                    getFillColor: [currentTime],
                    visible: [isOverview, this.map.zoom],
                    data: manager.visits?.length
                }
            }),
            new deck.ScatterplotLayer({
                id: 'place-outer-circles',
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
                id: 'place-inner-circles',
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

            if (this.photoClient) this.photoClient.updateClusters(currentState);
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

    reset() {
        this.deckOverlay.setProps([]);
    }
}