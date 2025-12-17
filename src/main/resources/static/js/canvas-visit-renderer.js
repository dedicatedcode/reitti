/**
 * Canvas-based renderer for visit markers using Leaflet's Canvas renderer
 */
class CanvasVisitRenderer {
    constructor(map) {
        this.map = map;
        this.visits = [];
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
    }
    
    setVisits(visits) {
        this.clearVisits();
        this.visits = visits;
        this.createVisitMarkers();
    }
    
    addVisit(visit) {
        this.visits.push(visit);
        this.createVisitMarker(visit);
    }
    
    clearVisits() {
        // Remove all existing visit markers
        this.visitMarkers.forEach(marker => {
            this.map.removeLayer(marker);
        });
        this.visitMarkers = [];
        this.visits = [];
    }
    
    createVisitMarkers() {
        this.visits.forEach(visit => {
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
            interactive: false // Make non-interactive to avoid interfering with inner marker
        });
        
        // Create inner marker
        const innerMarker = L.circleMarker([visit.lat, visit.lng], {
            radius: 5,
            fillColor: this.lightenHexColor(visit.color, 20),
            fillOpacity: 1,
            color: '#fff',
            weight: 1,
            renderer: this.canvasRenderer,
            interactive: true
        });
        
        // Create tooltip content
        const totalDurationText = this.humanizeDuration(visit.totalDurationMs);
        const visitCount = visit.visits.length;
        const visitText = visitCount === 1 ? 'visit' : 'visits';
        
        const tooltipContent = `
            <div style="font-weight: bold; margin-bottom: 4px;">${visit.place.name}</div>
            <div>${visitCount} ${visitText} — Total: ${totalDurationText}</div>
        `;
        let tooltip = L.tooltip({
            content: `<div class="visit-title">${visit.place.name}</div>
                             <div class="visit-description">
                                 ${visitCount} ${visitText} - Total: ${totalDurationText}
                             </div>`,
            className: 'visit-popup',
            permanent: false
        });
        // Bind tooltip only to inner marker (outer circle is non-interactive)
        innerMarker.bindTooltip(tooltip);

        // Add both circles to map
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
