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
        
        // Create navigation buttons
        this.prevButton = document.createElement('button');
        this.prevButton.className = 'date-nav-button date-nav-prev';
        this.prevButton.innerHTML = '<i class="fas fa-chevron-left"></i>';
        
        this.nextButton = document.createElement('button');
        this.nextButton.className = 'date-nav-button date-nav-next';
        this.nextButton.innerHTML = '<i class="fas fa-chevron-right"></i>';
        
        // Append elements
        this.element.appendChild(this.prevButton);
        this.element.appendChild(this.dateContainer);
        this.element.appendChild(this.nextButton);
        
        // Append to container
        this.options.container.appendChild(this.element);
    }
    
    populateDates() {
        this.dateContainer.innerHTML = '';
        
        const today = new Date();
        const startDate = new Date(today);
        startDate.setDate(today.getDate() - this.options.daysBeforeToday);
        
        for (let i = 0; i < this.options.daysToShow; i++) {
            const date = new Date(startDate);
            date.setDate(startDate.getDate() + i);
            
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
        
        // Navigation buttons
        this.prevButton.addEventListener('click', () => {
            this.navigateDates(-7);
        });
        
        this.nextButton.addEventListener('click', () => {
            this.navigateDates(7);
        });
    }
    
    selectDate(dateItem) {
        if (this.selectedElement) {
            this.selectedElement.classList.remove('selected');
        }
        
        dateItem.classList.add('selected');
        this.selectedElement = dateItem;
        
        const selectedDate = this.parseDate(dateItem.dataset.date);
        this.options.selectedDate = selectedDate;
        
        // Call onDateSelect callback if provided
        if (typeof this.options.onDateSelect === 'function') {
            this.options.onDateSelect(selectedDate, dateItem.dataset.date);
        }
        
        // Dispatch custom event
        const event = new CustomEvent('dateSelected', {
            detail: {
                date: selectedDate,
                formattedDate: dateItem.dataset.date
            }
        });
        this.element.dispatchEvent(event);
    }
    
    navigateDates(offset) {
        const firstDateElement = this.dateContainer.firstElementChild;
        const firstDate = this.parseDate(firstDateElement.dataset.date);
        
        firstDate.setDate(firstDate.getDate() + offset);
        
        const today = new Date();
        const startDate = new Date(firstDate);
        
        this.populateDates();
        this.scrollToSelectedDate(true);
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
    
    // Public methods
    setDate(date) {
        this.options.selectedDate = new Date(date);
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
