/**
 * Horizontal Date Picker
 * A responsive horizontal date picker that allows users to select dates by scrolling horizontally
 */
class HorizontalDatePicker {
    constructor(options = {}) {
        this.options = {
            container: document.body,
            daysToShow: 15,
            daysBeforeToday: 7,
            onDateSelect: null,
            onDateRangeSelect: null,
            selectedDate: new Date(),
            showNavButtons: true, // Option to show/hide navigation buttons
            minDate: null, // Minimum selectable date
            maxDate: null, // Maximum selectable date
            showMonthRow: false, // Option to show month selection row
            showYearRow: true, // Option to show year selection row
            yearsToShow: 3, // Number of years to show in the year row
            allowFutureDates: true, // Option to allow selection of future dates
            showTodayButton: false, // Option to show a "Today" button
            ...options
        };
        
        // Track the last valid date for reverting invalid selections
        this.lastValidDate = new Date(this.options.selectedDate);
        
        // Range mode properties
        this.rangeMode = false;
        this.rangeStartDate = null;
        this.rangeEndDate = null;
        
        // Track original month/year for hover restoration
        this.originalSelectedDate = null;
        
        this.init();
    }
    
    init() {
        this._isManualSelection = false;

        this.createElements();
        this.populateDates();
        this.attachEventListeners();
        this.attachTouchEventListeners();
        
        // Scroll to selected date after a brief delay to ensure DOM is ready
        setTimeout(() => {
            this.scrollToSelectedDate(false);
        }, 0);
        
        // Highlight the current month in the month row
        if (this.options.showMonthRow) {
            this.highlightSelectedMonth();
        }
    }
    
    createElements() {
        // Create main container
        this.element = document.createElement('div');
        this.element.className = 'horizontal-date-picker';
        
        // Create month row if enabled
        if (this.options.showMonthRow) {
            this.monthRowContainer = document.createElement('div');
            this.monthRowContainer.className = 'month-row-container';
            this.element.appendChild(this.monthRowContainer);
            
            // Populate months
            this.populateMonthRow();
        }
        
        // Create date container
        this.dateContainer = document.createElement('div');
        this.dateContainer.className = 'date-picker-container';
        
        // Create navigation buttons if enabled
        if (this.options.showNavButtons) {
            // Create navigation buttons
            this.prevButton = document.createElement('button');
            this.prevButton.className = 'date-nav-button date-nav-prev';
            this.prevButton.innerHTML = '<i class="fas fa-chevron-left"></i>';
            
            this.nextButton = document.createElement('button');
            this.nextButton.className = 'date-nav-button date-nav-next';
            this.nextButton.innerHTML = '<i class="fas fa-chevron-right"></i>';
            
            // Append navigation buttons
            this.element.appendChild(this.prevButton);
            this.element.appendChild(this.nextButton);
        }
        
        // Create clear range button (initially hidden)
        this.clearRangeButton = document.createElement('button');
        this.clearRangeButton.className = 'clear-range-button';
        this.clearRangeButton.innerHTML = '<i class="fas fa-times"></i> Clear Range';
        this.clearRangeButton.style.display = 'none';
        this.element.appendChild(this.clearRangeButton);
        
        // Append date container
        this.element.appendChild(this.dateContainer);
        
        // Append to container
        this.options.container.appendChild(this.element);
    }
    
    populateDates() {
        this.dateContainer.innerHTML = '';
        
        // Use the selected date as the center point instead of today
        const centerDate = this.options.selectedDate;
        const startDate = new Date(centerDate);
        startDate.setDate(centerDate.getDate() - this.options.daysBeforeToday);
        
        // Create more dates at the beginning for initial load (2 weeks before)
        const extendedStartDate = new Date(startDate);
        extendedStartDate.setDate(startDate.getDate() - 14);
        
        // Calculate total days to show (original + 2 weeks before + 2 weeks after)
        const totalDaysToShow = this.options.daysToShow + 28;
        
        // Generate all dates including the extended range
        for (let i = 0; i < totalDaysToShow; i++) {
            const date = new Date(extendedStartDate);
            date.setDate(extendedStartDate.getDate() + i);
            
            // Skip dates outside of min/max range if specified
            if ((this.options.minDate && date < new Date(this.options.minDate)) || 
                (this.options.maxDate && date > new Date(this.options.maxDate))) {
                continue;
            }
            
            const dateItem = this.createDateElement(date);
            this.dateContainer.appendChild(dateItem);
        }
    }
    
    attachEventListeners() {
        // Date selection
        this.dateContainer.addEventListener('click', (e) => {
            const dateItem = e.target.closest('.date-item');
            if (dateItem) {
                // Check if this date is already selected
                if (dateItem.classList.contains('selected') && !this.rangeMode) {
                    // Clicking on selected date enters range mode
                    this.enterRangeMode(dateItem);
                    return;
                }
                
                if (this.rangeMode) {
                    // Check if clicking on range start or end date to exit range mode
                    const clickedDate = this.parseDate(dateItem.dataset.date);
                    if ((this.rangeStartDate && this.isSameDay(clickedDate, this.rangeStartDate)) ||
                        (this.rangeEndDate && this.isSameDay(clickedDate, this.rangeEndDate))) {
                        this.exitRangeMode();
                        return;
                    }
                    
                    // In range mode, select the end date
                    this.selectRangeEnd(dateItem);
                    return;
                }
            
                // Prevent auto-selection from interfering with manual clicks
                this._isManualSelection = true;

                // Force selection of the clicked date
                this.selectDateItem(dateItem, true);
            
                // Reset the manual selection flag after a delay
                setTimeout(() => {
                    this._isManualSelection = false;
                }, 500);
            }
        });
        
        // Add hover listener for range preview
        this.dateContainer.addEventListener('mouseover', (e) => {
            const dateItem = e.target.closest('.date-item');
            if (dateItem) {
                if (this.rangeMode && this.rangeStartDate) {
                    this.showRangePreview(dateItem);
                }
                
                // Update month row for hovered date
                this.updateMonthRowForHoveredDate(dateItem);
            }
        });
        
        this.dateContainer.addEventListener('mouseout', (e) => {
            const dateItem = e.target.closest('.date-item');
            if (dateItem && this.rangeMode && this.rangeStartDate) {
                this.clearRangePreview();
            }
        });
        
        // Restore original month row when mouse leaves the date container
        this.dateContainer.addEventListener('mouseleave', () => {
            this.restoreOriginalMonthRow();
        });
        
        // Clear range button
        this.clearRangeButton.addEventListener('click', () => {
            this.exitRangeMode();
        });
        
        // Navigation buttons (if enabled)
        if (this.options.showNavButtons) {
            this.prevButton.addEventListener('click', () => {
                this.navigateDates(-7);
            });
            
            this.nextButton.addEventListener('click', () => {
                this.navigateDates(7);
            });
        }
        
        // Scroll event handling
        let scrollTimeout;
        let isScrolling = false;
        let lastScrollTime = 0;
        
        // Initialize properties for tracking
        this._isAddingDates = false;
        this._isManualSelection = false;
        
        this.dateContainer.addEventListener('scroll', () => {
            // Throttle scroll events for better performance
            const now = Date.now();
            if (now - lastScrollTime < 16) { // ~60fps
                return;
            }
            lastScrollTime = now;

            // Clear the previous timeout
            clearTimeout(scrollTimeout);
            
            // Check if we need to add more dates
            this.checkScrollPosition();
        }, { passive: true }); // Add passive flag for better performance
    }
    
    attachTouchEventListeners() {
        let touchStartX = 0;
        let touchStartY = 0;
        let touchStartTime = 0;
        let isTouchScrolling = false;
        let touchScrollTimeout;
        
        // Touch start
        this.dateContainer.addEventListener('touchstart', (e) => {
            touchStartX = e.touches[0].clientX;
            touchStartY = e.touches[0].clientY;
            touchStartTime = Date.now();
            isTouchScrolling = false;
            
            // Clear any existing timeout
            clearTimeout(touchScrollTimeout);

        }, { passive: true });
        
        // Touch move
        this.dateContainer.addEventListener('touchmove', (e) => {
            if (!isTouchScrolling) {
                isTouchScrolling = true;
            }

            // Check if we need to add more dates
            this.checkScrollPosition();
        }, { passive: true });
        
        // Touch end
        this.dateContainer.addEventListener('touchend', (e) => {
            const touchEndX = e.changedTouches[0].clientX;
            const touchEndY = e.changedTouches[0].clientY;
            const touchEndTime = Date.now();
            
            const deltaX = touchEndX - touchStartX;
            const deltaY = touchEndY - touchStartY;
            const deltaTime = touchEndTime - touchStartTime;
            
            // Check if this was a tap (short duration, small movement)
            const isTap = deltaTime < 300 && Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10;
            
            if (isTap) {
                // Handle tap - find the date item that was tapped
                const target = document.elementFromPoint(touchStartX, touchStartY);
                const dateItem = target ? target.closest('.date-item') : null;
                
                if (dateItem) {
                    // Check if this date is already selected
                    if (dateItem.classList.contains('selected') && !this.rangeMode) {
                        // Clicking on selected date enters range mode
                        this.enterRangeMode(dateItem);
                        return;
                    }
                    
                    if (this.rangeMode) {
                        // Check if tapping on range start or end date to exit range mode
                        const tappedDate = this.parseDate(dateItem.dataset.date);
                        if ((this.rangeStartDate && this.isSameDay(tappedDate, this.rangeStartDate)) ||
                            (this.rangeEndDate && this.isSameDay(tappedDate, this.rangeEndDate))) {
                            this.exitRangeMode();
                            return;
                        }
                        
                        // In range mode, select the end date
                        this.selectRangeEnd(dateItem);
                        return;
                    }
                    
                    // Prevent auto-selection from interfering with manual taps
                    this._isManualSelection = true;

                    // Force selection of the tapped date
                    this.selectDateItem(dateItem, true);
                    
                    // Reset the manual selection flag after a delay
                    setTimeout(() => {
                        this._isManualSelection = false;
                    }, 500);
                }
            } else if (isTouchScrolling) {
                // Handle scroll end for touch
                touchScrollTimeout = setTimeout(() => {
                    isTouchScrolling = false;
                    
                    // Only handle scroll end if not in manual selection mode
                    if (!this._isManualSelection) {
                        this.handleScrollEnd();
                    }
                }, 150);
            }
        }, { passive: true });
        
        // Handle touch cancel
        this.dateContainer.addEventListener('touchcancel', () => {
            isTouchScrolling = false;
            clearTimeout(touchScrollTimeout);
        }, { passive: true });
    }

    // Check scroll position and add more dates if needed
    checkScrollPosition() {
        const container = this.dateContainer;
        const scrollLeft = container.scrollLeft;
        const scrollWidth = container.scrollWidth;
        const clientWidth = container.clientWidth;
        
        // Use a debounce mechanism to prevent multiple rapid additions
        if (this._isAddingDates) return;
        
        // If we're near the start, add more dates at the beginning
        if (scrollLeft < clientWidth * 0.2) {
            this._isAddingDates = true;
            this.addMoreDatesAtStart();
            setTimeout(() => {
                this._isAddingDates = false;
            }, 200);
        }
        
        // If we're near the end, add more dates at the end
        if (scrollLeft + clientWidth > scrollWidth - clientWidth * 0.2) {
            this._isAddingDates = true;
            this.addMoreDatesAtEnd();
            setTimeout(() => {
                this._isAddingDates = false;
            }, 200);
        }
    }
    
    // Add more dates at the beginning of the container
    addMoreDatesAtStart() {
        // Get the first date currently displayed
        const firstDateElement = this.dateContainer.firstElementChild;
        if (!firstDateElement) return;
        
        const firstDate = this.parseDate(firstDateElement.dataset.date);
        const currentScrollPosition = this.dateContainer.scrollLeft;
        
        // Temporarily disable smooth scrolling to prevent jank
        this.dateContainer.style.scrollBehavior = 'auto';
        
        // Add 7 more days before the current first date
        const fragment = document.createDocumentFragment();
        
        // Pre-calculate the width of new items (using a fixed width for consistency)
        const estimatedItemWidth = 88; // 80px width + 8px margin
        const itemsToAdd = 7;
        const estimatedAddedWidth = estimatedItemWidth * itemsToAdd;
        
        for (let i = 7; i > 0; i--) {
            const date = new Date(firstDate);
            date.setDate(date.getDate() - i);
            
            // Skip dates outside of min/max range if specified
            if ((this.options.minDate && date < new Date(this.options.minDate))) {
                continue;
            }
            
            const dateItem = this.createDateElement(date);
            fragment.appendChild(dateItem);
        }
        
        // Insert at the beginning
        if (fragment.childNodes && fragment.childNodes.length > 0) {
            // Batch DOM operations
            requestAnimationFrame(() => {
                this.dateContainer.insertBefore(fragment, this.dateContainer.firstChild);
                
                // Adjust scroll position to keep the same dates visible
                this.dateContainer.scrollLeft = currentScrollPosition + estimatedAddedWidth;
                
                // Re-enable smooth scrolling after a short delay
                setTimeout(() => {
                    this.dateContainer.style.scrollBehavior = 'smooth';
                }, 50);
            });
        }
    }
    
    // Add more dates at the end of the container
    addMoreDatesAtEnd() {
        // Get the last date currently displayed
        const lastDateElement = this.dateContainer.lastElementChild;
        if (!lastDateElement) return;
        
        const lastDate = this.parseDate(lastDateElement.dataset.date);
        
        // Temporarily disable smooth scrolling to prevent jank
        this.dateContainer.style.scrollBehavior = 'auto';
        
        // Add 7 more days after the current last date
        const fragment = document.createDocumentFragment();
        for (let i = 1; i <= 7; i++) {
            const date = new Date(lastDate);
            date.setDate(date.getDate() + i);
            
            // Skip dates outside of min/max range if specified
            if ((this.options.maxDate && date > new Date(this.options.maxDate))) {
                continue;
            }
            
            const dateItem = this.createDateElement(date);
            fragment.appendChild(dateItem);
        }
        
        // Append at the end - use requestAnimationFrame for smoother rendering
        requestAnimationFrame(() => {
            this.dateContainer.appendChild(fragment);
            
            // Re-enable smooth scrolling after a short delay
            setTimeout(() => {
                this.dateContainer.style.scrollBehavior = 'smooth';
            }, 50);
        });
    }
    
    // Create a date element
    createDateElement(date) {
        const dateItem = document.createElement('div');
        dateItem.className = 'date-item';
        dateItem.dataset.date = this.formatDate(date);
        
        // Check if this date is unavailable
        const isUnavailable = this.isDateUnavailable(date);
        if (isUnavailable) {
            dateItem.classList.add('unavailable');
        }
        
        // Check if this date is in a range
        if (this.rangeMode && this.rangeStartDate && this.rangeEndDate) {
            if (this.isDateInRange(date, this.rangeStartDate, this.rangeEndDate)) {
                dateItem.classList.add('in-range');
            }
            if (this.isSameDay(date, this.rangeStartDate)) {
                dateItem.classList.add('range-start');
            }
            if (this.isSameDay(date, this.rangeEndDate)) {
                dateItem.classList.add('range-end');
            }
        } else if (this.rangeMode && this.rangeStartDate && !this.rangeEndDate) {
            // Only start date is selected
            if (this.isSameDay(date, this.rangeStartDate)) {
                dateItem.classList.add('range-start');
            }
        }
        
        // Check if this date is selected (for non-range mode)
        if (!this.rangeMode && this.isSameDay(date, this.options.selectedDate)) {
            dateItem.classList.add('selected');
            this.selectedElement = dateItem;
        }
        
        // Add day name (Mon, Tue, etc)
        const dayName = document.createElement('span');
        dayName.className = 'day-name';
        dayName.textContent = this.getDayName(date);
        dateItem.appendChild(dayName);
        
        // Add day number
        const dayNumber = document.createElement('span');
        dayNumber.className = 'day-number';
        dayNumber.textContent = date.getDate();
        dateItem.appendChild(dayNumber);
        
        // Add month name for first day of month, but not if it's the selected date
        // to avoid duplication with month-year-name
        if (date.getDate() === 1 && !this.isSameDay(date, this.options.selectedDate)) {
            const monthName = document.createElement('span');
            monthName.className = 'month-name';
            monthName.textContent = this.getMonthName(date);
            dateItem.appendChild(monthName);
        }
        
        // Add month and year for selected date (only in non-range mode)
        if (!this.rangeMode && this.isSameDay(date, this.options.selectedDate)) {
            const monthYearName = document.createElement('span');
            monthYearName.className = 'month-year-name';
            monthYearName.textContent = `${this.getMonthName(date)} ${date.getFullYear()}`;
            dateItem.appendChild(monthYearName);
        }
        
        return dateItem;
    }

    selectDateItem(dateItem, isManualSelection = false) {
        // Check if date is within min/max range, but only if they are set
        const dateToSelect = this.parseDate(dateItem.dataset.date);
        
        if ((this.options.minDate && dateToSelect < new Date(this.options.minDate)) || 
            (this.options.maxDate && dateToSelect > new Date(this.options.maxDate))) {
            if (isManualSelection) {
                this.flashInvalidSelection(dateItem);
            }
            return; // Don't select dates outside the allowed range
        }
        
        // Check if future dates are allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            today.setHours(23, 59, 59, 59);
            if (dateToSelect > today) {
                if (isManualSelection) {
                    this.flashInvalidSelection(dateItem);
                }
                return; // Don't select future dates if not allowed
            }
        }
        
        // If we're already selecting this date and it's not a manual selection, skip
        if (this.selectedElement === dateItem && !isManualSelection) {
            return;
        }

        // Clear any existing selection
        if (this.selectedElement) {
            this.selectedElement.classList.remove('selected');
            // Remove month-year-name from previously selected item
            const monthYearEl = this.selectedElement.querySelector('.month-year-name');
            if (monthYearEl) {
                this.selectedElement.removeChild(monthYearEl);
            }
            
            // Restore month-name for first day of month on previously selected item
            const prevDate = this.parseDate(this.selectedElement.dataset.date);
            if (prevDate.getDate() === 1 && !this.selectedElement.querySelector('.month-name')) {
                const monthName = document.createElement('span');
                monthName.className = 'month-name';
                monthName.textContent = this.getMonthName(prevDate);
                this.selectedElement.appendChild(monthName);
            }
        }
        
        // Mark the new date as selected
        dateItem.classList.add('selected');
        this.selectedElement = dateItem;
        
        // Remove month-name if it exists to avoid duplication
        const monthNameEl = dateItem.querySelector('.month-name');
        if (monthNameEl) {
            dateItem.removeChild(monthNameEl);
        }
        
        // Add month and year to the selected item
        if (!dateItem.querySelector('.month-year-name')) {
            const monthYearName = document.createElement('span');
            monthYearName.className = 'month-year-name';
            monthYearName.textContent = `${this.getMonthName(dateToSelect)} ${dateToSelect.getFullYear()}`;
            dateItem.appendChild(monthYearName);
        }
        
        this.options.selectedDate = dateToSelect;
        
        // Update last valid date
        this.lastValidDate = new Date(dateToSelect);
        
        // Update the month row to highlight the correct month
        if (this.options.showMonthRow) {
            this.highlightSelectedMonth();
        }
        
        // First center the selected date to ensure it's visible
        this.scrollToSelectedDate(true);
        
        // For manual selections, call the callback immediately
        if (isManualSelection) {
            if (typeof this.options.onDateSelect === 'function') {
                this.options.onDateSelect(dateToSelect, dateItem.dataset.date, true);
            }
            
            // Dispatch custom event
            const event = new CustomEvent('dateSelected', {
                detail: {
                    date: dateToSelect,
                    formattedDate: dateItem.dataset.date,
                    isRange: false,
                    rangeStart: null,
                    rangeEnd: null
                }
            });
            this.element.dispatchEvent(event);
        }
    }
    
    // Enter range mode
    enterRangeMode(dateItem) {
        this.rangeMode = true;
        this.rangeStartDate = this.parseDate(dateItem.dataset.date);
        this.rangeEndDate = this.parseDate(dateItem.dataset.date);
        
        // Show clear range button
        this.clearRangeButton.style.display = 'flex';
        
        // Update all date items to show range mode
        this.updateDateItemsForRange();
        
        // Add visual feedback
        dateItem.classList.add('range-start');
        dateItem.classList.remove('selected');
        
        // Remove month-year-name from the start date
        const monthYearEl = dateItem.querySelector('.month-year-name');
        if (monthYearEl) {
            dateItem.removeChild(monthYearEl);
        }
        
        console.log('Entered range mode, start date:', this.rangeStartDate);
    }
    
    // Select range end date
    selectRangeEnd(dateItem) {
        const clickedDate = this.parseDate(dateItem.dataset.date);
        
        // Check if date is within min/max range
        if ((this.options.minDate && clickedDate < new Date(this.options.minDate)) || 
            (this.options.maxDate && clickedDate > new Date(this.options.maxDate))) {
            this.flashInvalidSelection(dateItem);
            return;
        }
        
        // Check if future dates are allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            today.setHours(23, 59, 59, 59);
            if (clickedDate > today) {
                this.flashInvalidSelection(dateItem);
                return;
            }
        }
        
        // Determine behavior based on where the clicked date is relative to the current range
        if (clickedDate < this.rangeStartDate) {
            // Clicking before the start: move the start date
            this.rangeStartDate = clickedDate;
            // Keep the end date as is (if it exists)
        } else if (this.rangeEndDate && this.isDateInRange(clickedDate, this.rangeStartDate, this.rangeEndDate)) {
            // Clicking inside the range: move the end date
            this.rangeEndDate = clickedDate;
        } else if (this.isSameDay(clickedDate, this.rangeStartDate)) {
            // Clicking on the start date: exit range mode
            this.exitRangeMode();
            return;
        } else {
            // Clicking after the start (or after the current end): move the end date
            this.rangeEndDate = clickedDate;
        }
        
        // Clear any preview
        this.clearRangePreview();
        
        // Update all date items to show the complete range
        this.updateDateItemsForRange();
        
        // Call the onDateRangeSelect callback with range information only if both dates are set
        if (this.rangeStartDate && this.rangeEndDate) {
            if (typeof this.options.onDateRangeSelect === 'function') {
                this.options.onDateRangeSelect(
                    this.rangeStartDate,
                    this.rangeEndDate,
                    this.formatDate(this.rangeStartDate),
                    this.formatDate(this.rangeEndDate)
                );
            }
        }

        console.log('Range selected:', this.rangeStartDate, 'to', this.rangeEndDate);
    }
    
    // Exit range mode
    exitRangeMode() {
        this.rangeMode = false;
        const lastStartDate = this.rangeStartDate;
        this.rangeStartDate = null;
        this.rangeEndDate = null;
        
        // Hide clear range button
        this.clearRangeButton.style.display = 'none';
        
        // Clear any preview
        this.clearRangePreview();
        
        // Update all date items to remove range styling
        this.updateDateItemsForRange();
        
        // Restore the original selected date
        if (lastStartDate) {
            this.options.selectedDate = lastStartDate;
            const dateItems = this.dateContainer.querySelectorAll('.date-item');
            const formattedDate = this.formatDate(lastStartDate);
            
            for (const item of dateItems) {
                if (item.dataset.date === formattedDate) {
                    this.selectDateItem(item, true);
                    break;
                }
            }
        }
        
        console.log('Exited range mode');
    }
    
    // Show range preview on hover
    showRangePreview(dateItem) {
        const hoveredDate = this.parseDate(dateItem.dataset.date);
        
        // Don't show preview if hovering over the start date or end date
        if (this.isSameDay(hoveredDate, this.rangeStartDate) || 
            (this.rangeEndDate && this.isSameDay(hoveredDate, this.rangeEndDate))) {
            return;
        }
        
        // Clear any existing preview
        this.clearRangePreview();
        
        // Determine the preview range based on where the hovered date is
        let previewStart, previewEnd;
        
        if (hoveredDate < this.rangeStartDate) {
            // Hovering before start: preview shows new start to current end (or current start if no end)
            previewStart = hoveredDate;
            previewEnd = this.rangeEndDate || this.rangeStartDate;
        } else if (this.rangeEndDate && hoveredDate <= this.rangeEndDate) {
            // Hovering inside the range: preview shows start to hovered date
            previewStart = this.rangeStartDate;
            previewEnd = hoveredDate;
        } else {
            // Hovering after start (or after end): preview shows start to hovered date
            previewStart = this.rangeStartDate;
            previewEnd = hoveredDate;
        }
        
        // Apply preview styling to dates in the range
        const dateItems = this.dateContainer.querySelectorAll('.date-item');
        dateItems.forEach(item => {
            const date = this.parseDate(item.dataset.date);
            
            // Don't apply preview to start/end dates
            if (this.isDateInRange(date, previewStart, previewEnd) && 
                !this.isSameDay(date, this.rangeStartDate) &&
                !(this.rangeEndDate && this.isSameDay(date, this.rangeEndDate))) {
                item.classList.add('range-preview');
            }
        });
    }
    
    // Clear range preview
    clearRangePreview() {
        const dateItems = this.dateContainer.querySelectorAll('.date-item');
        dateItems.forEach(item => {
            item.classList.remove('range-preview');
        });
    }
    
    // Update all date items to reflect range mode
    updateDateItemsForRange() {
        const dateItems = this.dateContainer.querySelectorAll('.date-item');
        
        dateItems.forEach(item => {
            const date = this.parseDate(item.dataset.date);
            
            // Remove all range-related classes
            item.classList.remove('in-range', 'range-start', 'range-end', 'selected', 'range-preview');
            
            // Remove month-year-name if it exists
            const monthYearEl = item.querySelector('.month-year-name');
            if (monthYearEl) {
                item.removeChild(monthYearEl);
            }
            
            // Restore month-name for first day of month
            if (date.getDate() === 1 && !item.querySelector('.month-name')) {
                const monthName = document.createElement('span');
                monthName.className = 'month-name';
                monthName.textContent = this.getMonthName(date);
                item.appendChild(monthName);
            }
            
            if (this.rangeMode) {
                if (this.rangeStartDate && this.isSameDay(date, this.rangeStartDate)) {
                    item.classList.add('range-start');
                }
                
                if (this.rangeEndDate) {
                    if (this.isSameDay(date, this.rangeEndDate)) {
                        item.classList.add('range-end');
                    }
                    
                    if (this.isDateInRange(date, this.rangeStartDate, this.rangeEndDate)) {
                        item.classList.add('in-range');
                    }
                }
            } else {
                // Restore selected state for non-range mode
                if (this.isSameDay(date, this.options.selectedDate)) {
                    item.classList.add('selected');
                    this.selectedElement = item;
                    
                    // Remove month-name to avoid duplication
                    const monthNameEl = item.querySelector('.month-name');
                    if (monthNameEl) {
                        item.removeChild(monthNameEl);
                    }
                    
                    // Add month-year-name
                    if (!item.querySelector('.month-year-name')) {
                        const monthYearName = document.createElement('span');
                        monthYearName.className = 'month-year-name';
                        monthYearName.textContent = `${this.getMonthName(date)} ${date.getFullYear()}`;
                        item.appendChild(monthYearName);
                    }
                }
            }
        });
    }

    // Update month row for hovered date
    updateMonthRowForHoveredDate(dateItem) {
        if (!this.options.showMonthRow) return;

        const hoveredDate = this.parseDate(dateItem.dataset.date);
        const hoveredYear = hoveredDate.getFullYear();
        const hoveredMonth = hoveredDate.getMonth();

        // Store the original selected date if not already stored
        if (!this.originalSelectedDate) {
            this.originalSelectedDate = new Date(this.options.selectedDate);
        }

        // Check if the hovered date is in a different month or year
        const selectedYear = this.options.selectedDate.getFullYear();
        const selectedMonth = this.options.selectedDate.getMonth();

        // Update year items
        const yearItems = this.monthRowContainer.querySelectorAll('.year-item');
        yearItems.forEach(item => {
            const itemYear = parseInt(item.dataset.year);
            item.classList.remove('selected');

            if (itemYear === hoveredYear) {
                item.classList.add('selected');
            }
        });

        // Update month items
        const monthItems = this.monthRowContainer.querySelectorAll('.month-item');
        monthItems.forEach(item => {
            const itemYear = parseInt(item.dataset.year);
            const itemMonth = parseInt(item.dataset.month);
            item.classList.remove('selected');

            if (itemYear === hoveredYear && itemMonth === hoveredMonth) {
                item.classList.add('selected');

                // Scroll to the hovered month
                item.scrollIntoView({behavior: 'smooth', block: 'nearest', inline: 'center'});
            }
        });
    }
    
    // Restore original month row
    restoreOriginalMonthRow() {
        if (!this.options.showMonthRow || !this.originalSelectedDate) return;
        
        const originalYear = this.originalSelectedDate.getFullYear();
        const originalMonth = this.originalSelectedDate.getMonth();
        
        // Restore year items
        const yearItems = this.monthRowContainer.querySelectorAll('.year-item');
        yearItems.forEach(item => {
            const itemYear = parseInt(item.dataset.year);
            item.classList.remove('selected');
            
            if (itemYear === originalYear) {
                item.classList.add('selected');
            }
        });
        
        // Restore month items
        const monthItems = this.monthRowContainer.querySelectorAll('.month-item');
        monthItems.forEach(item => {
            const itemYear = parseInt(item.dataset.year);
            const itemMonth = parseInt(item.dataset.month);
            item.classList.remove('selected');
            
            if (itemYear === originalYear && itemMonth === originalMonth) {
                item.classList.add('selected');
                
                // Scroll back to the original month
                item.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            }
        });
        
        // Clear the stored original date
        this.originalSelectedDate = null;
    }
    
    // Check if a date is in a range
    isDateInRange(date, startDate, endDate) {
        if (!startDate || !endDate) return false;
        
        const dateTime = date.getTime();
        const startTime = startDate.getTime();
        const endTime = endDate.getTime();
        
        return dateTime >= startTime && dateTime <= endTime;
    }
    
    navigateDates(offset) {
        const firstDateElement = this.dateContainer.firstElementChild;
        const firstDate = this.parseDate(firstDateElement.dataset.date);
        
        // Create a new start date by adding the offset to the first date
        const newStartDate = new Date(firstDate);
        newStartDate.setDate(newStartDate.getDate() + offset);
        
        // Check if navigation would go beyond min/max dates, but only if they are set
        if (this.options.minDate) {
            const minDate = new Date(this.options.minDate);
            if (newStartDate < minDate) {
                newStartDate.setTime(minDate.getTime());
            }
        }
        
        if (this.options.maxDate) {
            const maxDate = new Date(this.options.maxDate);
            const lastVisibleDate = new Date(newStartDate);
            lastVisibleDate.setDate(lastVisibleDate.getDate() + this.options.daysToShow - 1);
            
            if (lastVisibleDate > maxDate) {
                // Adjust start date so that max date is the last visible date
                newStartDate.setTime(maxDate.getTime());
                newStartDate.setDate(newStartDate.getDate() - this.options.daysToShow + 1);
            }
        }
        
        // Clear the container
        this.dateContainer.innerHTML = '';
        
        // Generate new dates starting from the new start date
        for (let i = 0; i < this.options.daysToShow; i++) {
            const date = new Date(newStartDate);
            date.setDate(newStartDate.getDate() + i);
            
            // Skip dates outside of min/max range if specified
            if ((this.options.minDate && date < new Date(this.options.minDate)) || 
                (this.options.maxDate && date > new Date(this.options.maxDate))) {
                continue;
            }
            
            const dateItem = this.createDateElement(date);
            this.dateContainer.appendChild(dateItem);
        }
        
        // Add extra dates at both ends for continuous scrolling
        this.addMoreDatesAtStart();
        this.addMoreDatesAtEnd();
        
        // Scroll to the selected date if it's visible, otherwise to the middle
        if (this.selectedElement) {
            this.scrollToSelectedDate(true);
        } else {
            // Scroll to the middle of the container
            this.dateContainer.scrollTo({
                left: this.dateContainer.scrollWidth / 2 - this.dateContainer.clientWidth / 2,
                behavior: 'smooth'
            });
        }
    }
    
    scrollToSelectedDate(smooth = true) {
        if (this.selectedElement) {
            const containerWidth = this.dateContainer.offsetWidth;
            const itemLeft = this.selectedElement.offsetLeft;
            const itemWidth = this.selectedElement.offsetWidth;
            
            const scrollPosition = itemLeft - (containerWidth / 2) + (itemWidth / 2);
            
            this.dateContainer.scrollTo({
                left: scrollPosition,
                behavior: smooth ? 'smooth' : 'auto'
            });
            
            // Add animation effect to the selected element
            if (smooth) {
                this.selectedElement.style.transition = 'transform 0.3s ease, background-color 0.3s ease, box-shadow 0.3s ease';
                this.selectedElement.style.transform = 'scale(1.05)';
                setTimeout(() => {
                    this.selectedElement.style.transform = '';
                }, 300);
            }
        }
    }
    
    // Helper methods
    formatDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }
    
    parseDate(dateString) {
        const parts = dateString.split('-');
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10) - 1; // Month is 0-indexed
        const day = parseInt(parts[2], 10);
        return new Date(year, month, day);
    }
    
    getDayName(date) {
        return window.locale?.days ? window.locale.days[date.getDay()] : 
               ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][date.getDay()];
    }
    
    getMonthName(date) {
        return window.locale?.months ? window.locale.months[date.getMonth()] :
               ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][date.getMonth()];
    }
    
    isSameDay(date1, date2) {
        return date1.getDate() === date2.getDate() && 
               date1.getMonth() === date2.getMonth() && 
               date1.getFullYear() === date2.getFullYear();
    }
    
    // Check if a date is unavailable
    isDateUnavailable(date) {
        // Check if date is outside min/max range
        if (this.options.minDate && date < new Date(this.options.minDate)) {
            return true;
        }
        
        if (this.options.maxDate && date > new Date(this.options.maxDate)) {
            return true;
        }
        
        // Check if future dates are not allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            today.setHours(23, 59, 59, 59);
            
            if (date > today) {
                return true;
            }
        }
        
        return false;
    }
    
    // Check if a year is unavailable
    isYearUnavailable(year) {
        // Check if the entire year is outside min/max range
        const yearStart = new Date(year, 0, 1);
        const yearEnd = new Date(year, 11, 31);
        
        if (this.options.minDate && yearEnd < new Date(this.options.minDate)) {
            return true;
        }
        
        if (this.options.maxDate && yearStart > new Date(this.options.maxDate)) {
            return true;
        }
        
        // Check if future years are not allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            const currentYear = today.getFullYear();
            
            if (year > currentYear) {
                return true;
            }
        }
        
        return false;
    }
    
    // Check if a month is unavailable
    isMonthUnavailable(year, month) {
        // Check if the entire month is outside min/max range
        const monthStart = new Date(year, month, 1);
        const monthEnd = new Date(year, month + 1, 0);
        
        if (this.options.minDate && monthEnd < new Date(this.options.minDate)) {
            return true;
        }
        
        if (this.options.maxDate && monthStart > new Date(this.options.maxDate)) {
            return true;
        }
        
        // Check if future months are not allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            const currentYear = today.getFullYear();
            const currentMonth = today.getMonth();
            
            if (year > currentYear || (year === currentYear && month > currentMonth)) {
                return true;
            }
        }
        
        return false;
    }

    // Flash invalid selection and select today's date
    flashInvalidSelection(element) {
        if (!element) return;
        
        // Store original background color
        const originalBackground = element.style.backgroundColor || '';
        
        // Set up transition for smooth flashing
        element.style.transition = 'background-color 0.15s ease';
        
        let flashCount = 0;
        const maxFlashes = 6; // 3 complete flash cycles (red -> original -> red -> original -> red -> original)
        
        const flash = () => {
            if (flashCount < maxFlashes) {
                // Alternate between red and original color
                element.style.backgroundColor = flashCount % 2 === 0 ? '#ff4444' : originalBackground;
                flashCount++;
                
                // Continue flashing
                setTimeout(flash, 150);
            } else {
                // Restore original background and go to today
                element.style.backgroundColor = originalBackground;
                
                // Go to today's date after flashing is complete
                setTimeout(() => {
                    this.goToToday();
                }, 100);
            }
        };
        
        // Start the flashing animation
        flash();
    }

    // Populate the month row
    populateMonthRow() {
        this.monthRowContainer.innerHTML = '';
        
        const selectedYear = this.options.selectedDate.getFullYear();


        // Add Today button if enabled
        if (this.options.showTodayButton) {
            const todayButton = document.createElement('div');
            todayButton.className = 'today-button';
            todayButton.innerHTML = `<i class="fas fa-calendar-day"></i> ${window.locale?.today || 'Today'}`;
            todayButton.addEventListener('click', () => {
                this.goToToday();
            });
            this.monthRowContainer.appendChild(todayButton);
        }
        // Create year row if enabled
        if (this.options.showYearRow) {
            const yearRow = document.createElement('div');
            yearRow.className = 'year-row';

            
            // Calculate how many years to show before and after the selected year
            const yearsToShow = this.options.yearsToShow;
            const halfYears = Math.floor(yearsToShow / 2);
            const startYear = selectedYear - halfYears;
            
            // Add years to the row
            for (let i = 0; i < yearsToShow; i++) {
                const year = startYear + i;
                const yearItem = document.createElement('div');
                yearItem.className = 'year-item';
                yearItem.textContent = year;
                yearItem.dataset.year = year;
                
                // Check if this year is unavailable
                const isUnavailable = this.isYearUnavailable(year);
                if (isUnavailable) {
                    yearItem.classList.add('unavailable');
                }
                
                // Mark selected year
                if (year === selectedYear) {
                    yearItem.classList.add('selected');
                }
                
                yearItem.addEventListener('click', () => {
                    if (!isUnavailable) {
                        this.selectYear(year);
                    }
                });
                yearRow.appendChild(yearItem);
            }
            
            this.monthRowContainer.appendChild(yearRow);
        }
        
        // Create month row
        const monthRow = document.createElement('div');
        monthRow.className = 'month-row';
        
        // Show all 12 months of the selected year
        for (let month = 0; month < 12; month++) {
            let year = selectedYear;
            
            const monthDate = new Date(year, month, 1);
            
            // Skip months outside min/max range if specified
            if ((this.options.minDate && monthDate < new Date(this.options.minDate)) || 
                (this.options.maxDate && monthDate > new Date(this.options.maxDate))) {
                continue;
            }
            
            const monthItem = document.createElement('div');
            monthItem.className = 'month-item';
            monthItem.dataset.year = year;
            monthItem.dataset.month = month;
            
            // Check if this month is unavailable
            const isUnavailable = this.isMonthUnavailable(year, month);
            if (isUnavailable) {
                monthItem.classList.add('unavailable');
            }
            
            // Check if this is the selected month
            if (year === this.options.selectedDate.getFullYear() && 
                month === this.options.selectedDate.getMonth()) {
                monthItem.classList.add('selected');
                this.selectedMonthElement = monthItem;
            }
            
            // Add month name
            monthItem.textContent = this.getMonthName(monthDate);
            
            // Add click event
            monthItem.addEventListener('click', () => {
                // Check if this month is already selected
                if (year === this.options.selectedDate.getFullYear() && 
                    month === this.options.selectedDate.getMonth()) {
                    return; // Do nothing if clicking on already selected month
                }
                if (!isUnavailable) {
                    this.selectMonth(year, month);
                }
            });
            
            monthRow.appendChild(monthItem);
        }
        
        this.monthRowContainer.appendChild(monthRow);
    }
    
    // Select a month
    selectMonth(year, month) {
        // Check if this month is unavailable
        if (this.isMonthUnavailable(year, month)) {
            // Find the month element and flash it
            const monthItems = this.monthRowContainer.querySelectorAll('.month-item');
            for (const item of monthItems) {
                if (parseInt(item.dataset.year) === year && parseInt(item.dataset.month) === month) {
                    this.flashInvalidSelection(item);
                    break;
                }
            }
            return;
        }

        // Get the current day from the selected date
        const currentDay = this.options.selectedDate.getDate();
        
        // Create a new date with the selected month and current year
        const newDate = new Date(this.options.selectedDate);
        newDate.setMonth(month);
        
        // Check if future dates are allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            today.setHours(23, 59, 59, 59);
            
            if (newDate > today) {
                // Find the month element and flash it
                const monthItems = this.monthRowContainer.querySelectorAll('.month-item');
                for (const item of monthItems) {
                    if (parseInt(item.dataset.year) === year && parseInt(item.dataset.month) === month) {
                        this.flashInvalidSelection(item);
                        break;
                    }
                }
                return; // Don't select future months if not allowed
            }
        }
        
        // Get the last day of the selected month
        const lastDayOfMonth = new Date(newDate.getFullYear(), month + 1, 0).getDate();
        
        // Set the day to either the current day or the last day of the month if the current day exceeds it
        newDate.setDate(Math.min(currentDay, lastDayOfMonth));
        
        // Store the exact date we want to select
        const exactSelectedDate = new Date(newDate);
        
        // Update last valid date
        this.lastValidDate = new Date(exactSelectedDate);
        
        // Completely recreate the date picker with the new date as the center
        this.options.selectedDate = exactSelectedDate;
        this.options.daysBeforeToday = Math.floor(this.options.daysToShow / 2);
        this.populateDates();
        
        // Find and force select the exact date we want
        setTimeout(() => {
            const dateItems = this.dateContainer.querySelectorAll('.date-item');
            const formattedExactDate = this.formatDate(exactSelectedDate);
            
            for (const item of dateItems) {
                if (item.dataset.date === formattedExactDate) {
                    this.selectDateItem(item, true);
                    break;
                }
            }
            
            // Highlight the selected month
            this.highlightSelectedMonth();
        }, 0);
        
        // Call onDateSelect callback if provided
        const formattedDate = this.formatDate(exactSelectedDate);
        if (typeof this.options.onDateSelect === 'function') {
            this.options.onDateSelect(exactSelectedDate, formattedDate, false);
        }
        
        // Dispatch custom event
        const event = new CustomEvent('dateSelected', {
            detail: {
                date: exactSelectedDate,
                formattedDate: formattedDate,
                isRange: false,
                rangeStart: null,
                rangeEnd: null
            }
        });
        this.element.dispatchEvent(event);
    }
    
    // Select a year
    selectYear(year) {
        // Check if this year is unavailable
        if (this.isYearUnavailable(year)) {
            // Find the year element and flash it
            const yearItems = this.monthRowContainer.querySelectorAll('.year-item');
            for (const item of yearItems) {
                if (parseInt(item.dataset.year) === year) {
                    this.flashInvalidSelection(item);
                    break;
                }
            }
            return;
        }

        // Get the current month and day from the selected date
        const currentDate = new Date(this.options.selectedDate);
        
        // Create a new date with the selected year but keep month and day
        const newDate = new Date(currentDate);
        newDate.setFullYear(year);
        
        // Check if future dates are allowed
        if (!this.options.allowFutureDates) {
            const today = new Date();
            today.setHours(23, 59, 59, 59);
            
            if (newDate > today) {
                // Find the year element and flash it
                const yearItems = this.monthRowContainer.querySelectorAll('.year-item');
                for (const item of yearItems) {
                    if (parseInt(item.dataset.year) === year) {
                        this.flashInvalidSelection(item);
                        break;
                    }
                }
                return; // Don't select future years if not allowed
            }
        }
        
        // Get the last day of the selected month in the new year
        const month = newDate.getMonth();
        const lastDayOfMonth = new Date(year, month + 1, 0).getDate();
        
        // Set the day to either the current day or the last day of the month if the current day exceeds it
        if (currentDate.getDate() > lastDayOfMonth) {
            newDate.setDate(lastDayOfMonth);
        }
        
        // Store the exact date we want to select
        const exactSelectedDate = new Date(newDate);
        
        // Update last valid date
        this.lastValidDate = new Date(exactSelectedDate);
        
        // Completely recreate the date picker with the new date as the center
        this.options.selectedDate = exactSelectedDate;
        this.options.daysBeforeToday = Math.floor(this.options.daysToShow / 2);
        this.populateDates();
        
        // Find and force select the exact date we want
        setTimeout(() => {
            const dateItems = this.dateContainer.querySelectorAll('.date-item');
            const formattedExactDate = this.formatDate(exactSelectedDate);
            
            for (const item of dateItems) {
                if (item.dataset.date === formattedExactDate) {
                    this.selectDateItem(item, true);
                    break;
                }
            }
            
            // Repopulate the month row to show the new year
            this.populateMonthRow();
        }, 0);
        
        // Call onDateSelect callback if provided
        const formattedDate = this.formatDate(exactSelectedDate);
        if (typeof this.options.onDateSelect === 'function') {
            this.options.onDateSelect(exactSelectedDate, formattedDate, false);
        }
        
        // Dispatch custom event
        const event = new CustomEvent('dateSelected', {
            detail: {
                date: exactSelectedDate,
                formattedDate: formattedDate,
                isRange: false,
                rangeStart: null,
                rangeEnd: null
            }
        });
        this.element.dispatchEvent(event);
    }
    
    // Highlight the selected month in the month row
    highlightSelectedMonth() {
        if (!this.options.showMonthRow) return;
        
        // Check if we need to repopulate the month row to keep the selected month visible
        const selectedYear = this.options.selectedDate.getFullYear();
        const selectedMonth = this.options.selectedDate.getMonth();
        
        // Update year selection
        const yearItems = this.monthRowContainer.querySelectorAll('.year-item');
        let yearVisible = false;
        
        yearItems.forEach(item => {
            const itemYear = parseInt(item.dataset.year);
            item.classList.remove('selected');
            
            if (itemYear === selectedYear) {
                item.classList.add('selected');
                yearVisible = true;
            }
        });
        
        // If selected year is not visible, repopulate the month row
        if (!yearVisible) {
            this.populateMonthRow();
            return;
        }
        
        // Update month selection
        let selectedMonthVisible = false;
        const monthItems = this.monthRowContainer.querySelectorAll('.month-item');
        
        // Remove selected class from all month items
        monthItems.forEach(item => {
            item.classList.remove('selected');
            
            const itemYear = parseInt(item.dataset.year);
            const itemMonth = parseInt(item.dataset.month);
            
            if (itemYear === selectedYear && itemMonth === selectedMonth) {
                item.classList.add('selected');
                this.selectedMonthElement = item;
                selectedMonthVisible = true;
                
                // Scroll to the selected month
                item.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            }
        });
        
        // If selected month is not visible, repopulate the month row
        if (!selectedMonthVisible) {
            this.populateMonthRow();
        }
    }
    
    // Public methods
    setDate(date) {
        const newDate = new Date(date);
        // Check if date is within min/max range, but only if they are set
        if ((this.options.minDate && newDate < new Date(this.options.minDate)) || 
            (this.options.maxDate && newDate > new Date(this.options.maxDate))) {
            console.warn('Date is outside of allowed min/max range');
            return;
        }
        
        this.options.selectedDate = newDate;
        
        // Update last valid date
        this.lastValidDate = new Date(newDate);
        
        // Adjust daysBeforeToday to center the selected date
        const today = new Date();
        if (newDate < today || newDate > today) {
            // Calculate days difference between selected date and today
            const diffTime = Math.abs(newDate - today);
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
            
            // Center the selected date in the view
            if (newDate < today) {
                this.options.daysBeforeToday = diffDays + Math.floor(this.options.daysToShow / 2);
            } else {
                this.options.daysBeforeToday = Math.floor(this.options.daysToShow / 2) - diffDays;
            }
        }
        
        this.populateDates();
        
        // Update the month row if enabled
        if (this.options.showMonthRow) {
            this.highlightSelectedMonth();
        }


        // Find and mark the selected date element
        setTimeout(() => {
            const dateItems = this.dateContainer.querySelectorAll('.date-item');
            const formattedDate = this.formatDate(newDate);
            
            for (const item of dateItems) {
                if (item.dataset.date === formattedDate) {
                    if (this.selectedElement) {
                        this.selectedElement.classList.remove('selected');
                    }
                    item.classList.add('selected');
                    this.selectedElement = item;
                    break;
                }
            }

            if (typeof this.options.onDateSelect === 'function') {
                this.options.onDateSelect(today, formattedDate, false);
            }
            // Center the selected date
            this.scrollToSelectedDate(false);
            const event = new CustomEvent('dateSelected', {
                detail: {
                    date: newDate,
                    formattedDate: formattedDate,
                    isRange: false,
                    rangeStart: null,
                    rangeEnd: null
                }
            });
            this.element.dispatchEvent(event);

        }, 0);
    }
    
    // Go to today's date
    goToToday() {
        const today = new Date();
        
        // Check if future dates are allowed
        if (!this.options.allowFutureDates) {
            today.setHours(23, 59, 59, 59);
        }
        
        // Reset daysBeforeToday to default
        this.options.daysBeforeToday = Math.floor(this.options.daysToShow / 2);
        
        // Set date to today
        this.setDate(today);
        
        // Call onDateSelect callback
        const formattedDate = this.formatDate(today);
        if (typeof this.options.onDateSelect === 'function') {
            this.options.onDateSelect(today, formattedDate, true);
        }
        
        // Dispatch custom event
        const event = new CustomEvent('dateSelected', {
            detail: {
                date: today,
                formattedDate: formattedDate,
                isRange: false,
                rangeStart: null,
                rangeEnd: null
            }
        });
        this.element.dispatchEvent(event);
    }
}
