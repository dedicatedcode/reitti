class PhotoClient {
    constructor(map, enabled) {
        this.map = map;
        this.photoMarkers = [];
        this.photos = [];
        this.enabled = enabled;
        this.markerClusterGroup = null;
        
        if (this.enabled) {
            this.initializeClusterGroup();
        }
    }
    
    initializeClusterGroup() {
        this.markerClusterGroup = L.markerClusterGroup({
            maxClusterRadius: (zoom) => {
                // Adjust cluster radius based on zoom level
                if (zoom >= 15) return 20;  // Small clusters at high zoom
                if (zoom >= 12) return 40;  // Medium clusters
                if (zoom >= 10) return 60;  // Larger clusters
                return 80;                  // Large clusters at low zoom
            },
            iconCreateFunction: (cluster) => {
                const count = cluster.getChildCount();
                let className = 'photo-cluster-small';
                
                if (count > 10) {
                    className = 'photo-cluster-large';
                } else if (count > 5) {
                    className = 'photo-cluster-medium';
                }
                
                return L.divIcon({
                    html: `<div class="photo-cluster-inner">${count}</div>`,
                    className: `photo-cluster ${className}`,
                    iconSize: [40, 40]
                });
            },
            spiderfyOnMaxZoom: true,
            showCoverageOnHover: false,
            zoomToBoundsOnClick: true
        });
        
        this.map.addLayer(this.markerClusterGroup);
        console.log('Photo cluster group initialized and added to map');
        console.log('Map has cluster group layer:', this.map.hasLayer(this.markerClusterGroup));
        
        // Set a higher z-index to ensure photos appear above other layers
        if (this.markerClusterGroup.getPane) {
            const pane = this.markerClusterGroup.getPane();
            if (pane) {
                pane.style.zIndex = 500;
            }
        }
    }

    async updatePhotosForRange(start, end, timezone) {
        if (!this.enabled) {
            console.log('Photo client disabled, skipping photo fetch');
            return;
        }
        try {
            console.log('Fetching photos for range:', start, 'to', end);
            const response = await fetch(`/api/v1/photos/immich/range?timezone=${timezone}&startDate=${start}&endDate=${end}`);
            if (!response.ok) {
                console.warn('Could not fetch photos for date range:', start, 'to', end);
                this.photos = [];
            } else {
                this.photos = await response.json();
                console.log('Fetched', this.photos.length, 'photos');
            }
        } catch (error) {
            console.warn('Error fetching photos:', error);
            this.photos = [];
        }
        
        this.updatePhotoMarkers();
    }


    /**
     * Update photo markers based on current map bounds
     */
    updatePhotoMarkers() {
        console.log('Updating photo markers, photos count:', this.photos.length);
        
        // Clear existing markers
        this.clearPhotoMarkers();
        
        if (!this.photos || this.photos.length === 0) {
            console.log('No photos to display');
            return;
        }
        
        // Ensure cluster group exists and is on the map
        if (!this.markerClusterGroup) {
            console.log('Cluster group not initialized, initializing now');
            this.initializeClusterGroup();
        } else if (!this.map.hasLayer(this.markerClusterGroup)) {
            console.log('Cluster group not on map, re-adding it');
            this.map.addLayer(this.markerClusterGroup);
        }
        
        // Filter photos that have valid coordinates
        const validPhotos = this.photos.filter(photo => {
            return photo.latitude && photo.longitude;
        });
        
        console.log('Valid photos with coordinates:', validPhotos.length);
        
        // Create markers for all valid photos and add to cluster group
        validPhotos.forEach(photo => {
            this.createPhotoMarker(photo);
        });
        
        console.log('Created', validPhotos.length, 'photo markers');
        
        // Debug cluster group state
        if (this.markerClusterGroup) {
            console.log('Cluster group layers count:', this.markerClusterGroup.getLayers().length);
            console.log('Cluster group is on map:', this.map.hasLayer(this.markerClusterGroup));
            console.log('Cluster group bounds:', this.markerClusterGroup.getBounds());
        }
    }

    /**
     * Create a marker for a single photo
     * @param {Object} photo - Photo object
     */
    createPhotoMarker(photo) {
        const iconSize = getComputedStyle(document.documentElement)
            .getPropertyValue('--photo-marker-size').trim();
        const iconSizeNum = parseInt(iconSize) || 50; // fallback to 50px
        
        const iconHtml = `
            <div class="photo-marker-icon" style="width: ${iconSize}; height: ${iconSize};">
                <img src="${photo.thumbnailUrl}" 
                     alt="${photo.fileName || 'Photo'}"
                     onerror="this.style.display='none'; this.parentElement.innerHTML='📷';">
            </div>
        `;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'photo-marker',
            iconSize: [iconSizeNum, iconSizeNum],
            iconAnchor: [iconSizeNum / 2, iconSizeNum / 2]
        });

        const marker = L.marker([photo.latitude, photo.longitude], {
            icon: customIcon
        });

        // Add click handler to show photo modal
        marker.on('click', () => {
            this.showPhotoModal(photo);
        });

        // Add to cluster group instead of directly to map
        if (this.markerClusterGroup) {
            this.markerClusterGroup.addLayer(marker);
            this.photoMarkers.push(marker);
            console.log('Added photo marker at', photo.latitude, photo.longitude, 'Total markers in cluster:', this.markerClusterGroup.getLayers().length);
        } else {
            console.error('Cluster group not available when trying to add marker');
        }
    }


    /**
     * Show photo grid modal
     * @param {Array} photos - Array of photo objects
     */
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
            img.src = photo.fullImageUrl;
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
        img.src = photo.fullImageUrl;
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

    /**
     * Clear all photo markers from the map
     */
    clearPhotoMarkers() {
        if (this.markerClusterGroup) {
            this.markerClusterGroup.clearLayers();
            // Ensure cluster group stays on the map after clearing
            if (!this.map.hasLayer(this.markerClusterGroup)) {
                this.map.addLayer(this.markerClusterGroup);
            }
        }
        this.photoMarkers = [];
    }

    onMapMoveEnd() {
        if (this.photos.length > 0) {
            this.updatePhotoMarkers();
        }
    }
}
