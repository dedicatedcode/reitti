(function () {
    'use strict';

    const MONTH_NAMES = [
        'January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'
    ];

    const state = {
        map: null,
        deckOverlay: null,
        selectedUserId: null,
        selectedDeviceId: null,
        sliderMonths: [],
        currentSliderIndex: 0,
        isLoading: false,
        visitedCells: new Map(),
        coverageAreas: [],
        h3Layer: null,
        areaBoundsLayer: null,
        tooltipEl: null,
        currentZoom: 3,
        h3AggregationCache: new Map(),
        zoomDebounceId: null,
        userColor: [51, 136, 255],
    };

    function hexToRgb(hex) {
        const r = parseInt(hex.slice(1, 3), 16);
        const g = parseInt(hex.slice(3, 5), 16);
        const b = parseInt(hex.slice(5, 7), 16);
        return [r, g, b];
    }

    function initCoverage() {
        const settings = window.userSettings || {};
        state.selectedUserId = null;

        createTooltipElement();
        initMap(settings);
        initFabButtons();
        initTimeSlider(settings);
        initUserSelection();

        setSliderToToday();
    }

    function createTooltipElement() {
        state.tooltipEl = document.createElement('div');
        state.tooltipEl.className = 'coverage-tooltip hidden';
        state.tooltipEl.style.display = 'none';
        document.body.appendChild(state.tooltipEl);
    }

    function initMap(settings) {
        const style = getMapStyle();
        const map = new maplibregl.Map({
            container: 'coverage-map',
            style: style,
            center: [settings.homeLongitude || 24.9384, settings.homeLatitude || 60.1699],
            zoom: 3,
            pitch: 0,
            bearing: 0,
            projection: { type: 'mercator' },
            attributionControl: false,
            renderWorldCopies: false,
            interleaved: true,
        });

        const deckOverlay = new deck.MapboxOverlay({ layers: [] });
        map.addControl(deckOverlay);

        state.map = map;
        state.deckOverlay = deckOverlay;

        map.on('zoom', () => {
            const zoom = map.getZoom();
            if (zoom !== state.currentZoom) {
                state.currentZoom = zoom;
                if (state.zoomDebounceId) clearTimeout(state.zoomDebounceId);
                state.zoomDebounceId = setTimeout(() => renderLayers(), 100);
            }
        });

        map.on('load', () => {
            loadCoverageData();
        });
    }

    function getMapStyle() {
        if (window.reittiCustomMapStyles && window.reittiCustomMapStyles.length > 0) {
            const activeId = window.reittiActiveMapStyleId || window.reittiCustomMapStyles[0].id;
            const style = window.reittiCustomMapStyles.find(s => s.id === activeId) || window.reittiCustomMapStyles[0];
            if (style.url) return style.url;
            if (style.json) return JSON.parse(style.json);
            if (style.tiles) {
                return {
                    version: 8,
                    sources: {
                        'raster-tiles': {
                            type: 'raster',
                            tiles: [style.tiles],
                            tileSize: 256,
                            attribution: style.attribution || '',
                        }
                    },
                    layers: [{
                        id: 'raster-tiles-layer',
                        type: 'raster',
                        source: 'raster-tiles',
                        minzoom: 0,
                        maxzoom: 22,
                    }]
                };
            }
        }
        return {
            version: 8,
            sources: {
                'dark-matter': {
                    type: 'raster',
                    tiles: ['https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'],
                    tileSize: 256,
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/">CARTO</a>',
                }
            },
            layers: [{
                id: 'dark-matter-layer',
                type: 'raster',
                source: 'dark-matter',
                minzoom: 0,
                maxzoom: 22,
            }]
        };
    }

    function initFabButtons() {
        document.getElementById('coverage-prev-month').addEventListener('click', () => {
            if (state.currentSliderIndex > 0) {
                state.currentSliderIndex--;
                updateFromSlider();
            }
        });
        document.getElementById('coverage-next-month').addEventListener('click', () => {
            if (state.currentSliderIndex < state.sliderMonths.length - 1) {
                state.currentSliderIndex++;
                updateFromSlider();
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
            if (e.key === 'ArrowLeft') {
                e.preventDefault();
                if (state.currentSliderIndex > 0) {
                    state.currentSliderIndex--;
                    updateFromSlider();
                }
            } else if (e.key === 'ArrowRight') {
                e.preventDefault();
                if (state.currentSliderIndex < state.sliderMonths.length - 1) {
                    state.currentSliderIndex++;
                    updateFromSlider();
                }
            }
        });
    }

    function initTimeSlider(settings) {
        const now = new Date();
        const earliest = settings.earliestData ? new Date(settings.earliestData) : new Date(2015, 0, 1);
        const startYear = earliest.getFullYear();
        const startMonth = earliest.getMonth();
        const endYear = now.getFullYear();
        const endMonth = now.getMonth();

        state.sliderMonths = [];
        for (let y = startYear; y <= endYear; y++) {
            const mStart = (y === startYear) ? startMonth : 0;
            const mEnd = (y === endYear) ? endMonth : 11;
            for (let m = mStart; m <= mEnd; m++) {
                state.sliderMonths.push({ year: y, month: m });
            }
        }

        if (state.sliderMonths.length === 0) {
            state.sliderMonths.push({ year: now.getFullYear(), month: now.getMonth() });
        }

        const slider = document.getElementById('coverage-time-slider');
        slider.min = 0;
        slider.max = state.sliderMonths.length - 1;
        slider.value = state.sliderMonths.length - 1;

        slider.addEventListener('input', () => {
            state.currentSliderIndex = parseInt(slider.value);
            updateMonthDisplay();
        });

        slider.addEventListener('change', () => {
            state.currentSliderIndex = parseInt(slider.value);
            updateFromSlider();
        });

        renderSliderLabels();
        state.currentSliderIndex = state.sliderMonths.length - 1;
        updateMonthDisplay();
    }

    function renderSliderLabels() {
        const labelsEl = document.getElementById('time-slider-labels');
        labelsEl.innerHTML = '';
        const months = state.sliderMonths;
        if (months.length <= 12) {
            months.forEach(m => {
                const span = document.createElement('span');
                span.textContent = MONTH_NAMES[m.month].substring(0, 3) + " '" + String(m.year).slice(-2);
                labelsEl.appendChild(span);
            });
        } else {
            const years = new Set(months.map(m => m.year));
            const sorted = Array.from(years).sort((a, b) => a - b);
            sorted.forEach(y => {
                const span = document.createElement('span');
                span.textContent = String(y);
                labelsEl.appendChild(span);
            });
        }
    }

    function setSliderToToday() {
        const now = new Date();
        const currentYear = now.getFullYear();
        const currentMonth = now.getMonth();

        let idx = state.sliderMonths.length - 1;
        for (let i = state.sliderMonths.length - 1; i >= 0; i--) {
            const m = state.sliderMonths[i];
            if (m.year === currentYear && m.month === currentMonth) {
                idx = i;
                break;
            }
        }

        state.currentSliderIndex = idx;
        const slider = document.getElementById('coverage-time-slider');
        slider.value = idx;
        updateMonthDisplay();
    }

    function updateMonthDisplay() {
        const m = state.sliderMonths[state.currentSliderIndex];
        document.getElementById('coverage-month-display').textContent =
            MONTH_NAMES[m.month] + ' ' + m.year;
        const slider = document.getElementById('coverage-time-slider');
        if (parseInt(slider.value) !== state.currentSliderIndex) {
            slider.value = state.currentSliderIndex;
        }

        const sliderContainer = document.getElementById('time-slider-container');
        const pct = state.sliderMonths.length > 1
            ? (state.currentSliderIndex / (state.sliderMonths.length - 1)) * 100
            : 100;
        sliderContainer.style.setProperty('--slider-pct', pct + '%');
        const sliderEl = document.getElementById('coverage-time-slider');
        sliderEl.style.background =
            'linear-gradient(to right, var(--color-highlight) 0%, var(--color-highlight) ' + pct + '%, rgba(255,255,255,0.1) ' + pct + '%, rgba(255,255,255,0.1) 100%)';
    }

    function updateFromSlider() {
        updateMonthDisplay();
        loadCoverageData();
    }

    function getCurrentRange() {
        const first = state.sliderMonths[0];
        const m = state.sliderMonths[state.currentSliderIndex];
        const start = new Date(Date.UTC(first.year, first.month, 1));
        const end = new Date(Date.UTC(m.year, m.month + 1, 0, 23, 59, 59, 999));
        return {
            start: start.toISOString().substring(0, 10),
            end: end.toISOString().substring(0, 10),
        };
    }

    async function loadCoverageData() {
        if (state.isLoading) return;
        state.isLoading = true;
        showLoading(true);

        try {
            const range = getCurrentRange();
            const userId = getSelectedUserId();
            const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

            const url = '/api/v2/coverage/cells/' + userId +
                '?start=' + range.start +
                '&end=' + range.end +
                '&timezone=' + encodeURIComponent(tz);

            console.log('Coverage: fetching', url);
            const resp = await fetch(url);
            if (!resp.ok) {
                const errText = await resp.text();
                console.error('Coverage API error:', resp.status, errText);
                return;
            }

            const cells = await resp.json();
            console.log('Coverage: got', cells.length, 'cells');
            processCells(cells);
            state.h3AggregationCache.clear();

            await loadCoverageAreas();
            console.log('Coverage: rendering', state.visitedCells.size, 'visited cells');
            renderLayers();
            renderAreaInfo();
        } catch (e) {
            console.error('Error loading coverage data:', e);
        } finally {
            state.isLoading = false;
            showLoading(false);
        }
    }

    function getSelectedUserId() {
        if (state.selectedUserId) return state.selectedUserId;
        const activeUser = document.querySelector('.coverage-sidebar .user-header.active');
        if (activeUser && activeUser.dataset.userId) {
            return activeUser.dataset.userId;
        }
        const anyUser = document.querySelector('.coverage-sidebar .user-header');
        if (anyUser && anyUser.dataset.userId) {
            return anyUser.dataset.userId;
        }
        return window.currentUserId || '1';
    }

    function getSelectedDeviceId() {
        if (state.selectedDeviceId) return state.selectedDeviceId;
        const activeDevice = document.querySelector('.coverage-sidebar .device-header.active');
        if (activeDevice && activeDevice.dataset.deviceId) {
            return activeDevice.dataset.deviceId;
        }
        return null;
    }

    function processCells(cells) {
        state.visitedCells.clear();

        cells.forEach(c => {
            if (!c.hexagon) return;
            const h3Hex = BigInt(c.hexagon).toString(16);
            if (!state.visitedCells.has(h3Hex)) {
                state.visitedCells.set(h3Hex, []);
            }
            state.visitedCells.get(h3Hex).push({
                time: c.time,
                count: c.count,
            });
        });

        state.visitedCells.forEach(buckets =>
            buckets.sort((a, b) => a.time.localeCompare(b.time))
        );
    }

    function getTargetResolution(zoom) {
        if (zoom < 5) return 2;
        if (zoom < 7) return 3;
        if (zoom < 8) return 4;
        if (zoom < 9) return 5;
        if (zoom < 10) return 6;
        if (zoom < 11) return 8;
        if (zoom < 13) return 9;
        if (zoom < 14) return 10;
        if (zoom < 15) return 11;
        return 12;
    }

    function getAggregatedCells() {
        const targetRes = getTargetResolution(state.currentZoom);

        if (state.h3AggregationCache.has(targetRes)) {
            return state.h3AggregationCache.get(targetRes);
        }

        state.h3AggregationCache.clear();

        const aggregated = new Map();

        state.visitedCells.forEach((buckets, hexStr) => {
            const bigIntVal = BigInt('0x' + hexStr);
            const lower = Number(bigIntVal & 0xFFFFFFFFn);
            const upper = Number((bigIntVal >> 32n) & 0xFFFFFFFFn);
            const h3Index = h3.splitLongToH3Index(lower, upper);

            const parentIndex = h3.cellToParent(h3Index, targetRes);

            if (!aggregated.has(parentIndex)) {
                aggregated.set(parentIndex, 0);
            }
            const total = buckets.reduce((sum, b) => sum + b.count, 0);
            aggregated.set(parentIndex, aggregated.get(parentIndex) + total);
        });

        state.h3AggregationCache.set(targetRes, aggregated);
        return aggregated;
    }

    function renderLayers() {
        if (state.visitedCells.size === 0) {
            state.deckOverlay.setProps({ layers: [] });
            return;
        }

        const aggregatedCells = getAggregatedCells();
        const hexagons = Array.from(aggregatedCells.keys());

        const h3Layer = new deck.H3HexagonLayer({
            id: 'coverage-h3',
            data: hexagons,
            getHexagon: hex => hex,
            extruded: false,
            getFillColor: hex => {
                const count = aggregatedCells.get(hex) || 0;
                const alpha = Math.min(255, count * 2.5);
                return [...state.userColor, alpha];
            },
            stroked: true,
            getLineColor: [...state.userColor, 255],
            getLineWidth: 1.5,
            lineWidthMinPixels: 0.5,
            pickable: true,
            coverage: 0.90,
            opacity: 0.9,
            updateTriggers: {
                getFillColor: [state.currentZoom, state.currentSliderIndex, state.selectedUserId, state.selectedDeviceId, state.userColor],
            },
            onHover: (info) => {
                handleCellHover(info);
            },
        });

        state.h3Layer = h3Layer;
        updateDeckLayers();
    }

    function updateDeckLayers() {
        const layers = [];
        if (state.h3Layer) layers.push(state.h3Layer);
        state.deckOverlay.setProps({ layers });
    }

    function handleCellHover(info) {
        if (!state.tooltipEl) return;

        if (info.picked && info.object != null) {
            const buckets = state.visitedCells.get(info.object);
            const count = buckets ? buckets.reduce((sum, b) => sum + b.count, 0) : 0;
            const latest = buckets && buckets.length > 0 ? buckets[buckets.length - 1].time : null;

            state.tooltipEl.innerHTML = 'Visits: <b>' + count + '</b>';
            if (latest) {
                state.tooltipEl.innerHTML += '<br>Last: ' + new Date(latest).toLocaleDateString();
            }
            state.tooltipEl.style.display = 'block';
            state.tooltipEl.style.left = (info.x + 12) + 'px';
            state.tooltipEl.style.top = (info.y - 12) + 'px';
        } else {
            state.tooltipEl.style.display = 'none';
        }
    }

    async function loadCoverageAreas() {
        try {
            const deviceId = state.selectedDeviceId;
            const url = deviceId
                ? '/api/v2/coverage/device/' + deviceId
                : '/api/v2/coverage';
            const resp = await fetch(url);
            if (resp.ok) {
                state.coverageAreas = await resp.json();
            }
        } catch (e) {
            console.warn('Failed to load coverage areas:', e);
        }
    }

    function renderAreaInfo() {
        let panel = document.getElementById('coverage-area-info');
        if (!panel) {
            panel = document.createElement('div');
            panel.id = 'coverage-area-info';
            panel.className = 'coverage-area-info';
            document.body.appendChild(panel);
        }

        const areas = state.coverageAreas || [];
        if (areas.length === 0) {
            panel.style.display = 'none';
            return;
        }

        panel.style.display = 'block';
        const sorted = [...areas]
            .filter(a => a.coveragePercentage > 0)
            .sort((a, b) => b.coveragePercentage - a.coveragePercentage);

        if (sorted.length === 0) {
            panel.style.display = 'none';
            return;
        }

        panel.innerHTML = sorted.slice(0, 8).map(a => {
            const pct = a.coveragePercentage.toFixed(1);
            return '<div class="area-row">' +
                '<span class="area-name">' + escapeHtml(a.name) + '</span>' +
                '<span class="area-pct">' + pct + '%</span>' +
                '<div class="area-bar"><div class="area-bar-fill" style="width:' + Math.min(100, a.coveragePercentage) + '%"></div></div>' +
                '</div>';
        }).join('');
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function initUserSelection() {
        window.selectCoverageUser = selectCoverageUser;
        window.selectCoverageDevice = selectCoverageDevice;

        const activeUser = document.querySelector('.coverage-sidebar .user-header.active');
        if (activeUser && activeUser.dataset.baseColor) {
            state.userColor = hexToRgb(activeUser.dataset.baseColor);
        }
    }

    function selectCoverageUser(userId, el) {
        state.selectedUserId = userId;
        state.selectedDeviceId = null;

        document.querySelectorAll('.coverage-sidebar .user-header').forEach(h => h.classList.remove('active'));
        document.querySelectorAll('.coverage-sidebar .device-header').forEach(h => h.classList.remove('active'));

        el.classList.add('active');
        if (el.dataset.baseColor) {
            state.userColor = hexToRgb(el.dataset.baseColor);
        }
        loadCoverageData();
    }

    function selectCoverageDevice(userId, deviceId, el) {
        state.selectedUserId = userId;
        state.selectedDeviceId = deviceId;

        document.querySelectorAll('.coverage-sidebar .user-header').forEach(h => h.classList.remove('active'));
        document.querySelectorAll('.coverage-sidebar .device-header').forEach(h => h.classList.remove('active'));

        el.classList.add('active');
        if (el.dataset.deviceColor) {
            state.userColor = hexToRgb(el.dataset.deviceColor);
        }
        loadCoverageData();
    }

    function showLoading(show) {
        const overlay = document.getElementById('coverage-loading-overlay');
        if (overlay) {
            if (show) overlay.classList.remove('hidden');
            else overlay.classList.add('hidden');
        }
    }

    window.initCoverage = initCoverage;
})();
