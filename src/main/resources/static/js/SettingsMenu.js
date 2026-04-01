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
                    </div>
                </div>
            </div>
        `;
    }
    
    init() {
        // Set up close button
        const closeBtn = this.menu.querySelector('.close-settings-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.close());
        }
        
        // Set up overlay click to close
        this.overlay.addEventListener('click', () => this.close());
        
        // Prevent menu clicks from closing
        this.menu.addEventListener('click', (e) => e.stopPropagation());
        
        // Initialize checkboxes with current state
        this.initializeCheckboxes();
        
        // Set up checkbox event listeners
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
    
    open() {
        if (this.isVisible) return;
        
        this.menu.style.display = 'block';
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
            timelineControlsHidden: localStorage.getItem('timelineControlsHidden') === 'true'
        };
        
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
        
        // Apply timeline controls visibility
        this.applyTimelineControlsState(settings.timelineControlsHidden);
    }
    
    updateViewMode(value) {
        localStorage.setItem('view-mode', value);
        this.dispatchSettingsChange('viewMode', value);
    }
    
    updateAggregate(checked) {
        localStorage.setItem('aggregate', checked);
        this.dispatchSettingsChange('aggregate', checked);
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
    
    applyTimelineControlsState(isHidden) {
        const timelineControls = document.getElementById('timeline-controls');
        const toggleBtn = document.getElementById('toggle-time-control-btn');

        if (!timelineControls || !toggleBtn) return;

        if (isHidden) {
            timelineControls.classList.add('hidden');
            toggleBtn.title = t('timeline.state.show.title')
            toggleBtn.classList.remove('active');
        } else {
            timelineControls.classList.remove('hidden');
            toggleBtn.title = t('timeline.state.hide.title');
            toggleBtn.classList.add('active');
        }
    }
    
    restoreState() {
        this.loadSettings();
    }
}
