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
        
        // Create markers for visible photos
        visiblePhotos.forEach(photo => {
            this.createPhotoMarker(photo);
        });
    }

    /**
     * Create a marker for a photo
     * @param {Object} photo - Photo object with id, latitude, longitude, etc.
     */
    createPhotoMarker(photo) {
        // Create a custom div icon with the thumbnail image
        const iconSize = 50;
        const iconHtml = `
            <div style="
                width: ${iconSize}px;
                height: ${iconSize}px;
                border-radius: 50%;
                border: 3px solid #fff;
                box-shadow: 0 2px 8px rgba(0,0,0,0.3);
                overflow: hidden;
                background: #f0f0f0;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
            ">
                <img src="${photo.thumbnailUrl}" 
                     alt="${photo.fileName || 'Photo'}"
                     style="
                        width: 100%;
                        height: 100%;
                        object-fit: cover;
                        border-radius: 50%;
                     "
                     onerror="this.style.display='none'; this.parentElement.innerHTML='📷';">
            </div>
        `;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'photo-marker',
            iconSize: [iconSize, iconSize],
            iconAnchor: [iconSize / 2, iconSize / 2]
        });

        const marker = L.marker([photo.latitude, photo.longitude], {
            icon: customIcon
        });

        // Create popup content with photo info
        const popupContent = this.createPhotoPopupContent(photo);
        marker.bindPopup(popupContent, {
            maxWidth: 300,
            className: 'photo-popup'
        });

        // Add click handler to show full image
        marker.on('click', () => {
            this.showPhotoModal(photo);
        });

        marker.addTo(this.map);
        this.photoMarkers.push(marker);
    }

    /**
     * Create popup content for a photo
     * @param {Object} photo - Photo object
     * @returns {string} HTML content for popup
     */
    createPhotoPopupContent(photo) {
        const fileName = photo.fileName || 'Unknown';
        const dateTime = photo.dateTime ? new Date(photo.dateTime).toLocaleString() : 'Unknown time';
        
        return `
            <div class="photo-popup-content">
                <div style="font-weight: bold; margin-bottom: 4px;">${fileName}</div>
                <div style="font-size: 0.9em; color: #666; margin-bottom: 8px;">${dateTime}</div>
                <div style="font-size: 0.8em; color: #888;">
                    Click to view full size
                </div>
            </div>
        `;
    }

    /**
     * Show photo in a modal
     * @param {Object} photo - Photo object
     */
    showPhotoModal(photo) {
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
            z-index: 10000;
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
