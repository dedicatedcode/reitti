class PhotoClusterManager {
    constructor(map, options = {}) {
        this.map = map;
        this.options = {
            clusterRadius: options.clusterRadius || 80,
            clusterMaxZoom: options.clusterMaxZoom || 22,
            iconSize: options.iconSize || 56,
            borderWidth: options.borderWidth || 3,
            badgeFontSize: options.badgeFontSize || 11,
            showPhotoGridModal: options.showPhotoGridModal || this.showPhotoGridModal.bind(this),
            showPhotoModal: options.showPhotoModal || this.showPhotoModal.bind(this),
        };

        this.index = null;
        this.photos = [];
        this.markers = new Map();
        this._imageCache = new Map();
        this._writtenAssetIds = new Set();

        const self = this;
        this._moveHandler = function () {
            self._doUpdate();
        };
        this._moveEndHandler = function () {
            self._doUpdate();
        };

        map.on('move', this._moveHandler);
        map.on('moveend', this._moveEndHandler);
    }

    setPhotos(photos) {
        this.photos = (photos || []).filter(function (p) {
            return p.latitude != null && p.longitude != null &&
                isFinite(p.latitude) && isFinite(p.longitude);
        });
        this._buildIndex();
        this._doUpdate();
    }

    _buildIndex() {
        if (!this.photos.length) {
            this.index = null;
            this._clearMarkers();
            return;
        }

        const features = this.photos.map(function (photo) {
            return {
                type: 'Feature',
                geometry: {type: 'Point', coordinates: [photo.longitude, photo.latitude]},
                properties: {photo: photo},
            };
        });

        this.index = new Supercluster({
            radius: this.options.clusterRadius,
            maxZoom: this.options.clusterMaxZoom,
            map: function (props) {
                return {photos: [props.photo]};
            },
            reduce: function (acc, props) {
                acc.photos = acc.photos.concat(props.photos);
            },
        });

        this.index.load(features);
    }

    _doUpdate() {
        if (!this.index) return;

        const bounds = this.map.getBounds();
        const zoom = Math.round(this.map.getZoom());
        const pad = 0.05;

        const raw = this.index.getClusters(
            [bounds.getWest() - pad, bounds.getSouth() - pad,
                bounds.getEast() + pad, bounds.getNorth() + pad],
            zoom
        );

        const newClusters = new Map();

        for (let i = 0; i < raw.length; i++) {
            const feature = raw[i];
            const lng = feature.geometry.coordinates[0];
            const lat = feature.geometry.coordinates[1];
            const isCluster = feature.properties.cluster === true;
            let id, cluster;

            if (isCluster) {
                id = 'cluster-' + feature.properties.cluster_id;
                cluster = {
                    id: id,
                    clusterId: feature.properties.cluster_id,
                    longitude: lng,
                    latitude: lat,
                    isCluster: true,
                    count: feature.properties.point_count,
                    photos: feature.properties.photos || [],
                    thumbnailUrl: (feature.properties.photos && feature.properties.photos[0])
                        ? feature.properties.photos[0].thumbnailUrl : '',
                };
            } else {
                const photo = feature.properties.photo;
                id = 'photo-' + photo.id;
                cluster = {
                    id: id,
                    longitude: lng,
                    latitude: lat,
                    isCluster: false,
                    count: 1,
                    photo: photo,
                    photos: [photo],
                    thumbnailUrl: photo.thumbnailUrl || '',
                };
            }

            newClusters.set(id, cluster);
        }

        this._reconcileMarkers(newClusters);
    }

    _reconcileMarkers(newClusters) {
        for (const entry of this.markers) {
            if (!newClusters.has(entry[0])) {
                entry[1].remove();
                this.markers.delete(entry[0]);
            }
        }

        for (const entry of newClusters) {
            const id = entry[0];
            const cluster = entry[1];
            const existing = this.markers.get(id);
            if (existing) {
                existing.setLngLat([cluster.longitude, cluster.latitude]);
            } else {
                const el = this._createMarkerElement(cluster);
                const m = new maplibregl.Marker({element: el, anchor: 'center'})
                    .setLngLat([cluster.longitude, cluster.latitude])
                    .addTo(this.map);
                m.getElement().style.zIndex = 1000;
                this.markers.set(id, m);
            }
        }
    }

    _clearMarkers() {
        for (let entry of this.markers) {
            entry[1].remove();
        }
        this.markers.clear();
    }

    _createMarkerElement(cluster) {
        const SIZE = this.options.iconSize;
        const BORDER = this.options.borderWidth;
        const self = this;

        // Outer container - NO position:relative, MapLibre controls this
        const container = document.createElement('div');
        container.style.cssText =
            'width:' + SIZE + 'px;height:' + SIZE + 'px;cursor:pointer;';

        // Inner wrapper for badge positioning
        const inner = document.createElement('div');
        inner.style.cssText =
            'width:' + SIZE + 'px;height:' + SIZE + 'px;position:relative;';

        const circle = document.createElement('div');
        circle.style.cssText =
            'width:' + SIZE + 'px;height:' + SIZE + 'px;border-radius:50%;' +
            'border:' + BORDER + 'px solid #ffffff;' +
            'box-shadow:0 2px 6px rgba(0,0,0,0.35);overflow:hidden;' +
            'background:#ddd;box-sizing:border-box;';

        const placeholderSVG =
            '<svg viewBox="0 0 24 24" style="width:60%;height:60%;margin:20%;opacity:0.4;fill:#999;">' +
            '<path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z' +
            'M8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/></svg>';

        if (cluster.thumbnailUrl) {
            const cached = this._imageCache.get(cluster.thumbnailUrl);
            if (cached) {
                circle.style.backgroundImage = 'url(' + cached + ')';
                circle.style.backgroundSize = 'cover';
                circle.style.backgroundPosition = 'center';
            } else {
                circle.innerHTML = placeholderSVG;
                const img = new Image();
                img.crossOrigin = 'anonymous';
                img.onload = function () {
                    const c = document.createElement('canvas');
                    c.width = img.naturalWidth;
                    c.height = img.naturalHeight;
                    c.getContext('2d').drawImage(img, 0, 0);
                    c.toBlob(function (blob) {
                        if (blob) {
                            const blobUrl = URL.createObjectURL(blob);
                            self._imageCache.set(cluster.thumbnailUrl, blobUrl);
                            circle.style.backgroundImage = 'url(' + blobUrl + ')';
                            circle.style.backgroundSize = 'cover';
                            circle.style.backgroundPosition = 'center';
                            circle.innerHTML = '';
                        }
                    });
                };
                img.onerror = function () {
                };
                img.src = cluster.thumbnailUrl;
            }
        } else {
            circle.innerHTML = placeholderSVG;
        }

        inner.appendChild(circle);

        if (cluster.isCluster && cluster.count > 1) {
            const badge = document.createElement('div');
            badge.textContent = cluster.count > 99 ? '99+' : String(cluster.count);
            badge.style.cssText =
                'position:absolute;top:-4px;right:-4px;' +
                'background:#ff4444;color:#ffffff;' +
                'font-size:' + this.options.badgeFontSize + 'px;font-weight:bold;' +
                'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;' +
                'min-width:18px;height:18px;line-height:18px;text-align:center;' +
                'border-radius:9px;padding:0 5px;' +
                'border:1.5px solid #ffffff;box-sizing:border-box;' +
                'box-shadow:0 1px 3px rgba(0,0,0,0.3);';
            inner.appendChild(badge);
        }

        container.appendChild(inner);

        container.addEventListener('click', function (e) {
            e.stopPropagation();
            if (cluster.isCluster) {
                if (self.options.showPhotoGridModal) {
                    self.options.showPhotoGridModal(cluster.photos);
                }
            } else {
                if (self.options.showPhotoModal) {
                    self.options.showPhotoModal(cluster.photo, null, null, 0);
                }
            }
        });

        return container;
    }

    destroy() {
        this._clearMarkers();
        this.map.off('move', this._moveHandler);
        this.map.off('moveend', this._moveEndHandler);
        for (let entry of this._imageCache) {
            URL.revokeObjectURL(entry[1]);
        }
        this._imageCache.clear();
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

        // Count time-matched photos (excluding already-written ones)
        const timeMatchedPhotos = photos.filter(function (p) { return p.timeMatched === true && !self._writtenAssetIds.has(p.id); });
        const hasTimeMatched = timeMatchedPhotos.length > 0;

        // Create photo grid
        const photoGrid = document.createElement('div');
        photoGrid.className = 'photo-grid';
        const columns = Math.min(4, Math.ceil(Math.sqrt(photos.length)));
        const thumbnailSize = getComputedStyle(document.documentElement)
            .getPropertyValue('--photo-grid-thumbnail-size').trim();
        photoGrid.style.gridTemplateColumns = `repeat(${columns}, ${thumbnailSize})`;

        var self = this;

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

            // Add time-matched indicator if photo was aligned by time and not yet written
            if (photo.timeMatched === true && !self._writtenAssetIds.has(photo.id)) {
                const timeMatchedIndicator = document.createElement('div');
                timeMatchedIndicator.className = 'time-matched-indicator';
                timeMatchedIndicator.innerHTML = '!';
                timeMatchedIndicator.title = t('photo.time-matched.tooltip');
                photoElement.appendChild(timeMatchedIndicator);
            }

            photoElement.addEventListener('click', (e) => {
                e.stopPropagation();
                self.showPhotoModal(photo, () => {
                    // Return to grid when closing full image
                    self.showPhotoGridModal(photos);
                }, photos, index);
            });

// Add write-back button for time-matched photos not yet written
            if (photo.timeMatched === true && !self._writtenAssetIds.has(photo.id)) {
                const writeBtn = document.createElement('button');
                writeBtn.className = 'btn write-location-btn';
                writeBtn.setAttribute('data-asset-id', photo.id);
                writeBtn.innerHTML = '<i class="lni lni-map-marker"></i>';
                writeBtn.title = t('photo.write-location.title');
                writeBtn.addEventListener('click', function (ev) {
                    ev.stopPropagation();
                    writeBtn.disabled = true;
                    writeBtn.classList.add('writing');
                    fetch(window.contextPath + '/api/v1/photos/immich/' + encodeURIComponent(photo.id) + '/location?latitude=' + photo.latitude + '&longitude=' + photo.longitude, {
                        method: 'PUT'
                    })
                    .then(function (resp) {
                        if (resp.ok) {
                            self._writtenAssetIds.add(photo.id);
                            writeBtn.classList.remove('writing');
                            writeBtn.classList.add('success');
                            writeBtn.innerHTML = '<i class="lni lni-checkmark"></i>';
                            showToast(t('photo.write-location.success'));
                        } else {
                            throw new Error('HTTP ' + resp.status);
                        }
                    })
                    .catch(function () {
                        writeBtn.classList.remove('writing');
                        writeBtn.classList.add('error');
                        writeBtn.disabled = false;
                        writeBtn.innerHTML = '<i class="lni lni-close"></i>';
                        showToast(t('photo.write-location.error'), true);
                    });
                });
                photoElement.appendChild(writeBtn);
            }

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
            handleEscape({key: 'Escape'});
        });
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                handleEscape({key: 'Escape'});
            }
        });

        document.addEventListener('keydown', handleEscape);

        // Assemble modal
        gridContainer.appendChild(closeButton);
        if (hasTimeMatched) {
            const toolbar = document.createElement('div');
            toolbar.className = 'write-location-toolbar';

            const countLabel = document.createElement('span');
            countLabel.className = 'write-location-toolbar-label';
            countLabel.textContent = t('photo.time-matched.count', [timeMatchedPhotos.length]);
            toolbar.appendChild(countLabel);

            if (timeMatchedPhotos.length > 1) {
                const writeAllBtn = document.createElement('button');
                writeAllBtn.className = 'btn write-all-btn';
                writeAllBtn.textContent = t('photo.write-all.label', [timeMatchedPhotos.length]);
                writeAllBtn.addEventListener('click', function () {
                    writeAllBtn.disabled = true;
                    var total = timeMatchedPhotos.length;
                    var done = 0;
                    var failures = 0;
                    function updateLabel() {
                        writeAllBtn.textContent = t('photo.write-all.progress', [done, total]);
                    }
                    updateLabel();
                    timeMatchedPhotos.forEach(function (p) {
                        fetch(window.contextPath + '/api/v1/photos/immich/' + encodeURIComponent(p.id) + '/location?latitude=' + p.latitude + '&longitude=' + p.longitude, {
                            method: 'PUT'
                        })
                        .then(function (resp) {
                            return resp.ok ? Promise.resolve() : Promise.reject();
                        })
                        .then(function () {
                            done++;
                            self._writtenAssetIds.add(p.id);
                            var writeBtn = document.querySelector('.write-location-btn[data-asset-id="' + p.id + '"]');
                            if (writeBtn) {
                                writeBtn.classList.add('success');
                                writeBtn.innerHTML = '<i class="lni lni-checkmark"></i>';
                                writeBtn.disabled = true;
                            }
                        })
                        .catch(function () {
                            done++;
                            failures++;
                            var writeBtn = document.querySelector('.write-location-btn[data-asset-id="' + p.id + '"]');
                            if (writeBtn) {
                                writeBtn.classList.add('error');
                                writeBtn.innerHTML = '<i class="lni lni-close"></i>';
                            }
                        })
                        .finally(function () {
                            updateLabel();
                            if (done === total) {
                                if (failures === 0) {
                                    writeAllBtn.textContent = t('photo.write-all.done');
                                    writeAllBtn.classList.add('success');
                                    showToast(t('photo.write-all.success'));
                                } else {
                                    writeAllBtn.textContent = t('photo.write-all.done-failures', [failures]);
                                    writeAllBtn.classList.add('partial');
                                    showToast(t('photo.write-all.partial', [failures, total]), true);
                                }
                            }
                        });
                    });
                });
                toolbar.appendChild(writeAllBtn);
            }

            gridContainer.appendChild(toolbar);
        }
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

        // Time-matched info bar (only if not yet written)
        if (photo.timeMatched === true && !this._writtenAssetIds.has(photo.id)) {
            const timeMatchBar = document.createElement('div');
            timeMatchBar.className = 'photo-time-match-bar';

            const indicator = document.createElement('span');
            indicator.className = 'photo-time-match-indicator';
            indicator.textContent = '!';
            timeMatchBar.appendChild(indicator);

            const label = document.createElement('span');
            label.className = 'photo-time-match-label';
            label.textContent = t('photo.time-matched.bar-label');
            timeMatchBar.appendChild(label);

            var self = this;
            const writeBtn = document.createElement('button');
            writeBtn.className = 'btn photo-modal-write-btn';
            writeBtn.innerHTML = '<i class="lni lni-map-marker"></i> ' + t('photo.write-location.button');
            writeBtn.addEventListener('click', function (ev) {
                ev.stopPropagation();
                writeBtn.disabled = true;
                writeBtn.textContent = t('photo.write-location.writing');
                fetch(window.contextPath + '/api/v1/photos/immich/' + encodeURIComponent(photo.id) + '/location?latitude=' + photo.latitude + '&longitude=' + photo.longitude, {
                    method: 'PUT'
                })
                .then(function (resp) {
                    if (resp.ok) {
                        self._writtenAssetIds.add(photo.id);
                        writeBtn.innerHTML = '<i class="lni lni-checkmark"></i> ' + t('photo.write-location.written');
                        writeBtn.classList.add('success');
                        showToast(t('photo.write-location.success'));
                        setTimeout(function () {
                            if (timeMatchBar.parentNode) {
                                timeMatchBar.parentNode.removeChild(timeMatchBar);
                            }
                        }, 1500);
                    } else {
                        throw new Error('HTTP ' + resp.status);
                    }
                })
                .catch(function () {
                    writeBtn.disabled = false;
                    writeBtn.innerHTML = '<i class="lni lni-close"></i> ' + t('photo.write-location.failed');
                    writeBtn.classList.add('error');
                    showToast(t('photo.write-location.error'), true);
                });
            });
            timeMatchBar.appendChild(writeBtn);

            imageContainer.appendChild(timeMatchBar);
        }

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

function showToast(msg, isError) {
    var el = document.createElement('div');
    el.className = 'photo-toast';
    if (isError) {
        el.classList.add('error');
    }
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(function () {
        el.style.transition = 'opacity .35s';
        el.style.opacity = '0';
    }, 2400);
    setTimeout(function () {
        if (el.parentNode) el.parentNode.removeChild(el);
    }, 2800);
}