/**
 * Canvas-based renderer for visit markers to improve performance with large datasets
 */
class CanvasVisitRenderer {
    constructor(map) {
        this.map = map;
        this.canvas = null;
        this.ctx = null;
        this.visits = [];
        this.tooltipDiv = null;
        this.hoveredVisit = null;
        this.devicePixelRatio = window.devicePixelRatio || 1;
        
        this.init();
    }
    
    init() {
        // Create canvas element
        this.canvas = document.createElement('canvas');
        this.canvas.style.position = 'absolute';
        this.canvas.style.top = '0';
        this.canvas.style.left = '0';
        this.canvas.style.pointerEvents = 'auto';
        this.canvas.style.zIndex = '200';
        
        this.ctx = this.canvas.getContext('2d');
        
        // Create tooltip div
        this.tooltipDiv = document.createElement('div');
        this.tooltipDiv.className = 'canvas-visit-tooltip';
        this.tooltipDiv.style.position = 'absolute';
        this.tooltipDiv.style.display = 'none';
        this.tooltipDiv.style.background = 'rgba(0, 0, 0, 0.8)';
        this.tooltipDiv.style.color = 'white';
        this.tooltipDiv.style.padding = '8px 12px';
        this.tooltipDiv.style.borderRadius = '4px';
        this.tooltipDiv.style.fontSize = '12px';
        this.tooltipDiv.style.pointerEvents = 'none';
        this.tooltipDiv.style.zIndex = '1000';
        this.tooltipDiv.style.maxWidth = '200px';
        
        // Add canvas and tooltip to map container
        this.map.getContainer().appendChild(this.canvas);
        this.map.getContainer().appendChild(this.tooltipDiv);
        
        // Bind events
        this.map.on('viewreset', this.onViewReset.bind(this));
        this.map.on('zoom', this.onZoom.bind(this));
        this.map.on('move', this.onMove.bind(this));
        this.map.on('resize', this.onResize.bind(this));
        
        this.canvas.addEventListener('mousemove', this.onMouseMove.bind(this));
        this.canvas.addEventListener('mouseleave', this.onMouseLeave.bind(this));
        
        this.updateCanvasSize();
    }
    
    updateCanvasSize() {
        const size = this.map.getSize();
        const canvas = this.canvas;
        
        // Set actual size in memory (scaled to account for extra pixel density)
        canvas.width = size.x * this.devicePixelRatio;
        canvas.height = size.y * this.devicePixelRatio;
        
        // Scale the canvas back down using CSS
        canvas.style.width = size.x + 'px';
        canvas.style.height = size.y + 'px';
        
        // Scale the drawing context so everything draws at the higher resolution
        this.ctx.scale(this.devicePixelRatio, this.devicePixelRatio);
        
        this.redraw();
    }
    
    onViewReset() {
        this.updateCanvasSize();
    }
    
    onZoom() {
        this.redraw();
    }
    
    onMove() {
        this.redraw();
    }
    
    onResize() {
        this.updateCanvasSize();
    }
    
    setVisits(visits) {
        this.visits = visits;
        this.redraw();
    }
    
    addVisit(visit) {
        this.visits.push(visit);
        this.redraw();
    }
    
    clearVisits() {
        this.visits = [];
        this.redraw();
    }
    
    redraw() {
        if (!this.ctx) return;
        
        const size = this.map.getSize();
        this.ctx.clearRect(0, 0, size.x, size.y);
        
        this.visits.forEach(visit => {
            this.drawVisit(visit);
        });
    }
    
    drawVisit(visit) {
        const point = this.map.latLngToContainerPoint([visit.lat, visit.lng]);
        
        // Skip if point is outside visible area (with some margin)
        const size = this.map.getSize();
        const margin = 100;
        if (point.x < -margin || point.x > size.x + margin || 
            point.y < -margin || point.y > size.y + margin) {
            return;
        }
        
        const ctx = this.ctx;
        
        // Calculate radius using logarithmic scale
        const durationHours = visit.totalDurationMs / (1000 * 60 * 60);
        const baseRadius = 15;
        const maxRadius = 100;
        const minRadius = 15;
        
        const logScale = Math.log(1 + durationHours) / Math.log(1 + 24);
        const radius = Math.min(maxRadius, Math.max(minRadius, baseRadius + (logScale * (maxRadius - baseRadius))));
        
        // Draw outer circle (visit area)
        ctx.beginPath();
        ctx.arc(point.x, point.y, radius, 0, 2 * Math.PI);
        ctx.fillStyle = this.hexToRgba(this.lightenHexColor(visit.color, 20), 0.1);
        ctx.fill();
        ctx.strokeStyle = visit.color;
        ctx.lineWidth = 1;
        ctx.stroke();
        
        // Draw inner marker
        ctx.beginPath();
        ctx.arc(point.x, point.y, 5, 0, 2 * Math.PI);
        ctx.fillStyle = this.lightenHexColor(visit.color, 20);
        ctx.fill();
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 1;
        ctx.stroke();
        
        // Store hit area for mouse events
        visit._hitArea = {
            x: point.x,
            y: point.y,
            radius: Math.max(radius, 10) // Minimum hit area
        };
    }
    
    onMouseMove(event) {
        const rect = this.canvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;
        
        let hoveredVisit = null;
        
        // Check if mouse is over any visit
        for (const visit of this.visits) {
            if (visit._hitArea) {
                const dx = x - visit._hitArea.x;
                const dy = y - visit._hitArea.y;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= visit._hitArea.radius) {
                    hoveredVisit = visit;
                    break;
                }
            }
        }
        
        if (hoveredVisit !== this.hoveredVisit) {
            this.hoveredVisit = hoveredVisit;
            
            if (hoveredVisit) {
                this.showTooltip(hoveredVisit, event.clientX, event.clientY);
                this.canvas.style.cursor = 'pointer';
            } else {
                this.hideTooltip();
                this.canvas.style.cursor = 'default';
            }
        } else if (hoveredVisit) {
            // Update tooltip position
            this.updateTooltipPosition(event.clientX, event.clientY);
        }
    }
    
    onMouseLeave() {
        this.hoveredVisit = null;
        this.hideTooltip();
        this.canvas.style.cursor = 'default';
    }
    
    showTooltip(visit, clientX, clientY) {
        const totalDurationText = this.humanizeDuration(visit.totalDurationMs);
        const visitCount = visit.visits.length;
        const visitText = visitCount === 1 ? 'visit' : 'visits';
        
        this.tooltipDiv.innerHTML = `
            <div style="font-weight: bold; margin-bottom: 4px;">${visit.place.name}</div>
            <div>${visitCount} ${visitText} - Total: ${totalDurationText}</div>
        `;
        
        this.updateTooltipPosition(clientX, clientY);
        this.tooltipDiv.style.display = 'block';
    }
    
    updateTooltipPosition(clientX, clientY) {
        const tooltip = this.tooltipDiv;
        const offset = 10;
        
        // Position tooltip, ensuring it stays within viewport
        let left = clientX + offset;
        let top = clientY + offset;
        
        const tooltipRect = tooltip.getBoundingClientRect();
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;
        
        if (left + tooltipRect.width > viewportWidth) {
            left = clientX - tooltipRect.width - offset;
        }
        
        if (top + tooltipRect.height > viewportHeight) {
            top = clientY - tooltipRect.height - offset;
        }
        
        tooltip.style.left = left + 'px';
        tooltip.style.top = top + 'px';
    }
    
    hideTooltip() {
        this.tooltipDiv.style.display = 'none';
    }
    
    lightenHexColor(hex, percent) {
        // Remove # if present
        hex = hex.replace('#', '');
        
        // Parse RGB values
        const r = parseInt(hex.substr(0, 2), 16);
        const g = parseInt(hex.substr(2, 2), 16);
        const b = parseInt(hex.substr(4, 2), 16);
        
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
    
    hexToRgba(hex, alpha) {
        hex = hex.replace('#', '');
        const r = parseInt(hex.substr(0, 2), 16);
        const g = parseInt(hex.substr(2, 2), 16);
        const b = parseInt(hex.substr(4, 2), 16);
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
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
        if (this.canvas && this.canvas.parentNode) {
            this.canvas.parentNode.removeChild(this.canvas);
        }
        if (this.tooltipDiv && this.tooltipDiv.parentNode) {
            this.tooltipDiv.parentNode.removeChild(this.tooltipDiv);
        }
        
        this.map.off('viewreset', this.onViewReset);
        this.map.off('zoom', this.onZoom);
        this.map.off('move', this.onMove);
        this.map.off('resize', this.onResize);
    }
}
