class PhotoClient {
    constructor(enabled) {
        this.enabled = enabled;
        this.photos = [];
        this.index = null;
        this.clusters = [];
        this.iconCache = new Map(); // Cache to store the generated circular icons
        this.iconSize = 64;
        this.state = {loading: false};
    }

    async updatePhotosForRange(start, end, timezone) {
        if (!this.enabled) return;
        this.clusters = [];
        this.iconCache = new Map();
        this.index = null;
        this.state = {loading: true};
        try {
            const response = await fetch(`${window.contextPath}/api/v1/photos/immich/range?timezone=${timezone}&startDate=${start}&endDate=${end}`);
            this.photos = response.ok ? await response.json() : [];
            this.state = {loading: false};
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
        const dynamicScale = Math.max(1, 1 + (zoom - 10) * 0.1);
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
                        ? this.index?.getLeaves(d.properties.cluster_id, 1)[0].properties.photoData
                        : d.properties.photoData;

                    if (!photo) return;
                    const url = window.contextPath + photo.thumbnailUrl;

                    return {
                        url: this.getCircularIcon(url), // Returns placeholder or processed PNG
                        width: 128, height: 128,
                        anchorX: 64,
                        anchorY: 128, // Sits on the ground
                        mask: false
                    };
                },
                getSize: size,
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

    showPhotoGridModal(photos) {
        // Remove any existing photo grid modal first
        const existingModal = document.querySelector('.photo-grid-modal');
        if (existingModal) {
            document.body.removeChild(existingModal);
        }

        // Create modal overlay
        const modal = document.createElement('div');
        modal.className = 'photo-grid-modal';

        // Create grid container
        const gridContainer = document.createElement('div');
        gridContainer.className = 'photo-grid-container';

        // Create close button
        const closeButton = document.createElement('button');
        closeButton.innerHTML = '<i class="lni lni-xmark"></i>';
        closeButton.className = 'photo-grid-close-button';

        // Create photo grid
        const photoGrid = document.createElement('div');
        photoGrid.className = 'photo-grid';
        const columns = Math.min(4, Math.ceil(Math.sqrt(photos.length)));
        const thumbnailSize = getComputedStyle(document.documentElement)
            .getPropertyValue('--photo-grid-thumbnail-size').trim();
        photoGrid.style.gridTemplateColumns = `repeat(${columns}, ${thumbnailSize})`;

        photos.forEach((photo, index) => {
            const photoElement = document.createElement('div');
            photoElement.className = 'photo-grid-item';

            // Create loading spinner
            const spinner = document.createElement('div');
            spinner.className = 'photo-loading-spinner';
            photoElement.appendChild(spinner);

            const img = document.createElement('img');
            img.src = window.contextPath + photo.fullImageUrl;
            img.alt = photo.fileName || 'Photo';

            // Handle image load
            img.addEventListener('load', () => {
                img.classList.add('loaded');
                if (photoElement.contains(spinner)) {
                    photoElement.removeChild(spinner);
                }
            });

            // Handle image error
            img.addEventListener('error', () => {
                if (photoElement.contains(spinner)) {
                    photoElement.removeChild(spinner);
                }
                img.style.display = 'none';
                photoElement.innerHTML = '📷';
                photoElement.style.fontSize = '24px';
                photoElement.style.color = '#ccc';
            });

            // Add time-matched indicator if photo was aligned by time
            if (photo.timeMatched === true) {
                const timeMatchedIndicator = document.createElement('div');
                timeMatchedIndicator.className = 'time-matched-indicator';
                timeMatchedIndicator.innerHTML = '!';
                timeMatchedIndicator.title = 'This photo had no GPS coordinates and was aligned by time to your path';
                photoElement.appendChild(timeMatchedIndicator);
            }

            photoElement.addEventListener('click', (e) => {
                e.stopPropagation();
                this.showPhotoModal(photo, () => {
                    // Return to grid when closing full image
                    this.showPhotoGridModal(photos);
                }, photos, index);
            });

            photoElement.appendChild(img);
            photoGrid.appendChild(photoElement);
        });

        // Handle escape key
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                closeModal();
            }
        };

        // Add event listeners
        const closeModal = () => {
            document.removeEventListener('keydown', handleEscape);
            if (document.body.contains(modal)) {
                document.body.removeChild(modal);
            }
        };

        closeButton.addEventListener('click', (e) => {
            e.preventDefault();
            handleEscape({ key: 'Escape' });
        });
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                handleEscape({ key: 'Escape' });
            }
        });

        document.addEventListener('keydown', handleEscape);

        // Assemble modal
        gridContainer.appendChild(closeButton);
        gridContainer.appendChild(photoGrid);
        modal.appendChild(gridContainer);
        document.body.appendChild(modal);

        // Prevent click propagation on grid container
        gridContainer.addEventListener('click', (e) => {
            e.stopPropagation();
        });
    }

    /**
     * Show photo in a modal
     * @param {Object} photo - Photo object
     * @param {Function} onClose - Optional callback when modal is closed
     * @param {Array} allPhotos - All photos in the group for navigation
     * @param {number} currentIndex - Current photo index in the group
     */
    showPhotoModal(photo, onClose = null, allPhotos = null, currentIndex = 0) {
        // Create modal overlay
        const modal = document.createElement('div');
        modal.className = 'photo-modal';

        // Create image container
        const imageContainer = document.createElement('div');
        imageContainer.className = 'photo-modal-container';

        // Create loading spinner for modal
        const modalSpinner = document.createElement('div');
        modalSpinner.className = 'photo-modal-loading-spinner';
        imageContainer.appendChild(modalSpinner);

        // Create image
        const img = document.createElement('img');
        img.src = window.contextPath + photo.fullImageUrl;
        img.alt = photo.fileName || 'Photo';

        // Handle image load
        img.addEventListener('load', () => {
            img.classList.add('loaded');
            if (imageContainer.contains(modalSpinner)) {
                imageContainer.removeChild(modalSpinner);
            }
        });

        // Handle image error
        img.addEventListener('error', () => {
            if (imageContainer.contains(modalSpinner)) {
                imageContainer.removeChild(modalSpinner);
            }
            img.style.display = 'none';
            const errorMsg = document.createElement('div');
            errorMsg.textContent = 'Failed to load image';
            errorMsg.style.color = '#ccc';
            errorMsg.style.fontSize = '18px';
            imageContainer.appendChild(errorMsg);
        });

        // Create close button
        const closeButton = document.createElement('button');
        closeButton.innerHTML = '<i class="lni lni-xmark"></i>';
        closeButton.className = 'photo-modal-close-button';

        // Create navigation elements if we have multiple photos
        let prevButton, nextButton, counter;
        if (allPhotos && allPhotos.length > 1) {
            // Previous button
            prevButton = document.createElement('button');
            prevButton.innerHTML = '<i class="lni lni-chevron-left"></i>';
            prevButton.className = 'photo-nav-button photo-nav-prev';
            prevButton.disabled = currentIndex === 0;

            // Next button
            nextButton = document.createElement('button');
            nextButton.innerHTML = '<i class="lni lni-chevron-left lni-rotate-180"></i>';
            nextButton.className = 'photo-nav-button photo-nav-next';
            nextButton.disabled = currentIndex === allPhotos.length - 1;

            // Photo counter
            counter = document.createElement('div');
            counter.className = 'photo-counter';
            counter.textContent = `${currentIndex + 1} / ${allPhotos.length}`;
        }

        // Navigation functions
        const showPrevPhoto = () => {
            if (allPhotos && currentIndex > 0) {
                closeModal();
                this.showPhotoModal(allPhotos[currentIndex - 1], onClose, allPhotos, currentIndex - 1);
            }
        };

        const showNextPhoto = () => {
            if (allPhotos && currentIndex < allPhotos.length - 1) {
                closeModal();
                this.showPhotoModal(allPhotos[currentIndex + 1], onClose, allPhotos, currentIndex + 1);
            }
        };

        // Handle keyboard navigation
        const handleKeydown = (e) => {
            switch (e.key) {
                case 'Escape':
                    closeModal();
                    break;
                case 'ArrowLeft':
                    e.preventDefault();
                    showPrevPhoto();
                    break;
                case 'ArrowRight':
                    e.preventDefault();
                    showNextPhoto();
                    break;
            }
        };

        // Add event listeners
        const closeModal = () => {
            document.removeEventListener('keydown', handleKeydown);
            if (document.body.contains(modal)) {
                document.body.removeChild(modal);
            }
            if (onClose) {
                onClose();
            }
        };

        closeButton.addEventListener('click', (e) => {
            e.preventDefault();
            closeModal();
        });

        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // Add navigation button listeners
        if (prevButton) {
            prevButton.addEventListener('click', (e) => {
                e.stopPropagation();
                showPrevPhoto();
            });
        }

        if (nextButton) {
            nextButton.addEventListener('click', (e) => {
                e.stopPropagation();
                showNextPhoto();
            });
        }

        document.addEventListener('keydown', handleKeydown);

        // Assemble modal
        imageContainer.appendChild(img);
        imageContainer.appendChild(closeButton);

        if (prevButton) imageContainer.appendChild(prevButton);
        if (nextButton) imageContainer.appendChild(nextButton);
        if (counter) imageContainer.appendChild(counter);

        modal.appendChild(imageContainer);
        document.body.appendChild(modal);
    }
}
