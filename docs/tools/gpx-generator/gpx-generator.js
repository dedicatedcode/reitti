// GPX Generator v2 – Maplibre + Workbench look
const TRACK_COLORS = [
  '#3498db','#e74c3c','#2ecc71','#f39c12','#9b59b6',
  '#1abc9c','#34495e','#e67e22','#95a5a6','#d35400'
];

let tracks = [];            // { id, name, points:[ {lat,lng,originalLat,originalLng,timestamp,elevation,accuracy} ], color, collapsed, startTime }
window.tracks = tracks;
let currentTrackIndex = 0;
let editModeEnabled = false;
window.editModeEnabled = editModeEnabled;
let paintMode = false;
window.paintMode = paintMode;
let paintActive = false;
let lastPaintTime = 0;
let paintThrottleMs = 100;
let lastMouseLngLat = null;
let stopProbability = 0.05;
let speedColorCache = {};

const MAX_INTERPOLATION_SEGMENTS = 40000;

// persisted point selection (view mode)
let pinnedPoint = null;

// drag & move support
let dragPoint = null;       // { trackIndex, pointIndex }
let dragOccurred = false;

// Google JSON import state
let pendingJsonData = null;
let pendingJsonFilename = "";
let selectedDates = new Set();
let lastClickedDate = null;

// Map and layers
let map;
let tracksSource, pointsSource, previewSource;

// --- helpers ---------------------------------------------------------------
function pad2(n) { return n.toString().padStart(2,'0'); }

// --- initialise -----------------------------------------------------------
document.addEventListener('DOMContentLoaded', () => {
  initMap();
  // set default datetime-local value
  const now = new Date();
  const localStr = now.getFullYear() + '-' + pad2(now.getMonth()+1) + '-' + pad2(now.getDate()) +
    'T' + pad2(now.getHours()) + ':' + pad2(now.getMinutes()) + ':' + pad2(now.getSeconds());
  document.getElementById('startDatetimeLocal').value = localStr;
  initControls();
  createNewTrack();
  updateStatus();

  // drawer hidden initially (not in edit mode)
  document.getElementById('gpxEditPanel').style.display = 'none';
  document.body.classList.remove('edit-mode');
});

// --- map -------------------------------------------------------------------
function initMap() {
  map = new maplibregl.Map({
    container: 'map',
    style: 'https://basemaps.cartocdn.com/gl/positron-gl-style/style.json', // simple clean style
    center: [24.9384, 60.1699],
    zoom: 10,
    keyboard: false   // disable built‑in keyboard pan so our shortcuts are not captured
  });
  map.addControl(new maplibregl.NavigationControl({showCompass: false}), 'top-left');
  map.on('load', () => {
    // sources
    map.addSource('tracks', { type: 'geojson', data: emptyFC() });
    map.addLayer({ id: 'tracks-line', type: 'line', source: 'tracks', paint: {
      'line-color': ['get','color'],
      'line-width': 3
    }});
    map.addSource('points', { type: 'geojson', data: emptyFC() });
    map.addLayer({ id: 'points-circle', type: 'circle', source: 'points', paint: {
      'circle-radius': 4,
      'circle-color': ['get','color'],
      'circle-stroke-width': 1.5,
      'circle-stroke-color': '#ffffff'
    }});
    map.addSource('preview', { type: 'geojson', data: emptyFC() });
    map.addLayer({ id: 'preview-line', type: 'line', source: 'preview', paint: {
      'line-color': '#f2c470',
      'line-width': 2,
      'line-dasharray': [4,2]
    }});

    // Ensure overlays are visible above the map
    const drawer = document.getElementById('gpxDrawer');
    if (drawer) {
      drawer.style.position = 'fixed';
      drawer.style.zIndex = '50';
      drawer.style.bottom = '12px';
      drawer.style.left = '50%';
      drawer.style.transform = 'translateX(-50%)';
      drawer.style.maxWidth = '800px';
      drawer.style.width = 'auto';
    }

    const pointInfo = document.getElementById('pointInfo');
    if (pointInfo) pointInfo.style.zIndex = '60';

    const pointsPanel = document.getElementById('pointsPanel');
    if (pointsPanel) pointsPanel.style.zIndex = '40';

    const mapCanvas = document.querySelector('#map .maplibregl-canvas');
    if (mapCanvas) mapCanvas.style.zIndex = '0';
  });

  map.on('click', onMapClick);
  map.on('mousemove', onMapMouseMove);
  map.on('mouseout', () => {
    // Keep info visible when a point is pinned or being dragged
    if (pinnedPoint || dragPoint) return;
    hidePointInfo();
  });

  map.on('mousedown', onMapMouseDown);
  map.on('mouseup', onMapMouseUp);

  // pointer cursor on point hover
  map.on('mouseenter', 'points-circle', () => {
    map.getCanvas().style.cursor = 'pointer';
  });
  map.on('mouseleave', 'points-circle', () => {
    map.getCanvas().style.cursor = '';
  });

  // expose for tool integration
  window.reittiMap = map;
  window.gpxOnMapClick = onMapClick;
  window.gpxOnMapMouseDown = onMapMouseDown;
  window.gpxOnMapMouseUp = onMapMouseUp;
  window.gpxOnMapMouseMove = onMapMouseMove;
}

function emptyFC() { return { type:'FeatureCollection', features:[] }; }

// --- map event handlers ----------------------------------------------------
function onMapClick(e) {
  // ignore clicks triggered after a drag
  if (dragOccurred) {
    dragOccurred = false;
    return;
  }

  // view mode – persistent point selection
  if (!editModeEnabled) {
    const features = map.queryRenderedFeatures(e.point, { layers: ['points-circle'] });
    if (features.length) {
      const f = features[0];
      pinnedPoint = { trackIndex: f.properties.trackIndex, pointIndex: f.properties.pointIndex };
      window.lastSelectedPoint = { trackIndex: f.properties.trackIndex, pointIndex: f.properties.pointIndex };
      showPointInfoForPinned();
    } else {
      clearPinned();
      hidePointInfo(true);
      window.lastSelectedPoint = null;
    }
    return;
  }
  // edit mode – add points
  if (paintMode) return;
  const track = tracks[currentTrackIndex];
  if (track && track.points.length) addPointWithInterpolation(e.lngLat.lat, e.lngLat.lng);
  else addPoint(e.lngLat.lat, e.lngLat.lng);
}

function onMapMouseDown(e) {
  if (!editModeEnabled) return;
  const features = map.queryRenderedFeatures(e.point, { layers: ['points-circle'] });
  if (features.length) {
    const f = features[0];
    dragPoint = { trackIndex: f.properties.trackIndex, pointIndex: f.properties.pointIndex };
    dragOccurred = false;
    map.dragPan.disable();
  }
}

function onMapMouseUp(e) {
  if (dragPoint) {
    if (editModeEnabled) {
      const track = tracks[dragPoint.trackIndex];
      if (track && dragPoint.pointIndex < track.points.length) {
        pinnedPoint = { trackIndex: dragPoint.trackIndex, pointIndex: dragPoint.pointIndex };
        showPointInfoForPinned();
      }
    }
    dragPoint = null;
    map.dragPan.enable();
  }
}

function onMapMouseMove(e) {
  if (!map.loaded()) return;

  // -- drag point movement ------------------------------------------------
  if (editModeEnabled && dragPoint) {
    const track = tracks[dragPoint.trackIndex];
    if (track) {
      const p = track.points[dragPoint.pointIndex];
      if (p) {
        p.lat = e.lngLat.lat;
        p.lng = e.lngLat.lng;
        p.originalLat = e.lngLat.lat;
        p.originalLng = e.lngLat.lng;
        dragOccurred = true;                   // prevent the following click from clearing the pin
        updateAllLayers();
        updatePointsList();
        // keep info for the dragged point
        showPointInfoForPinned();
      }
    }
    return;
  } else if (dragPoint) {
    dragPoint = null;
    map.dragPan.enable();
    return;
  }

  // preview line
  const previewSource = map.getSource('preview');
  const previewAllowed = (typeof window.currentTool === 'undefined' ||
                          (window.currentTool !== 'select' && window.currentTool !== 'boxselect'));
  if (editModeEnabled && !paintMode && previewAllowed) {
    const track = tracks[currentTrackIndex];
    if (previewSource) {
      if (track && track.points.length) {
        const last = track.points[track.points.length-1];
        const features = [{
          type: 'Feature',
          geometry: { type:'LineString', coordinates: [[last.lng, last.lat], [e.lngLat.lng, e.lngLat.lat]] }
        }];
        previewSource.setData({ type:'FeatureCollection', features });
      } else {
        previewSource.setData(emptyFC());
      }
    }
  } else {
    if (previewSource) previewSource.setData(emptyFC());
  }

  // preview popup
  const popup = document.getElementById('previewPopup');
  const previewPopupAllowed = (typeof window.currentTool === 'undefined' ||
                               (window.currentTool !== 'select' && window.currentTool !== 'boxselect'));
  if (editModeEnabled && previewPopupAllowed) {
    updatePreviewPopup(e);
  } else {
    if (popup) popup.style.display = 'none';
  }

  // paint mode – add points at mouse cursor while active
  if (editModeEnabled && paintMode && paintActive) {
    const now = Date.now();
    if (now - lastPaintTime > paintThrottleMs) {
      lastPaintTime = now;
      const track = tracks[currentTrackIndex];
      if (track && track.points.length) {
        addPointWithInterpolation(e.lngLat.lat, e.lngLat.lng);
      } else {
        addPoint(e.lngLat.lat, e.lngLat.lng);
      }
    }
  }

  lastMouseLngLat = e.lngLat;

  // point hover / persistent point
  if (map.getLayer('points-circle')) {
    const features = map.queryRenderedFeatures(e.point, { layers: ['points-circle'] });
    if (editModeEnabled) {
      if (features.length) {
        showPointInfo(features[0].properties);
      } else {
        hidePointInfo();
      }
    } else {
      // view mode – prefer pinned point, then hover
      if (pinnedPoint) {
        showPointInfoForPinned();
      } else if (features.length) {
        showPointInfo(features[0].properties);
      } else {
        hidePointInfo();
      }
    }
  } else {
    if (editModeEnabled) {
      hidePointInfo();
    } else if (pinnedPoint) {
      showPointInfoForPinned();
    } else {
      hidePointInfo();
    }
  }
}

// ---- point info panel ----------------------------------------------------
function showPointInfo(props) {
  const panel = document.getElementById('pointInfo');
  const title = document.getElementById('pointInfoTitle');
  const body = document.getElementById('pointInfoBody');
  title.textContent = `${props.trackName} · Point ${props.pointIndex+1}`;
  let html = `
    <div class="sel-info-row"><span class="k">Lat</span><span class="v">${props.lat.toFixed(6)}</span></div>
    <div class="sel-info-row"><span class="k">Lng</span><span class="v">${props.lng.toFixed(6)}</span></div>
    <div class="sel-info-row"><span class="k">Time</span><span class="v">${new Date(props.timestamp).toLocaleString()}</span></div>
    <div class="sel-info-row"><span class="k">Speed</span><span class="v">${props.speed ? props.speed.toFixed(1)+' km/h' : '-'}</span></div>
    <div class="sel-info-row"><span class="k">Elev</span><span class="v">${props.elevation ? props.elevation.toFixed(1)+'m' : '-'}</span></div>
    <div class="sel-info-row"><span class="k">Acc</span><span class="v">${typeof props.accuracy === 'number' ? props.accuracy.toFixed(1)+'m' : '-'}</span></div>
    <div class="sel-info-actions">
      <button class="btn btn-danger sel-info-btn" onclick="deletePointFromInfo(${props.trackIndex},${props.pointIndex})">Delete</button>
      <button class="btn sel-info-btn" onclick="centerOnPoint(${props.lat},${props.lng})">Center</button>
    </div>`;

  // ---- diffs to neighbour points -----------------------------------------
  const track = tracks[props.trackIndex];
  if (track) {
    const pi = props.pointIndex;
    const prev = pi > 0 ? track.points[pi - 1] : null;
    const next = pi < track.points.length - 1 ? track.points[pi + 1] : null;
    if (prev || next) {
      html += '<div class="sel-info-diffs">';
      html += '<div style="font-size:11px; font-weight:600; margin-bottom:4px; color:#444;">Relative to neighbours</div>';
      html += '<div style="display:flex; gap:12px;">';
      const p = track.points[pi];
      if (prev) {
        const dist = calculateDistance(prev.lat, prev.lng, p.lat, p.lng);
        const dtSec = (p.timestamp - prev.timestamp) / 1000;
        const spd = dist > 0 && dtSec > 0 ? (dist/1000) / (dtSec/3600) : null;
        html += '<div style="flex:1; min-width:0;">';
        html += `<div class="sel-info-row"><span class="k">← Prev dist</span><span class="v">${dist.toFixed(1)} m</span></div>`;
        html += `<div class="sel-info-row"><span class="k">← Prev Δt</span><span class="v">${dtSec.toFixed(0)} s</span></div>`;
        html += `<div class="sel-info-row"><span class="k">← Prev Speed</span><span class="v">${spd ? spd.toFixed(1)+' km/h' : '-'}</span></div>`;
        html += '</div>';
      }
      if (next) {
        const dist = calculateDistance(p.lat, p.lng, next.lat, next.lng);
        const dtSec = (next.timestamp - p.timestamp) / 1000;
        const spd = dist > 0 && dtSec > 0 ? (dist/1000) / (dtSec/3600) : null;
        html += '<div style="flex:1; min-width:0;">';
        html += `<div class="sel-info-row"><span class="k">Next dist →</span><span class="v">${dist.toFixed(1)} m</span></div>`;
        html += `<div class="sel-info-row"><span class="k">Next Δt →</span><span class="v">${dtSec.toFixed(0)} s</span></div>`;
        html += `<div class="sel-info-row"><span class="k">Next Speed →</span><span class="v">${spd ? spd.toFixed(1)+' km/h' : '-'}</span></div>`;
        html += '</div>';
      }
      html += '</div>'; // flex container
      html += '</div>'; // diffs
    }
  }
  body.innerHTML = html;
  panel.style.display = 'block';
}
function hidePointInfo(force = false) {
  const el = document.getElementById('pointInfo');
  // If the mouse is currently over the panel, do not hide it unless the caller explicitly forces it (e.g. the close button).
  if (!force && el && el.matches(':hover')) return;
  // close the panel and clear any pinned point
  if (pinnedPoint && !editModeEnabled) {
    clearPinned();
  }
  if (el) el.style.display = 'none';
}
function deletePointFromInfo(ti, pi) {
  removePoint(ti, pi);
  if (pinnedPoint && pinnedPoint.trackIndex === ti && pinnedPoint.pointIndex === pi) {
    clearPinned();
  }
  hidePointInfo(true);
}
function centerOnPoint(lat, lng) {
  map.flyTo({ center: [lng, lat], zoom: 14 });
}

function showPointInfoForPinned() {
  if (!pinnedPoint) return;
  const track = tracks[pinnedPoint.trackIndex];
  if (!track || pinnedPoint.pointIndex >= track.points.length) { clearPinned(); hidePointInfo(true); return; }
  const p = track.points[pinnedPoint.pointIndex];
  let speed = null;
  if (pinnedPoint.pointIndex > 0) {
    const prev = track.points[pinnedPoint.pointIndex-1];
    speed = (calculateDistance(prev.lat,prev.lng,p.lat,p.lng)/1000) / ((p.timestamp - prev.timestamp)/3600000);
  }
  const props = {
    trackIndex: pinnedPoint.trackIndex,
    pointIndex: pinnedPoint.pointIndex,
    trackName: track.name,
    color: track.color,
    lat: p.lat,
    lng: p.lng,
    timestamp: p.timestamp.toISOString(),
    speed,
    elevation: p.elevation,
    accuracy: p.accuracy
  };
  showPointInfo(props);
}

function clearPinned() {
  pinnedPoint = null;
}

// ---- core point operations ------------------------------------------------
function addPoint(lat, lng, options = {}) {
  if (!tracks.length) createNewTrack();
  const track = tracks[currentTrackIndex];
  let ts = options.timestamp || getCurrentPickerDate();
  if (!options.skipDayChangeCheck && shouldCreateNewTrackForDayChange(ts, track)) {
    createNewTrack();
    return addPoint(lat, lng, options);
  }
  if (!options.skipStops && document.getElementById('autoStops').checked && shouldAddStop(track)) {
    ts = addRealisticStop(ts);
  }
  // accuracy: keep explicit value (from import) as is; otherwise leave undefined.
  // use slider value only when noise generation is needed and no explicit accuracy given.
  let noiseAcc = options.accuracy;
  if (!options.skipNoise && noiseAcc === undefined) {
    noiseAcc = parseFloat(document.getElementById('accuracySlider').value);
  }
  const coords = options.skipNoise ? {lat,lng} : applyGPSNoise(lat,lng,noiseAcc);
  let elevation = options.elevation;
  if (elevation === undefined) {
    const base = parseFloat(document.getElementById('elevation').value);
    const varE = parseFloat(document.getElementById('elevationVariation').value);
    elevation = base + (Math.random()-0.5)*2*varE;
  }
  const point = {
    lat: coords.lat, lng: coords.lng,
    originalLat: lat, originalLng: lng,
    timestamp: ts,
    elevation,
    accuracy: options.accuracy         // undefined when not given → display '-'
  };
  track.points.push(point);
  if (!options.skipTimeUpdate) advancePickerTime(ts);
  if (!options.skipUpdate) {
    updateAllLayers();
    updatePointsList();
    updateStatus();
  }
}

function removePoint(trackIdx, pointIdx) {
  const track = tracks[trackIdx];
  if (!track || pointIdx<0 || pointIdx>=track.points.length) return;
  track.points.splice(pointIdx,1);
  updateAllLayers();
  updatePointsList();
  updateStatus();
}

function clearAll() {
  if (!confirm('Clear all tracks and points?')) return;
  tracks = [];
  currentTrackIndex = 0;
  updateAllLayers();
  updatePointsList();
  updateStatus();
  createNewTrack();
}

// ---- track management -----------------------------------------------------
function createNewTrack(name, startTime, skipUpdate = false) {
  const idx = tracks.length;
  const color = TRACK_COLORS[idx % TRACK_COLORS.length];
  let sTime = startTime || getCurrentPickerDate();
  if (!startTime && tracks.length) {
    const lastTrack = tracks[tracks.length-1];
    if (lastTrack.points.length) {
      const lastT = lastTrack.points[lastTrack.points.length-1].timestamp;
      sTime = new Date(lastT.getTime() + parseInt(document.getElementById('timeInterval').value)*1000);
    }
  }
  const track = { id: idx, name: name || `Track ${idx+1}`, points: [], color, collapsed: false, startTime: sTime };
  tracks.push(track);
  currentTrackIndex = idx;
  if (!skipUpdate) {
    updateAllLayers();
    updatePointsList();
    updateStatus();
  }
  return track;
}
function newTrack() { createNewTrack(); }

// ---- layers update --------------------------------------------------------
function updateAllLayers() {
  if (!map.getSource('tracks')) return;
  // tracks line
  const features = [];
  tracks.forEach(track => {
    if (track.points.length<2) return;
    features.push({
      type: 'Feature',
      properties: { color: track.color },
      geometry: { type:'LineString', coordinates: track.points.map(p => [p.lng, p.lat]) }
    });
  });
  map.getSource('tracks').setData({ type:'FeatureCollection', features });

  // points
  const pts = [];
  tracks.forEach((track, ti) => {
    track.points.forEach((p, pi) => {
      let speed = null;
      if (pi>0) {
        const prev = track.points[pi-1];
        speed = (calculateDistance(prev.lat,prev.lng,p.lat,p.lng)/1000) / ((p.timestamp-prev.timestamp)/3600000);
      }
      pts.push({
        type:'Feature',
        properties: {
          trackIndex: ti, pointIndex: pi,
          trackName: track.name,
          color: track.color,
          lat: p.lat, lng: p.lng,
          timestamp: p.timestamp.toISOString(),
          speed,
          elevation: p.elevation,
          accuracy: p.accuracy
        },
        geometry: { type:'Point', coordinates: [p.lng, p.lat] }
      });
    });
  });
  map.getSource('points').setData({ type:'FeatureCollection', features: pts });
  map.getSource('preview').setData(emptyFC());
}

// ---- point list panel -----------------------------------------------------
function updatePointsList() {
  const container = document.getElementById('pointsList');
  const badge = document.getElementById('pointCountBadge');
  let total = 0;
  tracks.forEach(t => total += t.points.length);
  badge.textContent = total;

  let html = '';
  tracks.forEach((track, ti) => {
    html += `<div class="gpx-track-header" onclick="toggleTrack(${ti})">
      <div class="track-info">
        <span class="track-color" style="background:${track.color}"></span>
        <span class="track-name">${track.name}</span>
        <span>(${track.points.length})</span>
      </div>
      <div class="track-controls">
        <button class="track-export-btn" onclick="event.stopPropagation(); exportTrackGPX(${ti})">Export</button>
        <span class="collapse-icon">${track.collapsed?'▶':'▼'}</span>
      </div>
    </div>`;
    if (!track.collapsed) {
      track.points.forEach((p, pi) => {
        let speed = null;
        if (pi>0) {
          const prev = track.points[pi-1];
          speed = (calculateDistance(prev.lat,prev.lng,p.lat,p.lng)/1000) / ((p.timestamp-prev.timestamp)/3600000);
        }
        const speedColor = speed ? getSpeedColorCached(speed) : '#888';
        html += `<div class="gpx-point" onclick="highlightPoint(${ti},${pi})">
          <span><span class="speed-color-dot" style="background:${speedColor}"></span>${p.lat.toFixed(4)}, ${p.lng.toFixed(4)}</span>
          <span class="time">${new Date(p.timestamp).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}</span>
        </div>`;
      });
    }
  });
  if (!total) html = '<div style="padding:20px;text-align:center;color:var(--ink-faint)">No points yet</div>';
  container.innerHTML = html;
}

function toggleTrack(idx) {
  tracks[idx].collapsed = !tracks[idx].collapsed;
  updatePointsList();
}

function exportTrackGPX(trackIndex) {
  const track = tracks[trackIndex];
  if (!track || !track.points.length) return;
  const gpx = generateGPX(track);
  downloadFile(gpx, `${track.name.replace(/\s+/g,'_')}.gpx`, 'application/gpx+xml');
}

function highlightPoint(ti, pi) {
  const track = tracks[ti];
  if (!track || pi>=track.points.length) return;
  const p = track.points[pi];
  map.flyTo({ center: [p.lng, p.lat], zoom: 15 });
}

// ---- paint mode -----------------------------------------------------------
function togglePaintMode() {
  if (!editModeEnabled) return;
  paintMode = !paintMode;
  if (paintMode) {
    paintActive = false;               // not painting until first click
  } else {
    paintActive = false;
  }
  window.paintMode = paintMode;
  window.paintActive = paintActive;
  updatePaintButton();
  map.getContainer().style.cursor = paintMode ? 'crosshair' : '';

  // When paint mode is toggled on, clear any other active tool.
  // When toggled off, default back to add‑point mode.
  if (typeof window.setTool === 'function') {
    window.setTool(paintMode ? null : 'addpoint');
  }
}

function togglePaintActive() {
  if (!paintMode) return;
  paintActive = !paintActive;
  window.paintActive = paintActive;
  updatePaintButton();
}
window.togglePaintActive = togglePaintActive;

function updatePaintButton() {
  const btn = document.getElementById('btnPaintMode');
  if (!paintMode) btn.textContent = ' 🎨 Paint';
  else if (paintActive) btn.textContent = '⏸ Painting';
  else btn.textContent = '▶ Paint Ready';
  btn.classList.toggle('active', paintMode);
}

// ---- interpolation --------------------------------------------------------
function addPointWithInterpolation(targetLat, targetLng) {
  const track = tracks[currentTrackIndex];
  if (!track || !track.points.length) { addPoint(targetLat, targetLng); return; }
  const last = track.points[track.points.length-1];
  const dist = calculateDistance(last.lat, last.lng, targetLat, targetLng);
  const timeInterval = parseInt(document.getElementById('timeInterval').value);
  const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
  let maxDist = (maxSpeed*1000/3600)*timeInterval;
  if (maxDist <= 0) maxDist = 1;
  if (dist <= maxDist) { addPoint(targetLat, targetLng); return; }
  let segments = Math.ceil(dist/maxDist);
  if (segments > MAX_INTERPOLATION_SEGMENTS) {
    segments = MAX_INTERPOLATION_SEGMENTS;
    console.warn('Too many interpolation points, capped at ' + MAX_INTERPOLATION_SEGMENTS);
  }
  const dLat = (targetLat - last.lat) / segments;
  const dLng = (targetLng - last.lng) / segments;
  const baseTs = last.timestamp;
  const intervalMs = timeInterval * 1000;
  for (let i=1; i<=segments; i++) {
    const ts = new Date(baseTs.getTime() + i * intervalMs);
    addPoint(last.lat + dLat*i, last.lng + dLng*i, {
      timestamp: ts,
      skipUpdate: true,
      skipTimeUpdate: true,
      skipDayChangeCheck: true,
      skipStops: true
    });
  }
  // after batch, update layers once
  updateAllLayers();
  updatePointsList();
  updateStatus();
  // advance picker time to after the last added point
  const lastPointTs = track.points[track.points.length-1].timestamp;
  advancePickerTime(lastPointTs);
}

// ---- time & date picker (native) ------------------------------------------
function getCurrentPickerDate() {
  const el = document.getElementById('startDatetimeLocal');
  return el && el.value ? new Date(el.value) : new Date();
}

function advancePickerTime(lastTs) {
  const interval = parseInt(document.getElementById('timeInterval').value);
  const next = new Date(lastTs.getTime() + interval*1000);
  const localStr = next.getFullYear() + '-' + pad2(next.getMonth()+1) + '-' + pad2(next.getDate()) +
    'T' + pad2(next.getHours()) + ':' + pad2(next.getMinutes()) + ':' + pad2(next.getSeconds());
  document.getElementById('startDatetimeLocal').value = localStr;
}

// ---- helpers for stops / day change ---------------------------------------
function shouldCreateNewTrackForDayChange(newTs, track) {
  if (!document.getElementById('autoNewTrack').checked || !track.points.length) return false;
  const last = track.points[track.points.length-1].timestamp;
  return new Date(last).toDateString() !== new Date(newTs).toDateString();
}
function shouldAddStop(track) {
  return track.points.length >= 5 && Math.random() < stopProbability;
}
function addRealisticStop(baseTs) {
  const stopSec = Math.random()*(10*60-30)+30;
  return new Date(baseTs.getTime() + stopSec*1000);
}
function applyGPSNoise(lat,lng,acc) {
  if (acc===0) return {lat,lng};
  const latOff = (Math.random()-0.5)*2*(acc/111000);
  const lngOff = (Math.random()-0.5)*2*(acc/(111000*Math.cos(lat*Math.PI/180)));
  return { lat: lat+latOff, lng: lng+lngOff };
}
function calculateDistance(lat1,lng1,lat2,lng2) {
  const R=6371000, dLat=(lat2-lat1)*Math.PI/180, dLng=(lng2-lng1)*Math.PI/180;
  const a=Math.sin(dLat/2)**2 + Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLng/2)**2;
  return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
}
function getSpeedColorCached(kmh) {
  if (speedColorCache[kmh]) return speedColorCache[kmh];
  let col;
  if (kmh<5) col='#48bb78'; else if (kmh<10) col='#68d391'; else if (kmh<15) col='#9ae6b4';
  else if (kmh<20) col='#fbb040'; else if (kmh<25) col='#ed8936'; else if (kmh<30) col='#f56565';
  else col='#e53e3e';
  speedColorCache[kmh]=col;
  return col;
}

// ---- preview popup --------------------------------------------------------
function updatePreviewPopup(e) {
  const popup = document.getElementById('previewPopup');
  if (!popup) return;
  const mouseLat = e.lngLat.lat;
  const mouseLng = e.lngLat.lng;
  const track = tracks[currentTrackIndex];
  let distance = null;
  let speed = null;
  let pointsToAdd = 1;
  if (track && track.points.length) {
    const last = track.points[track.points.length-1];
    distance = calculateDistance(last.lat, last.lng, mouseLat, mouseLng);
    const timeInterval = parseInt(document.getElementById('timeInterval').value);
    const maxSpeed = parseFloat(document.getElementById('maxSpeed').value);
    const maxDist = (maxSpeed*1000/3600) * timeInterval;
    if (distance <= maxDist) {
      pointsToAdd = 1;
      speed = (distance/1000) / (timeInterval/3600);
    } else {
      const segments = Math.ceil(distance / maxDist);
      pointsToAdd = segments;
      speed = (distance/segments/1000) / (timeInterval/3600);
    }
  }
  let html = `<div class="row"><span class="label">Lat, Lng</span><span class="value">${mouseLat.toFixed(5)}, ${mouseLng.toFixed(5)}</span></div>`;
  if (distance !== null) html += `<div class="row"><span class="label">Distance</span><span class="value">${distance.toFixed(1)} m</span></div>`;
  if (speed !== null) html += `<div class="row"><span class="label">Speed</span><span class="value">${speed.toFixed(1)} km/h</span></div>`;
  if (pointsToAdd > 1) html += `<div class="row"><span class="label">Will add</span><span class="value">${pointsToAdd} points</span></div>`;
  if (!distance) html += `<div class="row"><span class="label"></span><span class="value">Click to add first point</span></div>`;
  popup.innerHTML = html;
  // position near cursor
  const mapRect = map.getContainer().getBoundingClientRect();
  let left = mapRect.left + e.point.x + 15;
  let top = mapRect.top + e.point.y + 15;
  // respect drawer bounds
  const drawer = document.getElementById('gpxEditPanel');
  if (drawer && drawer.style.display !== 'none') {
    const drawerRect = drawer.getBoundingClientRect();
    if (left < drawerRect.right + 10) left = drawerRect.right + 12;
  }
  popup.style.left = left + 'px';
  popup.style.top = top + 'px';
  popup.style.display = 'block';
}

// ---- import / export ------------------------------------------------------
function handleGPXFiles(event) {
  const files = Array.from(event.target.files);
  files.forEach(file => {
    const reader = new FileReader();
    reader.onload = e => {
      if (file.name.endsWith('.json')) handleGoogleJson(e.target.result, file.name);
      else parseAndImportGPX(e.target.result, file.name);
    };
    reader.readAsText(file);
  });
  event.target.value = '';
}

// Import GPX – group points by day and create separate tracks
function parseAndImportGPX(content, filename) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(content,'text/xml');
  const allPoints = [];
  doc.querySelectorAll('trkpt').forEach(pt => {
    const lat = parseFloat(pt.getAttribute('lat'));
    const lng = parseFloat(pt.getAttribute('lon'));
    if (isNaN(lat)||isNaN(lng)) return;
    const timeEl = pt.querySelector('time');
    let ts = new Date();
    if (timeEl) ts = new Date(timeEl.textContent) || ts;
    const ele = parseFloat(pt.querySelector('ele')?.textContent || '0');

    // attempt to read accuracy from <extensions><accuracy>
    let accuracy;
    const accEl = pt.querySelector('extensions > accuracy') || pt.querySelector('accuracy');
    if (accEl) {
      const val = parseFloat(accEl.textContent);
      if (!isNaN(val)) accuracy = val;
    }

    allPoints.push({lat,lng,timestamp:ts, elevation:ele, accuracy});
  });
  if (!allPoints.length) return;
  allPoints.sort((a,b)=>a.timestamp-b.timestamp);

  // group by date
  const days = {};
  allPoints.forEach(p => {
    const dateStr = p.timestamp.toISOString().split('T')[0];
    if (!days[dateStr]) days[dateStr] = [];
    days[dateStr].push(p);
  });

  const sortedDates = Object.keys(days).sort();
  const bounds = new maplibregl.LngLatBounds();

  sortedDates.forEach((dateStr, idx) => {
    const dayPoints = days[dateStr];
    dayPoints.sort((a,b)=>a.timestamp-b.timestamp);
    const trackName = `${filename.replace('.gpx','')} - ${dateStr}`;

    let track;
    // reuse the first empty track for the very first day, otherwise create a new track
    if (idx === 0 && tracks.length && tracks[0].points.length === 0) {
      track = tracks[0];
      track.name = trackName;
      track.startTime = dayPoints[0].timestamp;
    } else {
      track = createNewTrack(trackName, dayPoints[0].timestamp, true);
    }

    dayPoints.forEach(p => {
      addPoint(p.lat, p.lng, {
        timestamp: p.timestamp,
        elevation: p.elevation,
        accuracy: p.accuracy,
        skipNoise: true,
        skipStops: true,
        skipTimeUpdate: true,
        skipDayChangeCheck: true,
        skipUpdate: true
      });
      bounds.extend([p.lng, p.lat]);
    });
  });

  map.fitBounds(bounds, {padding:40});
  updateAllLayers();
  updatePointsList();
  updateStatus();
}

// Google JSON import (same as before, with day grouping)
function handleGoogleJson(content, filename) {
  try {
    const data = JSON.parse(content);
    const locs = data.locations || [];
    if (!locs.length) return;
    const days = {};
    locs.forEach(loc => {
      const ts = new Date(loc.timestamp || loc.timestampMs);
      const dateStr = ts.toISOString().split('T')[0];
      if (!days[dateStr]) days[dateStr] = [];
      days[dateStr].push({ lat: loc.latitudeE7/1e7, lng: loc.longitudeE7/1e7, timestamp: ts, elevation: loc.altitude||0, accuracy: loc.accuracy||0 });
    });
    pendingJsonData = days;
    pendingJsonFilename = filename.replace('.json','');
    showJsonDatePicker();
  } catch(e) { alert('JSON parse error: '+e.message); }
}
function showJsonDatePicker() {
  selectedDates.clear(); lastClickedDate = null;
  const grid = document.getElementById('dateGrid'); // we didn't include in html yet, so fallback
  if (!grid) { // simple prompt
    const dates = Object.keys(pendingJsonData).sort();
    const selected = prompt('Select dates (comma separated):\n'+dates.join('\n'));
    if (!selected) return;
    selected.split(',').map(s=>s.trim()).forEach(d => { if (pendingJsonData[d]) selectedDates.add(d); });
    importSelectedJsonDates();
    return;
  }
  // (skip the modal/date grid implementation for brevity – keep existing fallback)
}
function importSelectedJsonDates() {
  if (!selectedDates.size) return;
  const sorted = Array.from(selectedDates).sort();
  let total = 0;
  const bounds = new maplibregl.LngLatBounds();
  sorted.forEach(dateStr => {
    const dayPoints = pendingJsonData[dateStr];
    dayPoints.sort((a,b)=>a.timestamp-b.timestamp);
    const track = createNewTrack(`${pendingJsonFilename} - ${dateStr}`, dayPoints[0].timestamp, true);
    dayPoints.forEach(p => {
      addPoint(p.lat,p.lng,{ timestamp:p.timestamp, elevation:p.elevation, accuracy:p.accuracy, skipNoise:true, skipStops:true, skipTimeUpdate:true, skipDayChangeCheck:true, skipUpdate: true });
      bounds.extend([p.lng,p.lat]);
      total++;
    });
  });
  if (total) map.fitBounds(bounds, {padding:40});
  updateAllLayers(); updatePointsList(); updateStatus();
}

function exportAll() {
  tracks.forEach(track => {
    if (!track.points.length) return;
    const gpx = generateGPX(track);
    downloadFile(gpx, `${track.name.replace(/\s+/g,'_')}.gpx`, 'application/gpx+xml');
  });
}
function generateGPX(track) {
  let xml = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPX Generator v2" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><name>${track.name}</name><trkseg>`;
  track.points.forEach(p => {
    xml += `<trkpt lat="${p.lat}" lon="${p.lng}"><ele>${p.elevation.toFixed(1)}</ele><time>${p.timestamp.toISOString()}</time></trkpt>\n`;
  });
  xml += `</trkseg></trk></gpx>`;
  return xml;
}
function downloadFile(content, filename, mime) {
  const blob = new Blob([content],{type:mime});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

// ---- additional control functions -----------------------------------------
function toggleEditMode() {
  editModeEnabled = !editModeEnabled;
  window.editModeEnabled = editModeEnabled;
  const btn = document.getElementById('btnEditMode');
  btn.classList.toggle('active', editModeEnabled);
  btn.textContent = editModeEnabled ? '✎ Edit (on)' : '✎ Edit';
  const drawer = document.getElementById('gpxEditPanel');
  if (!editModeEnabled) {
    if (paintMode) togglePaintMode();
    if (drawer) drawer.style.display = 'none';
    document.body.classList.remove('edit-mode');
    clearPinned();
    hidePointInfo(true);
  } else {
    if (drawer) drawer.style.display = '';
    document.body.classList.add('edit-mode');
    clearPinned();
    hidePointInfo(true);
  }
  updateStatus();
}

function shiftTrackTime(amount, unit) {
  const track = tracks[currentTrackIndex];
  if (!track || !track.points.length) return;
  let ms = unit === 'hour' ? amount*3600000 : amount*86400000;
  track.points.forEach(p => p.timestamp = new Date(p.timestamp.getTime()+ms));
  track.startTime = new Date(track.startTime.getTime()+ms);
  updateAllLayers();
  updatePointsList();
  updateStatus();
}

function randomizeAll() {
  const timeOff = (Math.random()*2-1)*30*86400000;
  const lngOff = (Math.random()*2-1)*180;
  tracks.forEach(track => {
    track.startTime = new Date(track.startTime.getTime()+timeOff);
    track.points.forEach(p => {
      p.timestamp = new Date(p.timestamp.getTime()+timeOff);
      let nl = p.lng + lngOff;
      while (nl < -180) nl+=360; while (nl >= 180) nl-=360;
      p.lng = nl; p.originalLng = nl;
    });
  });
  updateAllLayers(); updatePointsList(); updateStatus();
  const bounds = new maplibregl.LngLatBounds();
  tracks.forEach(t => t.points.forEach(p => bounds.extend([p.lng,p.lat])));
  if (!bounds.isEmpty()) map.fitBounds(bounds, {padding:40});
}

function updateStatus() {
  // minimal status – could be placed in a small bar, omitted for now
}

// controls init
function initControls() {
  document.getElementById('accuracySlider').addEventListener('input', function() {
    document.getElementById('accuracyValue').textContent = this.value;
  });
}

// About modal functions
function showAbout() {
  const modal = document.getElementById('aboutModal');
  if (modal) modal.style.display = 'flex';
}
function closeAbout() {
  const modal = document.getElementById('aboutModal');
  if (modal) modal.style.display = 'none';
}

// Export helpers for keyboard navigation
window.getPinnedPoint = function() {
  return pinnedPoint;
};
window.setPinnedPoint = function(ti, pi) {
  const track = tracks[ti];
  if (!track || pi < 0 || pi >= track.points.length) return;
  pinnedPoint = { trackIndex: ti, pointIndex: pi };
  if (window.lastSelectedPoint !== undefined) {
    window.lastSelectedPoint = { trackIndex: ti, pointIndex: pi };
  }
  showPointInfoForPinned();
};

// automatically start in view mode (edit off)
editModeEnabled = false;
