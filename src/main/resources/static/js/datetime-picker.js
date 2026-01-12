/**
 * DateTimePicker - A custom datetime picker component
 * Provides calendar, year selection, and time selection functionality
 * Updated to work with separate date and time inputs
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
        this.dateInput = element.querySelector('.date-input');
        this.timeInput = element.querySelector('.time-input');
        this.triggerButton = element.querySelector('.picker-trigger');

        // Create popup structure if it doesn't exist
        if (!element.querySelector('.picker-popup')) {
            this.createPopupStructure();
        }

        this.popup = element.querySelector('.picker-popup');
        this.calendarSection = element.querySelector('.calendar-section');
        this.yearScroll = element.querySelector('.year-scroll');
        this.timeScroll = element.querySelector('.time-scroll');

        this.currentDate = new Date();
        this.selectedDate = null;

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

        calendarSection.appendChild(calendarHeader);
        calendarSection.appendChild(weekdays);
        calendarSection.appendChild(daysGrid);

        // Year scroll section
        const yearScroll = document.createElement('div');
        yearScroll.className = 'year-scroll';

        const yearList = document.createElement('div');
        yearList.className = 'year-list';

        yearScroll.appendChild(yearList);

        // Time scroll section
        const timeScroll = document.createElement('div');
        timeScroll.className = 'time-scroll';

        const timeList = document.createElement('div');
        timeList.className = 'time-list';

        timeScroll.appendChild(timeList);

        pickerContainer.appendChild(calendarSection);
        pickerContainer.appendChild(yearScroll);
        pickerContainer.appendChild(timeScroll);

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
        this.renderTimeList();
        this.updateFromInputs();
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

        this.timeInput.addEventListener('change', () => {
            this.updateFromInputs();
            if (this.options.onValidate) {
                this.options.onValidate();
            }
        });

        // Calendar navigation
        const prevMonthBtn = this.element.querySelector('.prev-month');
        const nextMonthBtn = this.element.querySelector('.next-month');

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
        this.updateInputs();
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
        this.updateInputs();
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
     * Update the date and time inputs with the selected date/time
     */
    updateInputs() {
        if (this.selectedDate) {
            // Format date as YYYY-MM-DD
            const year = this.selectedDate.getFullYear();
            const month = (this.selectedDate.getMonth() + 1).toString().padStart(2, '0');
            const day = this.selectedDate.getDate().toString().padStart(2, '0');
            this.dateInput.value = `${year}-${month}-${day}`;

            // Format time as HH:MM
            const hours = this.selectedDate.getHours().toString().padStart(2, '0');
            const minutes = this.selectedDate.getMinutes().toString().padStart(2, '0');
            this.timeInput.value = `${hours}:${minutes}`;

            // Trigger change events
            this.dateInput.dispatchEvent(new Event('change'));
            this.timeInput.dispatchEvent(new Event('change'));
        }
    }

    /**
     * Update the picker state from the input values
     */
    updateFromInputs() {
        if (this.dateInput.value && this.timeInput.value) {
            const dateTimeString = `${this.dateInput.value}T${this.timeInput.value}`;
            this.selectedDate = new Date(dateTimeString);
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
     * Get the current value as ISO string
     * @returns {string} Current datetime value
     */
    getValue() {
        if (this.dateInput.value && this.timeInput.value) {
            return `${this.dateInput.value}T${this.timeInput.value}`;
        }
        return '';
    }

    /**
     * Set the value from ISO string
     * @param {string} value - ISO datetime string
     */
    setValue(value) {
        if (value) {
            const date = new Date(value);
            this.dateInput.value = value.split('T')[0];
            this.timeInput.value = value.split('T')[1]?.substring(0, 5) || '';
            this.updateFromInputs();
        }
    }
}
