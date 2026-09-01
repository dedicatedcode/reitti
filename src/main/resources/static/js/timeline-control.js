class TimelineControl {

    constructor(timeline) {
        this.timeline = timeline;
        this.timeline.innerHTML = `
             <div class="time-bounds">
                <span id="start-label" class="bound-label">--</span>
                <span id="end-label" class="bound-label">--</span>
            </div>
            <div class="scrub-bar" role="slider" tabindex="0" aria-label="Time position"
                 aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
                <canvas class="activity-graph" aria-hidden="true"></canvas>
                <div class="scrub-track"></div>
                <div class="scrub-progress"></div>
                <div class="scrub-thumb"></div>
                <div class="scrub-bubble hidden"></div>
            </div>
        `;

        this.scrubBar = timeline.querySelector('.scrub-bar');
        this.activityCanvas = timeline.querySelector('.activity-graph');
        this.progressEl = timeline.querySelector('.scrub-progress');
        this.thumbEl = timeline.querySelector('.scrub-thumb');
        this.bubbleEl = timeline.querySelector('.scrub-bubble');
        this.startLabel = timeline.querySelector('#start-label');
        this.endLabel = timeline.querySelector('#end-label');

        this.eventListeners = {};
        this.aggregate = false;
        this.minTimestamp = 0;
        this.maxTimestamp = 0;
        this.value = 0;
        this.isDragging = false;
        this.isHovering = false;
        this.isAnimating = false;
        this.rafId = null;
        this.pendingValue = null;
        this.activityBins = null;
        this._init();

        this._resizeListener = () => this._drawActivity();
        window.addEventListener('resize', this._resizeListener);
    }

    _init() {
        this.scrubBar.addEventListener('pointerenter', () => {
            this.isHovering = true;
            this._updateBubbleVisibility();
            this._updateBubble();
        });
        this.scrubBar.addEventListener('pointerdown', (e) => {
            e.stopPropagation();
            this.scrubBar.setPointerCapture(e.pointerId);
            this.isDragging = true;
            this._updateBubbleVisibility();
            this._seekFromPointer(e);
        });
        this.scrubBar.addEventListener('pointermove', (e) => {
            e.stopPropagation();
            if (this.isDragging) {
                this._seekFromPointer(e);
            } else {
                this._showBubble(e);
            }
        });
        const endDrag = (e) => {
            if (!this.isDragging) return;
            this.isDragging = false;
            this._updateBubbleVisibility();
            try {
                this.scrubBar.releasePointerCapture(e.pointerId);
            } catch (_) {
                // pointer capture already released
            }
        };
        this.scrubBar.addEventListener('pointerup', endDrag);
        this.scrubBar.addEventListener('pointercancel', endDrag);
        this.scrubBar.addEventListener('pointerleave', () => {
            this.isHovering = false;
            this._updateBubbleVisibility();
        });
        this.scrubBar.addEventListener('keydown', (e) => {
            const step = this.aggregate ? 60 : 15 * 60;
            let handled = true;
            switch (e.key) {
                case 'ArrowRight':
                case 'ArrowUp':
                    this._userSeek(this.value + step);
                    break;
                case 'ArrowLeft':
                case 'ArrowDown':
                    this._userSeek(this.value - step);
                    break;
                case 'Home':
                    this._userSeek(this.minTimestamp);
                    break;
                case 'End':
                    this._userSeek(this.maxTimestamp);
                    break;
                default:
                    handled = false;
            }
            if (handled) e.preventDefault();
        });
    }

    _seekFromPointer(e) {
        const rect = this.scrubBar.getBoundingClientRect();
        const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
        const step = this.aggregate ? 60 : 1;
        const raw = this.minTimestamp + ratio * (this.maxTimestamp - this.minTimestamp);
        this._userSeek(Math.round(raw / step) * step);
    }

    _userSeek(value) {
        this._scheduleSeek(Math.min(this.maxTimestamp, Math.max(this.minTimestamp, value)));
    }

    /**
     * rAF-throttled user seek: applies the pending value once per frame and emits.
     * Programmatic updates (setOffset) never go through here and never emit.
     */
    _scheduleSeek(value) {
        this.pendingValue = value;
        if (this.rafId !== null) return;
        this.rafId = requestAnimationFrame(() => {
            this.rafId = null;
            const v = this.pendingValue;
            this.pendingValue = null;
            this.value = v;
            this._render();
            this.emit('offsetChanged', {offset: v - this.minTimestamp, value: v});
        });
    }

    _render() {
        const span = this.maxTimestamp - this.minTimestamp;
        const pct = span > 0 ? ((this.value - this.minTimestamp) / span) * 100 : 0;
        this.scrubBar.style.setProperty('--scrub-pct', pct + '%');
        this._updateLabels();
        this.scrubBar.setAttribute('aria-valuemin', this.minTimestamp);
        this.scrubBar.setAttribute('aria-valuemax', this.maxTimestamp);
        this.scrubBar.setAttribute('aria-valuenow', Math.round(this.value));
        this.scrubBar.setAttribute('aria-valuetext', this._formatValue(this.value));
        this._updateBubble();
    }

    _updateLabels() {
        const tz = getUserTimezone() || 'UTC';
        if (this.aggregate) {
            this.startLabel.innerText = "00:00";
            this.endLabel.innerText = "23:59";
        } else {
            // Use your helper for the bounds
            this.startLabel.innerText = this._formatDateTime(this.minTimestamp * 1000, tz);
            this.endLabel.innerText = this._formatDateTime(this.maxTimestamp * 1000, tz);
        }
    }

    /**
     * Helper to ensure boundary labels also respect the selected timezone
     */
    _formatDateTime(ms, tz) {
        return new Date(ms).toLocaleString(window.userSettings.selectedLocale, {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
            timeZone: tz,
            hour12: false
        });
    }

    _formatValue(value) {
        const tz = getUserTimezone() || 'UTC';
        const locale = window.userSettings.selectedLocale || 'de-DE';
        if (this.aggregate) {
            return new Date(value * 1000).toLocaleTimeString(locale, {
                hour: '2-digit', minute: '2-digit', timeZone: 'UTC'
            });
        }
        return this._formatDateTime(value * 1000, tz);
    }

    _showBubble(e) {
        const rect = this.scrubBar.getBoundingClientRect();
        const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
        const step = this.aggregate ? 60 : 1;
        const value = Math.round((this.minTimestamp + ratio * (this.maxTimestamp - this.minTimestamp)) / step) * step;
        this.bubbleEl.style.left = (ratio * 100) + '%';
        this.bubbleEl.innerText = this._formatValue(value);
    }

    /**
     * Keeps a visible bubble glued to the knob: while dragging it follows the
     * pointer (the knob tracks the pointer), while replaying it wanders with
     * the animation-driven knob. Render calls this on every update.
     */
    _updateBubble() {
        if (!this.bubbleEl || this.bubbleEl.classList.contains('hidden')) return;
        const span = this.maxTimestamp - this.minTimestamp;
        const pct = span > 0 ? ((this.value - this.minTimestamp) / span) * 100 : 0;
        this.bubbleEl.style.left = pct + '%';
        this.bubbleEl.innerText = this._formatValue(this.value);
    }

    _updateBubbleVisibility() {
        this.bubbleEl.classList.toggle('hidden', !(this.isDragging || this.isHovering || this.isAnimating));
    }

    /**
     * While a replay is running the bubble stays visible and wanders with the
     * knob (replay calls setOffset every frame, which re-positions it).
     */
    setAnimating(animating) {
        this.isAnimating = !!animating;
        this._updateBubbleVisibility();
        if (this.isAnimating) this._updateBubble();
    }

    setup(config) {
        if (config.aggregate) {
            this.aggregate = true;
            this.minTimestamp = 0;
            this.maxTimestamp = 86400;
        } else {
            this.aggregate = false;
            this.minTimestamp = config.minTimestamp;
            this.maxTimestamp = config.maxTimestamp;
        }
        this.value = this.minTimestamp;
        this._render();
        this._drawActivity();
    }

    setOffset(offset) {
        this.value = this.minTimestamp + offset;
        this._render();
    }

    getOffset() {
        return this.value - this.minTimestamp;
    }

    getValue() {
        return parseInt(this.value);
    }

    getSelectedDays() {
        this.dayButtons = document.getElementsByClassName('day-btn active');
        const days = [];
        for (let i = 0; i < this.dayButtons.length; i++) {
            days.push(parseInt(this.dayButtons[i].dataset.day));
        }
        return days;
    }

    getMaxOffset() {
        return this.maxTimestamp - this.minTimestamp;
    }

    isIdle() {
        return (this.value - this.minTimestamp) === 0;
    }

    isVisible() {
        return !this.timeline.classList.contains('hidden');
    }

    hide() {
        this.timeline.classList.add('hidden');
    }

    show() {
        this.timeline.classList.remove('hidden');
    }

    /** Faded activity histogram drawn behind the scrub track (one bin per bar). */
    setActivitySeries(bins) {
        this.activityBins = bins;
        this._drawActivity();
    }

    _drawActivity() {
        const canvas = this.activityCanvas;
        if (!canvas) return;
        const width = canvas.offsetWidth || 0;
        const height = canvas.offsetHeight || 0;
        if (!width || !height) return;
        if (canvas.width !== width) canvas.width = width;
        if (canvas.height !== height) canvas.height = height;

        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, width, height);

        const bins = this.activityBins;
        if (!bins || !bins.length) return;
        let max = 0;
        for (const bin of bins) {
            if (bin > max) max = bin;
        }
        if (max <= 0) return;

        const n = bins.length;
        ctx.beginPath();
        ctx.moveTo(0, height);
        for (let i = 0; i < n; i++) {
            // Compressed scaling: big spikes stay the tallest, but mid/low
            // activity is lifted so they remain readable next to the spike.
            const normalized = bins[i] / max;
            const scaled = Math.pow(normalized, 0.6);
            const x = (i / (n - 1)) * width;
            const y = height - scaled * (height - 2);
            ctx.lineTo(x, y);
        }
        ctx.lineTo(width, height);
        ctx.closePath();

        const highlight = getComputedStyle(document.documentElement)
            .getPropertyValue('--color-highlight').trim() || '#f1ba63';
        ctx.fillStyle = highlight;
        ctx.globalAlpha = 0.5;
        ctx.fill();
        ctx.globalAlpha = 1;
    }

    /** Events **/
    on(event, callback) {
        if (!this.eventListeners[event]) this.eventListeners[event] = [];
        this.eventListeners[event].push(callback);
    }

    off(event, callback) {
        if (!this.eventListeners[event]) return;
        this.eventListeners[event] =
            this.eventListeners[event].filter(cb => cb !== callback);
    }

    emit(event, data) {
        const list = this.eventListeners[event];
        if (!list || !list.length) return;
        list.forEach(cb => cb(data));
    }
}
