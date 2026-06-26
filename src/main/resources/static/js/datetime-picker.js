/**
 * DateTimePicker - A custom datetime picker component
 * Provides calendar, year selection, and (optionally) time selection.
 *
 * Modes:
 *   - Full mode:  container has both .date-input and .time-input
 *   - Date-only:  container has only .date-input
 *                 (time is always 00:00:00 in selectedDate and getValue)
 */
class DateTimePicker {
    /**
     * Creates a new DateTimePicker instance
     * @param {HTMLElement} element - The container element
     * @param {Object} options - Configuration options
     * @param {string} options.timeFormat - Time format ('12h' or '24h')
     * @param {Date} options.minDate - Minimum selectable date
     * @param {Date} options.maxDate - Maximum selectable date
     * @param {Function} options.onValidate - Validation callback function
     * @param {string} options.locale - Locale for date formatting
     */
    constructor(element, options = {}) {
        this.options = {
            timeFormat: options.timeFormat || '24h',
            minDate: options.minDate || null,
            maxDate: options.maxDate || null,
            onValidate: options.onValidate || null,
            locale: options.locale || navigator.language,
            date: options.date || null,
            popupPlacement: options.popupPlacement || 'auto'  // 'top' | 'bottom' | 'auto'
        };

        this.element = element;
        this.dateInput = element.querySelector('.date-input');
        this.timeInput = element.querySelector('.time-input');
        this.triggerButton = element.querySelector('.picker-trigger');

        // Date-only mode when no time input exists
        this.dateOnly = !this.timeInput;

        // Create popup structure if it doesn't exist
        if (!element.querySelector('.picker-popup')) {
            this.createPopupStructure();
        }

        this.popup = element.querySelector('.picker-popup');
        this.calendarSection = element.querySelector('.calendar-section');
        this.yearScroll = element.querySelector('.year-scroll');
        this.timeScroll = element.querySelector('.time-scroll');
        this._listeners = { change: [] };
        this.currentDate = new Date();
        if (this.options.date) {
            const [y, m, d] = options.date.split('-').map(Number);
            this.selectedDate = new Date(y, m - 1, d, 0, 0, 0, 0);
            this.updateInputs();
        } else {
            this.selectedDate = null;
        }

        this.init();
    }

    /**
     * Create the popup structure dynamically
     */
    createPopupStructure() {
        const popup = document.createElement('div');
        popup.className = 'picker-popup';
        popup.style.display = 'none';

        const pickerContainer = document.createElement('div');
        pickerContainer.className = 'picker-container';
        if (this.dateOnly) pickerContainer.classList.add('date-only');

        // Calendar section
        const calendarSection = document.createElement('div');
        calendarSection.className = 'calendar-section';

        const calendarHeader = document.createElement('div');
        calendarHeader.className = 'calendar-header';

        const prevMonthBtn = document.createElement('button');
        prevMonthBtn.type = 'button';
        prevMonthBtn.className = 'prev-month';
        prevMonthBtn.innerHTML = '&lt;';

        const monthYear = document.createElement('div');
        monthYear.className = 'month-year';

        const nextMonthBtn = document.createElement('button');
        nextMonthBtn.type = 'button';
        nextMonthBtn.className = 'next-month';
        nextMonthBtn.innerHTML = '&gt;';

        calendarHeader.appendChild(prevMonthBtn);
        calendarHeader.appendChild(monthYear);
        calendarHeader.appendChild(nextMonthBtn);

        const weekdays = document.createElement('div');
        weekdays.className = 'weekdays';

        const daysGrid = document.createElement('div');
        daysGrid.className = 'days-grid';

        // Today button
        const todayButton = document.createElement('button');
        todayButton.type = 'button';
        todayButton.className = 'today-button';
        todayButton.textContent = 'Today';

        calendarSection.appendChild(calendarHeader);
        calendarSection.appendChild(weekdays);
        calendarSection.appendChild(daysGrid);
        calendarSection.appendChild(todayButton);

        // Year scroll section
        const yearScroll = document.createElement('div');
        yearScroll.className = 'year-scroll';

        const yearList = document.createElement('div');
        yearList.className = 'year-list';

        yearScroll.appendChild(yearList);

        pickerContainer.appendChild(calendarSection);
        pickerContainer.appendChild(yearScroll);

        // Time scroll section — only in full mode
        if (!this.dateOnly) {
            const timeScroll = document.createElement('div');
            timeScroll.className = 'time-scroll';

            const timeList = document.createElement('div');
            timeList.className = 'time-list';

            timeScroll.appendChild(timeList);
            pickerContainer.appendChild(timeScroll);
        }

        popup.appendChild(pickerContainer);
        this.element.appendChild(popup);
    }

    /**
     * Initialize the datetime picker
     */
    init() {
        this.setupEventListeners();
        this.renderCalendar();
        this.renderYearList();
        if (!this.dateOnly) this.renderTimeList();
        this.updateFromInputs();

        // Preselect current date and closest time if no date is selected
        if (!this.selectedDate) {
            this.selectToday();
        }
    }

    /**
     * Set up event listeners for the picker
     */
    setupEventListeners() {
        // Trigger button click handler
        this.triggerButton.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.togglePopup();
        });

        // Input change events
        this.dateInput.addEventListener('change', () => {
            this.updateFromInputs();
            if (this.options.onValidate) {
                this.options.onValidate();
            }
        });

        if (this.timeInput) {
            this.timeInput.addEventListener('change', () => {
                this.updateFromInputs();
                if (this.options.onValidate) {
                    this.options.onValidate();
                }
            });
        }

        // Calendar navigation
        const prevMonthBtn = this.element.querySelector('.prev-month');
        const nextMonthBtn = this.element.querySelector('.next-month');
        const todayBtn = this.element.querySelector('.today-button');

        if (prevMonthBtn) {
            prevMonthBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.currentDate.setMonth(this.currentDate.getMonth() - 1);
                this.renderCalendar();
            });
        }

        if (nextMonthBtn) {
            nextMonthBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.currentDate.setMonth(this.currentDate.getMonth() + 1);
                this.renderCalendar();
            });
        }

        if (todayBtn) {
            todayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.selectToday();
            });
        }

        // Close popup when clicking outside
        document.addEventListener('click', (e) => {
            if (!this.element.contains(e.target)) {
                this.closePopup();
            }
        });

        // Prevent popup from closing when clicking inside it
        this.popup.addEventListener('click', (e) => {
            e.stopPropagation();
        });
    }

    /**
     * Select today's date (and, in full mode, closest 15-min time).
     * In date-only mode, time is pinned to 00:00:00.
     */
    selectToday() {
        const now = new Date();
        this.currentDate = new Date(now);
        this.selectedDate = new Date(now);

        if (this.dateOnly) {
            this.selectedDate.setHours(0, 0, 0, 0);
        } else {
            // Round to nearest 15 minutes
            const minutes = Math.round(now.getMinutes() / 15) * 15;
            this.selectedDate.setMinutes(minutes, 0, 0);
        }

        this.updateInputs();
        this.renderCalendar();
        this.renderYearList();
        if (!this.dateOnly) this.highlightSelectedTime();
    }

    /**
     * Render the calendar grid
     */
    renderCalendar() {
        const monthYear = this.element.querySelector('.month-year');
        const weekdays = this.element.querySelector('.weekdays');
        const daysGrid = this.element.querySelector('.days-grid');

        // Update month/year header
        monthYear.textContent = this.currentDate.toLocaleDateString(this.options.locale, {
            month: 'long',
            year: 'numeric'
        });

        // Render weekdays
        weekdays.innerHTML = '';
        const weekdayNames = [];
        for (let i = 0; i < 7; i++) {
            const date = new Date(2023, 0, i + 1); // Start from Sunday
            weekdayNames.push(date.toLocaleDateString(this.options.locale, { weekday: 'short' }));
        }
        weekdayNames.forEach(day => {
            const dayElement = document.createElement('div');
            dayElement.className = 'weekday';
            dayElement.textContent = day;
            weekdays.appendChild(dayElement);
        });

        // Render days grid
        daysGrid.innerHTML = '';
        const firstDay = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth(), 1);
        const startDate = new Date(firstDay);
        startDate.setDate(startDate.getDate() - firstDay.getDay());

        for (let i = 0; i < 42; i++) {
            const date = new Date(startDate);
            date.setDate(startDate.getDate() + i);

            const dayElement = document.createElement('button');
            dayElement.type = 'button';
            dayElement.className = 'day';
            dayElement.textContent = date.getDate();

            if (date.getMonth() !== this.currentDate.getMonth()) {
                dayElement.classList.add('other-month');
            }

            if (this.selectedDate && this.isSameDay(date, this.selectedDate)) {
                dayElement.classList.add('selected');
            }

            if (this.isDateDisabled(date)) {
                dayElement.disabled = true;
                dayElement.classList.add('disabled');
            }

            dayElement.addEventListener('click', (e) => {
                e.stopPropagation();
                if (!this.isDateDisabled(date)) {
                    this.selectDate(date);
                }
            });

            daysGrid.appendChild(dayElement);
        }
    }

    /**
     * Render the year selection list
     */
    renderYearList() {
        const yearList = this.element.querySelector('.year-list');
        yearList.innerHTML = '';

        const currentYear = new Date().getFullYear();
        const startYear = currentYear - 50;
        const endYear = currentYear + 50;

        for (let year = startYear; year <= endYear; year++) {
            const yearElement = document.createElement('button');
            yearElement.type = 'button';
            yearElement.className = 'year-item';
            yearElement.textContent = year;

            if (year === this.currentDate.getFullYear()) {
                yearElement.classList.add('selected');
            }

            yearElement.addEventListener('click', (e) => {
                e.stopPropagation();
                this.changeYear(year);
            });

            yearList.appendChild(yearElement);
        }
    }

    /**
     * Change the current year and maintain the selected day if possible
     * @param {number} year - The year to change to
     */
    changeYear(year) {
        const currentDay = this.selectedDate ? this.selectedDate.getDate() : 1;
        const currentMonth = this.selectedDate ? this.selectedDate.getMonth() : this.currentDate.getMonth();

        this.currentDate.setFullYear(year);

        let newDate = new Date(year, currentMonth, currentDay);

        if (newDate.getMonth() !== currentMonth || this.isDateDisabled(newDate)) {
            const lastDayOfMonth = new Date(year, currentMonth + 1, 0).getDate();
            newDate = new Date(year, currentMonth, Math.min(currentDay, lastDayOfMonth));

            if (this.isDateDisabled(newDate)) {
                newDate = this.findNextValidDate(newDate);
            }
        }

        if (this.selectedDate) {
            this.selectedDate.setFullYear(year, currentMonth, newDate.getDate());
            if (this.dateOnly) this.selectedDate.setHours(0, 0, 0, 0);
            this.updateInputs();
        }

        this.renderCalendar();
        this.renderYearList();
    }

    /**
     * Find the next valid date starting from a given date
     */
    findNextValidDate(startDate) {
        let currentDate = new Date(startDate);
        const maxAttempts = 31;

        for (let i = 0; i < maxAttempts; i++) {
            currentDate.setDate(currentDate.getDate() + 1);
            if (!this.isDateDisabled(currentDate)) {
                return currentDate;
            }
        }

        return new Date(startDate.getFullYear(), startDate.getMonth(), 1);
    }

    /**
     * Render the time selection list (full mode only)
     */
    renderTimeList() {
        if (this.dateOnly) return;
        const timeList = this.element.querySelector('.time-list');
        if (!timeList) return;
        timeList.innerHTML = '';

        for (let hour = 0; hour < 24; hour++) {
            for (let minute = 0; minute < 60; minute += 15) {
                const timeElement = document.createElement('button');
                timeElement.type = 'button';
                timeElement.className = 'time-item';

                const timeString = this.formatTime(hour, minute);
                timeElement.textContent = timeString;
                timeElement.dataset.hour = hour;
                timeElement.dataset.minute = minute;

                timeElement.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.selectTime(hour, minute);
                });

                timeList.appendChild(timeElement);
            }
        }
    }

    /**
     * Format time according to the specified format
     */
    formatTime(hour, minute) {
        if (this.options.timeFormat === '12h') {
            const period = hour >= 12 ? 'PM' : 'AM';
            const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
            return `${displayHour}:${minute.toString().padStart(2, '0')} ${period}`;
        } else {
            return `${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}`;
        }
    }
    /**
     * Update the picker's displayed date without firing the 'change' event.
     * Useful for transient sync (e.g., hover tracking) that shouldn't be
     * treated as a user selection.
     * @param {Date} date
     */
    setDateSilent(date) {
        if (!(date instanceof Date) || isNaN(date.getTime())) return;

        this.selectedDate = new Date(date);
        if (this.dateOnly) this.selectedDate.setHours(0, 0, 0, 0);
        this.currentDate = new Date(this.selectedDate);

        // Update inputs without triggering change on them either
        const y = this.selectedDate.getFullYear();
        const m = (this.selectedDate.getMonth() + 1).toString().padStart(2, '0');
        const d = this.selectedDate.getDate().toString().padStart(2, '0');
        this.dateInput.value = `${y}-${m}-${d}`;

        if (this.timeInput) {
            const hh = this.selectedDate.getHours().toString().padStart(2, '0');
            const mm = this.selectedDate.getMinutes().toString().padStart(2, '0');
            this.timeInput.value = `${hh}:${mm}`;
        }

        this.renderCalendar();
        this.renderYearList();
        if (!this.dateOnly) this.highlightSelectedTime();
    }
    /**
     * Select a specific date. In date-only mode the time is pinned to 00:00:00.
     */
    selectDate(date) {
        if (!this.selectedDate) {
            this.selectedDate = new Date();
        }
        this.selectedDate.setFullYear(date.getFullYear(), date.getMonth(), date.getDate());
        if (this.dateOnly) this.selectedDate.setHours(0, 0, 0, 0);
        this.updateInputs();
        this.renderCalendar();
    }

    /**
     * Select a specific time (no-op in date-only mode)
     */
    selectTime(hour, minute) {
        if (this.dateOnly) return;
        if (!this.selectedDate) {
            this.selectedDate = new Date();
        }
        this.selectedDate.setHours(hour, minute, 0, 0);
        this.updateInputs();
        this.highlightSelectedTime();
    }

    /**
     * Highlight the currently selected time in the time list (full mode only)
     */
    highlightSelectedTime() {
        if (this.dateOnly) return;
        this.element.querySelectorAll('.time-item').forEach(item => {
            item.classList.remove('selected');
            if (this.selectedDate &&
                parseInt(item.dataset.hour) === this.selectedDate.getHours() &&
                parseInt(item.dataset.minute) === this.selectedDate.getMinutes()) {
                item.classList.add('selected');
            }
        });
    }

    updateInputs() {
        if (!this.selectedDate) return;

        const year = this.selectedDate.getFullYear();
        const month = (this.selectedDate.getMonth() + 1).toString().padStart(2, '0');
        const day = this.selectedDate.getDate().toString().padStart(2, '0');
        this.dateInput.value = `${year}-${month}-${day}`;
        this.dateInput.dispatchEvent(new Event('change'));

        if (this.timeInput) {
            const hours = this.selectedDate.getHours().toString().padStart(2, '0');
            const minutes = this.selectedDate.getMinutes().toString().padStart(2, '0');
            this.timeInput.value = `${hours}:${minutes}`;
            this.timeInput.dispatchEvent(new Event('change'));
        }

        this._emit('change');
    }

    /**
     * Update the picker state from the input values
     */
    updateFromInputs() {
        if (!this.dateInput.value) return;

        if (this.dateOnly) {
            // Parse YYYY-MM-DD as local midnight
            const [y, m, d] = this.dateInput.value.split('-').map(Number);
            this.selectedDate = new Date(y, m - 1, d, 0, 0, 0, 0);
        } else if (this.timeInput && this.timeInput.value) {
            const dateTimeString = `${this.dateInput.value}T${this.timeInput.value}`;
            this.selectedDate = new Date(dateTimeString);
        } else {
            return;
        }

        this.currentDate = new Date(this.selectedDate);
        this.renderCalendar();
        this.renderYearList();
        if (!this.dateOnly) this.highlightSelectedTime();

        this._emit('change');
    }

    /**
     * Toggle the popup visibility
     */
    togglePopup() {
        const isVisible = this.popup.style.display !== 'none';
        if (isVisible) {
            this.closePopup();
        } else {
            this.openPopup();
        }
    }

    openPopup() {
        this.popup.style.display = 'block';
        this.applyPopupPlacement();
        if (this.selectedDate) {
            this.scrollToSelectedYear();
            if (!this.dateOnly) this.scrollToSelectedTime();
        }
    }

    /**
     * Decide whether the popup should render above or below the trigger.
     * - 'top' / 'bottom': explicit, always used
     * - 'auto': flip to top only if there isn't enough room below
     */
    applyPopupPlacement() {
        const mode = this.options.popupPlacement;
        let placeOnTop;

        if (mode === 'top')    placeOnTop = true;
        else if (mode === 'bottom') placeOnTop = false;
        else {
            // auto — measure available space
            const triggerRect = this.triggerButton.getBoundingClientRect();
            const popupHeight = this.popup.offsetHeight;
            const viewportH = window.innerHeight;
            const spaceBelow = viewportH - triggerRect.bottom;
            const spaceAbove = triggerRect.top;
            // Prefer bottom, but flip if it would clip and top has more room
            placeOnTop = spaceBelow < popupHeight && spaceAbove > spaceBelow;
        }

        this.popup.classList.toggle('placement-top', placeOnTop);
        this.popup.classList.toggle('placement-bottom', !placeOnTop);
    }
    /**
     * Close the popup
     */
    closePopup() {
        this.popup.style.display = 'none';
    }

    /**
     * Scroll to the selected year in the year list
     */
    scrollToSelectedYear() {
        const selectedYear = this.element.querySelector('.year-item.selected');
        if (selectedYear) {
            selectedYear.scrollIntoView({ block: 'center' });
        }
    }

    /**
     * Scroll to the selected time in the time list (full mode only)
     */
    scrollToSelectedTime() {
        if (this.dateOnly) return;
        const selectedTime = this.element.querySelector('.time-item.selected');
        if (selectedTime) {
            selectedTime.scrollIntoView({ block: 'center' });
        }
    }

    /**
     * Check if two dates are the same day
     */
    isSameDay(date1, date2) {
        return date1.getFullYear() === date2.getFullYear() &&
            date1.getMonth() === date2.getMonth() &&
            date1.getDate() === date2.getDate();
    }

    /**
     * Check if a date is disabled
     */
    isDateDisabled(date) {
        if (this.options.minDate && date < this.options.minDate) {
            return true;
        }
        return this.options.maxDate && date > this.options.maxDate;

    }

    /**
     * Get the current value as ISO string.
     * Date-only mode returns 'YYYY-MM-DDT00:00:00'.
     */
    getValue() {
        if (!this.dateInput.value) return '';
        if (this.dateOnly) {
            return `${this.dateInput.value}T00:00:00`;
        }
        if (this.timeInput && this.timeInput.value) {
            return `${this.dateInput.value}T${this.timeInput.value}`;
        }
        return '';
    }

    /**
     * Set the value from ISO string. In date-only mode the time portion
     * is ignored and the date is stored with time pinned to 00:00:00.
     */
    setValue(value) {
        if (!value) return;
        const [datePart, timePart] = value.split('T');
        this.dateInput.value = datePart;
        if (!this.dateOnly && this.timeInput) {
            this.timeInput.value = timePart ? timePart.substring(0, 5) : '';
        }
        this.updateFromInputs();
    }

    /**
     * Subscribe to a picker event.
     * @param {string} eventName - Currently supported: 'change'
     * @param {Function} handler - Called with (value, selectedDate, picker)
     * @returns {Function} Unsubscribe function
     */
    on(eventName, handler) {
        if (!this._listeners[eventName]) this._listeners[eventName] = [];
        this._listeners[eventName].push(handler);
        return () => this.off(eventName, handler);
    }

    /**
     * Unsubscribe a previously registered handler.
     */
    off(eventName, handler) {
        const arr = this._listeners[eventName];
        if (!arr) return;
        const i = arr.indexOf(handler);
        if (i >= 0) arr.splice(i, 1);
    }

    /**
     * Internal: invoke all handlers for an event.
     */
    _emit(eventName) {
        const arr = this._listeners[eventName];
        if (!arr || !arr.length) return;
        const value = this.getValue();
        const selectedDate = this.selectedDate ? new Date(this.selectedDate) : null;
        for (const h of arr) {
            try { h(value, selectedDate, this); }
            catch (err) { console.error(`[DateTimePicker] ${eventName} handler threw:`, err); }
        }
    }
}