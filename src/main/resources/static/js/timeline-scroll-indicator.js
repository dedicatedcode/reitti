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
        this.scrollEndTimer = null;
        this.snapPositions = [];
        this.isSnapping = false;
    }

    init() {
        this.timelineContainer = document.querySelector('.timeline-container');
        this.timeline = document.querySelector('.timeline');
        if (!this.timelineContainer || !this.timeline) return;

        // Create the scroll indicator
        this.createScrollIndicator();

        // Set up scroll listener
        this.scrollListener = () => this.handleScroll();
        this.timeline.addEventListener('scroll', this.scrollListener);
        
        // Set up resize listener
        this.resizeListener = () => this.updateScrollPosition();
        window.addEventListener('resize', this.resizeListener);
        
        // Calculate snap positions based on timeline entries
        this.calculateSnapPositions();
        
        // Initial update
        this.updateScrollPosition();
    }

    cleanup() {
        // Clear any pending timers
        if (this.scrollEndTimer) {
            clearTimeout(this.scrollEndTimer);
        }

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
        this.scrollEndTimer = null;
        this.snapPositions = [];
        this.isSnapping = false;
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

    calculateSnapPositions() {
        const timelineEntries = this.timelineContainer.querySelectorAll('.timeline-entry');
        const entryCount = timelineEntries.length;
        
        if (entryCount === 0) {
            this.snapPositions = [];
            return;
        }

        // Calculate snap positions based on entry count
        // Each entry gets an equal portion of the scroll track
        this.snapPositions = [];
        for (let i = 0; i < entryCount; i++) {
            const position = i / (entryCount - 1); // 0 to 1
            this.snapPositions.push(Math.max(0, Math.min(1, position)));
        }
    }

    handleScroll() {
        if (this.isSnapping) return;

        // Update position immediately for smooth scrolling
        this.updateScrollPosition();

        // Clear existing timer
        if (this.scrollEndTimer) {
            clearTimeout(this.scrollEndTimer);
        }

        // Set timer to detect scroll end
        this.scrollEndTimer = setTimeout(() => {
            this.snapToNearestPosition();
        }, 150); // Wait 150ms after scroll stops
    }

    snapToNearestPosition() {
        if (!this.timeline || !this.scrollThumb || this.snapPositions.length === 0) return;

        const scrollTop = this.timeline.scrollTop;
        const scrollHeight = this.timeline.scrollHeight;
        const clientHeight = this.timeline.clientHeight;
        const maxScroll = scrollHeight - clientHeight;
        
        if (maxScroll <= 0) return;

        const currentScrollPercentage = scrollTop / maxScroll;
        
        // Find the nearest snap position
        let nearestPosition = this.snapPositions[0];
        let minDistance = Math.abs(currentScrollPercentage - nearestPosition);
        
        for (let i = 1; i < this.snapPositions.length; i++) {
            const distance = Math.abs(currentScrollPercentage - this.snapPositions[i]);
            if (distance < minDistance) {
                minDistance = distance;
                nearestPosition = this.snapPositions[i];
            }
        }

        // Calculate target scroll position
        const targetScrollTop = nearestPosition * maxScroll;
            this.timeline.scrollTo({
                top: targetScrollTop,
                behavior: 'smooth'
            });
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
