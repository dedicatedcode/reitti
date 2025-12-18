/**
 * Canvas-based renderer for visit markers using Leaflet's Canvas renderer
 */
class CanvasVisitRenderer {
    constructor(map) {
        this.map = map;
        this.allVisits = [];
        this.visibleVisits = [];
        this.visitMarkers = [];
        this.canvasRenderer = null;
        
        this.init();
    }
    
    init() {
        // Create a Canvas renderer instance for high-performance rendering
        this.canvasRenderer = L.canvas({ 
            padding: 0.1,
            tolerance: 5 // Extend click tolerance for better interaction
        });
        
        // Add the canvas renderer to the map
        this.map.addLayer(this.canvasRenderer);
        
        // Listen for zoom changes to update visible visits
        this.map.on('zoomend', () => {
            this.updateVisibleVisits();
        });
    }
    
    setVisits(visits) {
        this.clearVisits();
        this.allVisits = visits;
        this.updateVisibleVisits();
    }
    
    addVisit(visit) {
        this.allVisits.push(visit);
        this.updateVisibleVisits();
    }
    
    clearVisits() {
        // Remove all existing visit markers
        this.visitMarkers.forEach(marker => {
            this.map.removeLayer(marker);
        });
        this.visitMarkers = [];
        this.allVisits = [];
        this.visibleVisits = [];
    }
    
    updateVisibleVisits() {
        const zoom = this.map.getZoom();
        
        // Filter visits based on zoom level and duration
        let minDurationMs;
        if (zoom >= 15) {
            minDurationMs = 5 * 60 * 1000; // 5 minutes at high zoom
        } else if (zoom >= 12) {
            minDurationMs = 30 * 60 * 1000; // 30 minutes at medium zoom
        } else if (zoom >= 10) {
            minDurationMs = 2 * 60 * 60 * 1000; // 2 hours at low zoom
        } else {
            minDurationMs = 6 * 60 * 60 * 1000; // 6+ hours at very low zoom
        }
        
        this.visibleVisits = this.allVisits.filter(visit => 
            visit.totalDurationMs >= minDurationMs
        );
        
        this.renderVisibleVisits();
    }
    
    renderVisibleVisits() {
        // Clear existing markers
        this.visitMarkers.forEach(marker => {
            this.map.removeLayer(marker);
        });
        this.visitMarkers = [];
        
        // Create markers for visible visits
        this.visibleVisits.forEach(visit => {
            this.createVisitMarker(visit);
        });
    }
    
    createVisitMarkers() {
        this.visibleVisits.forEach(visit => {
            this.createVisitMarker(visit);
        });
    }
    
    createVisitMarker(visit) {
        // Calculate radius using logarithmic scale
        const durationHours = visit.totalDurationMs / (1000 * 60 * 60);
        const baseRadius = 15;
        const maxRadius = 50;
        const minRadius = 15;
        
        const logScale = Math.log(1 + durationHours) / Math.log(1 + 24);
        const radius = Math.min(maxRadius, Math.max(minRadius, baseRadius + (logScale * (maxRadius - baseRadius))));

        // Create outer circle (visit area)
        const outerCircle = L.circle([visit.lat, visit.lng], {
            radius: radius * 5, // Convert to meters (approximate)
            fillColor:  this.lightenHexColor(visit.color, 20),
            fillOpacity: 0.1,
            color: visit.color,
            weight: 1,
            renderer: this.canvasRenderer,
            interactive: true
        });
        
        // Create inner marker
        const innerMarker = L.circleMarker([visit.lat, visit.lng], {
            radius: 5,
            fillOpacity: 1,
            fillColor: this.lightenHexColor(visit.color, 80),
            color: '#000',
            weight: 1,
            renderer: this.canvasRenderer,
            interactive: true
        });
        
        // Create tooltip content
        const totalDurationText = this.humanizeDuration(visit.totalDurationMs);
        const visitCount = visit.visits.length;
        const visitText = visitCount === 1 ? 'visit' : 'visits';
        
        let tooltip = L.tooltip({
            content: `<div class="visit-title">${visit.place.name}</div>
                             <div class="visit-description">
                                 ${visitCount} ${visitText} - Total: ${totalDurationText}
                             </div>`,
            className: 'visit-popup',
            permanent: false
        });
        innerMarker.bindTooltip(tooltip);
        outerCircle.bindTooltip(tooltip);

        this.map.addLayer(outerCircle);
        this.map.addLayer(innerMarker);
        
        // Store references for cleanup
        this.visitMarkers.push(outerCircle, innerMarker);
    }
    
    lightenHexColor(hex, percent) {
        // Remove # if present
        hex = hex.replace('#', '');
        // Parse RGB values
        const r = parseInt(hex.slice(0, 2), 16);
        const g = parseInt(hex.slice(2, 4), 16);
        const b = parseInt(hex.slice(4, 6), 16);
        
        // Lighten each component
        const newR = Math.min(255, Math.floor(r + (255 - r) * (percent / 100)));
        const newG = Math.min(255, Math.floor(g + (255 - g) * (percent / 100)));
        const newB = Math.min(255, Math.floor(b + (255 - b) * (percent / 100)));
        
        // Convert back to hex
        return '#' + 
            newR.toString(16).padStart(2, '0') +
            newG.toString(16).padStart(2, '0') +
            newB.toString(16).padStart(2, '0');
    }
    
    humanizeDuration(ms) {
        const hours = Math.floor(ms / (1000 * 60 * 60));
        const minutes = Math.floor((ms % (1000 * 60 * 60)) / (1000 * 60));
        
        if (hours > 0) {
            return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
        } else {
            return `${minutes}m`;
        }
    }
    
    destroy() {
        this.clearVisits();
        
        if (this.canvasRenderer) {
            this.map.removeLayer(this.canvasRenderer);
            this.canvasRenderer = null;
        }
    }
}
