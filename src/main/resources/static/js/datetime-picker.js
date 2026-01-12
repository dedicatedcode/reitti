/**
 * DateTimePicker - A custom datetime picker component
 * Provides calendar, year selection, and time selection functionality
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
            locale: options.locale || navigator.language
        };

        this.element = element;
        this.nativeInput = element.querySelector('.native-input');
        this.triggerButton = element.querySelector('.picker-trigger');
        this.popup = element.querySelector('.picker-popup');
        this.calendarSection = element.querySelector('.calendar-section');
        this.yearScroll = element.querySelector('.year-scroll');
        this.timeScroll = element.querySelector('.time-scroll');

        this.currentDate = new Date();
        this.selectedDate = null;

        this.init();
    }

    /**
     * Initialize the datetime picker
     */
    init() {
        this.setupEventListeners();
        this.renderCalendar();
        this.renderYearList();
        this.renderTimeList();
        this.updateFromNativeInput();
    }

    /**
     * Set up event listeners for the picker
     */
    setupEventListeners() {
        this.triggerButton.addEventListener('click', (e) => {
            e.preventDefault();
            this.togglePopup();
        });

        this.nativeInput.addEventListener('change', () => {
            this.updateFromNativeInput();
            if (this.options.onValidate) {
                this.options.onValidate(this.getValue());
            }
        });

        // Calendar navigation
        this.element.querySelector('.prev-month').addEventListener('click', () => {
            this.currentDate.setMonth(this.currentDate.getMonth() - 1);
            this.renderCalendar();
        });

        this.element.querySelector('.next-month').addEventListener('click', () => {
            this.currentDate.setMonth(this.currentDate.getMonth() + 1);
            this.renderCalendar();
        });

        // Close popup when clicking outside
        document.addEventListener('click', (e) => {
            if (!this.element.contains(e.target)) {
                this.closePopup();
            }
        });
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
        const lastDay = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth() + 1, 0);
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

            dayElement.addEventListener('click', () => {
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

            yearElement.addEventListener('click', () => {
                this.currentDate.setFullYear(year);
                this.renderCalendar();
                this.renderYearList();
            });

            yearList.appendChild(yearElement);
        }
    }

    /**
     * Render the time selection list
     */
    renderTimeList() {
        const timeList = this.element.querySelector('.time-list');
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

                timeElement.addEventListener('click', () => {
                    this.selectTime(hour, minute);
                });

                timeList.appendChild(timeElement);
            }
        }
    }

    /**
     * Format time according to the specified format
     * @param {number} hour - Hour value (0-23)
     * @param {number} minute - Minute value (0-59)
     * @returns {string} Formatted time string
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
     * Select a specific date
     * @param {Date} date - The date to select
     */
    selectDate(date) {
        if (!this.selectedDate) {
            this.selectedDate = new Date();
        }
        this.selectedDate.setFullYear(date.getFullYear(), date.getMonth(), date.getDate());
        this.updateNativeInput();
        this.renderCalendar();
    }

    /**
     * Select a specific time
     * @param {number} hour - Hour value (0-23)
     * @param {number} minute - Minute value (0-59)
     */
    selectTime(hour, minute) {
        if (!this.selectedDate) {
            this.selectedDate = new Date();
        }
        this.selectedDate.setHours(hour, minute, 0, 0);
        this.updateNativeInput();
        this.highlightSelectedTime();
    }

    /**
     * Highlight the currently selected time in the time list
     */
    highlightSelectedTime() {
        this.element.querySelectorAll('.time-item').forEach(item => {
            item.classList.remove('selected');
            if (this.selectedDate &&
                parseInt(item.dataset.hour) === this.selectedDate.getHours() &&
                parseInt(item.dataset.minute) === this.selectedDate.getMinutes()) {
                item.classList.add('selected');
            }
        });
    }

    /**
     * Update the native input with the selected date/time
     */
    updateNativeInput() {
        if (this.selectedDate) {
            const isoString = this.selectedDate.toISOString().slice(0, 16);
            this.nativeInput.value = isoString;
        }
    }

    /**
     * Update the picker state from the native input value
     */
    updateFromNativeInput() {
        if (this.nativeInput.value) {
            this.selectedDate = new Date(this.nativeInput.value);
            this.currentDate = new Date(this.selectedDate);
            this.renderCalendar();
            this.renderYearList();
            this.highlightSelectedTime();
        }
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

    /**
     * Open the popup
     */
    openPopup() {
        this.popup.style.display = 'block';
        if (this.selectedDate) {
            this.scrollToSelectedYear();
            this.scrollToSelectedTime();
        }
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
     * Scroll to the selected time in the time list
     */
    scrollToSelectedTime() {
        const selectedTime = this.element.querySelector('.time-item.selected');
        if (selectedTime) {
            selectedTime.scrollIntoView({ block: 'center' });
        }
    }

    /**
     * Check if two dates are the same day
     * @param {Date} date1 - First date
     * @param {Date} date2 - Second date
     * @returns {boolean} True if same day
     */
    isSameDay(date1, date2) {
        return date1.getFullYear() === date2.getFullYear() &&
               date1.getMonth() === date2.getMonth() &&
               date1.getDate() === date2.getDate();
    }

    /**
     * Check if a date is disabled
     * @param {Date} date - Date to check
     * @returns {boolean} True if disabled
     */
    isDateDisabled(date) {
        if (this.options.minDate && date < this.options.minDate) {
            return true;
        }
        if (this.options.maxDate && date > this.options.maxDate) {
            return true;
        }
        return false;
    }

    /**
     * Get the current value
     * @returns {string} Current input value
     */
    getValue() {
        return this.nativeInput.value;
    }

    /**
     * Set the value
     * @param {string} value - Value to set
     */
    setValue(value) {
        this.nativeInput.value = value;
        this.updateFromNativeInput();
    }
}
