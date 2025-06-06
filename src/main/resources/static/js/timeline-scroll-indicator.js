/**
 * Timeline Scroll Indicator
 * Shows a vertical scroll indicator on the left of the timeline container
 */
class TimelineScrollIndicator {
    constructor() {
        this.timelineContainer = null;
        this.timeline = null;
        this.scrollIndicator = null;
        this.scrollThumb = null;
        this.scrollListener = null;
        this.resizeListener = null;
    }

    init() {
        this.timelineContainer = document.querySelector('.timeline-container');
        this.timeline = document.querySelector('.timeline');
        if (!this.timelineContainer || !this.timeline) return;

        // Create the scroll indicator
        this.createScrollIndicator();

        // Set up scroll listener
        this.scrollListener = () => this.updateScrollPosition();
        this.timeline.addEventListener('scroll', this.scrollListener);
        
        // Set up resize listener
        this.resizeListener = () => this.updateScrollPosition();
        window.addEventListener('resize', this.resizeListener);
        
        // Initial update
        this.updateScrollPosition();
    }

    cleanup() {
        // Remove scroll indicator
        if (this.scrollIndicator && this.scrollIndicator.parentNode) {
            this.scrollIndicator.parentNode.removeChild(this.scrollIndicator);
        }

        // Remove event listeners
        if (this.scrollListener && this.timeline) {
            this.timeline.removeEventListener('scroll', this.scrollListener);
        }
        if (this.resizeListener) {
            window.removeEventListener('resize', this.resizeListener);
        }

        // Reset state
        this.scrollIndicator = null;
        this.scrollThumb = null;
        this.scrollListener = null;
        this.resizeListener = null;
    }

    createScrollIndicator() {
        // Create the main indicator container
        this.scrollIndicator = document.createElement('div');
        this.scrollIndicator.className = 'timeline-scroll-indicator';
        
        // Create the scroll track (the line)
        const scrollTrack = document.createElement('div');
        scrollTrack.className = 'scroll-track';
        
        // Create the scroll thumb (the position marker)
        this.scrollThumb = document.createElement('div');
        this.scrollThumb.className = 'scroll-thumb';
        
        // Assemble the indicator
        scrollTrack.appendChild(this.scrollThumb);
        this.scrollIndicator.appendChild(scrollTrack);
        
        // Add to timeline
        this.timeline.appendChild(this.scrollIndicator);
    }

    updateScrollPosition() {
        if (!this.timeline || !this.scrollThumb) return;

        const scrollTop = this.timeline.scrollTop;
        const scrollHeight = this.timeline.scrollHeight;
        const clientHeight = this.timeline.clientHeight;
        
        // Calculate scroll percentage
        const maxScroll = scrollHeight - clientHeight;
        const scrollPercentage = maxScroll > 0 ? (scrollTop / maxScroll) : 0;
        
        // Update thumb position
        const trackHeight = this.scrollIndicator.querySelector('.scroll-track').clientHeight;
        const thumbHeight = this.scrollThumb.clientHeight;
        const maxThumbPosition = trackHeight - thumbHeight;
        const thumbPosition = scrollPercentage * maxThumbPosition;
        
        this.scrollThumb.style.transform = `translateY(${thumbPosition}px)`;
        
        // Update opacity based on scroll activity
        this.scrollIndicator.style.opacity = maxScroll > 0 ? '1' : '0.3';
    }
}

// Global instance to be controlled from the main script
window.timelineScrollIndicator = null;
