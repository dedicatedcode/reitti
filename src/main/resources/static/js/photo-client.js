class PhotoClient {
    constructor(enabled) {
        this.enabled = enabled;
        this.photos = [];
        this.index = null;
        this.clusters = [];
        this.iconCache = new Map(); // Cache to store the generated circular icons
        this.iconSize = 64;
    }

    async updatePhotosForRange(start, end, timezone) {
        if (!this.enabled) return;
        try {
            const response = await fetch(`${window.contextPath}/api/v1/photos/immich/range?timezone=${timezone}&startDate=${start}&endDate=${end}`);
            this.photos = response.ok ? await response.json() : [];
        } catch (error) {
            this.photos = [];
        }
        this.initializeIndex();
    }

    initializeIndex() {
        this.index = new Supercluster({radius: 128});
        const points = this.photos
            .filter(p => p.latitude && p.longitude)
            .map(photo => ({
                type: 'Feature',
                properties: {photoData: photo},
                geometry: {type: 'Point', coordinates: [parseFloat(photo.longitude), parseFloat(photo.latitude)]}
            }));
        this.index.load(points);
    }

    updateClusters(viewState) {
        if (!this.index || !viewState) return;
        this.clusters = this.index.getClusters([-180, -85, 180, 85], Math.floor(viewState.zoom));
    }

    getCircularIcon(url) {
        // 1. Return cached version if available (Prevents flickering)
        if (this.iconCache.has(url)) {
            return this.iconCache.get(url);
        }

        // 2. Return a temporary placeholder while loading
        // (A simple white circle SVG)
        const placeholder = 'data:image/svg+xml;base64,' + btoa('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><circle cx="64" cy="64" r="60" fill="white" stroke="#ccc" stroke-width="2"/></svg>');

        // 3. Asynchronously load and process the image
        const img = new Image();
        img.crossOrigin = "anonymous";
        img.src = url;

        img.onload = () => {
            const canvas = document.createElement('canvas');
            const size = 128; // Higher res for sharp icons
            canvas.width = size;
            canvas.height = size;
            const ctx = canvas.getContext('2d');

            // A. Draw the "Mask" (Circle)
            ctx.beginPath();
            ctx.arc(size / 2, size / 2, 60, 0, Math.PI * 2);
            ctx.closePath();
            ctx.clip(); // Everything drawn after this is clipped to the circle

            // B. Draw the Image (Aspect Fill)
            // This math ensures the image covers the circle completely without stretching
            const aspect = img.width / img.height;
            let drawW = size, drawH = size, offX = 0, offY = 0;

            if (aspect > 1) { // Wide image
                drawH = size;
                drawW = size * aspect;
                offX = -(drawW - size) / 2;
            } else { // Tall image
                drawW = size;
                drawH = size / aspect;
                offY = -(drawH - size) / 2;
            }
            ctx.drawImage(img, offX, offY, drawW, drawH);

            // C. Draw the Border (Stroke) inside the circle
            ctx.beginPath();
            ctx.arc(size / 2, size / 2, 58, 0, Math.PI * 2); // Slightly smaller to sit inside
            ctx.lineWidth = 14;
            ctx.strokeStyle = 'white';
            ctx.stroke();

            // D. Save to cache and trigger a re-render
            const finalDataUrl = canvas.toDataURL('image/png');
            this.iconCache.set(url, finalDataUrl);
        };

        return placeholder;
    }

    getLayers(offset, viewState) {
        if (!this.enabled || this.clusters.length === 0) return [];

        const zoom = Math.floor(viewState.zoom);
        const dynamicScale = Math.max(1, 1 + (zoom - 10) * 0.25);
        const size = this.iconSize * dynamicScale;
        // Shared sizing for the badge (the red circle)
        const badgeSize = 36 * dynamicScale;
        // Calculate the position offset for the badge (top-right of the photo)
        const badgeOffset = [
            (this.iconSize * dynamicScale) / 3.5,
            -(this.iconSize * dynamicScale) * 0.9
        ];
        const layers = [
            // SINGLE ICON LAYER (Handles everything)
            new deck.IconLayer({
                id: 'photo-icons',
                data: this.clusters,
                pickable: true,
                getPosition: d => d.geometry.coordinates,
                getIcon: d => {
                    const photo = d.properties.cluster
                        ? this.index.getLeaves(d.properties.cluster_id, 1)[0].properties.photoData
                        : d.properties.photoData;

                    const url = window.contextPath + photo.thumbnailUrl;

                    return {
                        url: this.getCircularIcon(url), // Returns placeholder or processed PNG
                        width: 128, height: 128,
                        anchorX: 64,
                        anchorY: 128, // Sits on the ground
                        mask: false
                    };
                },
                getSize: d => (d.properties.cluster ? size : size * 0.85),
                billboard: true,
                parameters: {
                    depthTest: false,
                    depthMask: false
                },
                // Trigger update when cache size changes (images loaded)
                updateTriggers: {
                    getIcon: [zoom, this.iconCache.size]
                },
                onClick: info => this.handlePointClick(info)
            })
        ];
        const clusterData = this.clusters.filter(d => d.properties.cluster);
        if (clusterData.length > 0) {
            layers.push(
                // 2. BADGE BACKGROUND (Red Circle with White Border)
                new deck.IconLayer({
                    id: 'photo-badge-circles',
                    data: clusterData,
                    getPosition: d => d.geometry.coordinates,
                    getIcon: d => ({
                        // We increase the viewBox to 120x120 but keep the circle centered at 50.
                        // This provides 10 units of "safety padding" on every side.
                        url: 'data:image/svg+xml;base64,' + btoa(`
                            <svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="-10 -10 120 120">
                                <circle cx="50" cy="50" r="45" fill="#dc3545" stroke="white" stroke-width="8" />
                            </svg>`),
                        // We MUST increase these to 120 to match the viewBox
                        // so the internal scaling remains 1:1
                        width: 120,
                        height: 120,
                        // The anchor is now 60 (half of 120) to keep it perfectly centered
                        anchorX: 60,
                        anchorY: 60,
                        mask: false
                    }),
                    getSize: 1,
                    sizeScale: badgeSize,
                    getPixelOffset: badgeOffset,
                    billboard: true,
                    parameters: {
                        depthTest: false,
                        depthMask: false
                    },
                    updateTriggers: {
                        getPixelOffset: [zoom],
                        sizeScale: [zoom]
                    }
                }),

                // 3. BADGE TEXT (The Number)
                new deck.TextLayer({
                    id: 'photo-badge-text',
                    data: clusterData,
                    getPosition: d => d.geometry.coordinates,
                    getText: d => `${d.properties.point_count}`,
                    getSize: 12,
                    sizeScale: dynamicScale,
                    getPixelOffset: badgeOffset, // Matches the red circle exactly
                    getColor: [255, 255, 255, 255],
                    fontWeight: 'bold',
                    // Center the text within the badge
                    getTextAnchor: 'middle',
                    getAlignmentBaseline: 'center',
                    billboard: true,
                    parameters: {
                        depthTest: false,
                        depthMask: false
                    },
                    updateTriggers: {
                        getPixelOffset: [zoom],
                        sizeScale: [zoom]
                    }
                })
            );
        }
        return layers;
    }

    handlePointClick(info) {
        if (!info.object) return;
        const {properties} = info.object;
        if (properties.cluster) {
            const leaves = this.index.getLeaves(properties.cluster_id, Infinity);
            this.showPhotoGridModal(leaves.map(l => l.properties.photoData));
        } else {
            this.showPhotoModal(properties.photoData);
        }
    }

    /** --- UI MODAL LOGIC (PORTED FROM YOUR LEAFLET CODE) --- **/

    showPhotoGridModal(photos) {
        const existingModal = document.querySelector('.photo-grid-modal');
        if (existingModal) document.body.removeChild(existingModal);

        const modal = document.createElement('div');
        modal.className = 'photo-grid-modal';
        const gridContainer = document.createElement('div');
        gridContainer.className = 'photo-grid-container';

        const closeButton = document.createElement('button');
        closeButton.innerHTML = '✕';
        closeButton.className = 'photo-grid-close-button';
        closeButton.onclick = () => document.body.removeChild(modal);

        const photoGrid = document.createElement('div');
        photoGrid.className = 'photo-grid';

        // Match your sqrt calculation for columns
        const columns = Math.min(4, Math.ceil(Math.sqrt(photos.length)));
        const thumbnailSize = getComputedStyle(document.documentElement).getPropertyValue('--photo-grid-thumbnail-size').trim() || '100px';
        photoGrid.style.display = 'grid';
        photoGrid.style.gridTemplateColumns = `repeat(${columns}, ${thumbnailSize})`;
        photoGrid.style.gap = '10px';

        photos.forEach((photo, index) => {
            const item = document.createElement('div');
            item.className = 'photo-grid-item';
            const img = document.createElement('img');
            img.src = window.contextPath + photo.thumbnailUrl;
            img.onclick = (e) => {
                e.stopPropagation();
                this.showPhotoModal(photo, () => this.showPhotoGridModal(photos), photos, index);
            };
            item.appendChild(img);
            photoGrid.appendChild(item);
        });

        gridContainer.appendChild(closeButton);
        gridContainer.appendChild(photoGrid);
        modal.appendChild(gridContainer);
        document.body.appendChild(modal);
    }

    showPhotoModal(photo, onClose = null, allPhotos = null, currentIndex = 0) {
        const modal = document.createElement('div');
        modal.className = 'photo-modal';

        const container = document.createElement('div');
        container.className = 'photo-modal-container';

        const img = document.createElement('img');
        img.src = window.contextPath + photo.fullImageUrl;
        img.onload = () => img.classList.add('loaded');

        const closeBtn = document.createElement('button');
        closeBtn.innerHTML = '✕';
        closeBtn.className = 'photo-modal-close-button';

        const closeModal = () => {
            if (document.body.contains(modal)) document.body.removeChild(modal);
            if (onClose) onClose();
        };

        closeBtn.onclick = closeModal;
        modal.onclick = (e) => {
            if (e.target === modal) closeModal();
        };

        container.appendChild(img);
        container.appendChild(closeBtn);

        // Simple Nav (Previous/Next)
        if (allPhotos && allPhotos.length > 1) {
            const nav = document.createElement('div');
            nav.className = 'photo-counter';
            nav.textContent = `${currentIndex + 1} / ${allPhotos.length}`;
            container.appendChild(nav);
        }

        modal.appendChild(container);
        document.body.appendChild(modal);
    }
}
