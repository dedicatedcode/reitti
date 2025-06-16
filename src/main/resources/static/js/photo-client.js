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
