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
        const iconSize = 50;
        const primaryPhoto = group.photos[0];
        const photoCount = group.photos.length;
        
        // Create count indicator if more than one photo
        const countIndicator = photoCount > 1 ? `
            <div style="
                position: absolute;
                top: -5px;
                right: -5px;
                background: #e74c3c;
                color: white;
                border-radius: 50%;
                width: 20px;
                height: 20px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 12px;
                font-weight: bold;
                border: 2px solid #fff;
            ">+${photoCount - 1}</div>
        ` : '';
        
        const iconHtml = `
            <div style="
                width: ${iconSize}px;
                height: ${iconSize}px;
                border-radius: 50%;
                border: 3px solid #fff;
                box-shadow: 0 2px 8px rgba(0,0,0,0.3);
                background: #f0f0f0;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                position: relative;
            ">
                <img src="${primaryPhoto.thumbnailUrl}" 
                     alt="${primaryPhoto.fileName || 'Photo'}"
                     style="
                        width: 100%;
                        height: 100%;
                        object-fit: cover;
                        border-radius: 50%;
                     "
                     onerror="this.style.display='none'; this.parentElement.innerHTML='📷';">
                ${countIndicator}
            </div>
        `;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'photo-marker',
            iconSize: [iconSize, iconSize],
            iconAnchor: [iconSize / 2, iconSize / 2]
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
        // Create modal overlay
        const modal = document.createElement('div');
        modal.className = 'photo-grid-modal';
        modal.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.9);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 10000;
            cursor: pointer;
        `;

        // Create grid container
        const gridContainer = document.createElement('div');
        gridContainer.style.cssText = `
            max-width: 90%;
            max-height: 90%;
            overflow: auto;
            cursor: default;
            position: relative;
        `;

        // Create close button
        const closeButton = document.createElement('button');
        closeButton.innerHTML = '×';
        closeButton.style.cssText = `
            position: absolute;
            top: -40px;
            right: 0;
            background: rgba(255, 255, 255, 0.8);
            border: none;
            border-radius: 50%;
            width: 30px;
            height: 30px;
            font-size: 20px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 10001;
        `;

        // Create photo grid
        const photoGrid = document.createElement('div');
        const columns = Math.min(4, Math.ceil(Math.sqrt(photos.length)));
        photoGrid.style.cssText = `
            display: grid;
            grid-template-columns: repeat(${columns}, 450px);
            gap: 0;
            background: #000;
        `;

        photos.forEach(photo => {
            const photoElement = document.createElement('div');
            photoElement.style.cssText = `
                width: 450px;
                height: 450px;
                cursor: pointer;
                overflow: hidden;
            `;

            const img = document.createElement('img');
            img.src = photo.thumbnailUrl;
            img.alt = photo.fileName || 'Photo';
            img.style.cssText = `
                width: 100%;
                height: 100%;
                object-fit: cover;
                transition: transform 0.2s;
            `;

            img.addEventListener('mouseenter', () => {
                img.style.transform = 'scale(1.05)';
            });

            img.addEventListener('mouseleave', () => {
                img.style.transform = 'scale(1)';
            });

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

        // Add event listeners
        const closeModal = () => {
            document.body.removeChild(modal);
        };

        closeButton.addEventListener('click', closeModal);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // Handle escape key
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                closeModal();
                document.removeEventListener('keydown', handleEscape);
            }
        };
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
        modal.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.9);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 10001;
            cursor: pointer;
        `;

        // Create image container
        const imageContainer = document.createElement('div');
        imageContainer.style.cssText = `
            max-width: 90%;
            max-height: 90%;
            position: relative;
        `;

        // Create image
        const img = document.createElement('img');
        img.src = photo.fullImageUrl;
        img.alt = photo.fileName || 'Photo';
        img.style.cssText = `
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
        `;

        // Create close button
        const closeButton = document.createElement('button');
        closeButton.innerHTML = '×';
        closeButton.style.cssText = `
            position: absolute;
            top: -40px;
            right: 0;
            background: rgba(255, 255, 255, 0.8);
            border: none;
            border-radius: 50%;
            width: 30px;
            height: 30px;
            font-size: 20px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
        `;

        // Add event listeners
        const closeModal = () => {
            document.body.removeChild(modal);
            if (onClose) {
                onClose();
            }
        };

        closeButton.addEventListener('click', closeModal);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // Handle escape key
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                closeModal();
                document.removeEventListener('keydown', handleEscape);
            }
        };
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
