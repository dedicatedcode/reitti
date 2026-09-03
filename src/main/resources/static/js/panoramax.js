/**
 * Lazy loader and panel helper for the Panoramax web viewer (@panoramax/web-viewer, MIT license).
 * The viewer bundle (~2.8 MB) is only fetched the first time a photo panel is opened.
 */
const PanoramaxViewer = {
    _bundlePromise: null,

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
        container.appendChild(viewer);
        return viewer;
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
        const panel = closeButton?.closest('.panoramax-panel');
        const container = closeButton?.closest('.panoramax-panel-container');
        const dialog = closeButton?.closest('.reitti-dialog');
        if (container) {
            container.classList.remove('open');
            container.innerHTML = '';
        } else if (panel) {
            panel.remove();
        }
        if (dialog) {
            dialog.classList.remove('open');
            const body = dialog.querySelector('.reitti-dialog-body');
            if (body) body.innerHTML = '';
        }
    }
};

window.PanoramaxViewer = PanoramaxViewer;
