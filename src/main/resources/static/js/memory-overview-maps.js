/**
 * Lazy lifecycle for the memory overview card maps.
 *
 * The overview can contain many memories; creating a WebGL map for every card
 * at once exhausts the browser's WebGL context budget. Maps are therefore only
 * created while their card is (nearly) inside the viewport and destroyed again
 * once it leaves, mirroring the lifecycle used on the memory detail page.
 */
class MemoryOverviewMaps {
    constructor() {
        this.mapRegistry = new Map();
        this.loadTimeouts = new Map();
        this.observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const container = entry.target;
                const elementId = container.id;

                if (entry.isIntersecting) {
                    const registered = this.mapRegistry.get(elementId);
                    if (registered && registered.element === container) {
                        return;
                    }
                    const existingTimeout = this.loadTimeouts.get(elementId);
                    if (existingTimeout) {
                        clearTimeout(existingTimeout);
                    }
                    this.loadTimeouts.set(elementId, setTimeout(() => {
                        this.loadTimeouts.delete(elementId);
                        this.initializeMap(container, elementId);
                    }, 100));
                } else {
                    this.destroyMap(elementId, container);
                }
            });
        }, {
            root: null,
            rootMargin: '100px 0px',
            threshold: 0.01
        });
    }

    observeAll() {
        document.querySelectorAll('.memory-card-map-container').forEach(container => this.observer.observe(container));
    }

    initializeMap(container, elementId) {
        const registered = this.mapRegistry.get(elementId);
        if (registered) {
            if (registered.element === container) {
                return;
            }
            this.destroyMap(elementId);
        }
        const streamUrl = container.dataset.streamUrl;
        const metaDataUrl = container.dataset.metaDataUrl;
        if (!streamUrl || !metaDataUrl) {
            return;
        }
        const viewState = {
            is3d: false,
            renderTerrain: false,
            renderBuildings: false,
            renderSatelliteView: false,
            renderGlobe: false,
            aggregated: false,
            viewMode: 'LINEAR',
            currentTime: 0,
            animating: false,
            showPhotos: false,
            fixed: true
        };
        const mapRenderer = new MapRenderer(elementId, window.userSettings, viewState, {
            fitConfig: {
                padding: {
                    top: 10,
                    bottom: 10,
                    right: 10,
                    left: 10
                },
                maxZoom: 15,
                duration: 0,
                essential: true
            }
        });
        const gpsDataManager = new GpsDataManager({
            color: '#f1ba63',
            map: {
                streamUrl: streamUrl,
                metaDataUrl: metaDataUrl
            },
            continuous: true
        }, window.userSettings, 'UTC');
        mapRenderer.setGpsDataManagers([gpsDataManager]);
        gpsDataManager.loadFixedWithVisits([], (current, total, stage) => {
            if (stage === 'complete') {
                mapRenderer.updateViewState(viewState);
                mapRenderer.finishedLoading();
            }
        });
        this.mapRegistry.set(elementId, mapRenderer);
    }

    destroyMap(elementId, container = null) {
        const timeout = this.loadTimeouts.get(elementId);
        if (timeout) {
            clearTimeout(timeout);
            this.loadTimeouts.delete(elementId);
        }

        const renderer = this.mapRegistry.get(elementId);
        if (!renderer || (container && renderer.element !== container)) {
            return;
        }

        renderer.destroy();
        this.mapRegistry.delete(elementId);

        const mapContainer = renderer.element;
        if (mapContainer) {
            mapContainer.classList.remove('is-loading');
            mapContainer.classList.remove('is-loaded');
            mapContainer.innerHTML = '';
        }
    }

    destroyAll() {
        for (const elementId of Array.from(this.mapRegistry.keys())) {
            this.destroyMap(elementId);
        }
        this.loadTimeouts.forEach(timeout => clearTimeout(timeout));
        this.loadTimeouts.clear();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const overviewMaps = new MemoryOverviewMaps();
    document.body.addEventListener('htmx:afterSwap', (event) => {
        if (event.detail.target.classList.contains('memories-overview')) {
            overviewMaps.observeAll();
        }
    });
    document.body.addEventListener('htmx:beforeSwap', (event) => {
        if (event.detail.target.classList.contains('memories-overview')) {
            overviewMaps.destroyAll();
        }
    });
});
