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
            selectedDate: new Date(),
            autoSelectOnScroll: false, // Option to auto-select date when scrolling
            showNavButtons: true, // Option to show/hide navigation buttons
            minDate: null, // Minimum selectable date
            maxDate: null, // Maximum selectable date
            ...options
        };
        
        this.init();
    }
    
    init() {
        this.createElements();
        this.populateDates();
        this.attachEventListeners();
        this.scrollToSelectedDate(false);
    }
    
    createElements() {
        // Create main container
        this.element = document.createElement('div');
        this.element.className = 'horizontal-date-picker';
        
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
        
        // Append date container
        this.element.appendChild(this.dateContainer);
        
        // Append to container
        this.options.container.appendChild(this.element);
    }
    
    populateDates() {
        this.dateContainer.innerHTML = '';
        
        const today = new Date();
        const startDate = new Date(today);
        startDate.setDate(today.getDate() - this.options.daysBeforeToday);
        
        // Always show exactly daysToShow days
        for (let i = 0; i < this.options.daysToShow; i++) {
            const date = new Date(startDate);
            date.setDate(startDate.getDate() + i);
            
            // Skip dates outside of min/max range if specified
            if ((this.options.minDate && date < new Date(this.options.minDate)) || 
                (this.options.maxDate && date > new Date(this.options.maxDate))) {
                continue;
            }
            
            const dateItem = document.createElement('div');
            dateItem.className = 'date-item';
            dateItem.dataset.date = this.formatDate(date);
            
            // Check if this date is selected
            if (this.isSameDay(date, this.options.selectedDate)) {
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
            
            // Add month name for first day of month or first day in view
            if (date.getDate() === 1 || i === 0) {
                const monthName = document.createElement('span');
                monthName.className = 'month-name';
                monthName.textContent = this.getMonthName(date);
                dateItem.appendChild(monthName);
            }
            
            this.dateContainer.appendChild(dateItem);
        }
    }
    
    attachEventListeners() {
        // Date selection
        this.dateContainer.addEventListener('click', (e) => {
            const dateItem = e.target.closest('.date-item');
            if (dateItem) {
                this.selectDate(dateItem);
            }
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
        this.dateContainer.addEventListener('scroll', () => {
            // Clear the previous timeout
            clearTimeout(scrollTimeout);
            
            // Set a timeout to detect when scrolling stops
            scrollTimeout = setTimeout(() => {
                this.handleScrollEnd();
            }, 150);
        });
    }
    
    selectDate(dateItem) {
        // Check if date is within min/max range, but only if they are set
        const dateToSelect = this.parseDate(dateItem.dataset.date);
        
        if ((this.options.minDate && dateToSelect < new Date(this.options.minDate)) || 
            (this.options.maxDate && dateToSelect > new Date(this.options.maxDate))) {
            return; // Don't select dates outside the allowed range
        }
        
        if (this.selectedElement) {
            this.selectedElement.classList.remove('selected');
        }
        
        dateItem.classList.add('selected');
        this.selectedElement = dateItem;
        
        this.options.selectedDate = dateToSelect;
        
        // Call onDateSelect callback if provided
        if (typeof this.options.onDateSelect === 'function') {
            this.options.onDateSelect(dateToSelect, dateItem.dataset.date);
        }
        
        // Dispatch custom event
        const event = new CustomEvent('dateSelected', {
            detail: {
                date: dateToSelect,
                formattedDate: dateItem.dataset.date
            }
        });
        this.element.dispatchEvent(event);
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
            
            const dateItem = document.createElement('div');
            dateItem.className = 'date-item';
            dateItem.dataset.date = this.formatDate(date);
            
            // Check if this date is selected
            if (this.isSameDay(date, this.options.selectedDate)) {
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
            
            // Add month name for first day of month or first day in view
            if (date.getDate() === 1 || i === 0) {
                const monthName = document.createElement('span');
                monthName.className = 'month-name';
                monthName.textContent = this.getMonthName(date);
                dateItem.appendChild(monthName);
            }
            
            this.dateContainer.appendChild(dateItem);
        }
        
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
        return new Date(dateString);
    }
    
    getDayName(date) {
        const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
        return days[date.getDay()];
    }
    
    getMonthName(date) {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        return months[date.getMonth()];
    }
    
    isSameDay(date1, date2) {
        return date1.getDate() === date2.getDate() && 
               date1.getMonth() === date2.getMonth() && 
               date1.getFullYear() === date2.getFullYear();
    }
    
    // Handle scroll end event
    handleScrollEnd() {
        if (this.options.autoSelectOnScroll) {
            // Find the date item closest to the center of the container
            const containerRect = this.dateContainer.getBoundingClientRect();
            const containerCenter = containerRect.left + containerRect.width / 2;
            
            let closestItem = null;
            let closestDistance = Infinity;
            
            // Find the closest date item to the center
            const dateItems = this.dateContainer.querySelectorAll('.date-item');
            dateItems.forEach(item => {
                const itemRect = item.getBoundingClientRect();
                const itemCenter = itemRect.left + itemRect.width / 2;
                const distance = Math.abs(containerCenter - itemCenter);
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestItem = item;
                }
            });
            
            // Select the closest date if it's not already selected
            if (closestItem && !closestItem.classList.contains('selected')) {
                this.selectDate(closestItem);
            }
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
        this.scrollToSelectedDate(true);
    }
    
    getSelectedDate() {
        return this.options.selectedDate;
    }
    
    refresh() {
        this.populateDates();
        this.scrollToSelectedDate(false);
    }
}
