class MapControls {
    constructor(element, locale) {
        this.eventListeners = [];
        const defaultLocale = {
            map: {
                displayControl: {
                    mode: {
                        "2D": {
                            text: '2D',
                            title: 'Switch to 2D view',
                        },
                        "3D": {
                            text: '3D',
                            title: 'Switch to 3D view',
                        }
                    },
                    terrain: {
                        enable: {
                            text: 'Enable Terrain',
                            title: 'Enable Terrain',
                        },
                        disable: {
                            text: 'Disable Terrain',
                            title: 'Disable Terrain',
                        }
                    }
                }
            }
        };
        if (window.localStorage.getItem('is3d') === null) {
            localStorage.setItem('is3d', 'true');
            localStorage.setItem('displayTerrain', 'true');
            localStorage.setItem('displayBuildings', 'true');
        }
        this.locale = this._deepMerge(defaultLocale, locale || {});
        this.element = document.getElementById(element);
        this.element.innerHTML = `
         <div class="map-controls-section">
            <button type="button" class="btn fab-btn map-controls-btn" id="map-controls-btn" title="${this.locale.map.displayControl.title}">
                <i class="lni lni-map-marker-1"></i>
            </button>
            <div class="map-controls" id="map-controls">
                <button type="button" class="btn map-control-btn active" id="toggle-3d-btn" title="${this.locale.map.displayControl.mode["3D"].title}">
                    <i class="lni lni-map-marker-1"></i>
                    <span>${this.locale.map.displayControl.mode["3D"].text}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-globeprojection-btn" title="${this.locale.map.displayControl.globeProjection.disable.title}">
                    <i class="lni lni-globe-1"></i>
                    <span>${this.locale.map.displayControl.globeProjection.disable.text}</span>
                </button>
                <button type="button" class="btn map-control-btn" id="compass-btn" title="${this.locale.map.displayControl.northUp.title}">
                    <i class="lni lni-location-arrow-right"></i>
                    <span>${this.locale.map.displayControl.northUp.text}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-terrain-btn" title="${this.locale.map.displayControl.terrain.disable.title}">
                    <i class="lni lni-mountains-2"></i>
                    <span>${this.locale.map.displayControl.terrain.disable.text}</span>
                </button>
                <button type="button" class="btn map-control-btn active" id="toggle-buildings-btn" title="${this.locale.map.displayControl.buildings.disable.title}">
                    <i class="lni lni-buildings-1"></i>
                    <span>${this.locale.map.displayControl.buildings.disable.text}</span>
                </button>
                <button type="button" class="btn map-control-btn" id="toggle-satellite-btn" title="${this.locale.map.displayControl.satellite.disable.title}">
                    <i class="lni lni-globe-stand"></i>
                    <span>${this.locale.map.displayControl.satellite.disable.text}</span>
                </button>
            </div>
        </div>
`

        this.toggle3dBtn = document.getElementById('toggle-3d-btn');
        this.toggleTerrainModeBtn = document.getElementById('toggle-terrain-btn');
        this.toggleBuildingsModeBtn = document.getElementById('toggle-buildings-btn');
        this.toggleSatelliteModeBtn = document.getElementById('toggle-satellite-btn');
        this.toggleGlobeProjectionModeBtn = document.getElementById('toggle-globeprojection-btn');
        this.compassBtn = document.getElementById('compass-btn');
        this._setup();
    }

    _setup() {
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

    getState() {
        return {
            is3d: this.toggle3dBtn.classList.contains('active'),
            renderTerrain: this.toggleTerrainModeBtn.classList.contains('active'),
            renderBuildings: this.toggleBuildingsModeBtn.classList.contains('active'),
            renderSatelliteView: this.toggleSatelliteModeBtn.classList.contains('active'),
            renderGlobe: this.toggleGlobeProjectionModeBtn.classList.contains('active'),
        }
    }
    _enable3d() {
        const span = this.toggle3dBtn.querySelector('span');
        localStorage.setItem('is3d', 'true')
        this.toggle3dBtn.classList.add('active');
        span.textContent = this.locale.map.displayControl.mode["2D"].text;
        this.toggle3dBtn.title = this.locale.map.displayControl.mode["3D"].title;
    }

    _disable3d() {
        const span = this.toggle3dBtn.querySelector('span');
        localStorage.setItem('is3d', 'false')
        this.toggle3dBtn.classList.remove('active');
        span.textContent = this.locale.map.displayControl.mode["3D"].text;
        this.toggle3dBtn.title = this.locale.map.displayControl.mode["2D"].title;
    }

    _disableTerrain() {
        const span = this.toggleTerrainModeBtn.querySelector('span');
        localStorage.setItem('displayTerrain', 'false')
        this.toggleTerrainModeBtn.classList.remove('active');
        span.textContent = this.locale.map.displayControl.terrain.enable.text;
        this.toggleTerrainModeBtn.title = this.locale.map.displayControl.terrain.enable.title;
    }

    _enableTerrain() {
        const span = this.toggleTerrainModeBtn.querySelector('span');
        localStorage.setItem('displayTerrain', 'true')
        this.toggleTerrainModeBtn.classList.add('active');
        span.textContent = this.locale.map.displayControl.terrain.disable.text;
        this.toggleTerrainModeBtn.title = this.locale.map.displayControl.terrain.disable.title;
    }

    _enableBuildings() {
        const span = this.toggleBuildingsModeBtn.querySelector('span');
        localStorage.setItem('displayBuildings', 'true')
        this.toggleBuildingsModeBtn.classList.add('active');
        span.textContent = this.locale.map.displayControl.buildings.disable.text;
        this.toggleBuildingsModeBtn.title = this.locale.map.displayControl.buildings.disable.title;
    }

    _disableBuildings() {
        const span = this.toggleBuildingsModeBtn.querySelector('span');
        localStorage.setItem('displayBuildings', 'false')
        this.toggleBuildingsModeBtn.classList.remove('active');
        span.textContent = this.locale.map.displayControl.buildings.enable.text;
        this.toggleBuildingsModeBtn.title = this.locale.map.displayControl.buildings.enable.title;
    }

    _enableSatellite() {
        const span = this.toggleSatelliteModeBtn.querySelector('span');
        localStorage.setItem('displaySatelliteView', 'true')
        this.toggleSatelliteModeBtn.classList.add('active');
        span.textContent = this.locale.map.displayControl.satellite.disable.text;
        this.toggleSatelliteModeBtn.title = this.locale.map.displayControl.satellite.disable.title;
    }

    _disableSatellite() {
        const span = this.toggleSatelliteModeBtn.querySelector('span');
        localStorage.setItem('displaySatelliteView', 'false')
        this.toggleSatelliteModeBtn.classList.remove('active');
        span.textContent = this.locale.map.displayControl.satellite.enable.text;
        this.toggleSatelliteModeBtn.title = this.locale.map.displayControl.satellite.enable.title;
    }

    _enableGlobeProjection() {
        const span = this.toggleGlobeProjectionModeBtn.querySelector('span');
        localStorage.setItem('displayGlobeProjection', 'true')
        this.toggleGlobeProjectionModeBtn.classList.add('active');
        span.textContent = this.locale.map.displayControl.globeProjection.disable.text;
        this.toggleGlobeProjectionModeBtn.title = this.locale.map.displayControl.globeProjection.disable.title;
    }

    _disableGlobeProjection() {
        const span = this.toggleGlobeProjectionModeBtn.querySelector('span');
        localStorage.setItem('displayGlobeProjection', 'false')
        this.toggleGlobeProjectionModeBtn.classList.remove('active');
        span.textContent = this.locale.map.displayControl.globeProjection.enable.text;
        this.toggleGlobeProjectionModeBtn.title = this.locale.map.displayControl.globeProjection.enable.title;
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
