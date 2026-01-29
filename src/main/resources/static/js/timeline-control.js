class TimelineControl {


    constructor(timeline) {
        this.timeline = timeline;
        this.timeline.innerHTML = `
             <div class="time-bounds">
                <span id="start-label" style="align-self: end;">--</span>
                <div class="timer-display">
                    <span id="current-date">Select Date</span>
                    <span id="current-time">00:00</span>
                </div>
        
                <span id="end-label" style="align-self: end;">--</span>
            </div>
            <div class="slider-container"><input type="range" id="time-slider" min="0" max="100" value="0" step="1"></div>
        `;
        this.slider = timeline.getElementsByTagName('input')[0];
        this.startLabel = timeline.getElementsByTagName('span')[0];
        this.dateLabel =  timeline.getElementsByTagName('span')[1]
        this.timeLabel =  timeline.getElementsByTagName('span')[2]
        this.endLabel = timeline.getElementsByTagName('span')[3];

        this.eventListeners = {};
        this.aggregate = false;
        this.minTimestamp = 0;
        this.maxTimestamp = 0;
        this._init();

    }

    _init() {
        this.slider.oninput = (e) => {
            const value = parseInt(e.target.value);
            this._updateLabels(value - this.minTimestamp);
            this.emit('offsetChanged', {offset: value - this.minTimestamp, value: value});
        };
    }

    _updateLabels(offset) {
        const tz = getUserTimezone() || 'UTC';
        const locale = window.userSettings.selectedLocale || 'de-DE';
        const numericOffset = parseInt(offset);

        let dateObj;

        if (this.aggregate) {
            dateObj = new Date(numericOffset * 1000);
            this.dateLabel.innerText = 'Daily Pattern';
            this.timeLabel.innerText = dateObj.toLocaleTimeString(locale, {
                hour: '2-digit', minute: '2-digit', timeZone: 'UTC'
            });

            this.startLabel.innerText = "00:00";
            this.endLabel.innerText = "23:59";

        } else {
            const absoluteSeconds = this.minTimestamp + numericOffset;
            dateObj = new Date(absoluteSeconds * 1000);

            this.dateLabel.innerText = dateObj.toLocaleDateString(locale, {
                day: '2-digit', month: 'short', year: 'numeric', timeZone: tz
            });

            this.timeLabel.innerText = dateObj.toLocaleTimeString(locale, {
                hour: '2-digit', minute: '2-digit', timeZone: tz, hour12: false
            });

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

    setup(config) {
        if (config.aggregate) {
            this.aggregate = true;
            this.minTimestamp = 0;
            this.maxTimestamp = 86400;
            this.slider.min = 0;
            this.slider.step = 60;
            this.slider.max = 86400;
            this.setOffset(0);

        } else {
            this.minTimestamp = config.minTimestamp;
            this.maxTimestamp = config.maxTimestamp;
            this.aggregate = config.aggregate;
            this.slider.step = 1;
            this.slider.min = config.minTimestamp;
            this.slider.max = this.maxTimestamp;
            this.setOffset(0);
        }
    }

    setOffset(offset) {
        this.slider.value = this.minTimestamp + offset;
        this._updateLabels(offset);
    }

    getOffset() {
        return this.slider.value - this.minTimestamp;
    }

    getValue() {
        return parseInt(this.slider.value);
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
        return this.slider.max - this.slider.min;
    }

    isIdle() {
        return (this.slider.value - this.minTimestamp) === 0;
    }

    hide() {

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