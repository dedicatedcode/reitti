class LiveModeController {
    constructor(element, overlay) {
        this.element = document.getElementById(element);
        this.overlay = document.getElementById(overlay);
        this.autoUpdateMode = false;
        const messageDiv = document.createRange()
            .createContextualFragment(`<div id="message-container">
                                                <div id="sse-message"></div>
                                            </div>`);
        document.body.appendChild(messageDiv);
        this.messagesDiv = document.getElementById('sse-message');
        this.autoUpdateTimeInterval = null;
        this.eventSource = null;
        this.reloadTimeoutId = null;
        this.maxWaitTimeoutId = null;
        this.pendingEvents = [];
        this.firstEventTime = null;
        this.reconnectTimeoutId = null;
        this.eventListeners = [];


        this._setup();
    }

    enterLiveMode() {
        this.autoUpdateMode = true;
        if (this.element != null) {
            const icon = this.element.querySelector('i');
            icon.className = 'lni lni-pause';
                this.element.title = t('autoupdate.state.disable');
        }
        document.body.classList.add('auto-update-mode');
        this._updateAutoUpdateTime();
        this.overlay.classList.add('visible');
        this.autoUpdateTimeInterval = setInterval(() => this._updateAutoUpdateTime(), 1000);
        this._connect();
        this._emit('autoUpdateModeChanged', true)

    }

    exitLiveMode() {
        this.autoUpdateMode = false;
        if (this.element != null) {
            const icon = this.element.querySelector('i');
            icon.className = 'lni lni-play';
            this.element.title = t('autoupdate.state.enable');
        }
        document.body.classList.remove('auto-update-mode');
        this.overlay.classList.remove('visible');
        if (this.autoUpdateTimeInterval) {
            clearInterval(this.autoUpdateTimeInterval);
            this.autoUpdateTimeInterval = null;
        }
        if (this.reloadTimeoutId) {
            clearTimeout(this.reloadTimeoutId);
            this.reloadTimeoutId = null;
        }
        if (this.maxWaitTimeoutId) {
            clearTimeout(this.maxWaitTimeoutId);
            this.maxWaitTimeoutId = null;
        }
        this.pendingEvents = [];
        this.firstEventTime = null;


        // Clear reconnect timeout
        if (this.reconnectTimeoutId) {
            clearTimeout(this.reconnectTimeoutId);
            this.reconnectTimeoutId = null;
        }

        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
        this._emit('autoUpdateModeChanged', false)
    }


    /** Events **/
    on(event, callback) {
        if (!this.eventListeners[event]) this.eventListeners[event] = [];
        this.eventListeners[event].push(callback);
    }

    off(event, callback) {
        if (!this.eventListeners[event]) return;
        this.eventListeners[event] = this.eventListeners[event].filter(cb => cb !== callback);
    }

    _emit(event, data) {
        const list = this.eventListeners[event];
        if (!list || !list.length) return;
        list.forEach(cb => cb(data));
    }

    _setup() {
        if (this.element != null) {
            this.element.onclick = () => {
                if (!this.autoUpdateMode) {
                    this.enterLiveMode();
                } else {
                    this.exitLiveMode();
                }
            }
            this.overlay.getElementsByTagName('button')[0].onclick = () => this.exitLiveMode();
        }
    }

    _updateAutoUpdateTime() {
        const now = new Date();
        const dateElement = this.overlay.querySelector('#auto-update-date');
        const timeElement = this.overlay.querySelector('#auto-update-time');

        // Use browser locale for date formatting
        const dateOptions = {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        };
        const formattedDate = now.toLocaleDateString(navigator.language, dateOptions);

        // Use browser locale for time formatting
        const timeOptions = {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        };
        const formattedTime = now.toLocaleTimeString(navigator.language, timeOptions);

        dateElement.textContent = formattedDate;
        timeElement.textContent = formattedTime;
    }

    _connect() {
        this.eventSource = new EventSource(window.contextPath + '/events');
        this.eventSource.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this._emit('sseMessageReceived', data);
        }

        this.eventSource.onopen = () => {
            console.log('SSE connection opened.');
            this.messagesDiv.innerHTML = '';
            this.messagesDiv.classList.remove('active');
            if ( this.reconnectTimeoutId) {
                clearTimeout( this.reconnectTimeoutId);
                this.reconnectTimeoutId = null;
            }
        };

        this.eventSource.onerror = (error) => {
            console.error('EventSource failed:', error);
            this.messagesDiv.innerHTML = `<p><strong>${t('sse.error.connection-lost')}</strong></p>`;
            this.messagesDiv.classList.add('active');

            // Close the current connection
            if (this.eventSource) {
                this.eventSource.close();
                this.eventSource = null;
            }
            if (this.autoUpdateMode && !this.reconnectTimeoutId) {
                console.log('Scheduling SSE reconnection in 5 seconds...');
                this.reconnectTimeoutId = setTimeout(() => {
                    this.reconnectTimeoutId = null;
                    this._connect();
                }, 5000);
            }
        };

        this.eventSource.onmessage = (event) => {
            console.log('Received generic event:', event.data);

            if (this.messagesDiv.classList.contains('active')) {
                this.messagesDiv.innerHTML = '';
                this.messagesDiv.classList.remove('active');
            }

            // Clear any pending reconnect timeout since we're receiving messages
            if (this.reconnectTimeoutId) {
                clearTimeout(this.reconnectTimeoutId);
                this.reconnectTimeoutId = null;
            }

            // Parse the event data
            try {
                const eventData = JSON.parse(event.data);

                // Check if the event has a date field and it matches today
                if (eventData.date) {
                    const today = getCurrentLocalDate(); // YYYY-MM-DD format
                    const eventDate = eventData.date;

                    // If event date matches today and we're in auto-update mode, schedule reload
                    if (eventDate === today && this.autoUpdateMode) {
                        console.log('Auto-update: Scheduling timeline reload due to SSE event for today');
                        this._scheduleTimelineReload(eventData);
                    }
                }
            } catch (error) {
                console.warn('Could not parse SSE event data:', error);
            }
        };
    }

    _scheduleTimelineReload(eventData) {
        // Add event to pending events
        this.pendingEvents.push(eventData);

        if (this.firstEventTime === null) {
            this.firstEventTime = Date.now();

            // Set maximum wait timeout (30 seconds from first event)
            this.maxWaitTimeoutId = setTimeout(() => {
                if (this.autoUpdateMode && this.pendingEvents.length > 0) {
                    console.log(`Auto-update: Reloading timeline data after max wait time (30s) with ${this.pendingEvents.length} accumulated events`);
                    this._executeTimelineReload();
                }
            }, 30000);
        }

        if (this.reloadTimeoutId) {
            clearTimeout(this.reloadTimeoutId);
        }

        this.reloadTimeoutId = setTimeout(() => {
            if (this.autoUpdateMode && this.pendingEvents.length > 0) {
                console.log(`Auto-update: Reloading timeline data after 5s settle time with ${this.pendingEvents.length} accumulated events`);
                this._executeTimelineReload();
            }
        }, 5000);
    }



    _executeTimelineReload() {
        // Clear all timeouts
        if (this.reloadTimeoutId) {
            clearTimeout(this.reloadTimeoutId);
            this.reloadTimeoutId = null;
        }
        if (this.maxWaitTimeoutId) {
            clearTimeout(this.maxWaitTimeoutId);
            this.maxWaitTimeoutId = null;
        }

        // Reload timeline
        document.body.dispatchEvent(new CustomEvent('dateChanged'));

        this.pendingEvents = [];
        this.firstEventTime = null;
    }
}
