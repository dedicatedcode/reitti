class ExtensionLayerController {
    constructor(manager, extension, layer, key) {
        this.manager = manager;
        this.mapRenderer = manager.mapRenderer;
        this.extension = extension;
        this.layer = layer;
        this.key = key;
        this.signature = JSON.stringify(layer);
        this.generation = 0;
        this.active = false;
        this.abortController = null;
        this.pollTimer = null;
        this.viewportTimer = null;
        this.staleTimer = null;
        this.animationFrame = null;
        this.failures = 0;
        this.currentEntities = [];
        this.currentData = null;
        this.selectedEntityId = null;
        this.objectUrl = null;
        this.lastSuccessfulFetch = null;

        this.onViewportChange = () => {
            this.closeSelectedIfOutside();
            if (this.layer.pollTrigger === 'viewport' || this.layer.pollTrigger === 'both') {
                clearTimeout(this.viewportTimer);
                this.viewportTimer = setTimeout(() => this.fetchData(), 150);
            }
        };
        this.onZoomEnd = () => {
            if (this.currentData == null) return;
            this.closeSelectedIfOutside();
            const animatingEntities = this.animationFrame != null
                && ['IconLayer', 'ScenegraphLayer'].includes(this.layer.renderer);
            this.render(animatingEntities ? this.currentEntities : this.currentData);
        };
        this.onVisibilityChange = () => {
            if (!this.active) return;
            if (document.hidden) {
                this.suspend();
            } else {
                this.resume();
            }
        };
    }

    start() {
        if (this.active) return;
        this.active = true;
        this.generation++;
        this.mapRenderer.on('moveend', this.onViewportChange);
        this.mapRenderer.on('resize', this.onViewportChange);
        this.mapRenderer.on('zoomend', this.onZoomEnd);
        document.addEventListener('visibilitychange', this.onVisibilityChange);
        const cached = this.layer.type === 'static' ? this.manager.staticCache.get(this.key) : null;
        if (cached && cached.signature === this.signature && cached.selectedDate === this.manager.selectedDate) {
            try {
                this.lastSuccessfulFetch = cached.lastSuccessfulFetch;
                this.acceptPayload(cached.payload);
                this.manager.updateLayerState(this.key, {status: 'active', error: null, lastUpdated: this.lastSuccessfulFetch});
            } catch (_) {
                this.manager.staticCache.delete(this.key);
                this.fetchData();
            }
        } else if (!document.hidden) {
            this.fetchData();
        } else {
            this.manager.updateLayerState(this.key, {status: 'loading'});
        }
    }

    stop() {
        if (!this.active) return;
        this.active = false;
        this.suspend();
        this.mapRenderer.off('moveend', this.onViewportChange);
        this.mapRenderer.off('resize', this.onViewportChange);
        this.mapRenderer.off('zoomend', this.onZoomEnd);
        document.removeEventListener('visibilitychange', this.onVisibilityChange);
        this.currentData = null;
        this.currentEntities = [];
        this.selectedEntityId = null;
        this.mapRenderer.setExtensionLayers(this.key, null);
        this.mapRenderer.closeExtensionPopup(this.key);
        this.revokeObjectUrl();
    }

    suspend() {
        this.generation++;
        clearTimeout(this.pollTimer);
        clearTimeout(this.viewportTimer);
        clearTimeout(this.staleTimer);
        this.abortController?.abort();
        this.abortController = null;
        if (this.animationFrame != null) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
    }

    resume() {
        if (!this.active || document.hidden) return;
        this.generation++;
        this.fetchData();
    }

    rerender() {
        if (!this.active) return;
        if (this.animationFrame != null) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
        if (['IconLayer', 'ScenegraphLayer'].includes(this.layer.renderer)) {
            this.render(this.currentEntities);
        } else if (this.currentData != null) {
            this.render(this.currentData);
        }
    }

    extensionBeforeId() {
        return this.mapRenderer.getExtensionBeforeId(this.layer.placement || 'top');
    }

    placementParameters() {
        return (this.layer.placement || 'top') === 'behind-buildings'
            ? { depthTest: true, depthWriteEnabled: true }
            : { depthTest: false, depthCompare: 'always', depthWriteEnabled: false };
    }

    terrainLayerProps() {
        const terrainExtension = this.mapRenderer.getExtensionTerrainExtension?.();
        return terrainExtension
            ? { extensions: [terrainExtension], terrainDrawMode: 'offset' }
            : {};
    }

    setExtensionLayers(layers) {
        this.mapRenderer.setExtensionLayers(this.key, layers, {
            placement: this.layer.placement || 'top',
            zIndex: this.layer.zIndex ?? 0
        });
    }

    selectEntity(entityId) {
        this.selectedEntityId = entityId;
    }

    clearSelection() {
        this.selectedEntityId = null;
    }

    selectedEntity() {
        if (this.selectedEntityId == null) return null;
        const idField = this.layer.mapping?.idField;
        return this.currentEntities.find((entity, index) => {
            const id = idField ? readPath(entity, idField) : index;
            return String(id ?? index) === this.selectedEntityId;
        }) || null;
    }

    selectedInsideViewport() {
        const entity = this.selectedEntity();
        if (!entity) return false;
        const viewport = this.mapRenderer.getViewport();
        const [west, south, east, north] = viewport.bbox;
        const [lon, lat] = entityPosition(entity, this.layer.mapping || {});
        if (lon < west || lon > east || lat < south || lat > north) return false;
        const zoom = viewport.zoom;
        if (zoom < (this.layer.minZoom ?? -Infinity)) return false;
        const modelName = String(readPath(entity, this.layer.mapping?.modelField) || 'default');
        const model = this.layer.assets?.models?.[modelName] || this.layer.assets?.models?.default || this.layer.assets;
        return zoom >= (model?.minZoom ?? -Infinity);
    }

    closeSelectedIfOutside() {
        if (this.selectedEntityId == null) return;
        if (this.selectedInsideViewport()) return;
        this.selectedEntityId = null;
        this.mapRenderer.closeExtensionPopup(this.key);
    }

    followSelectedEntity() {
        if (this.selectedEntityId == null) return;
        const entity = this.selectedEntity();
        if (!entity || !this.selectedInsideViewport()) {
            this.selectedEntityId = null;
            this.mapRenderer.closeExtensionPopup(this.key);
            return;
        }
        this.mapRenderer.updateExtensionPopup(this.key, entityPosition(entity, this.layer.mapping || {}));
    }

    async fetchData() {
        if (!this.active || document.hidden) return;
        clearTimeout(this.pollTimer);
        clearTimeout(this.viewportTimer);
        this.abortController?.abort();
        const generation = ++this.generation;
        const abortController = new AbortController();
        this.abortController = abortController;
        if (this.lastSuccessfulFetch == null) {
            this.manager.updateLayerState(this.key, {status: 'loading'});
        }

        try {
            const response = await fetch(this.buildDataUrl(), {
                signal: abortController.signal,
                headers: {'Accept': this.layer.format === 'image' ? 'image/*' : 'application/json, application/gpx+xml;q=0.9, */*;q=0.1'},
                cache: 'no-store'
            });
            if (!response.ok) {
                const error = new Error(`Extension request failed with HTTP ${response.status}`);
                error.status = response.status;
                throw error;
            }
            const payload = this.layer.format === 'image' ? await response.blob() : await response.text();
            if (!this.active || generation !== this.generation) return;

            const fetchedAt = Date.now();
            this.acceptPayload(payload);
            this.failures = 0;
            this.lastSuccessfulFetch = fetchedAt;
            this.manager.updateLayerState(this.key, {status: 'active', error: null, lastUpdated: this.lastSuccessfulFetch});
            this.armStaleTimer();
        } catch (error) {
            if (error.name === 'AbortError' || !this.active || generation !== this.generation) return;
            this.failures++;
            const hasData = this.lastSuccessfulFetch != null && this.currentData != null;
            this.manager.updateLayerState(this.key, {
                status: hasData ? 'stale' : 'error',
                error: error.message,
                lastUpdated: this.lastSuccessfulFetch
            });
            if (responseStatusIsFatal(error.status)) {
                this.manager.refresh();
                return;
            }
            this.scheduleNext(this.retryDelay(), true);
            return;
        }

        if (this.active && generation === this.generation && this.layer.type === 'polling') {
            this.scheduleNext(this.layer.pollIntervalMs || 10000);
        }
    }

    buildDataUrl() {
        const url = new URL(extensionUrl(this.layer.dataUrl), window.location.origin);
        if (this.manager.selectedDate) {
            url.searchParams.set('date', this.manager.selectedDate);
        }
        if (this.layer.type === 'polling' && (this.layer.pollTrigger === 'viewport' || this.layer.pollTrigger === 'both')) {
            const viewport = this.mapRenderer.getViewport();
            url.searchParams.set('bbox', viewport.bbox.map(value => value.toFixed(6)).join(','));
            url.searchParams.set('zoom', String(Math.round(viewport.zoom)));
            url.searchParams.set('width', String(Math.round(viewport.width)));
            url.searchParams.set('height', String(Math.round(viewport.height)));
        }
        return url;
    }

    scheduleNext(delay, isRetry = false) {
        clearTimeout(this.pollTimer);
        if (!this.active || document.hidden || this.layer.type !== 'polling') return;
        if (this.layer.pollTrigger === 'viewport' && !isRetry) return;
        this.pollTimer = setTimeout(() => this.fetchData(), Math.max(1000, delay));
    }

    retryDelay() {
        const interval = this.layer.pollIntervalMs || 10000;
        return Math.min(60000, interval * Math.pow(2, Math.min(this.failures - 1, 3)));
    }

    armStaleTimer() {
        clearTimeout(this.staleTimer);
        if (this.layer.type !== 'polling') return;
        if (this.layer.pollTrigger === 'viewport' && !this.layer.staleAfterMs) return;
        const staleAfter = this.layer.staleAfterMs || Math.max(90000, (this.layer.pollIntervalMs || 10000) * 3);
        this.staleTimer = setTimeout(() => {
            if (!this.active) return;
            this.currentData = null;
            this.currentEntities = [];
            this.selectedEntityId = null;
            this.mapRenderer.setExtensionLayers(this.key, null);
            this.mapRenderer.closeExtensionPopup(this.key);
            this.manager.updateLayerState(this.key, {
                status: 'error',
                error: t('map.display-control.extensions.error.stale'),
                lastUpdated: this.lastSuccessfulFetch
            });
        }, staleAfter);
    }

    acceptPayload(payload) {
        if (this.layer.format === 'image') {
            const previousUrl = this.objectUrl;
            const nextUrl = URL.createObjectURL(payload);
            try {
                this.objectUrl = nextUrl;
                this.currentData = nextUrl;
                this.render(nextUrl);
                this.cacheStaticPayload(payload);
                if (previousUrl) URL.revokeObjectURL(previousUrl);
            } catch (error) {
                this.objectUrl = previousUrl;
                this.currentData = previousUrl;
                URL.revokeObjectURL(nextUrl);
                throw error;
            }
            return;
        }

        let parsed;
        if (this.layer.format === 'gpx') {
            parsed = parseGpx(payload);
        } else {
            parsed = JSON.parse(payload);
        }
        this.currentData = parsed;

        if (['IconLayer', 'ScenegraphLayer'].includes(this.layer.renderer)) {
            const nextEntities = this.filterStaleEntities(readPath(parsed, this.layer.dataPath) || []);
            this.transitionEntities(nextEntities);
        } else {
            this.render(parsed);
        }
        this.cacheStaticPayload(payload);
    }

    cacheStaticPayload(payload) {
        if (this.layer.type !== 'static') return;
        this.manager.staticCache.set(this.key, {
            signature: this.signature,
            payload,
            selectedDate: this.manager.selectedDate,
            lastSuccessfulFetch: this.lastSuccessfulFetch || Date.now()
        });
    }

    filterStaleEntities(entities) {
        if (!Array.isArray(entities)) return [];
        const coordinateFields = this.layer.mapping?.coordinateField || ['lon', 'lat'];
        entities = entities.filter(entity => {
            const lon = Number(readPath(entity, coordinateFields[0]));
            const lat = Number(readPath(entity, coordinateFields[1]));
            return Number.isFinite(lon) && Number.isFinite(lat) && Math.abs(lon) <= 180 && Math.abs(lat) <= 90;
        });
        const staleAfter = this.layer.staleAfterMs;
        if (this.layer.type !== 'polling' || !staleAfter) return entities;
        const oldest = Date.now() - staleAfter;
        return entities.filter(entity => {
            if (!entity.updatedAt) return true;
            const timestamp = Date.parse(entity.updatedAt);
            return !Number.isFinite(timestamp) || timestamp >= oldest;
        });
    }

    transitionEntities(nextEntities) {
        if (this.animationFrame != null) cancelAnimationFrame(this.animationFrame);
        const mapping = this.layer.mapping || {};
        const coordinateFields = mapping.coordinateField || ['lon', 'lat'];
        const idField = mapping.idField;
        const previous = new Map(this.currentEntities.map((entity, index) => [idField ? String(readPath(entity, idField) ?? index) : String(index), entity]));
        const previousCoordinateFields = Array.isArray(mapping.previousCoordinateField)
            && mapping.previousCoordinateField.length >= 2
            ? mapping.previousCoordinateField
            : null;
        const pollDuration = this.layer.pollIntervalMs || 10000;
        const receivedAt = Date.now();
        const transitions = nextEntities.map((entity, index) => {
            const id = idField ? String(readPath(entity, idField) ?? index) : String(index);
            let from = previous.get(id) || null;
            let duration = pollDuration;
            if (!from && previousCoordinateFields) {
                const previousLon = readPath(entity, previousCoordinateFields[0]);
                const previousLat = readPath(entity, previousCoordinateFields[1]);
                const lon = previousLon == null ? NaN : Number(previousLon);
                const lat = previousLat == null ? NaN : Number(previousLat);
                if (Number.isFinite(lon) && Number.isFinite(lat) && Math.abs(lon) <= 180 && Math.abs(lat) <= 90) {
                    from = structuredClone(entity);
                    writePath(from, coordinateFields[0], lon);
                    writePath(from, coordinateFields[1], lat);
                    if (mapping.previousHeadingField && mapping.headingField) {
                        const previousHeading = Number(readPath(entity, mapping.previousHeadingField));
                        if (Number.isFinite(previousHeading)) writePath(from, mapping.headingField, previousHeading);
                    }
                    if (mapping.previousTimestampField) {
                        const previousTimestamp = Date.parse(readPath(entity, mapping.previousTimestampField));
                        if (Number.isFinite(previousTimestamp)) {
                            duration = Math.max(500, Math.min(pollDuration, Math.max(0, receivedAt - previousTimestamp)));
                        }
                    }
                }
            }
            return {entity, from, duration};
        });
        const startedAt = performance.now();
        const generation = this.generation;

        const animate = now => {
            if (!this.active || generation !== this.generation) return;
            let animating = false;
            this.currentEntities = transitions.map(({entity, from, duration: transitionDuration}) => {
                if (!from) return entity;
                const progress = Math.max(0, Math.min(1, (now - startedAt) / transitionDuration));
                if (progress < 1) animating = true;
                return interpolateEntity(from, entity, mapping, progress);
            });
            this.render(this.currentEntities);
            if (animating) {
                this.animationFrame = requestAnimationFrame(animate);
            } else {
                this.animationFrame = null;
            }
        };
        animate(startedAt);
    }

    render(data) {
        const visible = this.mapRenderer.getViewport().zoom >= (this.layer.minZoom ?? -Infinity);
        const beforeId = this.extensionBeforeId();
        const parameters = this.placementParameters();
        let layers = [];
        if (['IconLayer', 'ScenegraphLayer'].includes(this.layer.renderer)) {
            layers = this.buildEntityLayers(Array.isArray(data) ? data : readPath(data, this.layer.dataPath) || []);
        } else if (this.layer.renderer === 'PathLayer') {
            layers = [new deck.PathLayer({
                id: `extension-${this.key}-path`,
                beforeId,
                parameters,
                ...this.terrainLayerProps(),
                data: this.layer.format === 'gpx' ? data : geoJsonPaths(data),
                getPath: item => item.path,
                getColor: this.layer.assets?.lineColor || [38, 139, 210, 220],
                getWidth: this.layer.assets?.lineWidth || 3,
                widthUnits: 'pixels',
                widthMinPixels: 1,
                pickable: true,
                visible,
                jointRounded: true,
                capRounded: true
            })];
        } else if (this.layer.renderer === 'PolygonLayer') {
            layers = [new deck.PolygonLayer({
                id: `extension-${this.key}-polygon`,
                beforeId,
                parameters,
                ...this.terrainLayerProps(),
                data: geoJsonPolygons(data),
                getPolygon: item => item.polygon,
                getFillColor: this.layer.assets?.fillColor || [38, 139, 210, 70],
                getLineColor: this.layer.assets?.lineColor || [38, 139, 210, 220],
                getLineWidth: this.layer.assets?.lineWidth || 2,
                lineWidthUnits: 'pixels',
                pickable: true,
                visible
            })];
        } else if (this.layer.renderer === 'BitmapLayer') {
            const bounds = this.layer.assets?.bounds;
            if (!Array.isArray(bounds) || bounds.length !== 4) {
                throw new Error('BitmapLayer requires assets.bounds');
            }
            layers = [new deck.BitmapLayer({
                id: `extension-${this.key}-bitmap`,
                beforeId,
                parameters,
                image: data,
                bounds,
                opacity: this.layer.assets?.opacity ?? 0.8,
                desaturate: 0,
                transparentColor: [0, 0, 0, 0],
                visible
            })];
        }
        this.setExtensionLayers(layers);
        this.followSelectedEntity();
    }

    buildEntityLayers(entities) {
        const mapping = this.layer.mapping || {};
        const assets = this.layer.assets || {};
        const beforeId = this.extensionBeforeId();
        const parameters = this.placementParameters();
        const iconUrl = assets.iconUrl || this.extension.iconUrl;
        const icon = iconUrl
            ? {url: extensionUrl(iconUrl), width: assets.iconWidth || 48, height: assets.iconHeight || 48, anchorY: assets.anchorY || 24}
            : fallbackMarkerIcon();
        const zoom = this.mapRenderer.getViewport().zoom;
        const minZoom = this.layer.minZoom ?? -Infinity;
        const visible = zoom >= minZoom;
        const position = entity => entityPosition(entity, mapping);
        const onClick = info => {
            if (info.object) this.manager.showEntityDetails(info.object, position(info.object), this.key);
        };
        const layers = [];

        if (this.layer.renderer === 'ScenegraphLayer') {
            const groups = new Map();
            entities.forEach(entity => {
                const modelName = String(readPath(entity, mapping.modelField) || 'default');
                const model = assets.models?.[modelName] || assets.models?.default || assets;
                if (!groups.has(modelName)) groups.set(modelName, {model, entities: []});
                groups.get(modelName).entities.push(entity);
            });
            let groupIndex = 0;
            groups.forEach((group, modelName) => {
                const model = group.model;
                if (!model.modelUrl) throw new Error(`ScenegraphLayer requires a model URL for ${modelName}`);
                const modelVisible = visible && zoom >= (model.minZoom ?? -Infinity);
                const referenceZoom = model.scaleReferenceZoom;
                const zoomScaleExponent = Number.isFinite(model.zoomScaleExponent) ? model.zoomScaleExponent : 1;
                const zoomScale = Number.isFinite(referenceZoom)
                    ? Math.min(model.maxZoomScale ?? 192, Math.pow(2, Math.max(0, referenceZoom - zoom) * zoomScaleExponent)) : 1;
                layers.push(new deck.ScenegraphLayer({
                    id: `extension-${this.key}-model-${groupIndex++}`,
                    beforeId,
                    parameters,
                    ...this.terrainLayerProps(),
                    data: group.entities,
                    scenegraph: extensionUrl(model.modelUrl),
                    getPosition: position,
                    // Models point along glTF -Z; this yaw maps them to clockwise bearing from north.
                    getOrientation: entity => [0, -(Number(readPath(entity, mapping.headingField)) || 0), 90],
                    getScale: model.modelScale || assets.modelScale || [1, 1, 1],
                    getTranslation: model.translation || assets.translation || [0, 0, 0],
                    sizeScale: (model.scale ?? assets.scale ?? 1) * zoomScale,
                    _lighting: 'pbr',
                    getColor: mapping.colorField
                        ? entity => entityColor(readPath(entity, mapping.colorField))
                        : [255, 255, 255, 255],
                    visible: modelVisible,
                    pickable: modelVisible,
                    onClick,
                    updateTriggers: {
                        getPosition: [group.entities],
                        getOrientation: [group.entities],
                        getColor: [group.entities]
                    }
                }));
            });
        } else {
            layers.push(new deck.IconLayer({
                id: `extension-${this.key}-icons`,
                beforeId,
                parameters,
                ...this.terrainLayerProps(),
                data: entities,
                getPosition: position,
                getIcon: () => icon,
                getSize: assets.iconSize || 32,
                sizeUnits: 'pixels',
                sizeScale: 1,
                getAngle: entity => -(Number(readPath(entity, mapping.headingField)) || 0),
                getColor: assets.color || [255, 255, 255, 255],
                visible,
                pickable: true,
                onClick,
                updateTriggers: {
                    getPosition: [entities],
                    getAngle: [entities]
                }
            }));
        }

        if (mapping.labelField && zoom >= (assets.labelMinZoom ?? 12)) {
            const labelledEntities = entities.filter(entity => {
                if (this.layer.renderer !== 'ScenegraphLayer') return true;
                const modelName = String(readPath(entity, mapping.modelField) || 'default');
                const model = assets.models?.[modelName] || assets.models?.default || assets;
                return zoom >= (model.minZoom ?? -Infinity)
                    && zoom >= (model.labelMinZoom ?? assets.labelMinZoom ?? 12);
            });
            layers.push(new deck.TextLayer({
                id: `extension-${this.key}-labels`,
                beforeId,
                parameters,
                ...this.terrainLayerProps(),
                data: labelledEntities,
                getPosition: position,
                getText: entity => String(readPath(entity, mapping.labelField) ?? ''),
                getSize: assets.labelSize || 12,
                sizeUnits: 'pixels',
                getColor: entity => (mapping.labelColorField ? readPath(entity, mapping.labelColorField) : null)
                    || assets.labelColor || [255, 255, 255, 255],
                getPixelOffset: [0, -(assets.iconSize || 32) / 2 - (assets.labelOffsetY ?? 10)],
                fontFamily: 'sans-serif',
                fontWeight: 600,
                outlineWidth: 2,
                outlineColor: [0, 0, 0, 220],
                fontSettings: {sdf: true},
                visible,
                pickable: true,
                onClick
            }));
        }
        return layers;
    }

    revokeObjectUrl() {
        if (this.objectUrl) {
            URL.revokeObjectURL(this.objectUrl);
            this.objectUrl = null;
        }
    }
}

class ExtensionLayerManager {
    constructor(mapRenderer, mapControls) {
        this.mapRenderer = mapRenderer;
        this.mapControls = mapControls;
        this.container = mapControls.getExtensionContainer();
        this.controllers = new Map();
        this.layerStates = new Map();
        this.registry = {extensions: []};
        this.currentDaySelected = false;
        this.selectedDate = null;
        this.preferences = this.loadPreferences();
        this.staticCache = new Map();
        this.destroyed = false;
        this.refreshPromise = null;

        this.onControlsOpened = () => this.refresh();
        this.onPageHide = event => {
            if (event.persisted) {
                this.controllers.forEach(controller => controller.suspend());
            } else {
                this.destroy();
            }
        };
        this.onPageShow = event => {
            if (event.persisted) {
                this.controllers.forEach(controller => controller.resume());
                this.refresh();
            }
        };
        this.mapControls.on('opened', this.onControlsOpened);
        window.addEventListener('pagehide', this.onPageHide);
        window.addEventListener('pageshow', this.onPageShow);
        this.onStyleChange = () => {
            this.controllers.forEach(controller => controller.rerender());
        };
        this.onTerrainChange = () => {
            this.controllers.forEach(controller => controller.rerender());
        };
        this.removeDestroyListener = mapRenderer.addDestroyListener(() => this.destroy());
        this.removeStyleChangeListener = mapRenderer.addStyleChangeListener(this.onStyleChange);
        this.removeTerrainChangeListener = mapRenderer.addTerrainChangeListener(this.onTerrainChange);
        this.registryTimer = setInterval(() => {
            if (!document.hidden) this.refresh();
        }, 60000);
    }

    async init() {
        await this.refresh();
    }

    isCurrentDaySelected() {
        return this.currentDaySelected;
    }

    setDateRange(range, today) {
        const singleDay = range?.startDate && (!range?.endDate || range?.endDate === range?.startDate);
        this.currentDaySelected = singleDay && range.startDate === today;
        this.selectedDate = range?.startDate === today && !this.currentDaySelected
            ? range?.endDate || null : range?.startDate || null;
        this.controllers.forEach(controller => controller.fetchData());
        this.refresh();
    }

    async refresh() {
        if (this.destroyed) return;
        if (this.refreshPromise) return this.refreshPromise;
        this.refreshPromise = (async () => {
            try {
                const response = await fetch(extensionUrl('/api/v1/extensions'), {cache: 'no-store'});
                if (!response.ok) return;
                const registry = await response.json();
                if (this.destroyed) return;
                this.registry = registry && Array.isArray(registry.extensions) ? registry : {extensions: []};
                this.reconcile();
                this.renderControls();
            } catch (error) {
                console.warn('Unable to refresh map extensions', error);
            } finally {
                this.refreshPromise = null;
            }
        })();
        return this.refreshPromise;
    }

    reconcile() {
        const available = new Map();
        this.registry.extensions.forEach(extension => {
            (extension.layers || []).forEach(layer => {
                if ((layer.type !== 'static' && layer.type !== 'polling') || layer.protocol !== 'http') return;
                const key = `${extension.id}:${layer.id}`;
                available.set(key, {extension, layer});
            });
        });

        for (const [key, controller] of this.controllers) {
            const entry = available.get(key);
            if (!entry || controller.signature !== JSON.stringify(entry.layer)) {
                controller.stop();
                this.controllers.delete(key);
                this.layerStates.delete(key);
            }
        }

        for (const key of this.staticCache.keys()) {
            const entry = available.get(key);
            if (!entry || this.staticCache.get(key).signature !== JSON.stringify(entry.layer)) {
                this.staticCache.delete(key);
            }
        }

        for (const [key, entry] of available) {
            if (this.preferences[key] === true && !this.controllers.has(key)) {
                this.startController(key, entry.extension, entry.layer);
            }
        }
    }

    startController(key, extension, layer) {
        const controller = new ExtensionLayerController(this, extension, layer, key);
        this.controllers.set(key, controller);
        this.updateLayerState(key, {status: 'loading'});
        controller.start();
    }

    toggleLayer(extension, layer, enabled) {
        const key = `${extension.id}:${layer.id}`;
        this.preferences[key] = enabled;
        this.savePreferences();
        if (enabled) {
            this.controllers.get(key)?.stop();
            this.startController(key, extension, layer);
        } else {
            this.controllers.get(key)?.stop();
            this.controllers.delete(key);
            this.updateLayerState(key, {status: 'off'});
        }
        this.renderControls();
    }

    updateLayerState(key, state) {
        this.layerStates.set(key, {...(this.layerStates.get(key) || {}), ...state});
        this.renderControls();
    }

    renderControls() {
        if (this.destroyed || !this.container) return;
        this.container.replaceChildren();
        if (!this.registry.extensions.length) return;

        const title = document.createElement('div');
        title.className = 'extension-map-controls-title';
        title.textContent = t('map.display-control.extensions.title');
        this.container.appendChild(title);

        this.registry.extensions.forEach(extension => {
            const layers = (extension.layers || []).filter(layer =>
                (layer.type === 'static' || layer.type === 'polling') && layer.protocol === 'http');
            if (!layers.length) return;
            const group = document.createElement('section');
            group.className = 'extension-map-control-group';
            const heading = document.createElement('div');
            heading.className = 'extension-map-control-heading';
            if (extension.iconUrl) {
                const icon = document.createElement('img');
                icon.src = extensionUrl(extension.iconUrl);
                icon.alt = '';
                icon.loading = 'lazy';
                heading.appendChild(icon);
            }
            const name = document.createElement('span');
            name.textContent = extension.name;
            heading.appendChild(name);
            group.appendChild(heading);

            layers.forEach(layer => {
                const key = `${extension.id}:${layer.id}`;
                const state = this.layerStates.get(key) || {status: this.controllers.has(key) ? 'loading' : 'off'};
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn map-control-btn extension-layer-toggle';
                button.dataset.status = state.status;
                button.setAttribute('aria-pressed', String(this.controllers.has(key)));
                button.title = state.error || layer.description || layer.name;
                if (['active', 'stale', 'loading'].includes(state.status)) button.classList.add('active');

                const label = document.createElement('span');
                label.textContent = layer.name || layer.id;
                const status = document.createElement('span');
                status.className = 'extension-layer-status';
                status.textContent = t(`map.display-control.extensions.status.${state.status}`);
                button.append(label, status);
                button.addEventListener('click', () => this.toggleLayer(extension, layer, !this.controllers.has(key)));
                group.appendChild(button);
            });
            this.container.appendChild(group);
        });
    }

    showEntityDetails(entity, position, ownerKey) {
        const controller = this.controllers.get(ownerKey);
        const mapping = controller?.layer.mapping || {};
        const details = mapping.details || {};
        const content = document.createElement('div');
        content.className = 'extension-entity-details map-popup';
        const heading = document.createElement('div');
        heading.className = 'sel-info-head';
        const title = document.createElement('div');
        title.className = 'sel-info-title';
        const titleFields = details.titleFields || [mapping.labelField || mapping.idField];
        title.textContent = titleFields.filter(Boolean).map(field => readPath(entity, field)).filter(value => value != null && value !== '')
            .join(details.titleSeparator || ' — ');
        heading.appendChild(title);

        if (details.status?.field) {
            const value = readPath(entity, details.status.field);
            const status = details.status.values?.[value];
            if (status?.label) {
                const badge = document.createElement('span');
                badge.className = 'extension-entity-source';
                badge.dataset.source = String(value);
                badge.textContent = status.label;
                heading.appendChild(badge);
            }
        }
        content.appendChild(heading);

        (details.rows || []).forEach(row => {
            const value = readPath(entity, row.field);
            if (value == null || value === '') return;
            const element = document.createElement('div');
            element.className = 'sel-info-row';
            const label = document.createElement('span');
            label.className = 'k';
            label.textContent = row.label;
            const description = document.createElement('span');
            description.className = 'v';
            const time = row.timeField ? readPath(entity, row.timeField) : null;
            description.textContent = `${value}${time ? ` · ${formatEntityTime(time)}` : ''}`;
            element.append(label, description);
            content.appendChild(element);
        });
        this.controllers.forEach((candidate, key) => {
            if (key !== ownerKey) candidate.clearSelection();
        });
        this.mapRenderer.showExtensionPopup(ownerKey, position, content, () => controller?.clearSelection());
        const idField = mapping.idField;
        const entityId = idField ? readPath(entity, idField) : null;
        if (entityId != null) controller?.selectEntity(String(entityId));
    }

    loadPreferences() {
        try {
            const value = JSON.parse(localStorage.getItem('reitti.map.extension-layers.v1') || '{}');
            return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
        } catch (_) {
            return {};
        }
    }

    savePreferences() {
        localStorage.setItem('reitti.map.extension-layers.v1', JSON.stringify(this.preferences));
    }

    destroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        clearInterval(this.registryTimer);
        this.mapControls.off('opened', this.onControlsOpened);
        window.removeEventListener('pagehide', this.onPageHide);
        window.removeEventListener('pageshow', this.onPageShow);
        this.controllers.forEach(controller => controller.stop());
        this.controllers.clear();
        this.staticCache.clear();
        this.container?.replaceChildren();
        this.removeDestroyListener?.();
        this.removeStyleChangeListener?.();
        this.removeTerrainChangeListener?.();
    }
}

function formatEntityTime(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
}

function extensionUrl(path) {
    if (/^https?:\/\//i.test(path) || path.startsWith('data:') || path.startsWith('blob:')) return path;
    const contextPath = window.contextPath || '';
    const normalized = path.startsWith('/') ? path : `/${path}`;
    return contextPath && !normalized.startsWith(`${contextPath}/`) ? `${contextPath}${normalized}` : normalized;
}

function responseStatusIsFatal(status) {
    return status === 401 || status === 403 || status === 404 || status === 410;
}

function readPath(value, path) {
    if (!path || path === '$') return value;
    const normalized = String(path).replace(/^\$\./, '').replace(/\[(\d+)]/g, '.$1');
    return normalized.split('.').reduce((current, part) => current == null ? undefined : current[part], value);
}

function entityColor(value, fallback = [255, 255, 255, 255]) {
    if (Array.isArray(value) && value.length >= 3) {
        const channels = value.slice(0, 4).map(channel => Number(channel));
        if (channels.every(channel => Number.isFinite(channel) && channel >= 0 && channel <= 255)) {
            if (channels.length === 3) channels.push(255);
            return channels;
        }
    }
    if (typeof value === 'string') {
        const match = /^#?([\da-f]{6})([\da-f]{2})?$/i.exec(value.trim());
        if (match) {
            return [
                Number.parseInt(match[1].slice(0, 2), 16),
                Number.parseInt(match[1].slice(2, 4), 16),
                Number.parseInt(match[1].slice(4, 6), 16),
                match[2] ? Number.parseInt(match[2], 16) : 255
            ];
        }
    }
    return fallback;
}

function writePath(value, path, replacement) {
    const parts = String(path).split('.');
    let target = value;
    for (let index = 0; index < parts.length - 1; index++) {
        target[parts[index]] = target[parts[index]] || {};
        target = target[parts[index]];
    }
    target[parts[parts.length - 1]] = replacement;
}

function entityPosition(entity, mapping) {
    const fields = mapping.coordinateField || ['lon', 'lat'];
    const lon = Number(readPath(entity, fields[0]));
    const lat = Number(readPath(entity, fields[1]));
    const position = [Number.isFinite(lon) ? lon : 0, Number.isFinite(lat) ? lat : 0];
    if (fields.length > 2) {
        const altitude = Number(readPath(entity, fields[2]));
        position.push(Number.isFinite(altitude) ? altitude : 0);
    }
    return position;
}

function interpolateEntity(from, to, mapping, progress) {
    const fields = mapping.coordinateField || ['lon', 'lat'];
    const result = structuredClone(to);
    const fromPosition = entityPosition(from, mapping);
    const toPosition = entityPosition(to, mapping);
    writePath(result, fields[0], fromPosition[0] + (toPosition[0] - fromPosition[0]) * progress);
    writePath(result, fields[1], fromPosition[1] + (toPosition[1] - fromPosition[1]) * progress);
    if (mapping.headingField) {
        const fromHeading = Number(readPath(from, mapping.headingField)) || 0;
        const toHeading = Number(readPath(to, mapping.headingField)) || 0;
        const difference = ((toHeading - fromHeading + 540) % 360) - 180;
        writePath(result, mapping.headingField, (fromHeading + difference * progress + 360) % 360);
    }
    return result;
}

let cachedFallbackMarker;
function fallbackMarkerIcon() {
    if (cachedFallbackMarker) return cachedFallbackMarker;
    const canvas = document.createElement('canvas');
    canvas.width = 48;
    canvas.height = 48;
    const context = canvas.getContext('2d');
    context.fillStyle = '#268bd2';
    context.strokeStyle = '#ffffff';
    context.lineWidth = 3;
    context.beginPath();
    context.arc(24, 24, 18, 0, Math.PI * 2);
    context.fill();
    context.stroke();
    context.fillStyle = '#ffffff';
    context.beginPath();
    context.moveTo(24, 9);
    context.lineTo(32, 29);
    context.lineTo(24, 25);
    context.lineTo(16, 29);
    context.closePath();
    context.fill();
    cachedFallbackMarker = {url: canvas.toDataURL(), width: 48, height: 48, anchorY: 24};
    return cachedFallbackMarker;
}

function geoJsonFeatures(payload) {
    if (!payload) return [];
    if (payload.type === 'FeatureCollection') return payload.features || [];
    if (payload.type === 'Feature') return [payload];
    return [{type: 'Feature', geometry: payload, properties: {}}];
}

function geoJsonPaths(payload) {
    const paths = [];
    geoJsonFeatures(payload).forEach(feature => {
        const geometry = feature.geometry;
        if (!geometry) return;
        if (geometry.type === 'LineString') paths.push({path: geometry.coordinates, properties: feature.properties});
        if (geometry.type === 'MultiLineString') geometry.coordinates.forEach(path => paths.push({path, properties: feature.properties}));
    });
    return paths;
}

function geoJsonPolygons(payload) {
    const polygons = [];
    geoJsonFeatures(payload).forEach(feature => {
        const geometry = feature.geometry;
        if (!geometry) return;
        if (geometry.type === 'Polygon') polygons.push({polygon: geometry.coordinates, properties: feature.properties});
        if (geometry.type === 'MultiPolygon') geometry.coordinates.forEach(polygon => polygons.push({polygon, properties: feature.properties}));
    });
    return polygons;
}

function parseGpx(source) {
    const documentXml = new DOMParser().parseFromString(source, 'application/xml');
    if (documentXml.querySelector('parsererror')) throw new Error('Invalid GPX document');
    const paths = [];
    Array.from(documentXml.getElementsByTagNameNS('*', 'trkseg')).forEach(segment => {
        const path = childTrackPoints(segment, 'trkpt');
        if (path.length >= 2) paths.push({path});
    });
    Array.from(documentXml.getElementsByTagNameNS('*', 'rte')).forEach(route => {
        const path = childTrackPoints(route, 'rtept');
        if (path.length >= 2) paths.push({path});
    });
    return paths;
}

function childTrackPoints(parent, elementName) {
    return Array.from(parent.children)
        .filter(element => element.localName === elementName)
        .map(element => {
            const point = [Number(element.getAttribute('lon')), Number(element.getAttribute('lat'))];
            const elevation = Array.from(element.children).find(child => child.localName === 'ele');
            if (elevation && Number.isFinite(Number(elevation.textContent))) point.push(Number(elevation.textContent));
            return point;
        })
        .filter(point => Number.isFinite(point[0]) && Number.isFinite(point[1]));
}
