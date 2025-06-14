class PhotoClient {
    constructor(map) {
        this.map = map;
        this.photoMarkers = [];
        this.currentDate = null;
        this.photos = [];
    }

    /**
     * Update photos for the selected date
     * @param {string} date - Date in YYYY-MM-DD format
     */
    async updatePhotosForDate(date) {
        this.currentDate = date;
        
        try {
            const response = await fetch(`/api/v1/photos/day/${date}`);
            if (!response.ok) {
                console.warn('Could not fetch photos for date:', date);
                this.photos = [];
            } else {
                this.photos = await response.json();
            }
        } catch (error) {
            console.warn('Error fetching photos:', error);
            this.photos = [];
        }
        
        this.updatePhotoMarkers();
    }

    /**
     * Clear all photos (when date is deselected)
     */
    clearPhotos() {
        this.currentDate = null;
        this.photos = [];
        this.clearPhotoMarkers();
    }

    /**
     * Update photo markers based on current map bounds
     */
    updatePhotoMarkers() {
        // Clear existing markers
        this.clearPhotoMarkers();
        
        if (!this.photos || this.photos.length === 0) {
            return;
        }
        
        // Get current map bounds
        const bounds = this.map.getBounds();
        
        // Filter photos that are within the current bounds and have valid coordinates
        const visiblePhotos = this.photos.filter(photo => {
            if (!photo.latitude || !photo.longitude) {
                return false;
            }
            
            const photoLatLng = L.latLng(photo.latitude, photo.longitude);
            return bounds.contains(photoLatLng);
        });
        
        // Group photos by location (with small tolerance for GPS precision)
        const photoGroups = this.groupPhotosByLocation(visiblePhotos);
        
        // Create markers for photo groups
        photoGroups.forEach(group => {
            this.createPhotoGroupMarker(group);
        });
    }

    /**
     * Group photos by location with tolerance for GPS precision
     * @param {Array} photos - Array of photo objects
     * @returns {Array} Array of photo groups
     */
    groupPhotosByLocation(photos) {
        const groups = [];
        const tolerance = 0.0001; // ~10 meters tolerance
        
        photos.forEach(photo => {
            let foundGroup = false;
            
            for (let group of groups) {
                const latDiff = Math.abs(group.latitude - photo.latitude);
                const lngDiff = Math.abs(group.longitude - photo.longitude);
                
                if (latDiff < tolerance && lngDiff < tolerance) {
                    group.photos.push(photo);
                    foundGroup = true;
                    break;
                }
            }
            
            if (!foundGroup) {
                groups.push({
                    latitude: photo.latitude,
                    longitude: photo.longitude,
                    photos: [photo]
                });
            }
        });
        
        return groups;
    }

    /**
     * Create a marker for a photo group
     * @param {Object} group - Photo group object with latitude, longitude, and photos array
     */
    createPhotoGroupMarker(group) {
        const iconSize = getComputedStyle(document.documentElement)
            .getPropertyValue('--photo-marker-size').trim();
        const iconSizeNum = parseInt(iconSize);
        const primaryPhoto = group.photos[0];
        const photoCount = group.photos.length;
        
        // Create count indicator if more than one photo
        const countIndicator = photoCount > 1 ? `
            <div class="photo-count-indicator">+${photoCount - 1}</div>
        ` : '';
        
        const iconHtml = `
            <div class="photo-marker-icon" style="width: ${iconSize}; height: ${iconSize};">
                <img src="${primaryPhoto.thumbnailUrl}" 
                     alt="${primaryPhoto.fileName || 'Photo'}"
                     onerror="this.style.display='none'; this.parentElement.innerHTML='📷';">
                ${countIndicator}
            </div>
        `;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'photo-marker',
            iconSize: [iconSizeNum, iconSizeNum],
            iconAnchor: [iconSizeNum / 2, iconSizeNum / 2]
        });

        const marker = L.marker([group.latitude, group.longitude], {
            icon: customIcon
        });

        // Add click handler to show photo grid
        marker.on('click', () => {
            this.showPhotoGridModal(group.photos);
        });

        marker.addTo(this.map);
        this.photoMarkers.push(marker);
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
        closeButton.innerHTML = '×';
        closeButton.className = 'photo-grid-close-button';

        // Create photo grid
        const photoGrid = document.createElement('div');
        photoGrid.className = 'photo-grid';
        const columns = Math.min(4, Math.ceil(Math.sqrt(photos.length)));
        const thumbnailSize = getComputedStyle(document.documentElement)
            .getPropertyValue('--photo-grid-thumbnail-size').trim();
        photoGrid.style.gridTemplateColumns = `repeat(${columns}, ${thumbnailSize})`;

        photos.forEach(photo => {
            const photoElement = document.createElement('div');
            photoElement.className = 'photo-grid-item';

            const img = document.createElement('img');
            img.src = photo.fullImageUrl;
            img.alt = photo.fileName || 'Photo';

            photoElement.addEventListener('click', (e) => {
                e.stopPropagation();
                this.showPhotoModal(photo, () => {
                    // Return to grid when closing full image
                    this.showPhotoGridModal(photos);
                });
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
            document.body.removeChild(modal);
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
     */
    showPhotoModal(photo, onClose = null) {
        // Create modal overlay
        const modal = document.createElement('div');
        modal.className = 'photo-modal';

        // Create image container
        const imageContainer = document.createElement('div');
        imageContainer.className = 'photo-modal-container';

        // Create image
        const img = document.createElement('img');
        img.src = photo.fullImageUrl;
        img.alt = photo.fileName || 'Photo';

        // Create close button
        const closeButton = document.createElement('button');
        closeButton.innerHTML = '×';
        closeButton.className = 'photo-modal-close-button';

        // Handle escape key
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                closeModal();
            }
        };

        // Add event listeners
        const closeModal = () => {
            document.removeEventListener('keydown', handleEscape);
            document.body.removeChild(modal);
            if (onClose) {
                onClose();
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
        imageContainer.appendChild(img);
        imageContainer.appendChild(closeButton);
        modal.appendChild(imageContainer);
        document.body.appendChild(modal);
    }

    /**
     * Clear all photo markers from the map
     */
    clearPhotoMarkers() {
        this.photoMarkers.forEach(marker => {
            this.map.removeLayer(marker);
        });
        this.photoMarkers = [];
    }

    /**
     * Handle map move/zoom events to update visible photos
     */
    onMapMoveEnd() {
        if (this.currentDate && this.photos.length > 0) {
            this.updatePhotoMarkers();
        }
    }
}
