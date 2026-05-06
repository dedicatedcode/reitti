class MapControls {
    constructor(element) {
        this.eventListeners = [];
        if (window.localStorage.getItem('is3d') === null) {
            localStorage.setItem('is3d', 'true');
            localStorage.setItem('displayTerrain', 'true');
            localStorage.setItem('displayBuildings', 'true');
        }
        this.element = document.getElementById(element);
        this.element.innerHTML = `
         <div class="map-controls-section">
            <button type="button" class="btn fab-btn map-controls-btn" id="map-controls-btn" title="${t('map.display-control.title')}">
                <i class="lni lni-map-marker-1"></i>
            </button>
            <div class="map-controls" id="map-controls">
                <label class="map-style-selector" title="Change Map style">
                    <i class="lni lni-layers-1"></i>
                    <select id="map-style-select">
                    </select>
                </label>
                <button type="button" class="btn map-control-btn active" id="toggle-3d-btn" title="${t('map.display-control.mode.3d.enabled.title')}">
                    <i class="lni lni-map-marker-1"></i>
                    <span>${t('map.display-control.mode.3d.enabled.text')}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-globeprojection-btn" title="${t('map.display-control.globe_projection.enabled.title')}">
                    <i class="lni lni-globe-1"></i>
                    <span>${t('map.display-control.globe_projection.enabled.text')}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-terrain-btn" title="${t('map.display-control.terrain.enabled.title')}">
                    <i class="lni lni-mountains-2"></i>
                    <span>${t('map.display-control.terrain.enabled.text')}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-buildings-btn" title="${t('map.display-control.buildings.enabled.title')}">
                    <i class="lni lni-buildings-1"></i>
                    <span>${t('map.display-control.buildings.enabled.text')}</span>
                </button>
                <button type="button" class="btn map-control-btn" id="toggle-satellite-btn" title="${t('map.display-control.satellite.disabled.title')}">
                    <i class="lni lni-globe-stand"></i>
                    <span>${t('map.display-control.satellite.disabled.text')}</span>
                </button>
                <button type="button" class="btn map-control-btn" id="compass-btn" title="${t('map.display-control.north-up.title')}">
                    <i class="lni lni-location-arrow-right"></i>
                    <span>${t('map.display-control.north-up.text')}</span>
                </button>
                
            </div>
        </div>
`
        this.rootElement = this.element.querySelector('.map-controls-section');

        this.toggle3dBtn = document.getElementById('toggle-3d-btn');
        this.toggleTerrainModeBtn = document.getElementById('toggle-terrain-btn');
        this.toggleBuildingsModeBtn = document.getElementById('toggle-buildings-btn');
        this.toggleSatelliteModeBtn = document.getElementById('toggle-satellite-btn');
        this.toggleGlobeProjectionModeBtn = document.getElementById('toggle-globeprojection-btn');
        this.compassBtn = document.getElementById('compass-btn');
        this.mapStyleSelect = document.getElementById('map-style-select');
        this.refreshMapStyleOptions();
        this._setup();
    }


    _setup() {
        this.mapStyleSelect.addEventListener('change', () => {
            MapRenderer.setActiveMapStyleId(this.mapStyleSelect.value);
            this.emit('selectionChanged', this.getState());
        });
        document.addEventListener('mapStylesChanged', (event) => {
            this.refreshMapStyleOptions(event.detail?.activeStyleId);
            this.emit('selectionChanged', this.getState());
        });

        this.toggle3dBtn.addEventListener('click', () => {
            const isEnabled = this.toggle3dBtn.classList.contains('active');
            if (isEnabled) {
                this._disable3d();
            } else {
                this._enable3d();
            }
            this.emit('selectionChanged', this.getState());
        });
        this.toggleTerrainModeBtn.addEventListener('click', () => {
            const isEnabled = this.toggleTerrainModeBtn.classList.contains('active');
            if (isEnabled) {
                this._disableTerrain();
            } else {
                this._enableTerrain();
            }
            this.emit('selectionChanged', this.getState());
        });
        this.toggleBuildingsModeBtn.addEventListener('click', () => {
            const isEnabled = this.toggleBuildingsModeBtn.classList.contains('active');
            if (isEnabled) {
                this._disableBuildings();
            } else {
                this._enableBuildings();
            }
            this.emit('selectionChanged', this.getState());
        });
        this.toggleSatelliteModeBtn.addEventListener('click', () => {
            const isEnabled = this.toggleSatelliteModeBtn.classList.contains('active');
            if (isEnabled) {
                this._disableSatellite();
            } else {
                this._enableSatellite();
            }
            this.emit('selectionChanged', this.getState());
        });
        this.toggleGlobeProjectionModeBtn.addEventListener('click', () => {
            const isEnabled = this.toggleGlobeProjectionModeBtn.classList.contains('active');
            if (isEnabled) {
                this._disableGlobeProjection();
            } else {
                this._enableGlobeProjection();
            }
            this.emit('selectionChanged', this.getState());
        });

        this.compassBtn.addEventListener('click', () => {
            this.emit('compassClicked');
        });

        const is3d = localStorage.getItem('is3d') === 'true';
        if (is3d) {
            this._enable3d();
        } else {
            this._disable3d();
        }
        const isTerrainEnabled = localStorage.getItem('displayTerrain') === 'true';
        if (isTerrainEnabled) {
            this._enableTerrain()
        } else {
            this._disableTerrain();
        }
        const isBuildingsEnabled = localStorage.getItem('displayBuildings') === 'true';
        if (isBuildingsEnabled) {
            this._enableBuildings()
        } else {
            this._disableBuildings();
        }
        const isSatelliteEnabled = localStorage.getItem('displaySatelliteView') === 'true';
        if (isSatelliteEnabled) {
            this._enableSatellite()
        } else {
            this._disableSatellite();
        }

        const isGlobeEnabled = localStorage.getItem('displayGlobeProjection') === 'true';
        if (isGlobeEnabled) {
            this._enableGlobeProjection()
        } else {
            this._disableGlobeProjection();
        }
    }

    mountTo(newContainer) {
        if (newContainer && this.rootElement) {
            newContainer.appendChild(this.rootElement);
        }
    }

    getState() {
        return {
            mapStyleId: this.mapStyleSelect.value,
            is3d: this.toggle3dBtn.classList.contains('active'),
            renderTerrain: this.toggleTerrainModeBtn.classList.contains('active'),
            renderBuildings: this.toggleBuildingsModeBtn.classList.contains('active'),
            renderSatelliteView: this.toggleSatelliteModeBtn.classList.contains('active'),
            renderGlobe: this.toggleGlobeProjectionModeBtn.classList.contains('active'),
        }
    }

    refreshMapStyleOptions(preferredStyleId = null) {
        const mapStyles = MapRenderer.getMapStyles();
        const storedMapStyleId = preferredStyleId || MapRenderer.getActiveMapStyleId();
        const selectedMapStyleId = mapStyles.some(style => style.id === storedMapStyleId)
            ? storedMapStyleId
            : MapRenderer.getDefaultMapStyleId();

        this.mapStyleSelect.innerHTML = mapStyles
            .map(style => `<option value="${this._escapeHtml(style.id)}"${style.id === selectedMapStyleId ? ' selected' : ''}>${this._escapeHtml(style.label)}</option>`)
            .join('');
        window.reittiActiveMapStyleId = selectedMapStyleId;
    }

    setMapStyleId(mapStyleId) {
        this.refreshMapStyleOptions(mapStyleId);
        window.reittiActiveMapStyleId = this.mapStyleSelect.value;
    }

    _enable3d() {
        const span = this.toggle3dBtn.querySelector('span');
        localStorage.setItem('is3d', 'true')
        this.toggle3dBtn.classList.add('active');
        span.textContent = t('map.display-control.mode.3d.enabled.text');
        this.toggle3dBtn.title = t('map.display-control.mode.3d.enabled.title');
    }

    _disable3d() {
        const span = this.toggle3dBtn.querySelector('span');
        localStorage.setItem('is3d', 'false')
        this.toggle3dBtn.classList.remove('active');
        span.textContent = t('map.display-control.mode.3d.disabled.text');
        this.toggle3dBtn.title = t('map.display-control.mode.3d.disabled.title');
    }

    _disableTerrain() {
        const span = this.toggleTerrainModeBtn.querySelector('span');
        localStorage.setItem('displayTerrain', 'false')
        this.toggleTerrainModeBtn.classList.remove('active');
        span.textContent = t('map.display-control.terrain.disabled.text');
        this.toggleTerrainModeBtn.title = t('map.display-control.terrain.disabled.title');
    }

    _enableTerrain() {
        const span = this.toggleTerrainModeBtn.querySelector('span');
        localStorage.setItem('displayTerrain', 'true')
        this.toggleTerrainModeBtn.classList.add('active');
        span.textContent = t('map.display-control.terrain.enabled.text');
        this.toggleTerrainModeBtn.title = t('map.display-control.terrain.enabled.title');
    }

    _enableBuildings() {
        const span = this.toggleBuildingsModeBtn.querySelector('span');
        localStorage.setItem('displayBuildings', 'true')
        this.toggleBuildingsModeBtn.classList.add('active');
        span.textContent = t('map.display-control.buildings.enabled.text');
        this.toggleBuildingsModeBtn.title = t('map.display-control.buildings.enabled.title');
    }

    _disableBuildings() {
        const span = this.toggleBuildingsModeBtn.querySelector('span');
        localStorage.setItem('displayBuildings', 'false')
        this.toggleBuildingsModeBtn.classList.remove('active');
        span.textContent = t('map.display-control.buildings.disabled.text');
        this.toggleBuildingsModeBtn.title = t('map.display-control.buildings.disabled.title');
    }

    _enableSatellite() {
        const span = this.toggleSatelliteModeBtn.querySelector('span');
        localStorage.setItem('displaySatelliteView', 'true')
        this.toggleSatelliteModeBtn.classList.add('active');
        span.textContent = t('map.display-control.satellite.enabled.text');
        this.toggleSatelliteModeBtn.title = t('map.display-control.satellite.enabled.title');
    }

    _disableSatellite() {
        const span = this.toggleSatelliteModeBtn.querySelector('span');
        localStorage.setItem('displaySatelliteView', 'false')
        this.toggleSatelliteModeBtn.classList.remove('active');
        span.textContent = t('map.display-control.satellite.disabled.text');
        this.toggleSatelliteModeBtn.title = t('map.display-control.satellite.disabled.title');
    }

    _enableGlobeProjection() {
        const span = this.toggleGlobeProjectionModeBtn.querySelector('span');
        localStorage.setItem('displayGlobeProjection', 'true')
        this.toggleGlobeProjectionModeBtn.classList.add('active');
        span.textContent = t('map.display-control.globe_projection.enabled.text');
        this.toggleGlobeProjectionModeBtn.title = t('map.display-control.globe_projection.enabled.title');
    }

    _disableGlobeProjection() {
        const span = this.toggleGlobeProjectionModeBtn.querySelector('span');
        localStorage.setItem('displayGlobeProjection', 'false')
        this.toggleGlobeProjectionModeBtn.classList.remove('active');
        span.textContent = t('map.display-control.globe_projection.disabled.text');
        this.toggleGlobeProjectionModeBtn.title = t('map.display-control.globe_projection.disabled.title');
    }

    _escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, character => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[character]));
    }

    _deepMerge(target, source) {
        let result = {...target};
        for (const key of Object.keys(source)) {
            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key]) && target[key] && typeof target[key] === 'object') {
                result[key] = this._deepMerge(target[key], source[key]);
            } else {
                result[key] = source[key];
            }
        }
        return result;
    }

    is3d() {
        return this.toggle3dBtn.classList.contains('active');
    }

    isTerrainEnabled() {
        return this.toggleTerrainModeBtn.classList.contains('active');
    }

    isBuildingsEnabled() {
        return this.toggleBuildingsModeBtn.classList.contains('active');
    }

    isSatelliteViewEnabled() {
        return this.toggleSatelliteModeBtn.classList.contains('active');
    }

    /** Events **/
    on(event, callback) {
        if (!this.eventListeners[event]) this.eventListeners[event] = [];
        this.eventListeners[event].push(callback);
    }

    off(event, callback) {
        if (!this.eventListeners[event]) return;
        this.eventListeners[event] =
            this.eventListeners[event].filter(cb => cb !== callback);
    }

    emit(event, data) {
        const list = this.eventListeners[event];
        if (!list || !list.length) return;
        list.forEach(cb => cb(data));
    }
}
