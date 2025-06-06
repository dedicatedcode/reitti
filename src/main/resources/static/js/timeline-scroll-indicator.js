/**
 * Timeline Scroll Indicator
 * Adds vertical scroll indicators to timeline entries based on their position in the viewport
 */
class TimelineScrollIndicator {
    constructor() {
        this.timelineContainer = null;
        this.entries = [];
        this.scrollListener = null;
        this.resizeListener = null;
        this.mutationObserver = null;
    }

    init() {
        this.timelineContainer = document.querySelector('.timeline-container');
        if (!this.timelineContainer) return;

        // Set up scroll listener
        this.scrollListener = () => this.updateIndicators();
        this.timelineContainer.addEventListener('scroll', this.scrollListener);
        
        // Set up resize listener
        this.resizeListener = () => this.updateIndicators();
        window.addEventListener('resize', this.resizeListener);
        
        // Set up mutation observer to handle dynamic content
        this.setupMutationObserver();
        
        // Initial update
        this.updateEntries();
        this.updateIndicators();
    }

    cleanup() {
        // Remove all scroll indicators
        if (this.timelineContainer) {
            const indicators = this.timelineContainer.querySelectorAll('.scroll-indicator');
            indicators.forEach(indicator => indicator.remove());
        }

        // Remove event listeners
        if (this.scrollListener && this.timelineContainer) {
            this.timelineContainer.removeEventListener('scroll', this.scrollListener);
        }
        if (this.resizeListener) {
            window.removeEventListener('resize', this.resizeListener);
        }

        // Disconnect mutation observer
        if (this.mutationObserver) {
            this.mutationObserver.disconnect();
        }

        // Reset state
        this.entries = [];
        this.scrollListener = null;
        this.resizeListener = null;
        this.mutationObserver = null;
    }

    setupMutationObserver() {
        this.mutationObserver = new MutationObserver(() => {
            this.updateEntries();
            this.updateIndicators();
        });

        this.mutationObserver.observe(this.timelineContainer, {
            childList: true,
            subtree: true
        });
    }

    updateEntries() {
        this.entries = Array.from(this.timelineContainer.querySelectorAll('.timeline-entry'));
        
        // Add scroll indicator elements to entries that don't have them
        this.entries.forEach(entry => {
            if (!entry.querySelector('.scroll-indicator')) {
                const indicator = document.createElement('div');
                indicator.className = 'scroll-indicator';
                entry.appendChild(indicator);
            }
        });
    }

    updateIndicators() {
        if (!this.timelineContainer || this.entries.length === 0) return;

        const containerRect = this.timelineContainer.getBoundingClientRect();
        const containerTop = containerRect.top;
        const containerBottom = containerRect.bottom;
        const containerHeight = containerRect.height;

        this.entries.forEach((entry, index) => {
            const indicator = entry.querySelector('.scroll-indicator');
            if (!indicator) return;

            const entryRect = entry.getBoundingClientRect();
            const entryTop = entryRect.top;
            const entryBottom = entryRect.bottom;
            const entryHeight = entryRect.height;

            // Calculate visibility and position
            let visibility = 0;
            let position = 'below'; // 'above', 'visible', 'below'

            if (entryBottom < containerTop) {
                // Entry is above viewport
                position = 'above';
                visibility = 0;
            } else if (entryTop > containerBottom) {
                // Entry is below viewport
                position = 'below';
                visibility = 0;
            } else {
                // Entry is at least partially visible
                position = 'visible';
                
                // Calculate how much of the entry is visible
                const visibleTop = Math.max(entryTop, containerTop);
                const visibleBottom = Math.min(entryBottom, containerBottom);
                const visibleHeight = Math.max(0, visibleBottom - visibleTop);
                visibility = visibleHeight / entryHeight;
            }

            // Update indicator
            this.updateIndicator(indicator, position, visibility, index, this.entries.length);
        });
    }

    updateIndicator(indicator, position, visibility, index, totalEntries) {
        // Remove existing classes
        indicator.classList.remove('above', 'visible', 'below', 'fully-visible', 'partially-visible');
        
        // Add position class
        indicator.classList.add(position);
        
        if (position === 'visible') {
            if (visibility >= 0.9) {
                indicator.classList.add('fully-visible');
            } else {
                indicator.classList.add('partially-visible');
            }
            
            // Set opacity based on visibility
            indicator.style.opacity = Math.max(0.3, visibility);
        } else {
            indicator.style.opacity = '0.6';
        }

        // Add progress indicator for scroll position
        const progress = (index + 1) / totalEntries;
        indicator.style.setProperty('--scroll-progress', `${progress * 100}%`);
    }
}

// Global instance to be controlled from the main script
window.timelineScrollIndicator = null;
