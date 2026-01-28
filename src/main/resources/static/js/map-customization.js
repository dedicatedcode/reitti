class MapCustomization {
    constructor(element, deckParams) {
        this.element = element;
        this.defaultParams = structuredClone(deckParams);
        this.deckParams = deckParams;
        this.eventListeners = [];
        this.element.innerHTML = `
    <h4 style="margin: 0 0 10px 0; text-align: center;">Deck.gl Parameters</h4>

    <!-- Tab Navigation -->
    <div style="display: flex; gap: 5px; margin-bottom: 10px; flex-wrap: wrap;">
        <button class="tab-btn active" data-tab="trips"
                style="flex: 1; padding: 5px; background: #444; border: none; color: white; cursor: pointer; border-radius: 3px;">
            Trips
        </button>
        <button class="tab-btn" data-tab="visits"
                style="flex: 1; padding: 5px; background: #444; border: none; color: white; cursor: pointer; border-radius: 3px;">
            Visits
        </button>
        <button class="tab-btn" data-tab="paths"
                style="flex: 1; padding: 5px; background: #444; border: none; color: white; cursor: pointer; border-radius: 3px;">
            Paths
        </button>
        <button class="tab-btn" data-tab="bundled"
                style="flex: 1; padding: 5px; background: #444; border: none; color: white; cursor: pointer; border-radius: 3px;">
            Bundled
        </button>
    </div>

    <!-- Trips Tab -->
    <div id="tab-trips" class="tab-content" style="display: block;">
        <div style="margin-bottom: 10px;">
            <label for="trail-length">Trail Length (seconds):</label>
            <input type="range" id="trail-length" min="100" max="10000" value="1800" step="100" style="width: 100%;">
            <span id="trail-length-value">1800</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="static-path-width">Static Path Width (pixels):</label>
            <input type="range" id="static-path-width" min="1" max="20" value="8" step="1" style="width: 100%;">
            <span id="static-path-width-value">8</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="static-path-opacity">Static Path Opacity (0-255):</label>
            <input type="range" id="static-path-opacity" min="0" max="255" value="255" step="5" style="width: 100%;">
            <span id="static-path-opacity-value">255</span>
        </div>
        
        <div style="margin-bottom: 10px;">
            <label for="path-width">Path Width (pixels):</label>
            <input type="range" id="path-width" min="1" max="20" value="8" step="1" style="width: 100%;">
            <span id="path-width-value">8</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="path-opacity">Path Opacity (0-255):</label>
            <input type="range" id="path-opacity" min="0" max="255" value="255" step="5" style="width: 100%;">
            <span id="path-opacity-value">255</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="comet-width">Comet Width (pixels):</label>
            <input type="range" id="comet-width" min="1" max="20" value="8" step="1" style="width: 100%;">
            <span id="comet-width-value">8</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="comet-opacity">Comet Opacity (0-255):</label>
            <input type="range" id="comet-opacity" min="0" max="255" value="255" step="5" style="width: 100%;">
            <span id="comet-opacity-value">255</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="shadow-width">Shadow Width (pixels):</label>
            <input type="range" id="shadow-width" min="1" max="20" value="6" step="1" style="width: 100%;">
            <span id="shadow-width-value">6</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="shadow-opacity">Shadow Opacity (0-255):</label>
            <input type="range" id="shadow-opacity" min="0" max="255" value="51" step="5" style="width: 100%;">
            <span id="shadow-opacity-value">51</span>
        </div>
    </div>

    <!-- Visits Tab -->
    <div id="tab-visits" class="tab-content" style="display: none;">
        <div style="margin-bottom: 10px;">
            <label for="visit-radius">Visit Radius (pixels):</label>
            <input type="range" id="visit-radius" min="5" max="50" value="50" step="1" style="width: 100%;">
            <span id="visit-radius-value">50</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="visit-opacity">Visit Opacity (0-255):</label>
            <input type="range" id="visit-opacity" min="0" max="255" value="140" step="1" style="width: 100%;">
            <span id="visit-opacity-value">140</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="visit-polygon-opacity">Polygon Opacity (0-255):</label>
            <input type="range" id="visit-polygon-opacity" min="0" max="255" value="140" step="1" style="width: 100%;">
            <span id="visit-polygon-opacity-value">140</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="visit-line-width">Line Width (pixels):</label>
            <input type="range" id="visit-line-width" min="1" max="10" value="3" step="1" style="width: 100%;">
            <span id="visit-line-width-value">3</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="visit-min-zoom">Minimum Zoon:</label>
            <input type="range" id="visit-min-zoom" min="1" max="22" value="15" step="1" style="width: 100%;">
            <span id="visit-min-zoom-value">3</span>
        </div>
    </div>

    <!-- Paths Tab -->
    <div id="tab-paths" class="tab-content" style="display: none;">
        <div style="margin-bottom: 10px;">
            <label for="path-width">Path Width (pixels):</label>
            <input type="range" id="path-width" min="1" max="20" value="4" step="1" style="width: 100%;">
            <span id="path-width-value">4</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="path-opacity">Path Opacity (0-255):</label>
            <input type="range" id="path-opacity" min="0" max="255" value="40" step="1" style="width: 100%;">
            <span id="path-opacity-value">40</span>
        </div>
        <div style="margin-bottom: 10px;">
            <label for="path-opacity_static">Path Opacity Static (0-255):</label>
            <input type="range" id="path-opacity_static" min="0" max="255" value="200" step="1" style="width: 100%;">
            <span id="path-opacity_static-value">200</span>
        </div>
    </div>

    <!-- Bundled Tab -->
    <div id="tab-bundled" class="tab-content" style="display: none;">
        <div style="margin-bottom: 10px;">
            <label for="bundled-precision">Bundled Precision (degrees):</label>
            <input type="range" id="bundled-precision" min="0.0001" max="0.1" value="0.0005" step="0.0001"
                   style="width: 100%;">
            <span id="bundled-precision-value">0.0001</span>
        </div>
        
        <div style="margin-bottom: 10px;">
            <label for="bundled-weight">Bundled Weight:</label>
            <input type="range" id="bundled-weight" min="0" max="100" value="0.5" step="0.1"
                   style="width: 100%;">
            <span id="bundled-weight-value">0.05</span>
        </div>

        
        <div style="margin-bottom: 10px;">
            <label for="bundled-static-path-width">Static Path Width (pixels):</label>
            <input type="range" id="bundled-static-path-width" min="0" max="20" value="6" step="0.1" style="width: 100%;">
            <span id="bundled-static-path-width-value">6</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="bundled-static-path-opacity">Static Path Opacity (0-255):</label>
            <input type="range" id="bundled-static-path-opacity" min="0" max="255" value="30" step="5" style="width: 100%;">
            <span id="bundled-static-path-opacity-value">30</span>
        </div>      
        
        <div style="margin-bottom: 10px;">
            <label for="bundled-path-width">Path Width (pixels):</label>
            <input type="range" id="bundled-path-width" min="0" max="20" value="6" step="0.1" style="width: 100%;">
            <span id="bundled-path-width-value">6</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="bundled-path-opacity">Path Opacity (0-255):</label>
            <input type="range" id="bundled-path-opacity" min="0" max="255" value="30" step="5" style="width: 100%;">
            <span id="bundled-path-opacity-value">30</span>
        </div>       
           
        
        <div style="margin-bottom: 10px;">
            <label for="bundled-comet-width">Comet Width (pixels):</label>
            <input type="range" id="bundled-comet-width" min="0" max="20" value="6" step="0.1" style="width: 100%;">
            <span id="bundled-comet-width-value">6</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="bundled-comet-opacity">Comet Opacity (0-255):</label>
            <input type="range" id="bundled-comet-opacity" min="0" max="255" value="30" step="5" style="width: 100%;">
            <span id="bundled-comet-opacity-value">30</span>
        </div>        
        
        <div style="margin-bottom: 10px;">
            <label for="bundled-shadow-width">Shadow Width (pixels):</label>
            <input type="range" id="bundled-shadow-width" min="0" max="20" value="6" step="0.1" style="width: 100%;">
            <span id="bundled-shadow-width-value">6</span>
        </div>

        <div style="margin-bottom: 10px;">
            <label for="bundled-shadow-opacity">Shadow Opacity (0-255):</label>
            <input type="range" id="bundled-shadow-opacity" min="0" max="255" value="30" step="5" style="width: 100%;">
            <span id="bundled-shadow-opacity-value">30</span>
        </div>        
        
    </div>

    <button id="reset-params"
            style="width: 100%; padding: 8px; background: #f44336; color: white; border: none; border-radius: 4px; cursor: pointer; margin-top: 10px;">
        Reset to Defaults
    </button>
`;
        this._init();
    }

    _updateSliderDisplays() {
        // Trips
        document.getElementById('trail-length-value').textContent = deckParams.trips.trailLength;
        document.getElementById('static-path-width-value').textContent = deckParams.trips.staticPathWidth;
        document.getElementById('static-path-opacity-value').textContent = deckParams.trips.staticPathOpacity;
        document.getElementById('path-width-value').textContent = deckParams.trips.pathWidth;
        document.getElementById('path-opacity-value').textContent = deckParams.trips.pathOpacity;
        document.getElementById('comet-width-value').textContent = deckParams.trips.cometWidth;
        document.getElementById('comet-opacity-value').textContent = deckParams.trips.cometOpacity;
        document.getElementById('shadow-width-value').textContent = deckParams.trips.shadowWidth;
        document.getElementById('shadow-opacity-value').textContent = deckParams.trips.shadowOpacity;

        // Visits
        document.getElementById('visit-radius-value').textContent = deckParams.visits.radius;
        document.getElementById('visit-opacity-value').textContent = deckParams.visits.opacity;
        document.getElementById('visit-polygon-opacity-value').textContent = deckParams.visits.polygonOpacity;
        document.getElementById('visit-line-width-value').textContent = deckParams.visits.lineWidth;
        document.getElementById('visit-min-zoom-value').textContent = deckParams.visits.minZoom;

        // Bundled
        document.getElementById('bundled-precision-value').textContent = deckParams.bundled.precision;
        document.getElementById('bundled-weight-value').textContent = deckParams.bundled.weight;
        document.getElementById('bundled-static-path-width-value').textContent = deckParams.bundled.staticPathWidth;
        document.getElementById('bundled-static-path-opacity-value').textContent = deckParams.bundled.staticPathOpacity;

        document.getElementById('bundled-path-width-value').textContent = deckParams.bundled.pathWidth;
        document.getElementById('bundled-path-opacity-value').textContent = deckParams.bundled.pathOpacity;
        document.getElementById('bundled-comet-width-value').textContent = deckParams.bundled.cometWidth;
        document.getElementById('bundled-comet-opacity-value').textContent = deckParams.bundled.cometOpacity;
        document.getElementById('bundled-shadow-width-value').textContent = deckParams.bundled.shadowWidth;
        document.getElementById('bundled-shadow-opacity-value').textContent = deckParams.bundled.shadowOpacity;

    }

    _init() {
        const tabButtons = document.querySelectorAll('.tab-btn');
        const tabContents = document.querySelectorAll('.tab-content');

        tabButtons.forEach(button => {
            button.addEventListener('click', function () {
                const tabName = this.getAttribute('data-tab');

                // Update active button
                tabButtons.forEach(btn => btn.classList.remove('active'));
                this.classList.add('active');

                // Show corresponding content
                tabContents.forEach(content => {
                    content.style.display = content.id === `tab-${tabName}` ? 'block' : 'none';
                });
            });
        });

        document.getElementById('trail-length').value = deckParams.trips.trailLength;
        document.getElementById('comet-width').value = deckParams.trips.cometWidth;
        document.getElementById('comet-opacity').value = deckParams.trips.cometOpacity;
        document.getElementById('shadow-width').value = deckParams.trips.shadowWidth;
        document.getElementById('shadow-opacity').value = deckParams.trips.shadowOpacity;
        document.getElementById('static-path-width-value').value = deckParams.trips.staticPathWidth;
        document.getElementById('static-path-opacity-value').value = deckParams.trips.staticPathOpacity;
        document.getElementById('path-width-value').value = deckParams.trips.pathWidth;
        document.getElementById('path-opacity-value').value = deckParams.trips.pathOpacity;

        // Visits
        document.getElementById('visit-radius').value = deckParams.visits.radius;
        document.getElementById('visit-opacity').value = deckParams.visits.opacity;
        document.getElementById('visit-polygon-opacity').value = deckParams.visits.polygonOpacity;
        document.getElementById('visit-line-width').value = deckParams.visits.lineWidth;
        document.getElementById('visit-min-zoom').value = deckParams.visits.minZoom;

        // Bundled
        document.getElementById('bundled-precision').value = deckParams.bundled.precision;
        document.getElementById('bundled-weight').value = deckParams.bundled.weight;
        document.getElementById('bundled-path-width').value = deckParams.bundled.pathWidth;
        document.getElementById('bundled-path-opacity').value = deckParams.bundled.pathOpacity;
        document.getElementById('bundled-static-path-width').value = deckParams.bundled.staticPathWidth;
        document.getElementById('bundled-static-path-opacity').value = deckParams.bundled.staticPathOpacity;
        document.getElementById('bundled-comet-width').value = deckParams.bundled.cometWidth;
        document.getElementById('bundled-comet-opacity').value = deckParams.bundled.cometOpacity;
        document.getElementById('bundled-shadow-width').value = deckParams.bundled.shadowWidth;
        document.getElementById('bundled-shadow-opacity').value = deckParams.bundled.shadowOpacity;

        // Event listeners for all sliders
        document.getElementById('trail-length').oninput = (e) => {
            deckParams.trips.trailLength = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('path-width').oninput = (e) => {
            deckParams.trips.pathWidth = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('path-opacity').oninput = (e) => {
            deckParams.trips.pathOpacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };
        document.getElementById('static-path-width').oninput = (e) => {
            deckParams.trips.staticPathWidth = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('static-path-opacity').oninput = (e) => {
            deckParams.trips.staticPathOpacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('comet-width').oninput = (e) => {
            deckParams.trips.cometWidth = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('comet-opacity').oninput = (e) => {
            deckParams.trips.cometOpacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('shadow-width').oninput = (e) => {
            deckParams.trips.shadowWidth = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});

        };

        document.getElementById('shadow-opacity').oninput = (e) => {
            deckParams.trips.shadowOpacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});

        };

        document.getElementById('visit-radius').oninput = (e) => {
            deckParams.visits.radius = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});

        };

        document.getElementById('visit-opacity').oninput = (e) => {
            deckParams.visits.opacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('visit-polygon-opacity').oninput = (e) => {
            deckParams.visits.polygonOpacity = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('visit-line-width').oninput = (e) => {
            deckParams.visits.lineWidth = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('visit-min-zoom').oninput = (e) => {
            deckParams.visits.minZoom = parseInt(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-precision').oninput = (e) => {
            deckParams.bundled.precision = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams, field: 'bundled.precision'});
        };

        document.getElementById('bundled-weight').oninput = (e) => {
            deckParams.bundled.weight = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams, field: 'bundled.weight'});
        };

        document.getElementById('bundled-path-width').oninput = (e) => {
            deckParams.bundled.pathWidth = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-path-opacity').oninput = (e) => {
            deckParams.bundled.pathOpacity = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };
        document.getElementById('bundled-static-path-width').oninput = (e) => {
            deckParams.bundled.staticPathWidth = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-static-path-opacity').oninput = (e) => {
            deckParams.bundled.staticPathOpacity = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-comet-width').oninput = (e) => {
            deckParams.bundled.cometWidth = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-comet-opacity').oninput = (e) => {
            deckParams.bundled.cometOpacity = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-shadow-width').oninput = (e) => {
            deckParams.bundled.shadowWidth = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        document.getElementById('bundled-shadow-opacity').oninput = (e) => {
            deckParams.bundled.shadowOpacity = parseFloat(e.target.value);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        // Reset button
        document.getElementById('reset-params').oninput = (e) => {
            this.deckParams = structuredClone(this.defaultParams);
            this._updateSliderDisplays();
            this.emit('update-deck-params', {params: this.deckParams});
        };

        this._updateSliderDisplays()

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