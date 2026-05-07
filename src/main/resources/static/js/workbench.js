/* ============================================================
   JOURNEY WEAVER — v11: Full File with Select-Mode Dots
   ============================================================ */

let _idCounter = 0;
const nextId = () => ++_idCounter;

const T_TOTAL_BOUNDS = 365 * 24 * 3600 * 1000;
const T_START_REAL = new Date('2026-05-07T00:00:00Z').getTime();
const ORIGIN = {lat: 37.7694, lng: -122.4562};

const DeviceSources = {
    'null':   {key: null,     name: 'Phone',          color: '#b89adc'},
    'garmin': {key: 'garmin', name: 'Garmin Fenix 7', color: '#8fc5e6'},
    'gopro':  {key: 'gopro',  name: 'GoPro Hero 12',  color: '#b89adc'}
};
const GOLD = '#d9a441';
const colorOf = (sid) => sid == null ? GOLD : (DeviceSources[sid]?.color || '#8fc5e6');
const nameOf  = (sid) => DeviceSources[sid == null ? 'null' : sid]?.name || 'Phone';

// Normalized per-source cache: sourceId -> array of {id, sourceId, t, lat, lng, alt}
const SourceData = new Map();
// Final derived timeline: [{id, t, lat, lng, alt, sourceId, sourceRefId, interpolated?}]
let FinalTimeline = [];

/* ============================================================
   GeoJSON ADAPTERS
   ============================================================ */
function featureToPoint(feature) {
    const g = feature.geometry;
    const p = feature.properties || {};
    const [lng, lat, alt] = g.coordinates;
    return {
        id: p.id,
        sourceId: p.sourceId ?? null,
        t: p.t,
        lat, lng,
        alt: alt ?? p.alt ?? 0
    };
}

function pointToFeature(p) {
    return {
        type: 'Feature',
        properties: {id: p.id, sourceId: p.sourceId, t: p.t, alt: p.alt},
        geometry: {type: 'Point', coordinates: [p.lng, p.lat, p.alt]}
    };
}

/* ============================================================
   EDIT STORE — persistent changeset (authoritative for server)
   ============================================================
   - patches:        copy ops — [{tStart, tEnd, sourceId, seq}]
   - deletedPoints:  Set<"sourceId|id"> — deleted backend points
   - movedPoints:    Map<"sourceId|id" -> {lat, lng}>
   - hydratedRanges: cache-only, ranges currently loaded
   ============================================================ */
const EditStore = {
    patches: [],
    deletedPoints: new Set(),
    movedPoints: new Map()
};
const hydratedRanges = [];

const pointKey = (sourceId, id) => `${sourceId ?? 'null'}|${id}`;

function mergeIntoRanges(ranges, tStart, tEnd) {
    if (tEnd < tStart) [tStart, tEnd] = [tEnd, tStart];
    ranges.push({tStart, tEnd});
    ranges.sort((a, b) => a.tStart - b.tStart);
    for (let i = 0; i < ranges.length - 1;) {
        if (ranges[i].tEnd >= ranges[i + 1].tStart) {
            ranges[i].tEnd = Math.max(ranges[i].tEnd, ranges[i + 1].tEnd);
            ranges.splice(i + 1, 1);
        } else i++;
    }
}

function subtractRanges(ranges, tStart, tEnd) {
    const out = [];
    let cursor = tStart;
    for (const r of ranges) {
        if (r.tEnd < cursor) continue;
        if (r.tStart > tEnd) break;
        if (r.tStart > cursor) out.push({tStart: cursor, tEnd: Math.min(tEnd, r.tStart)});
        cursor = Math.max(cursor, r.tEnd);
        if (cursor >= tEnd) break;
    }
    if (cursor < tEnd) out.push({tStart: cursor, tEnd});
    return out;
}

function patchOwnerAt(t) {
    let owner = null, bestSeq = -1;
    for (const p of EditStore.patches) {
        if (t >= p.tStart && t <= p.tEnd && p.seq > bestSeq) {
            owner = p.sourceId;
            bestSeq = p.seq;
        }
    }
    return {owner, hasPatch: bestSeq >= 0};
}

/* ============================================================
   VIEWPORT
   ============================================================ */
let viewportDuration = 45 * 60 * 1000;
let viewportStartT = T_TOTAL_BOUNDS / 2;

const W = {
    tool: 'inspect',
    selectedDevice: 'garmin',
    selected: new Set(),
    patch: {tStart: viewportStartT + 10 * 60000, tEnd: viewportStartT + 15 * 60000},
    hoverT: null,
    isLoading: false
};

const tToXf = (t, w) => ((t - viewportStartT) / viewportDuration) * w;
const xToTf = (x, w) => viewportStartT + (x / w) * viewportDuration;

function fmtClock(ms) {
    return new Date(T_START_REAL + ms).toISOString().substring(11, 19);
}
function fmtClockShort(ms) {
    return new Date(T_START_REAL + ms).toISOString().substring(11, 16);
}
function fmtDateCompact(ms) {
    const d = new Date(T_START_REAL + ms);
    return `${d.getMonth()+1}/${d.getDate()} ` + d.toISOString().substring(11, 16);
}

/* ============================================================
   MOCK BACKEND — returns GeoJSON FeatureCollection
   ============================================================ */
const BackendMockAPI = {
    async fetchDevicePoints(sourceId, startT, endT, resolutionMs) {
        return new Promise(resolve => {
            setTimeout(() => {
                const features = [];
                const posNoise = sourceId === 'garmin' ? 0.00009 : (sourceId === 'gopro' ? 0.00005 : 0.00025);
                const altNoise = sourceId === 'garmin' ? 1.5 : (sourceId === 'gopro' ? 1 : 4);
                const tStep = Math.max(1000, resolutionMs);
                const _genStartT = Math.max(0, startT);
                const _genEndT = Math.min(T_TOTAL_BOUNDS, endT);
                const gridStart = Math.ceil(_genStartT / tStep) * tStep;

                for (let t = gridStart; t <= _genEndT; t += tStep) {
                    if (t % 18000000 < 9000000 && sourceId === 'null') continue;
                    const tn = t / (30 * 60 * 1000);
                    const theta = tn * Math.PI * 2.6;
                    const lat = ORIGIN.lat + Math.sin(theta)*0.0085 + tn*0.0025 - 0.001;
                    const lng = ORIGIN.lng + Math.cos(theta*0.75)*0.012 + Math.sin(theta*1.5)*0.003;
                    const alt = 55 + 45*Math.sin(tn*Math.PI*2.2) + 8*Math.sin(tn*Math.PI*14);
                    const r = Math.abs(Math.sin((t ^ (sourceId ? sourceId.length : 9))*10000));

                    features.push({
                        type: 'Feature',
                        properties: {
                            id: `${sourceId || 'phone'}_${t}`,
                            sourceId: sourceId,
                            t,
                            alt: alt + (r - 0.5) * altNoise
                        },
                        geometry: {
                            type: 'Point',
                            coordinates: [
                                lng + (r - 0.5) * posNoise,
                                lat + (r - 0.5) * posNoise,
                                alt + (r - 0.5) * altNoise
                            ]
                        }
                    });
                }
                resolve({type: 'FeatureCollection', features});
            }, 350);
        });
    }
};

let fetchDebounceId = null;

function mergeSourceCacheFromGeoJSON(sourceId, featureCollection) {
    const existing = SourceData.get(sourceId) || [];
    const byId = new Map(existing.map(p => [p.id, p]));
    for (const f of featureCollection.features) {
        const p = featureToPoint(f);
        const mv = EditStore.movedPoints.get(pointKey(sourceId, p.id));
        byId.set(p.id, mv ? {...p, lat: mv.lat, lng: mv.lng} : p);
    }
    const merged = Array.from(byId.values()).sort((a, b) => a.t - b.t);
    SourceData.set(sourceId, merged);
}

function rebuildFinalInRange(tStart, tEnd) {
    FinalTimeline = FinalTimeline.filter(p => p.t < tStart || p.t > tEnd);

    for (const [sid, arr] of SourceData.entries()) {
        for (const p of arr) {
            if (p.t < tStart || p.t > tEnd) continue;
            if (EditStore.deletedPoints.has(pointKey(sid, p.id))) continue;

            const {owner, hasPatch} = patchOwnerAt(p.t);
            const desiredOwner = hasPatch ? owner : null;
            if (sid !== desiredOwner) continue;

            FinalTimeline.push({
                id: `auto_${sid ?? 'null'}_${p.id}`,
                t: p.t, lat: p.lat, lng: p.lng, alt: p.alt,
                sourceId: sid, sourceRefId: p.id
            });
        }
    }

    FinalTimeline.sort((a, b) => a.t - b.t);
    FinalTimeline = fillGaps(FinalTimeline, 6000);
    mergeIntoRanges(hydratedRanges, tStart, tEnd);
}

// Keep ±24h around viewport in memory
const KEEP_WINDOW_MS = 24 * 3600 * 1000;

function evictFarData() {
    const keepStart = viewportStartT - KEEP_WINDOW_MS;
    const keepEnd = viewportStartT + viewportDuration + KEEP_WINDOW_MS;

    for (const [sid, arr] of SourceData.entries()) {
        const filtered = arr.filter(p => p.t >= keepStart && p.t <= keepEnd);
        if (filtered.length !== arr.length) SourceData.set(sid, filtered);
    }

    const before = FinalTimeline.length;
    FinalTimeline = FinalTimeline.filter(p => p.t >= keepStart && p.t <= keepEnd);

    const trimmed = [];
    for (const r of hydratedRanges) {
        if (r.tEnd < keepStart || r.tStart > keepEnd) continue;
        trimmed.push({
            tStart: Math.max(keepStart, r.tStart),
            tEnd: Math.min(keepEnd, r.tEnd)
        });
    }
    hydratedRanges.length = 0;
    hydratedRanges.push(...trimmed);

    const liveIds = new Set(FinalTimeline.map(p => p.id));
    for (const id of W.selected) if (!liveIds.has(id)) W.selected.delete(id);

    if (before !== FinalTimeline.length) {
        console.debug(`[evict] dropped ${before - FinalTimeline.length} points outside ±24h`);
    }
}

async function loadViewportData() {
    W.isLoading = true;
    const zi = document.getElementById('zoomIndicator');
    if (zi && !zi.innerHTML.includes('Loading')) {
        zi.innerHTML += ` <span style="color:#d9a441">[Loading…]</span>`;
    }

    const pad = viewportDuration * 0.5;
    const startT = Math.max(0, viewportStartT - pad);
    const endT = Math.min(T_TOTAL_BOUNDS, viewportStartT + viewportDuration + pad);

    const needed = subtractRanges(hydratedRanges, startT, endT);
    if (!needed.length && FinalTimeline.length > 0) {
        W.isLoading = false;
        refreshAll();
        return;
    }

    let resolutionMs = 1500;
    if (viewportDuration > 24 * 3600 * 1000) resolutionMs = 5 * 60 * 1000;
    else if (viewportDuration > 4 * 3600 * 1000) resolutionMs = 60 * 1000;

    try {
        const firstBoot = FinalTimeline.length === 0 && !histStartSnapshot;

        for (const range of needed) {
            const [phoneFC, garminFC, goproFC] = await Promise.all([
                BackendMockAPI.fetchDevicePoints(null,     range.tStart, range.tEnd, resolutionMs),
                BackendMockAPI.fetchDevicePoints('garmin', range.tStart, range.tEnd, resolutionMs),
                BackendMockAPI.fetchDevicePoints('gopro',  range.tStart, range.tEnd, Math.max(500, resolutionMs/2))
            ]);
            mergeSourceCacheFromGeoJSON(null,     phoneFC);
            mergeSourceCacheFromGeoJSON('garmin', garminFC);
            mergeSourceCacheFromGeoJSON('gopro',  goproFC);
            rebuildFinalInRange(range.tStart, range.tEnd);

            if (firstBoot) {
                histStartSnapshot = {
                    finalCount: FinalTimeline.length,
                    sources: {
                        phone:  phoneFC.features.length,
                        garmin: garminFC.features.length,
                        gopro:  goproFC.features.length
                    }
                };
            }
        }

        evictFarData();
    } catch (err) {
        console.error("Data load failed", err);
    } finally {
        W.isLoading = false;
        refreshAll();
    }
}

function triggerDebouncedDataLoad() {
    if (fetchDebounceId) clearTimeout(fetchDebounceId);
    fetchDebounceId = setTimeout(() => { loadViewportData(); }, 250);
}

function fillGaps(points, threshold) {
    const out = [];
    const sorted = [...points].sort((a, b) => a.t - b.t);
    for (let i = 0; i < sorted.length; i++) {
        out.push(sorted[i]);
        if (i < sorted.length - 1) {
            const a = sorted[i], b = sorted[i + 1];
            const dt = b.t - a.t;
            if (dt > threshold && dt < threshold * 20) {
                const n = Math.ceil(dt / threshold) - 1;
                for (let j = 1; j <= n; j++) {
                    const f = j / (n + 1);
                    out.push({
                        id: `interp_${nextId()}`, t: a.t + dt * f,
                        lat: a.lat + (b.lat - a.lat) * f,
                        lng: a.lng + (b.lng - a.lng) * f,
                        alt: a.alt + (b.alt - a.alt) * f,
                        sourceId: a.sourceId, interpolated: true
                    });
                }
            }
        }
    }
    return out;
}

/* ============================================================
   HISTORY (client-side undo; server uses EditStore directly)
   ============================================================ */
const History = [];
let histStartSnapshot = null;

function pushAction(action) {
    action.id = nextId();
    action.at = new Date().toISOString();
    action.seq = History.length + 1;
    action.undone = false;
    History.push(action);
    renderHistory();
}

function undoAction(actionId) {
    const a = History.find(x => x.id === actionId);
    if (!a || a.undone) return;

    if (a.type === 'copy' && a._inverse) {
        const idx = EditStore.patches.indexOf(a._inverse.patchRecord);
        if (idx >= 0) EditStore.patches.splice(idx, 1);
        rebuildFinalInRange(a._inverse.patchRecord.tStart, a._inverse.patchRecord.tEnd);
    } else if (a.type === 'delete' && a._inverse) {
        for (const key of a._inverse.deletedKeys || []) {
            EditStore.deletedPoints.delete(key);
        }
        rebuildFinalInRange(a._inverse.tStart, a._inverse.tEnd);
    } else if (a.type === 'move' && a._inverse) {
        for (const g of a._inverse.group) {
            const fp = FinalTimeline.find(p => p.id === g.pointId);
            if (fp) { fp.lat = g.from.lat; fp.lng = g.from.lng; }
            if (g.sourceRefId != null && g.sourceId != null && g.sourceFrom) {
                const s = SourceData.get(g.sourceId)?.find(pp => pp.id === g.sourceRefId);
                if (s) { s.lat = g.sourceFrom.lat; s.lng = g.sourceFrom.lng; }
                EditStore.movedPoints.delete(pointKey(g.sourceId, g.sourceRefId));
            }
        }
    }

    a.undone = true;
    W.selected.clear();
    toast(`Reverted: ${a.shortDesc}`);
    renderHistory();
    refreshAll();
}

function clearHistory() {
    if (!History.length) return;
    if (!confirm('Clear action history? (Won\'t revert changes — just clears the log.)')) return;
    History.length = 0;
    renderHistory();
}

function renderHistory() {
    const list = document.getElementById('histList');
    const badge = document.getElementById('commitBadge');
    const count = document.getElementById('histCount');
    const active = History.filter(a => !a.undone).length;
    count.textContent = active;
    badge.textContent = active;
    badge.style.display = active ? '' : 'none';

    if (!History.length) {
        list.innerHTML = `<div class="history-empty">No actions yet.<br>Copy a patch or move points to begin.</div>`;
        return;
    }

    const iconFor = (t) => ({
        copy: `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#8fc5e6" stroke-width="2.3"><path d="M9 2h9l3 3v13a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z"/><path d="M17 2v4h4"/></svg>`,
        delete: `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#e07a6b" stroke-width="2.3"><path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>`,
        move: `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#d9a441" stroke-width="2.3"><path d="M12 2v20M2 12h20M5 9l-3 3 3 3M19 9l3 3-3 3M9 5l3-3 3 3M9 19l3 3 3-3"/></svg>`
    })[t] || '';

    list.innerHTML = History.slice().reverse().map(a => `
    <div class="history-item type-${a.type} ${a.undone ? 'undone' : ''}">
      <div class="history-icon">${iconFor(a.type)}</div>
      <div class="history-body">
        <div class="history-desc">${a.desc}</div>
        <div class="history-meta">#${String(a.seq).padStart(3, '0')} · ${fmtAgo(a.at)}${a.undone ? ' · reverted' : ''}</div>
      </div>
      <button class="history-undo" data-undo-id="${a.id}" ${a.undone ? 'disabled' : ''} title="Undo">
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M3 7v6h6"/><path d="M3 13a9 9 0 1 0 3-7.7L3 8"/></svg>
      </button>
    </div>`).join('');

    list.querySelectorAll('[data-undo-id]').forEach(btn => {
        btn.addEventListener('click', () => undoAction(btn.dataset.undoId));
    });
}

function fmtAgo(iso) {
    const ms = Date.now() - new Date(iso).getTime();
    if (ms < 5000) return 'just now';
    if (ms < 60000) return Math.floor(ms / 1000) + 's ago';
    return Math.floor(ms / 60000) + 'm ago';
}

setInterval(() => { if (History.length) renderHistory(); }, 15000);
document.getElementById('histClear').addEventListener('click', clearHistory);

/* ============================================================
   MAPLIBRE
   ============================================================ */
const map = new maplibregl.Map({
    container: 'map',
    style: window.contextPath + '/map/reitti.json?ts=' + new Date().getTime(),
    center: [window.userSettings.homeLongitude, window.userSettings.homeLatitude], zoom: 13.7
});
map.addControl(new maplibregl.NavigationControl({showCompass: false}), 'top-left');

map.on('move', () => {
    const c = map.getCenter();
    document.getElementById('zoomIndicator').textContent =
        `Zoom: ${map.getZoom().toFixed(1)}, ${c.lat.toFixed(3)}, ${c.lng.toFixed(3)}`;
});

map.on('load', () => {
    if (map.getLayer('hillshading')) map.removeLayer('hillshading');
    if (map.getLayer('building-3d')) map.removeLayer('building-3d');
    map.setTerrain(null);

    const mapContainer = document.getElementById('map');
    mapContainer.classList.remove('is-loading');
    mapContainer.classList.add('is-loaded');

    map.addSource('final-line', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'final-line-casing', type: 'line', source: 'final-line',
        paint: {'line-color': '#0a1320', 'line-width': 8, 'line-opacity': 0.75},
        layout: {'line-cap': 'round', 'line-join': 'round'}
    });
    map.addLayer({
        id: 'final-line', type: 'line', source: 'final-line',
        paint: {'line-color': ['get', 'color'], 'line-width': 4.5, 'line-opacity': 0.98},
        layout: {'line-cap': 'round', 'line-join': 'round'}
    });

    map.addSource('time-labels', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'time-labels', type: 'symbol', source: 'time-labels',
        layout: {
            'text-field': ['get', 'label'], 'text-size': 11,
            'text-font': ['Open Sans Semibold'], 'text-offset': [0, -1.4],
            'text-allow-overlap': false, 'text-padding': 4
        },
        paint: {
            'text-color': '#ece6d6', 'text-halo-color': '#0a1320',
            'text-halo-width': 2.5, 'text-halo-blur': 0.5
        }
    });

    map.addSource('final-pts', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'final-pts', type: 'circle', source: 'final-pts',
        paint: {
            'circle-radius': ['case', ['get', 'selected'], 8, 5],
            'circle-color':  ['case', ['get', 'selected'], '#f2c470', ['get', 'color']],
            'circle-stroke-width': 2,
            'circle-stroke-color': ['case', ['get', 'selected'], '#fff5d6', '#0a1320'],
            'circle-blur': ['case', ['get', 'selected'], 0.08, 0]
        }
    });

    map.addSource('cand-line', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'cand-line', type: 'line', source: 'cand-line',
        paint: {
            'line-color': ['get', 'color'], 'line-width': 2.2,
            'line-dasharray': [2, 2], 'line-opacity': 0.85
        }
    });

    map.addSource('cand-active', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'cand-active', type: 'line', source: 'cand-active',
        paint: {'line-color': ['get', 'color'], 'line-width': 4, 'line-opacity': 1}
    });

    map.addSource('scrub-pt', {type: 'geojson', data: emptyFC()});
    map.addLayer({
        id: 'scrub-halo', type: 'circle', source: 'scrub-pt',
        paint: {
            'circle-radius': 22, 'circle-color': '#fff',
            'circle-opacity': 0.3, 'circle-blur': 0.8
        }
    });
    map.addLayer({
        id: 'scrub-pt', type: 'circle', source: 'scrub-pt',
        paint: {
            'circle-radius': 8, 'circle-color': '#e07a6b',
            'circle-stroke-width': 3, 'circle-stroke-color': '#ffffff',
            'circle-pitch-alignment': 'map'
        }
    });

    loadViewportData();
    setupMapInteractions();
});

function emptyFC() { return {type: 'FeatureCollection', features: []}; }

function buildFinalLineFC() {
    const features = [];
    if (FinalTimeline.length < 2) return emptyFC();
    let coords = [[FinalTimeline[0].lng, FinalTimeline[0].lat]];
    let currentSid = FinalTimeline[0].sourceId;
    for (let i = 1; i < FinalTimeline.length; i++) {
        const p = FinalTimeline[i];
        if (p.sourceId !== currentSid) {
            coords.push([p.lng, p.lat]);
            features.push({
                type: 'Feature', properties: {color: colorOf(currentSid)},
                geometry: {type: 'LineString', coordinates: coords}
            });
            coords = [[p.lng, p.lat]];
            currentSid = p.sourceId;
        } else coords.push([p.lng, p.lat]);
    }
    if (coords.length > 1) features.push({
        type: 'Feature',
        properties: {color: colorOf(currentSid)},
        geometry: {type: 'LineString', coordinates: coords}
    });
    return {type: 'FeatureCollection', features};
}

function buildTimeLabelsFC() {
    if (FinalTimeline.length < 2) return emptyFC();
    const features = [];
    const stepMs = Math.max(5 * 60000, viewportDuration / 10);
    for (let t = viewportStartT; t <= viewportStartT + viewportDuration; t += stepMs) {
        const p = interpolateAtT(FinalTimeline, t);
        if (!p) continue;
        features.push({
            type: 'Feature', properties: {label: fmtClockShort(t)},
            geometry: {type: 'Point', coordinates: [p.lng, p.lat]}
        });
    }
    return {type: 'FeatureCollection', features};
}

function buildCandidateFC() {
    const devKey = W.selectedDevice === 'null' ? null : W.selectedDevice;
    const arr = (SourceData.get(devKey) || [])
        .filter(p => p.t >= viewportStartT - viewportDuration * 0.1 && p.t <= viewportStartT + viewportDuration * 1.1);
    const color = DeviceSources[devKey == null ? 'null' : devKey].color;
    if (arr.length < 2) return emptyFC();
    const segs = [];
    let seg = [[arr[0].lng, arr[0].lat]];
    for (let i = 1; i < arr.length; i++) {
        if (arr[i].t - arr[i - 1].t > 15000) {
            if (seg.length > 1) segs.push(seg);
            seg = [];
        }
        seg.push([arr[i].lng, arr[i].lat]);
    }
    if (seg.length > 1) segs.push(seg);
    return {
        type: 'FeatureCollection', features: segs.map(s => ({
            type: 'Feature', properties: {color}, geometry: {type: 'LineString', coordinates: s}
        }))
    };
}

function buildCandidateActiveFC() {
    const devKey = W.selectedDevice === 'null' ? null : W.selectedDevice;
    const arr = (SourceData.get(devKey) || []).filter(p => p.t >= W.patch.tStart && p.t <= W.patch.tEnd);
    const color = DeviceSources[devKey == null ? 'null' : devKey].color;
    if (arr.length < 2) return emptyFC();
    return {
        type: 'FeatureCollection', features: [{
            type: 'Feature', properties: {color},
            geometry: {type: 'LineString', coordinates: arr.map(p => [p.lng, p.lat])}
        }]
    };
}

function buildFinalPointsFC() {
    if (W.tool !== 'select' && W.tool !== 'boxselect') return emptyFC();
    const viewPoints = FinalTimeline.filter(p =>
        p.t >= viewportStartT && p.t <= viewportStartT + viewportDuration
    );
    return {
        type: 'FeatureCollection', features: viewPoints.map(p => ({
            type: 'Feature',
            properties: {id: p.id, color: colorOf(p.sourceId), selected: W.selected.has(p.id)},
            geometry: {type: 'Point', coordinates: [p.lng, p.lat]}
        }))
    };
}

function refreshMap() {
    if (!map.getSource || !map.getSource('final-line')) return;
    map.getSource('final-line').setData(buildFinalLineFC());
    map.getSource('time-labels').setData(buildTimeLabelsFC());
    map.getSource('cand-line').setData(buildCandidateFC());
    map.getSource('cand-active').setData(buildCandidateActiveFC());
    map.getSource('final-pts').setData(buildFinalPointsFC());
}

function refreshAll() {
    refreshMap();
    drawMainLane();
    drawDeviceLane();
    syncPatchBox();
    updateStats();
    updateButtons();
    updateWovenCount();
}

/* ============================================================
   CANVASES
   ============================================================ */
const mainCanvas = document.getElementById('mainCanvas');
const deviceCanvas = document.getElementById('deviceCanvas');
const mctx = mainCanvas.getContext('2d');
const dctx = deviceCanvas.getContext('2d');
const patchBox = document.getElementById('patchBox');
const playhead = document.getElementById('playhead');
const boxOverlay = document.getElementById('boxSelectOverlay');
const btnCopy = document.getElementById('btnCopy');
const connectorGuide = document.getElementById('connectorGuide');

function sizeCanvas(canvas, ctx) {
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.parentElement.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = rect.height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return {w: rect.width, h: rect.height};
}

function hexAlpha(hex, a) {
    const h = hex.replace('#', '');
    return `rgba(${parseInt(h.substr(0, 2), 16)},${parseInt(h.substr(2, 2), 16)},${parseInt(h.substr(4, 2), 16)},${a})`;
}

function getGridTickMs(duration) {
    if (duration <= 5 * 60000) return 30000;
    if (duration <= 60 * 60000) return 5 * 60000;
    if (duration <= 4 * 3600000) return 15 * 60000;
    if (duration <= 12 * 3600000) return 3600000;
    if (duration <= 7 * 24 * 3600000) return 12 * 3600000;
    return 24 * 3600000;
}

function drawTimeGrid(ctx, w, h) {
    const tickMs = getGridTickMs(viewportDuration);
    const firstTick = Math.ceil(viewportStartT / tickMs) * tickMs;
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 1;
    ctx.fillStyle = 'rgba(165,169,159,0.7)';
    ctx.font = '9.5px ui-monospace, monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let t = firstTick; t <= viewportStartT + viewportDuration; t += tickMs) {
        const x = tToXf(t, w);
        ctx.beginPath();
        ctx.moveTo(x + 0.5, 0);
        ctx.lineTo(x + 0.5, h);
        ctx.stroke();
        if (ctx === mctx && x > 15 && x < w - 15) {
            const label = viewportDuration > 3*24*3600*1000 ? fmtDateCompact(t) : fmtClockShort(t);
            ctx.fillText(label, x, h - 14);
        }
    }
    ctx.textAlign = 'left';
}

function drawMainLane() {
    const {w, h} = sizeCanvas(mainCanvas, mctx);
    mctx.clearRect(0, 0, w, h);
    drawTimeGrid(mctx, w, h);

    if (FinalTimeline.length < 2) return;

    const visiblePoints = FinalTimeline.filter(p =>
        p.t >= viewportStartT - viewportDuration*0.1 &&
        p.t <= viewportStartT + viewportDuration*1.1
    );
    if (visiblePoints.length < 2) return;

    const runs = [];
    let cur = {
        tStart: visiblePoints[0].t, tEnd: visiblePoints[0].t,
        sid: visiblePoints[0].sourceId, interp: !!visiblePoints[0].interpolated
    };
    for (let i = 1; i < visiblePoints.length; i++) {
        const p = visiblePoints[i];
        const interp = !!p.interpolated;
        if (p.sourceId === cur.sid && interp === cur.interp) cur.tEnd = p.t;
        else { runs.push(cur); cur = {tStart: p.t, tEnd: p.t, sid: p.sourceId, interp}; }
    }
    runs.push(cur);

    const blockY = Math.max(10, h * 0.22);
    const blockH = h - blockY - Math.max(18, h * 0.25);

    for (const r of runs) {
        const x0 = tToXf(r.tStart, w), x1 = tToXf(r.tEnd, w);
        if (x1 < 0 || x0 > w) continue;
        const rectW = Math.max(2, x1 - x0);
        const color = colorOf(r.sid);
        const grad = mctx.createLinearGradient(0, blockY, 0, blockY + blockH);
        grad.addColorStop(0, hexAlpha(color, r.interp ? 0.22 : 0.55));
        grad.addColorStop(1, hexAlpha(color, r.interp ? 0.10 : 0.3));
        mctx.fillStyle = grad;
        roundRect(mctx, x0, blockY, rectW, blockH, 5);
        mctx.fill();

        if (r.interp) {
            mctx.save();
            mctx.beginPath();
            roundRect(mctx, x0, blockY, rectW, blockH, 5);
            mctx.clip();
            mctx.strokeStyle = hexAlpha(color, 0.45);
            mctx.lineWidth = 1;
            for (let d = -blockH; d < rectW + blockH; d += 7) {
                mctx.beginPath();
                mctx.moveTo(x0 + d, blockY);
                mctx.lineTo(x0 + d + blockH, blockY + blockH);
                mctx.stroke();
            }
            mctx.restore();
        }
        mctx.lineWidth = 1.2;
        mctx.strokeStyle = hexAlpha(color, 0.85);
        roundRect(mctx, x0 + 0.5, blockY + 0.5, rectW - 1, blockH - 1, 5);
        mctx.stroke();
    }

    const px0 = tToXf(W.patch.tStart, w);
    const px1 = tToXf(W.patch.tEnd, w);
    if (!(px1 < 0 || px0 > w)) {
        mctx.save();
        mctx.strokeStyle = 'rgba(242, 196, 112, 0.55)';
        mctx.fillStyle = 'rgba(242, 196, 112, 0.08)';
        mctx.lineWidth = 1;
        mctx.setLineDash([4, 3]);
        roundRect(mctx, Math.max(0, px0), blockY - 2, Math.max(2, px1 - px0), blockH + 4, 6);
        mctx.fill();
        mctx.stroke();
        mctx.setLineDash([]);
        mctx.restore();
    }

    const mapZeroX = tToXf(0, w);
    if (mapZeroX > 0) {
        mctx.fillStyle = 'rgba(224, 122, 107, 0.1)';
        mctx.fillRect(0, 0, mapZeroX, h);
    }
    const mapEndX = tToXf(T_TOTAL_BOUNDS, w);
    if (mapEndX < w) {
        mctx.fillStyle = 'rgba(224, 122, 107, 0.1)';
        mctx.fillRect(mapEndX, 0, w - mapEndX, h);
    }

    // Render all points as dots when in select/boxselect tools
    if (W.tool === 'select' || W.tool === 'boxselect') {
        const dotY = blockY + blockH / 2;
        const minSpacingPx = 3;
        let lastX = -Infinity;

        for (const p of visiblePoints) {
            const x = tToXf(p.t, w);
            if (x < -4 || x > w + 4) continue;
            if (x - lastX < minSpacingPx && !W.selected.has(p.id)) continue;
            lastX = x;

            const selected = W.selected.has(p.id);
            const color = colorOf(p.sourceId);

            mctx.fillStyle = '#0a1320';
            mctx.beginPath();
            mctx.arc(x, dotY, selected ? 5 : 3.2, 0, Math.PI * 2);
            mctx.fill();

            mctx.fillStyle = selected ? '#f2c470' : color;
            mctx.beginPath();
            mctx.arc(x, dotY, selected ? 3.6 : 2, 0, Math.PI * 2);
            mctx.fill();
        }
    }

    // Selection glow (kept on top)
    for (const p of visiblePoints) {
        if (!W.selected.has(p.id)) continue;
        const x = tToXf(p.t, w);
        mctx.fillStyle = '#f2c470';
        mctx.shadowColor = 'rgba(242,196,112,0.8)';
        mctx.shadowBlur = 8;
        mctx.beginPath();
        mctx.arc(x, blockY + blockH / 2, 4, 0, Math.PI * 2);
        mctx.fill();
        mctx.shadowBlur = 0;
        mctx.strokeStyle = '#fff5d6';
        mctx.lineWidth = 1.2;
        mctx.stroke();
    }
}

function roundRect(ctx, x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
}

function drawDeviceLane() {
    const {w, h} = sizeCanvas(deviceCanvas, dctx);
    dctx.clearRect(0, 0, w, h);
    drawTimeGrid(dctx, w, h);

    const devKey = W.selectedDevice === 'null' ? null : W.selectedDevice;
    const arr = (SourceData.get(devKey) || []).filter(p =>
        p.t >= viewportStartT - viewportDuration*0.2 &&
        p.t <= viewportStartT + viewportDuration*1.2
    );
    const color = DeviceSources[devKey == null ? 'null' : devKey].color;
    if (arr.length < 2) return;

    let aMin = Infinity, aMax = -Infinity;
    for (const p of arr) {
        if (p.alt < aMin) aMin = p.alt;
        if (p.alt > aMax) aMax = p.alt;
    }
    const pad = (aMax - aMin) * 0.18 + 1;
    aMin -= pad; aMax += pad;

    const midY = h / 2;
    const ampH = h * 0.38;
    const altToY = (a) => midY - (((a - aMin) / (aMax - aMin)) - 0.5) * 2 * ampH;

    const segments = [];
    let seg = [arr[0]];
    for (let i = 1; i < arr.length; i++) {
        if (arr[i].t - arr[i - 1].t > 15000) {
            if (seg.length > 1) segments.push(seg);
            seg = [];
        }
        seg.push(arr[i]);
    }
    if (seg.length > 1) segments.push(seg);

    dctx.save();
    dctx.shadowColor = hexAlpha(color, 0.55);
    dctx.shadowBlur = 6;
    dctx.strokeStyle = color;
    dctx.lineWidth = 1.4;
    dctx.lineJoin = 'round';
    dctx.lineCap = 'round';
    for (const s of segments) {
        dctx.beginPath();
        for (let i = 0; i < s.length; i++) {
            const x = tToXf(s[i].t, w), y = altToY(s[i].alt);
            if (i === 0) dctx.moveTo(x, y); else dctx.lineTo(x, y);
        }
        dctx.stroke();
    }
    dctx.restore();

    dctx.strokeStyle = color;
    dctx.lineWidth = 1.3;
    for (const s of segments) {
        dctx.beginPath();
        for (let i = 0; i < s.length; i++) {
            const x = tToXf(s[i].t, w), y = altToY(s[i].alt);
            if (i === 0) dctx.moveTo(x, y); else dctx.lineTo(x, y);
        }
        dctx.stroke();
    }

    for (let i = 1; i < arr.length; i++) {
        const dt = arr[i].t - arr[i - 1].t;
        if (dt > 15000) {
            const x0 = tToXf(arr[i - 1].t, w), x1 = tToXf(arr[i].t, w);
            if(x1 < 0 || x0 > w) continue;
            dctx.fillStyle = 'rgba(224, 122, 107, 0.08)';
            dctx.fillRect(x0, 4, x1 - x0, h - 8);
            if (x1 - x0 > 30) {
                dctx.fillStyle = 'rgba(224,122,107,0.7)';
                dctx.font = '9.5px ui-monospace, monospace';
                dctx.textAlign = 'center';
                dctx.textBaseline = 'middle';
                const centerVal = (Math.max(0, x0) + Math.min(w, x1)) / 2;
                dctx.fillText('gap', centerVal, h / 2);
                dctx.textAlign = 'left';
            }
        }
    }
}

function syncPatchBox() {
    const cell = document.getElementById('deviceLaneCell');
    const w = cell.clientWidth;
    if (!w) return;
    const x0 = tToXf(W.patch.tStart, w);
    const x1 = tToXf(W.patch.tEnd, w);
    if (x1 < 0 || x0 > w) {
        patchBox.style.display = 'none';
        btnCopy.style.display = 'none';
        connectorGuide.style.display = 'none';
        return;
    }
    patchBox.style.display = 'block';
    btnCopy.style.display = 'inline-flex';
    connectorGuide.style.display = 'block';
    patchBox.style.left = x0 + 'px';
    patchBox.style.width = Math.max(2, (x1 - x0)) + 'px';
    document.getElementById('patchStartLabel').textContent = fmtClock(W.patch.tStart);
    document.getElementById('patchEndLabel').textContent = fmtClock(W.patch.tEnd);
    document.getElementById('statPatch').textContent =
        `${fmtClockShort(W.patch.tStart)}–${fmtClockShort(W.patch.tEnd)}`;
    const center = (x0 + x1) / 2;
    const btnW = btnCopy.offsetWidth || 150;
    const clamped = Math.max(btnW / 2 + 4, Math.min(w - btnW / 2 - 4, center));
    btnCopy.style.left = clamped + 'px';
    connectorGuide.style.left = center + 'px';
    drawMainLane();
}

/* ============================================================
   WHEEL ZOOM / PAN
   ============================================================ */
function clampViewport() {
    if (viewportStartT < 0) viewportStartT = 0;
    if (viewportStartT + viewportDuration > T_TOTAL_BOUNDS)
        viewportStartT = T_TOTAL_BOUNDS - viewportDuration;
}

document.getElementById('drawer').addEventListener('wheel', (e) => {
    if (e.target.closest('#historyPanel')) return;
    const cell = document.getElementById('deviceLaneCell');
    const w = cell.clientWidth;
    if(!w) return;

    if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const rect = cell.getBoundingClientRect();
        const cursorX = Math.max(0, Math.min(w, e.clientX - rect.left));
        const tMouse = xToTf(cursorX, w);
        const zoomRatio = e.deltaY > 0 ? 1.08 : 0.92;
        const proposedDuration = viewportDuration * zoomRatio;
        viewportDuration = Math.max(60 * 1000, Math.min(T_TOTAL_BOUNDS, proposedDuration));
        viewportStartT = tMouse - (cursorX / w) * viewportDuration;
    } else {
        e.preventDefault();
        const shiftX = (Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY);
        const panTimeShift = (shiftX / w) * viewportDuration * 0.8;
        viewportStartT += panTimeShift;
    }

    clampViewport();
    triggerDebouncedDataLoad();
    refreshAll();
}, { passive: false });

/* ============================================================
   PATCH-BOX DRAG
   ============================================================ */
let patchDrag = null;
patchBox.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const edge = e.target.dataset?.edge;
    patchDrag = {
        kind: edge || 'move', startX: e.clientX,
        origStart: W.patch.tStart, origEnd: W.patch.tEnd
    };
    document.body.style.userSelect = 'none';
});

window.addEventListener('mousemove', (e) => {
    if (!patchDrag) return;
    const cell = document.getElementById('deviceLaneCell');
    const w = cell.clientWidth;
    const dt = ((e.clientX - patchDrag.startX) / w) * viewportDuration;
    let ns = patchDrag.origStart, ne = patchDrag.origEnd;
    const minSpan = 5000;
    if (patchDrag.kind === 'move') {
        const span = ne - ns;
        ns = Math.max(0, Math.min(T_TOTAL_BOUNDS - span, patchDrag.origStart + dt));
        ne = ns + span;
    } else if (patchDrag.kind === 'left') {
        ns = Math.max(0, Math.min(ne - minSpan, patchDrag.origStart + dt));
    } else if (patchDrag.kind === 'right') {
        ne = Math.min(T_TOTAL_BOUNDS, Math.max(ns + minSpan, patchDrag.origEnd + dt));
    }
    W.patch.tStart = ns;
    W.patch.tEnd = ne;
    syncPatchBox();
    map.getSource('cand-active')?.setData(buildCandidateActiveFC());
});

window.addEventListener('mouseup', () => {
    if (patchDrag) {
        patchDrag = null;
        document.body.style.userSelect = '';
    }
});

/* ============================================================
   TIMELINE DRAG-TO-PAN
   ============================================================ */
let timelinePan = null;

function beginTimelinePan(e, cellEl) {
    if (e.target.closest('#patchBox')) return false;
    if (e.target.closest('#btnCopy')) return false;
    if (e.button !== 0) return false;

    const rect = cellEl.getBoundingClientRect();
    timelinePan = {
        startX: e.clientX,
        startY: e.clientY,
        origViewportStart: viewportStartT,
        cellW: rect.width,
        moved: false,
        cell: cellEl
    };
    document.body.style.cursor = 'grabbing';
    document.body.style.userSelect = 'none';
    return true;
}

function attachPanHandler(cellId) {
    const el = document.getElementById(cellId);
    if (!el) return;
    el.addEventListener('mousedown', (e) => {
        if (patchDrag) return;
        beginTimelinePan(e, el);
    });
    el.style.cursor = 'grab';
}

attachPanHandler('mainLaneCell');
attachPanHandler('deviceLaneCell');

window.addEventListener('mousemove', (e) => {
    if (!timelinePan) return;
    const dx = e.clientX - timelinePan.startX;
    if (!timelinePan.moved && Math.abs(dx) < 3) return;
    timelinePan.moved = true;

    const shift = -(dx / timelinePan.cellW) * viewportDuration;
    viewportStartT = timelinePan.origViewportStart + shift;
    clampViewport();
    refreshAll();
    playhead.style.display = 'none';
});

window.addEventListener('mouseup', () => {
    if (!timelinePan) return;
    const wasMoved = timelinePan.moved;
    timelinePan = null;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    if (wasMoved) triggerDebouncedDataLoad();
});

/* ============================================================
   SCRUBBING + MAP FOLLOW
   ============================================================ */
let lastScrubPanAt = 0;

function onScrub(e) {
    if (timelinePan && timelinePan.moved) return;
    const cell = e.currentTarget;
    const rect = cell.getBoundingClientRect();
    const x = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
    const t = xToTf(x, rect.width);
    W.hoverT = t;
    playhead.style.display = 'block';
    playhead.style.left = x + 'px';

    const p = interpolateAtT(FinalTimeline, t);
    if (p) {
        map.getSource('scrub-pt')?.setData({
            type: 'FeatureCollection',
            features: [{type: 'Feature', properties: {}, geometry: {type: 'Point', coordinates: [p.lng, p.lat]}}]
        });
        document.getElementById('statCursor').textContent = `${fmtClock(t)}`;
        document.getElementById('drawerClock').textContent = fmtClock(t);

        const now = performance.now();
        if (now - lastScrubPanAt > 120) {
            const b = map.getBounds();
            const pad = 0.15;
            const lngSpan = b.getEast() - b.getWest();
            const latSpan = b.getNorth() - b.getSouth();
            const outside =
                p.lng < b.getWest() + lngSpan * pad ||
                p.lng > b.getEast() - lngSpan * pad ||
                p.lat < b.getSouth() + latSpan * pad ||
                p.lat > b.getNorth() - latSpan * pad;
            if (outside) {
                map.easeTo({center: [p.lng, p.lat], duration: 400, essential: true});
                lastScrubPanAt = now;
            }
        }
    }
}

function offScrub() {
    W.hoverT = null;
    playhead.style.display = 'none';
    map.getSource('scrub-pt')?.setData(emptyFC());
    document.getElementById('statCursor').textContent = '—';
}

document.getElementById('deviceLaneCell').addEventListener('mousemove', onScrub);
document.getElementById('deviceLaneCell').addEventListener('mouseleave', offScrub);
document.getElementById('mainLaneCell').addEventListener('mousemove', onScrub);
document.getElementById('mainLaneCell').addEventListener('mouseleave', offScrub);

function interpolateAtT(arr, t) {
    if (!arr.length) return null;
    if (t <= arr[0].t) return arr[0];
    if (t >= arr[arr.length - 1].t) return arr[arr.length - 1];
    let lo = 0, hi = arr.length - 1;
    while (hi - lo > 1) {
        const mid = (lo + hi) >> 1;
        if (arr[mid].t <= t) lo = mid; else hi = mid;
    }
    const a = arr[lo], b = arr[hi];
    const f = (t - a.t) / (b.t - a.t);
    return {
        lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f,
        alt: a.alt + (b.alt - a.alt) * f
    };
}

/* ============================================================
   WEAVE OPS
   ============================================================ */
function copyToFinal() {
    const devKey = W.selectedDevice === 'null' ? null : W.selectedDevice;
    const src = (SourceData.get(devKey) || []).filter(p => p.t >= W.patch.tStart && p.t <= W.patch.tEnd);
    if (!src.length) { toast('No points in patch range', true); return; }

    const patchSeq = (EditStore.patches.at(-1)?.seq ?? 0) + 1;
    const patchRecord = {
        tStart: W.patch.tStart, tEnd: W.patch.tEnd,
        sourceId: devKey, seq: patchSeq
    };
    EditStore.patches.push(patchRecord);

    const priorCount = FinalTimeline.filter(p => p.t >= W.patch.tStart && p.t <= W.patch.tEnd).length;
    rebuildFinalInRange(W.patch.tStart, W.patch.tEnd);
    const injected = FinalTimeline.filter(p =>
        p.t >= W.patch.tStart && p.t <= W.patch.tEnd && p.sourceId === devKey
    ).length;

    const niceName = nameOf(devKey);
    pushAction({
        type: 'copy',
        shortDesc: `${niceName} patch`,
        desc: `Wove in <b style="color:${DeviceSources[devKey == null ? 'null': devKey].color}">${niceName}</b> from ${fmtClockShort(W.patch.tStart)} to ${fmtClockShort(W.patch.tEnd)} <span style="color:var(--ink-faint)">· +${injected} / −${priorCount}</span>`,
        payload: {
            device: devKey, tStart: W.patch.tStart, tEnd: W.patch.tEnd,
            pointsInjected: injected, pointsRemoved: priorCount
        },
        _inverse: {patchRecord}
    });

    toast(`Wove ${injected} ${niceName} points into the story`);
    refreshAll();
}

function deleteSelected() {
    if (!W.selected.size) return;

    const sel = FinalTimeline.filter(p => W.selected.has(p.id));
    if (!sel.length) return;

    const deletedKeys = [];
    const seenKeys = new Set();

    for (const p of sel) {
        if (p.sourceRefId != null) {
            const k = pointKey(p.sourceId, p.sourceRefId);
            if (!seenKeys.has(k)) {
                seenKeys.add(k);
                deletedKeys.push({key: k, sourceId: p.sourceId, refId: p.sourceRefId});
            }
        } else if (p.interpolated) {
            const idx = FinalTimeline.indexOf(p);
            let neighbor = null;
            for (let d = 1; d < 5; d++) {
                const r = FinalTimeline[idx + d];
                const l = FinalTimeline[idx - d];
                if (r && r.sourceRefId != null) { neighbor = r; break; }
                if (l && l.sourceRefId != null) { neighbor = l; break; }
            }
            if (neighbor) {
                const k = pointKey(neighbor.sourceId, neighbor.sourceRefId);
                if (!seenKeys.has(k)) {
                    seenKeys.add(k);
                    deletedKeys.push({key: k, sourceId: neighbor.sourceId, refId: neighbor.sourceRefId});
                }
            }
        }
    }

    if (!deletedKeys.length) {
        toast('Nothing to delete (selection was all interpolated with no neighbors)', true);
        return;
    }

    for (const d of deletedKeys) EditStore.deletedPoints.add(d.key);

    const tMin = Math.min(...sel.map(p => p.t));
    const tMax = Math.max(...sel.map(p => p.t));
    rebuildFinalInRange(tMin - 1, tMax + 1);
    W.selected.clear();

    const n = deletedKeys.length;
    pushAction({
        type: 'delete',
        shortDesc: `${n} point${n > 1 ? 's' : ''}`,
        desc: `Removed <b>${n}</b> point${n > 1 ? 's' : ''} between ${fmtClockShort(tMin)} and ${fmtClockShort(tMax)}`,
        payload: {
            count: n,
            tStart: tMin, tEnd: tMax,
            points: deletedKeys.map(d => ({sourceId: d.sourceId, id: d.refId}))
        },
        _inverse: {deletedKeys: deletedKeys.map(d => d.key), tStart: tMin - 1, tEnd: tMax + 1}
    });

    toast(`Removed ${n} point${n > 1 ? 's' : ''}`);
    refreshAll();
}

/* ============================================================
   MAP INTERACTIONS
   ============================================================ */
function snapshotDragGroup(pointIds) {
    const group = [];
    for (const id of pointIds) {
        const fp = FinalTimeline.find(p => p.id === id);
        if (!fp) continue;
        const entry = {
            pointId: fp.id, originLat: fp.lat, originLng: fp.lng,
            sourceId: fp.sourceId, sourceRefId: fp.sourceRefId ?? null,
            sourceOriginLat: null, sourceOriginLng: null
        };
        if (fp.sourceRefId != null && fp.sourceId != null) {
            const s = SourceData.get(fp.sourceId)?.find(pp => pp.id === fp.sourceRefId);
            if (s) { entry.sourceOriginLat = s.lat; entry.sourceOriginLng = s.lng; }
        }
        group.push(entry);
    }
    return group;
}

function setupMapInteractions() {
    const mapEl = map.getCanvas();
    let mouseDown = false, boxStart = null;
    let draggingAnchor = null, dragGroup = null, anchorOrigin = null, draggedMoved = false;

    map.on('mousemove', 'final-pts', () => {
        if (W.tool === 'select' || W.tool === 'boxselect') mapEl.style.cursor = 'grab';
    });
    map.on('mouseleave', 'final-pts', () => { mapEl.style.cursor = ''; });

    map.on('mousedown', (e) => {
        if (W.tool === 'inspect') return;
        if (e.originalEvent.button !== 0) return;
        mouseDown = true;
        draggedMoved = false;

        const pt = nearestFinalPoint(e.point, 14);
        if (pt) {
            const clickedIsSelected = W.selected.has(pt.id);
            let idsToDrag;
            if (clickedIsSelected && W.selected.size > 1) idsToDrag = Array.from(W.selected);
            else if (W.tool === 'select') idsToDrag = [pt.id];
            else idsToDrag = null;

            if (idsToDrag) {
                draggingAnchor = pt;
                anchorOrigin = {lat: pt.lat, lng: pt.lng};
                dragGroup = snapshotDragGroup(idsToDrag);
                map.dragPan.disable();
                return;
            }
        }

        if (W.tool === 'boxselect') {
            boxStart = {x: e.originalEvent.clientX, y: e.originalEvent.clientY, mapPt: e.point};
            map.dragPan.disable();
            boxOverlay.style.display = 'block';
            boxOverlay.style.left = boxStart.x + 'px';
            boxOverlay.style.top = boxStart.y + 'px';
            boxOverlay.style.width = '0px';
            boxOverlay.style.height = '0px';
        }
    });

    map.on('mousemove', (e) => {
        if (!mouseDown) return;
        if (draggingAnchor && dragGroup) {
            draggedMoved = true;
            const dLat = e.lngLat.lat - anchorOrigin.lat;
            const dLng = e.lngLat.lng - anchorOrigin.lng;
            for (const g of dragGroup) {
                const fp = FinalTimeline.find(p => p.id === g.pointId);
                if (fp) {
                    fp.lat = g.originLat + dLat;
                    fp.lng = g.originLng + dLng;
                    if (g.sourceRefId != null && g.sourceId != null && g.sourceOriginLat != null) {
                        const s = SourceData.get(g.sourceId)?.find(pp => pp.id === g.sourceRefId);
                        if (s) {
                            s.lat = g.sourceOriginLat + dLat;
                            s.lng = g.sourceOriginLng + dLng;
                        }
                    }
                }
            }
            refreshMap();
            drawMainLane();
            drawDeviceLane();
            mapEl.style.cursor = 'grabbing';
            return;
        }
        if (boxStart) {
            const cx = e.originalEvent.clientX, cy = e.originalEvent.clientY;
            boxOverlay.style.left = Math.min(cx, boxStart.x) + 'px';
            boxOverlay.style.top = Math.min(cy, boxStart.y) + 'px';
            boxOverlay.style.width = Math.abs(cx - boxStart.x) + 'px';
            boxOverlay.style.height = Math.abs(cy - boxStart.y) + 'px';
        }
    });

    window.addEventListener('mouseup', (e) => {
        if (!mouseDown) return;
        mouseDown = false;
        map.dragPan.enable();

        if (draggingAnchor && dragGroup) {
            if (draggedMoved) {
                const anchor = FinalTimeline.find(p => p.id === draggingAnchor.id);
                const dLat = anchor.lat - anchorOrigin.lat, dLng = anchor.lng - anchorOrigin.lng;
                const deltaM = Math.sqrt(dLat * dLat + dLng * dLng) * 111320;
                const n = dragGroup.length;

                const movedEntries = [];
                for (const g of dragGroup) {
                    const fp = FinalTimeline.find(p => p.id === g.pointId);
                    if (fp && g.sourceRefId != null) {
                        EditStore.movedPoints.set(
                            pointKey(g.sourceId, g.sourceRefId),
                            {lat: fp.lat, lng: fp.lng}
                        );
                        movedEntries.push({
                            sourceId: g.sourceId, id: g.sourceRefId,
                            lat: fp.lat, lng: fp.lng
                        });
                    }
                }

                const inverseGroup = dragGroup.map(g => ({
                    pointId: g.pointId,
                    from: {lat: g.originLat, lng: g.originLng},
                    sourceId: g.sourceId, sourceRefId: g.sourceRefId,
                    sourceFrom: g.sourceOriginLat != null
                        ? {lat: g.sourceOriginLat, lng: g.sourceOriginLng} : null
                }));

                if (n === 1) {
                    const g = dragGroup[0];
                    const linked = g.sourceRefId != null && g.sourceId != null;
                    const srcName = linked ? nameOf(g.sourceId) : 'primary';
                    pushAction({
                        type: 'move',
                        shortDesc: `move @ ${fmtClockShort(anchor.t)}`,
                        desc: `Nudged vertex at ${fmtClockShort(anchor.t)} by <b>${deltaM.toFixed(1)} m</b>${linked ? ` <span style="color:var(--ink-faint)">· linked to ${srcName}</span>` : ''}`,
                        payload: {count: 1, points: movedEntries, deltaMeters: +deltaM.toFixed(2)},
                        _inverse: {group: inverseGroup}
                    });
                } else {
                    pushAction({
                        type: 'move',
                        shortDesc: `move ${n} points`,
                        desc: `Nudged <b>${n} points</b> by <b>${deltaM.toFixed(1)} m</b> <span style="color:var(--ink-faint)">· group</span>`,
                        payload: {count: n, points: movedEntries, deltaMeters: +deltaM.toFixed(2)},
                        _inverse: {group: inverseGroup}
                    });
                }
                drawMainLane();
                drawDeviceLane();
            } else {
                if (!e.shiftKey && !e.metaKey && !e.ctrlKey) W.selected.clear();
                if (W.selected.has(draggingAnchor.id)) W.selected.delete(draggingAnchor.id);
                else W.selected.add(draggingAnchor.id);
                refreshAll();
            }
            draggingAnchor = null;
            dragGroup = null;
            anchorOrigin = null;
            mapEl.style.cursor = '';
            return;
        }

        if (boxStart) {
            const x1 = boxStart.mapPt.x, y1 = boxStart.mapPt.y;
            const endRect = map.getContainer().getBoundingClientRect();
            const x2 = e.clientX - endRect.left, y2 = e.clientY - endRect.top;
            boxOverlay.style.display = 'none';
            boxStart = null;
            if (Math.abs(x2 - x1) < 4 && Math.abs(y2 - y1) < 4) return;
            const c1 = map.unproject([Math.min(x1, x2), Math.min(y1, y2)]);
            const c2 = map.unproject([Math.max(x1, x2), Math.max(y1, y2)]);
            const minLng = Math.min(c1.lng, c2.lng), maxLng = Math.max(c1.lng, c2.lng);
            const minLat = Math.min(c1.lat, c2.lat), maxLat = Math.max(c1.lat, c2.lat);
            if (!e.shiftKey) W.selected.clear();
            let added = 0;
            for (const p of FinalTimeline) {
                if (p.lat >= minLat && p.lat <= maxLat && p.lng >= minLng && p.lng <= maxLng) {
                    W.selected.add(p.id);
                    added++;
                }
            }
            if (added > 0) {
                toast(`Selected ${added} vertices · drag any to move them all`);
                activateTool('select');
            }
            refreshAll();
        }
    });
}

function nearestFinalPoint(screenPt, thresholdPx) {
    let best = null, bestD = thresholdPx * thresholdPx;
    const searchTarget = FinalTimeline.filter(p =>
        p.t >= viewportStartT - viewportDuration*0.5 &&
        p.t <= viewportStartT + viewportDuration*1.5
    );
    for (const p of searchTarget) {
        const proj = map.project([p.lng, p.lat]);
        const dx = proj.x - screenPt.x, dy = proj.y - screenPt.y;
        const d = dx * dx + dy * dy;
        if (d < bestD) { bestD = d; best = p; }
    }
    return best;
}

/* ============================================================
   UI
   ============================================================ */
function activateTool(tool) {
    W.tool = tool;
    document.querySelectorAll('.head-btn[data-tool]').forEach(x =>
        x.classList.toggle('active', x.dataset.tool === tool));
    refreshMap();
    drawMainLane();
    drawDeviceLane();
}

document.querySelectorAll('.head-btn[data-tool]').forEach(b => {
    b.addEventListener('click', () => activateTool(b.dataset.tool));
});

document.getElementById('deviceSelect').addEventListener('change', (e) => {
    W.selectedDevice = e.target.value;
    const name = nameOf(W.selectedDevice === 'null' ? null : W.selectedDevice);
    document.getElementById('deviceSubLabel').textContent = `(${name})`;
    refreshAll();
});

btnCopy.addEventListener('click', copyToFinal);
document.getElementById('btnDelete').addEventListener('click', deleteSelected);

function openCommit() {
    const modal = document.getElementById('commitModal');
    const pre = document.getElementById('commitPayload');
    const summary = document.getElementById('commitSummary');
    const active = History.filter(a => !a.undone);
    const composition = FinalTimeline.reduce((acc, p) => {
        const k = p.sourceId === null ? 'primary' : p.sourceId;
        acc[k] = (acc[k] || 0) + 1;
        return acc;
    }, {});
    const payload = {
        schema: 'journey-weaver/commit@4',
        journey: 'Alpine Crossing (Infinite Canvas bounds)',
        user: 'explorer_ben',
        clientTs: new Date().toISOString(),
        initialState: histStartSnapshot,
        editStore: {
            patches: EditStore.patches,
            deletedPoints: Array.from(EditStore.deletedPoints).map(k => {
                const [sid, id] = k.split('|');
                return {sourceId: sid === 'null' ? null : sid, id};
            }),
            movedPoints: Array.from(EditStore.movedPoints.entries()).map(([k, v]) => {
                const [sid, id] = k.split('|');
                return {sourceId: sid === 'null' ? null : sid, id, lat: v.lat, lng: v.lng};
            })
        },
        actions: active.map(a => ({seq: a.seq, type: a.type, at: a.at, ...a.payload})),
        undoneActions: History.filter(a => a.undone).map(a => ({seq: a.seq, type: a.type})),
        finalState: {
            pointCount: FinalTimeline.length, composition,
            tStart: FinalTimeline[0]?.t ?? 0,
            tEnd: FinalTimeline[FinalTimeline.length - 1]?.t ?? 0,
            note: 'Cached window only — server should rebuild from editStore.'
        }
    };
    summary.textContent = `${active.length} action${active.length === 1 ? '' : 's'} · ${FinalTimeline.length} cached story points`;
    pre.textContent = JSON.stringify(payload, null, 2);
    modal.classList.add('open');
}

function closeCommit() { document.getElementById('commitModal').classList.remove('open'); }

document.getElementById('btnCommit').addEventListener('click', openCommit);
document.getElementById('commitClose').addEventListener('click', closeCommit);
document.getElementById('commitCancel').addEventListener('click', closeCommit);
document.getElementById('commitConfirm').addEventListener('click', () => {
    const payload = JSON.parse(document.getElementById('commitPayload').textContent);
    console.log('[Journey Weaver] POST /commit', payload);
    toast(`Your journey was sent to the server ✓`);
    closeCommit();
});

document.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
    if (e.key === 'Delete' || e.key === 'Backspace') {
        if (W.selected.size) { e.preventDefault(); deleteSelected(); }
    } else if (e.key === 'Escape') {
        closeCommit();
        W.selected.clear();
        refreshAll();
    } else if (e.key === '1') activateTool('inspect');
    else if (e.key === '2') activateTool('select');
    else if (e.key === '3') activateTool('boxselect');
});

window.addEventListener('resize', () => { refreshAll(); });

function updateStats() {
    document.getElementById('statFinal').textContent = FinalTimeline.length;
    const devKey = W.selectedDevice === 'null' ? null : W.selectedDevice;
    document.getElementById('statDevice').textContent = (SourceData.get(devKey) || []).length;
    document.getElementById('statSel').textContent = W.selected.size;
}

function updateButtons() {
    document.getElementById('btnDelete').disabled = W.selected.size === 0;
}

function updateWovenCount() {
    let n = 0, last = null;
    for (const p of FinalTimeline) {
        if (p.sourceId != null && p.sourceId !== last) n++;
        last = p.sourceId;
    }
    document.getElementById('wovenCount').textContent = n;
}

function toast(msg, warn) {
    const el = document.createElement('div');
    el.className = 'toast';
    if (warn) el.style.borderColor = 'rgba(224,122,107,0.4)';
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => {
        el.style.transition = 'opacity .35s';
        el.style.opacity = '0';
    }, 2400);
    setTimeout(() => el.remove(), 2800);
}

requestAnimationFrame(() => { refreshAll(); });