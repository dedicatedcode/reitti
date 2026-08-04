(function () {
    'use strict';

    const MONTH_NAMES = [
        t('month.1'),
        t('month.2'),
        t('month.3'),
        t('month.4'),
        t('month.5'),
        t('month.6'),
        t('month.7'),
        t('month.8'),
        t('month.9'),
        t('month.10'),
        t('month.11'),
        t('month.12')
    ];

    const ADMIN_LEVEL_LABELS = {
        2: 'Countries',
        4: 'States',
        6: 'Counties',
        8: 'Cities',
        9: 'Districts',
        10: 'Neighborhoods',
    };

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
        boundaryLayer: null,
        hoverBoundaryLayer: null,
        selectedOsmId: null,
        selectedAreaInfo: null,
        hoveredOsmId: null,
        selectedResolution: null,
        boundaryCache: new Map(),
        tooltipEl: null,
        currentZoom: 3,
        h3AggregationCache: new Map(),
        zoomDebounceId: null,
        viewportDebounceId: null,
        userColor: [51, 136, 255],
        treeSections: {2: true, 4: true, 6: true, 8: true, 9: true, 10: true},
        lastViewportBounds: null,
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
            try {
                map.setProjection({ type: 'globe' });
            } catch (e) {
                console.warn('Could not enable globe projection:', e);
            }
            loadCoverageData();
        });

        map.on('moveend', () => {
            if (state.viewportDebounceId) clearTimeout(state.viewportDebounceId);
            state.viewportDebounceId = setTimeout(() => {
                const bounds = map.getBounds();
                if (bounds) {
                    state.lastViewportBounds = {
                        minLat: bounds.getSouth(),
                        minLon: bounds.getWest(),
                        maxLat: bounds.getNorth(),
                        maxLon: bounds.getEast(),
                    };
                    loadFilteredAreas();
                }
            }, 300);
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

            const resp = await fetch(url);
            if (!resp.ok) {
                const errText = await resp.text();
                console.error('Coverage API error:', resp.status, errText);
                return;
            }

            const cells = await resp.json();
            processCells(cells);
            state.h3AggregationCache.clear();

            await loadFilteredAreas();
            renderLayers();
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
        if (state.selectedResolution !== null) return state.selectedResolution;
        if (zoom < 4) return 3;
        if (zoom < 5) return 4;
        if (zoom < 6) return 5;
        if (zoom < 7) return 6;
        if (zoom < 8) return 7;
        if (zoom < 9) return 8;
        if (zoom < 10) return 9;
        if (zoom < 12) return 10;
        if (zoom < 14) return 11;
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

            const total = buckets.reduce((sum, b) => sum + b.count, 0);
            const latestTime = buckets.length > 0 ? buckets[buckets.length - 1].time : null;

            if (!aggregated.has(parentIndex)) {
                aggregated.set(parentIndex, { count: 0, latestTime: null });
            }
            const entry = aggregated.get(parentIndex);
            entry.count += total;
            if (latestTime && (!entry.latestTime || latestTime > entry.latestTime)) {
                entry.latestTime = latestTime;
            }
        });

        state.h3AggregationCache.set(targetRes, aggregated);
        return aggregated;
    }

    function renderLayers() {
        const layers = [];

        if (state.visitedCells.size > 0) {
            const aggregatedCells = getAggregatedCells();
            state.aggregatedCells = aggregatedCells;
            const hexagons = Array.from(aggregatedCells.keys());

            const h3Layer = new deck.H3HexagonLayer({
                id: 'coverage-h3',
                data: hexagons,
                getHexagon: hex => hex,
                extruded: false,
                getFillColor: hex => {
                    const entry = aggregatedCells.get(hex);
                    const count = entry ? entry.count : 0;
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
            layers.push(h3Layer);
        }

        if (state.hoverBoundaryLayer) {
            layers.push(state.hoverBoundaryLayer);
        }

        if (state.boundaryLayer) {
            layers.push(state.boundaryLayer);
        }

        state.deckOverlay.setProps({ layers });
    }

    function handleCellHover(info) {
        if (!state.tooltipEl) return;

        if (info.picked && info.object != null) {
            const entry = state.aggregatedCells ? state.aggregatedCells.get(info.object) : null;
            const count = entry ? entry.count : 0;
            const latest = entry ? entry.latestTime : null;

            state.tooltipEl.innerHTML = t('coverage.cell.tooltip.header')+ ' <b>' + count + '</b>';
            if (latest) {
                state.tooltipEl.innerHTML += `<br>${t('coverage.cell.tooltip.last-visited')} ${new Date(latest).toLocaleDateString()}`;
            }
            state.tooltipEl.style.display = 'block';
            state.tooltipEl.style.left = (info.x + 12) + 'px';
            state.tooltipEl.style.top = (info.y - 12) + 'px';
        } else {
            state.tooltipEl.style.display = 'none';
        }
    }

    async function loadFilteredAreas() {
        try {
            const deviceId = state.selectedDeviceId;
            let url;

            if (state.lastViewportBounds) {
                const b = state.lastViewportBounds;
                url = '/api/v2/coverage/areas' +
                    '?minLat=' + b.minLat +
                    '&minLon=' + b.minLon +
                    '&maxLat=' + b.maxLat +
                    '&maxLon=' + b.maxLon;
            } else {
                url = '/api/v2/coverage';
            }

            const resp = await fetch(url);
            if (resp.ok) {
                state.coverageAreas = await resp.json();
                renderTree();
            }
        } catch (e) {
            console.warn('Failed to load filtered areas:', e);
        }
    }

    function renderTree() {
        debugger
        const content = document.getElementById('tree-content');
        if (!content) return;

        const areas = state.coverageAreas || [];

        if (areas.length === 0) {
            content.innerHTML = `<div class="tree-empty">${t('coverage.tree.no.data')}</div>`;
            return;
        }

        const bestPerOsm = new Map();
        areas.forEach(a => {
            const existing = bestPerOsm.get(a.osmId);
            if (!existing || a.coveragePercentage > existing.coveragePercentage) {
                bestPerOsm.set(a.osmId, a);
            }
        });

        const grouped = new Map();
        bestPerOsm.forEach(a => {
            const level = a.adminLevel >= 0 ? a.adminLevel : 99;
            if (!grouped.has(level)) grouped.set(level, []);
            grouped.get(level).push(a);
        });

        const order = [2, 4, 6, 8, 9, 10];
        const fallbackOrder = [];
        grouped.forEach((v, k) => {
            if (!order.includes(k)) fallbackOrder.push(k);
        });
        fallbackOrder.sort((a, b) => a - b);
        let html = '';

        if (state.selectedOsmId && !bestPerOsm.has(state.selectedOsmId)) {
            const pinned = state.selectedAreaInfo || findAreaInfo(state.selectedOsmId);
            if (pinned) {
                const pct = pinned.coveragePercentage.toFixed(1);
                html += '<div class="tree-section pinned">';
                html += '<div class="tree-section-items open" data-level="pinned">';
                html += '<div class="tree-item selected pinned-item" data-osm-id="' + pinned.osmId + '" data-resolution="' + pinned.resolution + '">';
                html += '<span class="tree-item-name" title="' + escapeHtml(pinned.name) + '">' + escapeHtml(pinned.name) + '</span>';
                html += '<div class="tree-item-bar"><div class="tree-item-bar-fill" style="width:' + Math.min(100, pinned.coveragePercentage) + '%"></div></div>';
                html += '<span class="tree-item-pct">' + pct + '%</span>';
                html += '</div>';
                html += '</div>';
                html += '</div>';
            }
        }

        const allOrder = [...order, ...fallbackOrder];
        allOrder.forEach(level => {
            const levelAreas = grouped.get(level) || [];
            if (levelAreas.length === 0) return;

            const sorted = [...levelAreas]
                .filter(a => a.coveragePercentage > 0)
                .sort((a, b) => b.coveragePercentage - a.coveragePercentage);

            if (sorted.length === 0) return;

            const label = ADMIN_LEVEL_LABELS[level] || t('coverage.admin_level.label.fallback', [level]);
            const isOpen = state.treeSections[level] !== false;
            const count = sorted.length;

            html += '<div class="tree-section">';
            html += '<div class="tree-section-header' + (isOpen ? ' open' : '') + '" data-level="' + level + '">';
            html += '<span>' + label + ' (' + count + ')</span>';
            html += '<span class="tree-chevron">&#9654;</span>';
            html += '</div>';
            html += '<div class="tree-section-items' + (isOpen ? ' open' : '') + '" data-level="' + level + '">';

            sorted.forEach(a => {
                const pct = a.coveragePercentage.toFixed(1);
                const isSelected = a.osmId === state.selectedOsmId;
                html += '<div class="tree-item' + (isSelected ? ' selected' : '') + '" data-osm-id="' + a.osmId + '" data-resolution="' + a.resolution + '">';
                html += '<span class="tree-item-name" title="' + escapeHtml(a.name) + '">' + escapeHtml(a.name) + '</span>';
                html += '<div class="tree-item-bar"><div class="tree-item-bar-fill" style="width:' + Math.min(100, a.coveragePercentage) + '%"></div></div>';
                html += '<span class="tree-item-pct">' + pct + '%</span>';
                html += '</div>';
            });

            html += '</div>';
            html += '</div>';
        });

        if (!html) {
            content.innerHTML = `<div class="tree-empty">${t('coverage.tree.no.data')}</div>`;
        } else {
            content.innerHTML = html;
            attachTreeEvents(content);
        }
    }

    function attachTreeEvents(container) {
        container.querySelectorAll('.tree-section-header').forEach(header => {
            header.addEventListener('click', () => {
                const level = parseInt(header.dataset.level);
                state.treeSections[level] = !(state.treeSections[level] !== false);
                header.classList.toggle('open');
                const items = container.querySelector('.tree-section-items[data-level="' + level + '"]');
                if (items) items.classList.toggle('open');
            });
        });

        container.querySelectorAll('.tree-item').forEach(item => {
            item.addEventListener('click', () => {
                const osmId = parseInt(item.dataset.osmId);
                const resolution = parseInt(item.dataset.resolution) || null;
                handleTreeClick(osmId, resolution);
            });
            item.addEventListener('mouseenter', () => {
                const osmId = parseInt(item.dataset.osmId);
                showHoverBoundary(osmId);
            });
            item.addEventListener('mouseleave', () => {
                const osmId = parseInt(item.dataset.osmId);
                hideHoverBoundary(osmId);
            });
        });
    }

        async function showHoverBoundary(osmId) {
        if (osmId === state.selectedOsmId) return;

        state.hoveredOsmId = osmId;

        const cached = state.boundaryCache.get(osmId);
        if (cached) {
            state.hoverBoundaryLayer = cached.layer;
            renderLayers();
            return;
        }

        try {
            const resp = await fetch('/api/v2/coverage/boundary/' + osmId);
            if (!resp.ok || state.hoveredOsmId !== osmId) return;

            const data = await resp.json();
            if (!data.geojson || state.hoveredOsmId !== osmId) return;

            const feature = { type: 'Feature', geometry: JSON.parse(data.geojson) };
            const layer = new deck.GeoJsonLayer({
                id: 'coverage-hover-boundary',
                data: { type: 'FeatureCollection', features: [feature] },
                filled: true,
                getFillColor: [245, 222, 179, 30],
                getLineColor: [245, 222, 179, 180],
                getLineWidth: 1.5,
                lineWidthMinPixels: 0.5,
                pickable: false,
            });

            state.boundaryCache.set(osmId, { layer, feature, bbox: data.bbox });
            if (state.hoveredOsmId === osmId) {
                state.hoverBoundaryLayer = layer;
                renderLayers();
            }
        } catch (e) {
            console.warn('Failed to load hover boundary:', e);
        }
    }

    function hideHoverBoundary(osmId) {
        state.hoveredOsmId = null;
        if (state.hoverBoundaryLayer) {
            state.hoverBoundaryLayer = null;
            renderLayers();
        }
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    async function handleTreeClick(osmId, resolution) {
        state.hoveredOsmId = null;
        state.hoverBoundaryLayer = null;

        if (state.selectedOsmId === osmId) {
            removeBoundaryLayer();
            state.selectedOsmId = null;
            state.selectedAreaInfo = null;
            state.selectedResolution = null;
            state.h3AggregationCache.clear();
            renderLayers();
            highlightTreeItem();
            return;
        }

        try {
            let feature, bbox;
            const cached = state.boundaryCache.get(osmId);
            if (cached) {
                feature = cached.feature;
                bbox = cached.bbox;
            } else {
                const resp = await fetch('/api/v2/coverage/boundary/' + osmId);
                if (!resp.ok) {
                    console.warn('No boundary for OSM ID ' + osmId);
                    return;
                }
                const data = await resp.json();
                if (!data.geojson) return;
                feature = { type: 'Feature', geometry: JSON.parse(data.geojson) };
                bbox = data.bbox;
                state.boundaryCache.set(osmId, { layer: null, feature, bbox });
            }

            state.boundaryLayer = new deck.GeoJsonLayer({
                id: 'coverage-boundary',
                data: {type: 'FeatureCollection', features: [feature]},
                filled: true,
                getFillColor: [245, 222, 179, 50],
                getLineColor: [245, 222, 179, 255],
                getLineWidth: 2,
                lineWidthMinPixels: 1,
                pickable: false,
            });
            state.selectedOsmId = osmId;
            state.selectedResolution = resolution;
            state.selectedAreaInfo = findAreaInfo(osmId);
            state.h3AggregationCache.clear();

            renderLayers();
            highlightTreeItem();

            if (bbox) {
                state.map.fitBounds(
                    [[bbox.minLon, bbox.minLat], [bbox.maxLon, bbox.maxLat]],
                    { padding: 40, duration: 800, maxZoom: 14 }
                );
            }
        } catch (e) {
            console.warn('Failed to load boundary:', e);
        }
    }

    function removeBoundaryLayer() {
        state.boundaryLayer = null;
        state.hoverBoundaryLayer = null;
        state.selectedOsmId = null;
        state.selectedAreaInfo = null;
        state.hoveredOsmId = null;
        state.selectedResolution = null;
        state.h3AggregationCache.clear();
        renderLayers();
    }

    function findAreaInfo(osmId) {
        return state.coverageAreas.find(a => a.osmId === osmId) || state.selectedAreaInfo;
    }

    function highlightTreeItem() {
        document.querySelectorAll('.tree-item').forEach(el => {
            const itemOsmId = parseInt(el.dataset.osmId);
            if (itemOsmId === state.selectedOsmId) {
                el.classList.add('selected');
            } else {
                el.classList.remove('selected');
            }
        });
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
        state.selectedOsmId = null;
        state.selectedAreaInfo = null;
        state.hoveredOsmId = null;
        state.selectedResolution = null;
        state.boundaryLayer = null;
        state.hoverBoundaryLayer = null;
        state.h3AggregationCache.clear();

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
        state.selectedOsmId = null;
        state.selectedAreaInfo = null;
        state.hoveredOsmId = null;
        state.selectedResolution = null;
        state.boundaryLayer = null;
        state.hoverBoundaryLayer = null;
        state.h3AggregationCache.clear();

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
