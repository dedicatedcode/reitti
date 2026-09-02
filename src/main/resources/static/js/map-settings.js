class SettingsMenu {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.isVisible = false;
        
        this.createHTML();
        this.menu = this.container.querySelector('.settings-menu');
        this.overlay = this.container.querySelector('.settings-overlay');
        this.init();
    }
    
    createHTML() {
        this.container.innerHTML = `
            <div class="settings-overlay" style="display: none;"></div>
            <div class="settings-menu">
                <div class="settings-menu-header">
                    <h3>${t('map.map-settings.title')}</h3>
                    <button class="close-settings-btn">
                        <i class="lni lni-xmark-circle"></i>
                    </button>
                </div>
                <div class="settings-menu-content">
                    <div class="divider left">${t('map.settings.dialog.appearance.title')}</div>
                    <div class="settings-section">
                        <div class="form-group">
                            <label for="view-mode">${t('map.settings.dialog.appearance.view-mode.title')}</label>
                            <select id="view-mode">
                                <option value="LINEAR">${t('map.settings.dialog.appearance.view-mode.standard')}</option>
                                <option value="RAW">${t('map.settings.dialog.appearance.view-mode.raw')}</option>
                                <option value="BUNDLED">${t('map.settings.dialog.appearance.view-mode.edged_bundling')}</option>
                            </select>
                        </div>
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="aggregate-toggle">
                            <label for="aggregate-toggle" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.appearance.view-mode.24h_aggregate')}</span>
                            </label>
                        </div>
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="show-transport-modes-checkbox">
                            <label for="show-transport-modes-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.appearance.show-transport-modes')}</span>
                            </label>
                            <span class="form-description font-small">${t('map.settings.dialog.appearance.show-transport-modes.description')}</span>
                        </div>
                    </div>
                    <div class="divider left">${t('map.settings.dialog.interface.title')}</div>
                    <div class="settings-section">
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="timeline-visible-checkbox">
                            <label for="timeline-visible-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.interface.timeline-visible')}</span>
                            </label>
                        </div>
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="datepicker-visible-checkbox">
                            <label for="datepicker-visible-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.interface.datepicker-visible')}</span>
                            </label>
                        </div>
                        <div class="form-group">
                            <label for="date-picker-style">${t('map.settings.interface.date-picker-style')}</label>
                            <select id="date-picker-style">
                                <option value="strip">${t('map.settings.interface.date-picker-style.strip')}</option>
                                <option value="inputs">${t('map.settings.interface.date-picker-style.inputs')}</option>
                            </select>
                            <span class="form-description font-small">${t('map.settings.interface.date-picker-style.description')}</span>
                        </div>
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="allow-future-dates-checkbox">
                            <label for="allow-future-dates-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.interface.allow-future-dates')}</span>
                            </label>
                            <span class="form-description font-small">${t('map.settings.interface.allow-future-dates.description')}</span>
                        </div>
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="show-avatars-checkbox">
                            <label for="show-avatars-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.interface.show-avatars')}</span>
                            </label>
                            <span class="form-description font-small">${t('map.settings.dialog.interface.show-avatars.description')}</span>
                        </div>
                    </div>
                    <div class="divider left">${t('map.settings.dialog.replay.title')}</div>
                    <div class="settings-section">
                        <div class="form-group slide-reveal-container">
                            <input type="checkbox" id="follow-trail-checkbox">
                            <label for="follow-trail-checkbox" class="slide-reveal">
                                <span class="slide-box"></span>
                                <span class="label-text">${t('map.settings.dialog.replay.follow-trail')}</span>
                            </label>
                            <span class="form-description font-small">${t('map.settings.dialog.replay.follow-trail.description')}</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    init() {
        if (window.userSettings.h3Enabled) {
            const elementById = document.getElementById('view-mode');
            const h3Option = document.createElement('option');
            h3Option.value = 'H3';
            h3Option.textContent = t('map.settings.dialog.appearance.view-mode.h3');
            elementById.appendChild(h3Option);
        }
        const closeBtn = this.menu.querySelector('.close-settings-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.close());
        }
        
        this.overlay.addEventListener('click', () => this.close());
        
        this.menu.addEventListener('click', (e) => e.stopPropagation());
        
        this.initializeCheckboxes();
        
        this.setupCheckboxListeners();
    }
    
    initializeCheckboxes() {
        // Timeline visibility
        const timelineCheckbox = this.menu.querySelector('#timeline-visible-checkbox');
        if (timelineCheckbox) {
            timelineCheckbox.checked = !document.body.classList.contains('timeline-hidden');
        }
        
        // Date picker visibility
        const datepickerCheckbox = this.menu.querySelector('#datepicker-visible-checkbox');
        if (datepickerCheckbox) {
            datepickerCheckbox.checked = !document.body.classList.contains('datepicker-hidden');
        }

        // Date picker style
        const datePickerStyleSelect = this.menu.querySelector('#date-picker-style');
        if (datePickerStyleSelect) {
            datePickerStyleSelect.value = localStorage.getItem('datePickerStyle') || 'strip';
        }

        const showAvatarsCheckbox = this.menu.querySelector('#show-avatars-checkbox');
        if (showAvatarsCheckbox) {
            showAvatarsCheckbox.checked = localStorage.getItem('showAvatars') !== 'false'; // default true
        }

        const followTrailCheckbox = this.menu.querySelector('#follow-trail-checkbox');
        if (followTrailCheckbox) {
            followTrailCheckbox.checked = localStorage.getItem('followTrail') !== 'false';
        }

        const allowFutureDatesCheckbox = this.menu.querySelector('#allow-future-dates-checkbox');
        if (allowFutureDatesCheckbox) {
            allowFutureDatesCheckbox.checked = localStorage.getItem('allowFutureDates') === 'true';
        }

        const showTransportModesCheckbox = this.menu.querySelector('#show-transport-modes-checkbox');
        if (showTransportModesCheckbox) {
            showTransportModesCheckbox.checked = localStorage.getItem('showTransportModes') !== 'false'; // default true
        }

        // Load and apply saved settings
        this.loadSettings();
    }

    setupCheckboxListeners() {
        // Timeline visibility
        const timelineCheckbox = this.menu.querySelector('#timeline-visible-checkbox');
        if (timelineCheckbox) {
            timelineCheckbox.addEventListener('change', (e) => {
                this.toggleTimeline(e.target.checked);
            });
        }
        
        // Date picker visibility
        const datepickerCheckbox = this.menu.querySelector('#datepicker-visible-checkbox');
        if (datepickerCheckbox) {
            datepickerCheckbox.addEventListener('change', (e) => {
                this.toggleDatePicker(e.target.checked);
            });
        }

        // Date picker style
        const datePickerStyleSelect = this.menu.querySelector('#date-picker-style');
        if (datePickerStyleSelect) {
            datePickerStyleSelect.addEventListener('change', (e) => {
                localStorage.setItem('datePickerStyle', e.target.value);
                this.dispatchSettingsChange('datePickerStyle', e.target.value);
            });
        }
        
        // View mode selector
        const viewModeSelect = this.menu.querySelector('#view-mode');
        if (viewModeSelect) {
            viewModeSelect.addEventListener('change', (e) => {
                this.updateViewMode(e.target.value);
            });
        }
        
        // Aggregate toggle
        const aggregateToggle = this.menu.querySelector('#aggregate-toggle');
        if (aggregateToggle) {
            aggregateToggle.addEventListener('change', (e) => {
                this.updateAggregate(e.target.checked);
            });
        }

        const showAvatarsCheckbox = this.menu.querySelector('#show-avatars-checkbox');
        if (showAvatarsCheckbox) {
            showAvatarsCheckbox.addEventListener('change', (e) => {
                this.updateShowAvatars(e.target.checked);
            });
        }

        const followTrailCheckbox = this.menu.querySelector('#follow-trail-checkbox');
        if (followTrailCheckbox) {
            followTrailCheckbox.addEventListener('change', (e) => {
                this.updateFollowTrail(e.target.checked);
            });
        }

        const allowFutureDatesCheckbox = this.menu.querySelector('#allow-future-dates-checkbox');
        if (allowFutureDatesCheckbox) {
            allowFutureDatesCheckbox.addEventListener('change', (e) => {
                const value = e.target.checked;
                localStorage.setItem('allowFutureDates', value);
                this.dispatchSettingsChange('allowFutureDates', value);
            });
        }

        const showTransportModesCheckbox = this.menu.querySelector('#show-transport-modes-checkbox');
        if (showTransportModesCheckbox) {
            showTransportModesCheckbox.addEventListener('change', (e) => {
                this.updateShowTransportModes(e.target.checked);
            });
        }
    }
    
    toggleTimeline(visible) {
        const body = document.body;
        
        if (visible) {
            body.classList.remove('timeline-hidden');
        } else {
            // Scroll timeline back to top before hiding to ensure navbar is accessible
            const timeline = document.querySelector('.timeline');
            if (timeline) {
                timeline.scrollTop = 0;
            }
            body.classList.add('timeline-hidden');
        }
        
        this.updateTimelineToggleButton();
        this.updateTimelineVisibility(visible);
        
        // Update checkbox state
        const timelineCheckbox = this.menu.querySelector('#timeline-visible-checkbox');
        if (timelineCheckbox) {
            timelineCheckbox.checked = visible;
        }
    }
    
    toggleDatePicker(visible) {
        const body = document.body;
        
        if (visible) {
            body.classList.remove('datepicker-hidden');
        } else {
            body.classList.add('datepicker-hidden');
        }
        
        this.updateDatePickerToggleButton();
        this.updateDatePickerVisibility(visible);
        
        // Update checkbox state
        const datepickerCheckbox = this.menu.querySelector('#datepicker-visible-checkbox');
        if (datepickerCheckbox) {
            datepickerCheckbox.checked = visible;
        }
        
        // Update today FAB visibility when date picker visibility changes
        if (window.updateTodayFabVisibility) {
            window.updateTodayFabVisibility();
        }
    }
    
    updateTimelineToggleButton() {
        const btn = document.getElementById('timeline-toggle-btn');
        if (!btn) return;
        
        const icon = btn.querySelector('i');
        const isHidden = document.body.classList.contains('timeline-hidden');
        
        if (isHidden) {
            icon.className = 'lni lni-enter';
            btn.title = t('timeline.state.show.title');
        } else {
            icon.className = 'lni lni-exit';
            btn.title = t('timeline.state.hide.title');
        }
    }
    
    updateDatePickerToggleButton() {
        const btn = document.getElementById('datepicker-toggle-btn');
        if (!btn) return;
        
        const icon = btn.querySelector('i');
        const isHidden = document.body.classList.contains('datepicker-hidden');
        
        if (isHidden) {
            icon.className = 'lni lni-exit-up';
            btn.title = t('datepicker.state.show.title');
        } else {
            icon.className = 'lni lni-enter-down';
            btn.title =  t('datepicker.state.hide.title');
        }
    }
    updateShowAvatars(visible) {
        localStorage.setItem('showAvatars', visible);
        this.dispatchSettingsChange('showAvatars', visible);
    }

    updateFollowTrail(visible) {
        localStorage.setItem('followTrail', visible);
        this.dispatchSettingsChange('followTrail', visible);
    }

    open() {
        if (this.isVisible) return;

        // flex (not block): the menu is a flex column — the header stays
        // pinned and the content scrolls within the max-height cap.
        this.menu.style.display = 'flex';
        this.overlay.style.display = 'block';
        
        // Update checkbox states before showing
        this.initializeCheckboxes();
        
        // Trigger reflow to enable transition
        setTimeout(() => {
            this.menu.classList.add('visible');
            this.overlay.classList.add('visible');
            this.isVisible = true;
        }, 10);
    }
    
    close() {
        if (!this.isVisible) return;
        
        this.menu.classList.remove('visible');
        this.overlay.classList.remove('visible');
        this.isVisible = false;
        
        setTimeout(() => {
            this.menu.style.display = 'none';
            this.overlay.style.display = 'none';
        }, 300);
    }
    
    loadSettings() {
        const settings = {
            aggregate: localStorage.getItem('aggregate') === 'true',
            viewMode: localStorage.getItem('view-mode') || 'LINEAR',
            timelineHidden: localStorage.getItem('timelineHidden') === 'true',
            datepickerHidden: localStorage.getItem('datepickerHidden') === 'true',
            showAvatars: localStorage.getItem('showAvatars') !== 'false',
            followTrail: localStorage.getItem('followTrail') !== 'false',
            allowFutureDates: localStorage.getItem('allowFutureDates') === 'true',
            showTransportModes: localStorage.getItem('showTransportModes') !== 'false'
        };

        if (!window.userSettings.h3Enabled && settings.viewMode === 'H3') {
            settings.viewMode = 'LINEAR';
        }
        
        this.applySettings(settings);
        return settings;
    }
    
    applySettings(settings) {
        // Apply view mode
        const viewModeSelect = this.menu.querySelector('#view-mode');
        if (viewModeSelect) {
            viewModeSelect.value = settings.viewMode;
        }
        
        // Apply aggregate setting
        const aggregateToggle = this.menu.querySelector('#aggregate-toggle');
        if (aggregateToggle) {
            aggregateToggle.checked = settings.aggregate;
        }

        const showAvatarsCheckbox = this.menu.querySelector('#show-avatars-checkbox');
        if (showAvatarsCheckbox) {
            showAvatarsCheckbox.checked = settings.showAvatars;
        }

        const followTrailCheckbox = this.menu.querySelector('#follow-trail-checkbox');
        if (followTrailCheckbox) {
            followTrailCheckbox.checked = settings.followTrail;
        }

        const showTransportModesCheckbox = this.menu.querySelector('#show-transport-modes-checkbox');
        if (showTransportModesCheckbox) {
            showTransportModesCheckbox.checked = settings.showTransportModes;
        }

        // Apply timeline visibility
        if (settings.timelineHidden) {
            document.body.classList.add('timeline-hidden');
        }
        this.updateTimelineToggleButton();
        
        // Apply date picker visibility
        if (settings.datepickerHidden) {
            document.body.classList.add('datepicker-hidden');
        }
        this.updateDatePickerToggleButton();
    }
    
    updateViewMode(value) {
        localStorage.setItem('view-mode', value);
        this.dispatchSettingsChange('viewMode', value);
    }
    
    updateAggregate(checked) {
        localStorage.setItem('aggregate', checked);
        this.dispatchSettingsChange('aggregate', checked);
    }

    updateShowTransportModes(checked) {
        localStorage.setItem('showTransportModes', checked);
        this.dispatchSettingsChange('showTransportModes', checked);
    }
    
    updateTimelineVisibility(visible) {
        localStorage.setItem('timelineHidden', (!visible).toString());
        this.dispatchSettingsChange('timelineVisible', visible);
    }
    
    updateDatePickerVisibility(visible) {
        localStorage.setItem('datepickerHidden', (!visible).toString());
        this.dispatchSettingsChange('datepickerVisible', visible);
    }
    
    dispatchSettingsChange(setting, value) {
        const event = new CustomEvent('settingsChanged', {
            detail: { setting, value }
        });
        document.dispatchEvent(event);
    }

    restoreState() {
        this.loadSettings();
    }
}
