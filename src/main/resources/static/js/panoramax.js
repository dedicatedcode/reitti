/**
 * Lazy loader and panel helper for the Panoramax web viewer (@panoramax/web-viewer, MIT license).
 * The viewer bundle (~2.8 MB) is only fetched the first time a photo panel is opened.
 */
const PanoramaxViewer = {
    _bundlePromise: null,

    /**
     * Optional callback invoked whenever the photo shown in the viewer changes.
     * Receives {lng, lat, first} of the currently displayed picture. Set this
     * per page to make the map follow the drawer content.
     */
    onPictureMove: null,

    loadBundle() {
        if (!this._bundlePromise) {
            this._bundlePromise = new Promise((resolve, reject) => {
                if (window.customElements && window.customElements.get('pnx-photo-viewer')) {
                    resolve();
                    return;
                }
                const script = document.createElement('script');
                const contextPath = (window.contextPath || '').replace(/\/$/, '');
                script.src = `${contextPath}/js/vendor/panoramax-web-viewer.js`;
                script.onload = () => resolve();
                script.onerror = () => {
                    this._bundlePromise = null;
                    reject(new Error('Unable to load the Panoramax viewer library.'));
                };
                document.head.appendChild(script);
            });
        }
        return this._bundlePromise;
    },

    /**
     * Mounts a pnx-photo-viewer inside the given container element.
     * Reads data-endpoint, data-sequence and data-picture attributes from the container.
     */
    async mountInto(container) {
        if (!container) return null;
        await this.loadBundle();
        container.innerHTML = '';
        const viewer = document.createElement('pnx-photo-viewer');
        const endpoint = container.dataset.endpoint;
        const sequence = container.dataset.sequence;
        const picture = container.dataset.picture;
        if (endpoint) viewer.setAttribute('endpoint', endpoint);
        if (sequence) viewer.setAttribute('sequence', sequence);
        if (picture) viewer.setAttribute('picture', picture);
        viewer.setAttribute('url-parameters', 'false');
        viewer.setAttribute('widgets', 'false');
        const player = document.createElement('pnx-widget-player');
        player.setAttribute('slot', 'top');
        player.setAttribute('size', 'md');
        player.setAttribute('more', 'never');
        viewer.appendChild(player);
        const legend = document.createElement('pnx-widget-legend');
        legend.setAttribute('light', '');
        legend.setAttribute('slot', 'bottom-right');
        viewer.appendChild(legend);
        // The viewer dispatches picture events on its internal psv instance
        // (viewer.psv), which is only created after the API is ready. The host
        // element itself does not receive them.
        viewer.addEventListener('picture-loaded', (event) => {
            this._handlePicturePosition(event.detail);
        });
        const attachPsvListener = () => {
            if (viewer.psv) {
                viewer.psv.addEventListener('picture-loaded', (event) => {
                    this._handlePicturePosition(event.detail);
                });
                return true;
            }
            return false;
        };
        if (!attachPsvListener()) {
            const psvInterval = setInterval(() => {
                if (!viewer.isConnected || attachPsvListener()) {
                    clearInterval(psvInterval);
                }
            }, 250);
            setTimeout(() => clearInterval(psvInterval), 30000);
        }
        container.appendChild(viewer);
        this._viewer = viewer;
        return viewer;
    },

    _handlePicturePosition(detail) {
        if (typeof detail?.lon === 'number' && typeof detail?.lat === 'number'
            && typeof this.onPictureMove === 'function') {
            this.onPictureMove({ lng: detail.lon, lat: detail.lat, first: detail.first === true });
        }
    },

    /**
     * Places/moves a marker at the position of the currently displayed photo
     * and pans the map (easing) if the position is not visible.
     *
     * @param mapRenderer the MapRenderer instance of the map to update
     * @param lngLat {lng, lat, first} of the currently displayed photo
     * @param reservedRightPx width of an area on the right side of the map that
     *                        is covered by an overlay (0 for the push layout,
     *                        where the map container already excludes the drawer)
     * @param forceCenter when true, the map is panned even if the position is
     *                    already visible — used for the first photo after the
     *                    drawer opened, to compensate the resize shift
     */
    updatePhotoLocation(mapRenderer, lngLat, reservedRightPx = 0, forceCenter = false) {
        const map = mapRenderer?.map;
        if (!map || typeof map.project !== 'function') return;

        this._ensurePhotoMarker(mapRenderer, lngLat);
        this._photoMarker.marker.setLngLat([lngLat.lng, lngLat.lat]);

        const canvas = map.getContainer();
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        const point = map.project([lngLat.lng, lngLat.lat]);
        const visibleRight = width - reservedRightPx;
        const visible = point.x >= 0 && point.x <= visibleRight && point.y >= 0 && point.y <= height;
        if (visible && !forceCenter) {
            return;
        }
        // Ease to a camera that places the photo into the middle of the visible area
        const center = map.unproject({
            x: point.x + width / 2 - visibleRight / 2,
            y: point.y + height / 2 - height / 2
        });
        map.easeTo({ center, duration: 600 });
    },

    _ensurePhotoMarker(mapRenderer, lngLat) {
        if (this._photoMarker && this._photoMarker.mapRenderer !== mapRenderer) {
            this._removePhotoMarker();
        }
        if (!this._photoMarker) {
            const element = document.createElement('div');
            element.className = 'panoramax-photo-marker';
            this._photoMarker = {
                mapRenderer,
                marker: new maplibregl.Marker({
                    element,
                    anchor: 'center',
                    pitchAlignment: 'viewport',
                    rotationAlignment: 'viewport'
                })
                    .setLngLat([lngLat.lng, lngLat.lat])
                    .addTo(mapRenderer.map)
            };
        }
    },

    _removePhotoMarker() {
        if (this._photoMarker) {
            this._photoMarker.marker.remove();
            this._photoMarker = null;
        }
    },

    /**
     * Initializes the viewer inside a freshly swapped panel fragment.
     */
    initPanel(panelElement) {
        const container = panelElement?.querySelector('.panoramax-viewer-container');
        if (container) {
            this.mountInto(container).catch(error => console.warn(error.message));
        }
    },

    /**
     * Closes the panel or dialog containing the given close button and disposes its viewer.
     */
    closePanel(closeButton) {
        const container = closeButton?.closest('.panoramax-panel-container');
        const dialog = closeButton?.closest('.reitti-dialog');
        if (container) {
            this._closeContainer(container);
        } else {
            closeButton?.closest('.panoramax-panel')?.remove();
        }
        if (dialog) {
            this._closeDialog(dialog);
        }
    },

    /**
     * Closes the main map side panel if it is open.
     */
    closeOpenPanel() {
        const container = document.getElementById('panoramax-panel-container');
        if (container && container.classList.contains('open')) {
            this._closeContainer(container);
        }
    },

    _closeContainer(container) {
        container.classList.remove('open');
        container.innerHTML = '';
        document.body.classList.remove('panoramax-open');
        this._removePhotoMarker();
    },

    _closeDialog(dialog) {
        dialog.classList.remove('open');
        dialog.setAttribute('aria-hidden', 'true');
        const body = dialog.querySelector('.reitti-dialog-body');
        if (body) body.innerHTML = '';
        this._removePhotoMarker();
    }
};

window.PanoramaxViewer = PanoramaxViewer;

document.addEventListener('htmx:afterSwap', (event) => {
    const target = event.detail?.target;
    const panel = target?.querySelector?.('.panoramax-panel');
    if (panel) {
        PanoramaxViewer.initPanel(panel);
    }
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        PanoramaxViewer.closeOpenPanel();
    }
});