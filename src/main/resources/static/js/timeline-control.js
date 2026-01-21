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
            <div class="slider-container"><input type="range" id="time-slider" min="0" max="100" value="0" step="1">
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
            const offset = parseInt(e.target.value);
            currentSliderValue = offset;
            this._updateLabels(offset);
            this.emit('offsetChanged', {offset: offset});
        };
    }

    _updateLabels(offset) {
        const absoluteSeconds = this.minTimestamp + parseInt(offset);
        const dateObj = new Date(absoluteSeconds * 1000);

       this.dateLabel.innerText = dateObj.toLocaleDateString(window.userSettings.selectedLocale, {
            day: '2-digit', month: 'short', year: 'numeric'
        });

        this.timeLabel.innerText = dateObj.toLocaleTimeString(window.userSettings.selectedLocale, {
            hour: '2-digit', minute: '2-digit'
        });

        this.startLabel.innerText = this._formatDateTime(this.minTimestamp * 1000);
        this.endLabel.innerText = this._formatDateTime(this.maxTimestamp * 1000);
    }

    _formatDateTime(ts) {
        return new Date(ts).toLocaleDateString(window.userSettings.selectedLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    }

    setup(config) {
        this.minTimestamp = config.minTimestamp;
        this.maxTimestamp = config.maxTimestamp;
        this.aggregate = config.aggregate;

        this.slider.min = 0;
        this.slider.max = this.maxTimestamp - this.minTimestamp;

        this.setOffset(0);
    }

    setOffset(offset) {
        this.slider.value = offset;
        this._updateLabels(offset);
    }

    getOffset() {
        return this.slider.value;
    }

    getMax() {
        return this.slider.max;
    }

    isIdle() {
        return parseFloat(this.slider.value) === 0;
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