/** Timeband constants and helpers **/

const TIMEBANDS = {
    DAY: 'day',
    MONTH: 'month',
    YEAR: 'year'
};

const TIMEBAND_ORDER = {
    [TIMEBANDS.YEAR]: 0,
    [TIMEBANDS.MONTH]: 1,
    [TIMEBANDS.DAY]: 2
};

const DEFAULT_ITEMS_TO_ADD = {
    [TIMEBANDS.DAY]: 25,
    [TIMEBANDS.MONTH]: 6,
    [TIMEBANDS.YEAR]: 5
};

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

function clampToStartOfDay(date) {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    return d;
}

function areSameDay(a, b) {
    return !!a && !!b &&
        a.getFullYear() === b.getFullYear() &&
        a.getMonth() === b.getMonth() &&
        a.getDate() === b.getDate();
}

function areSameMonth(a, b) {
    return !!a && !!b &&
        a.getFullYear() === b.getFullYear() &&
        a.getMonth() === b.getMonth();
}

function areSameYear(a, b) {
    return !!a && !!b && a.getFullYear() === b.getFullYear();
}

function getTimebandStart(date, timeband) {
    const d = new Date(date);
    switch (timeband) {
        case TIMEBANDS.DAY:
            return clampToStartOfDay(d);
        case TIMEBANDS.MONTH:
            return new Date(d.getFullYear(), d.getMonth(), 1);
        case TIMEBANDS.YEAR:
            return new Date(d.getFullYear(), 0, 1);
        default:
            return clampToStartOfDay(d);
    }
}

function getTimebandEnd(date, timeband) {
    const d = new Date(date);
    switch (timeband) {
        case TIMEBANDS.DAY:
            return clampToStartOfDay(d);
        case TIMEBANDS.MONTH:
            // last day of current month
            return new Date(d.getFullYear(), d.getMonth() + 1, 0);
        case TIMEBANDS.YEAR:
            return new Date(d.getFullYear(), 11, 31);
        default:
            return clampToStartOfDay(d);
    }
}

function addToTimeband(date, timeband, offset) {
    const d = new Date(date);
    switch (timeband) {
        case TIMEBANDS.DAY:
            return new Date(d.getTime() + offset * ONE_DAY_MS);
        case TIMEBANDS.MONTH:
            return new Date(d.getFullYear(), d.getMonth() + offset, 1);
        case TIMEBANDS.YEAR:
            return new Date(d.getFullYear() + offset, 0, 1);
        default:
            return new Date(d);
    }
}

function isSameByTimeband(a, b, timeband) {
    switch (timeband) {
        case TIMEBANDS.YEAR:
            return areSameYear(a, b);
        case TIMEBANDS.MONTH:
            return areSameMonth(a, b);
        case TIMEBANDS.DAY:
        default:
            return areSameDay(a, b);
    }
}

/** Timeband utilities **/

class TimebandUtils {
    /**
     * Dynamically add items to the left or right for a given timeband.
     * Keeps scroll position stable when adding to the left.
     */
    static addItemsToDirection(datePicker, direction, timeband) {
        const isLeft = direction === 'left';
        const { items, scrollContainer, timebandConfigs } = datePicker;

        if (!items.length) return;

        const config = timebandConfigs[timeband];
        if (!config) return;

        const referenceDate = isLeft
            ? items[0].date
            : items[items.length - 1].date;

        const previousScrollLeft = scrollContainer.scrollLeft;
        const previousBehavior = scrollContainer.style.scrollBehavior;
        scrollContainer.style.scrollBehavior = 'auto';

        const estimatedItemWidth = (config.itemWidth || 0) + 1;
        const count = this.getItemsToAddCount(timeband);
        const estimatedAddedWidth = estimatedItemWidth * count;

        const newItems = this.generateNewItems(
            datePicker,
            referenceDate,
            timeband,
            count,
            isLeft
        );

        // Update in-memory items
        if (isLeft) {
            datePicker.items = newItems.concat(datePicker.items);
        } else {
            datePicker.items = datePicker.items.concat(newItems);
        }

        // Render using fragment for minimal reflow
        const fragment = document.createDocumentFragment();
        newItems.forEach((itemData) => {
            const el = config.createItemElement(itemData, 0); // index will be updated later
            itemData.element = el;
            fragment.appendChild(el);
        });

        requestAnimationFrame(() => {
            if (isLeft) {
                scrollContainer.insertBefore(fragment, scrollContainer.firstChild);
                scrollContainer.scrollLeft = previousScrollLeft + estimatedAddedWidth;
            } else {
                scrollContainer.appendChild(fragment);
            }

            // Restore smooth scrolling after a short delay
            setTimeout(() => {
                scrollContainer.style.scrollBehavior = previousBehavior || 'smooth';
            }, 50);

            // Update dataset.idx for all elements to maintain correct indices
            this.updateElementIndices(datePicker);
        });
    }

    static updateElementIndices(datePicker) {
        const { scrollContainer } = datePicker;
        const elements = scrollContainer.querySelectorAll('.timeband-item');
        elements.forEach((el, index) => {
            el.dataset.idx = String(index);
        });
    }

    static getItemsToAddCount(timeband) {
        return DEFAULT_ITEMS_TO_ADD[timeband] || DEFAULT_ITEMS_TO_ADD[TIMEBANDS.DAY];
    }

    static generateNewItems(datePicker, referenceDate, timeband, count, isLeft) {
        const { timebandConfigs } = datePicker;
        const cfg = timebandConfigs[timeband];
        if (!cfg || typeof cfg.getItemData !== 'function') return [];

        const items = [];
        if (isLeft) {
            // Insert dates before reference (negative offsets)
            for (let i = count; i > 0; i--) {
                const date = addToTimeband(referenceDate, timeband, -i);
                items.push(cfg.getItemData(date));
            }
        } else {
            // Insert dates after reference (positive offsets)
            for (let i = 1; i <= count; i++) {
                const date = addToTimeband(referenceDate, timeband, i);
                items.push(cfg.getItemData(date));
            }
        }
        return items;
    }

    static getItemRangeStart(itemData, timeband) {
        return getTimebandStart(itemData.date, timeband);
    }

    static getItemRangeEnd(itemData, timeband) {
        return getTimebandEnd(itemData.date, timeband);
    }
}

/** Selection manager **/

class SelectionManager {
    /**
     * Responsible solely for selection state + semantics.
     * No DOM here; DatePicker asks it and updates UI.
     */
    constructor(datePicker) {
        this.datePicker = datePicker;

        this.selectedStartDate = null;
        this.selectedEndDate = null;

        // true while user has picked start and is hovering/selecting end
        this.isSelectingRange = false;

        // lock semantics for single-date mode
        this.isDateLocked = false;

        // timeband where selection was started, used to validate completion
        this.selectionTimeband = null;
    }

    /* Basic API */

    clearSelection() {
        this.selectedStartDate = null;
        this.selectedEndDate = null;
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    setSelectedRange(startDate, endDate = null) {
        this.selectedStartDate = startDate ? clampToStartOfDay(startDate) : null;
        this.selectedEndDate = endDate ? clampToStartOfDay(endDate) : null;
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    getSelectedRange() {
        const { selectedStartDate, selectedEndDate, datePicker } = this;
        return {
            startDate: selectedStartDate
                ? datePicker.formatDate(selectedStartDate)
                : null,
            endDate: selectedEndDate
                ? datePicker.formatDate(selectedEndDate)
                : null,
            timeband: datePicker.currentTimeband
        };
    }

    /* Day click handlers */

    handleDayClick(itemData) {
        const { options } = this.datePicker;
        if (options.singleDateMode) {
            this.#handleSingleDateModeDayClick(itemData);
        } else if (!options.allowRangeSelection) {
            this.#handleSingleSelectionClick(itemData);
        } else {
            this.#handleRangeSelectionClick(itemData);
        }
    }

    #handleSingleDateModeDayClick(itemData) {
        const clicked = clampToStartOfDay(itemData.date);

        if (!this.selectedStartDate) {
            return this.#selectSingleDate(clicked);
        }

        const sameAsStart = this.isSameDate(clicked, this.selectedStartDate);

        if (sameAsStart && !this.selectedEndDate) {
            return this.#toggleDateLock();
        }

        if (this.isDateLocked && !this.selectedEndDate) {
            return this.#createRangeFromLockedDate(clicked);
        }

        if (this.selectedStartDate && this.selectedEndDate) {
            return this.#handleExistingRangeClick(clicked);
        }

        return this.#selectSingleDate(clicked);
    }

    #selectSingleDate(date) {
        this.selectedStartDate = new Date(date);
        this.selectedEndDate = null;
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = this.datePicker.currentTimeband;
    }

    #toggleDateLock() {
        this.isDateLocked = !this.isDateLocked;
        if (this.isDateLocked) {
            this.selectionTimeband = this.datePicker.currentTimeband;
        }
    }

    #createRangeFromLockedDate(clicked) {
        if (clicked < this.selectedStartDate) {
            this.selectedEndDate = this.selectedStartDate;
            this.selectedStartDate = clicked;
        } else {
            this.selectedEndDate = clicked;
        }
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    #handleExistingRangeClick(clicked) {
        const start = new Date(this.selectedStartDate);
        const end = new Date(this.selectedEndDate);

        if (this.isSameDate(clicked, start)) {
            this.#handleSingleSelectionClick({date: clicked});
        } else if (this.isSameDate(clicked, end)) {
            this.#handleSingleSelectionClick({date: clicked});
        } else if (clicked < start) {
            this.selectedStartDate = clicked;
        } else if (clicked > end) {
            this.selectedEndDate = clicked;
        } else {
            // Click inside range adjusts the range start
            this.selectedStartDate = clicked;
        }
    }

    #handleSingleSelectionClick(itemData) {
        this.selectedStartDate = clampToStartOfDay(itemData.date);
        this.selectedEndDate = null;
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    #handleRangeSelectionClick(itemData) {
        const clicked = clampToStartOfDay(itemData.date);

        if (!this.selectedStartDate || (this.selectedStartDate && this.selectedEndDate)) {
            // Start new range
            this.selectedStartDate = clicked;
            this.selectedEndDate = null;
            this.isSelectingRange = true;
            this.selectionTimeband = this.datePicker.currentTimeband;
            return;
        }

        if (this.isSelectingRange) {
            // Complete current range
            if (clicked < this.selectedStartDate) {
                this.selectedEndDate = this.selectedStartDate;
                this.selectedStartDate = clicked;
            } else {
                this.selectedEndDate = clicked;
            }
            this.isSelectingRange = false;
            this.selectionTimeband = null;
        }
    }

    /* Timeband-based interactions (month/year) */

    handleTimebandRangeSelection(itemData, timeband) {
        const clickedStart = getTimebandStart(itemData.date, timeband);

        if (this.datePicker.options.singleDateMode) {
            this.#handleTimebandSingleDateMode(clickedStart, timeband);
        } else {
            this.#handleTimebandRangeMode(clickedStart, timeband);
        }
    }

    #handleTimebandSingleDateMode(clickedStart, timeband) {
        if (!this.selectedStartDate) {
            return this.#selectSingleTimebandDate(clickedStart, timeband);
        }

        const same = this.isSameTimebandDate(clickedStart, this.selectedStartDate, timeband);

        if (same && !this.selectedEndDate) {
            return this.#toggleDateLock();
        }

        if (this.isDateLocked && !this.selectedEndDate) {
            return this.#createTimebandRangeFromLockedDate(clickedStart, timeband);
        }

        if (this.selectedStartDate && this.selectedEndDate) {
            return this.#handleExistingTimebandRangeClick(clickedStart, timeband);
        }

        return this.#selectSingleTimebandDate(clickedStart, timeband);
    }

    #selectSingleTimebandDate(date, timeband) {
        this.selectedStartDate = new Date(date);
        this.selectedEndDate = null;
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = timeband;
    }

    #createTimebandRangeFromLockedDate(clickedStart, timeband) {
        const selectedStart = this.selectedStartDate;
        const newEnd = getTimebandEnd(clickedStart, timeband);

        if (clickedStart < selectedStart) {
            this.selectedEndDate = getTimebandEnd(selectedStart, timeband);
            this.selectedStartDate = clickedStart;
        } else {
            this.selectedEndDate = newEnd;
        }

        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    #handleExistingTimebandRangeClick(clickedStart, timeband) {
        const start = getTimebandStart(this.selectedStartDate, timeband);
        const end = getTimebandStart(this.selectedEndDate, timeband);

        if (this.isSameTimebandDate(clickedStart, start, timeband) ||
            this.isSameTimebandDate(clickedStart, end, timeband)) {
            this.clearSelection();
            return;
        }

        if (clickedStart < start) {
            this.selectedStartDate = clickedStart;
        } else if (clickedStart > end) {
            this.selectedEndDate = getTimebandEnd(clickedStart, timeband);
        } else {
            // Inside current range: treat as new start
            this.selectedStartDate = clickedStart;
        }

        this.isSelectingRange = false;
        this.selectionTimeband = null;
    }

    #handleTimebandRangeMode(clickedStart, timeband) {
        if (!this.selectedStartDate || (this.selectedStartDate && this.selectedEndDate)) {
            // Start new timeband range
            this.startTimebandRangeSelection(clickedStart, timeband);
        } else if (this.isSelectingRange) {
            this.completeTimebandRangeSelection(clickedStart, timeband);
        } else {
            this.#handleExistingTimebandRangeClick(clickedStart, timeband);
        }
    }

    startTimebandRangeSelection(clickedStart, timeband) {
        this.selectedStartDate = clickedStart;
        this.selectedEndDate = null;
        this.isSelectingRange = true;
        this.selectionTimeband = timeband;
    }

    completeTimebandRangeSelection(clickedStart, timeband) {
        const normalizedStart = getTimebandStart(this.selectedStartDate, timeband);

        if (clickedStart < normalizedStart) {
            this.selectedEndDate = getTimebandEnd(normalizedStart, timeband);
            this.selectedStartDate = clickedStart;
        } else {
            this.selectedEndDate = getTimebandEnd(clickedStart, timeband);
        }

        this.isSelectingRange = false;
        this.selectionTimeband = null;
    }

    selectFullTimeband(date, timeband) {
        this.selectedStartDate = getTimebandStart(date, timeband);
        this.selectedEndDate = getTimebandEnd(date, timeband);
        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
    }

    /* Cross-timeband range completion */

    canCompleteRangeAtCurrentTimeband() {
        if (!this.isSelectingRange || !this.selectedStartDate || !this.selectionTimeband) {
            return false;
        }
        const current = this.datePicker.currentTimeband;
        return TIMEBAND_ORDER[current] >= TIMEBAND_ORDER[this.selectionTimeband];
    }

    completeRangeAtTimeband(itemData) {
        const tb = this.selectionTimeband;
        if (!tb) return false;

        let end;
        if (tb === TIMEBANDS.YEAR) {
            end = this.#getYearEndDateForTimeband(itemData, this.datePicker.currentTimeband);
        } else if (tb === TIMEBANDS.MONTH) {
            end = this.#getMonthEndDateForTimeband(itemData, this.datePicker.currentTimeband);
        } else if (tb === TIMEBANDS.DAY) {
            end = this.#getDayEndDateForTimeband(itemData, this.datePicker.currentTimeband);
        }

        if (!end) return false;

        if (end < this.selectedStartDate) {
            this.selectedEndDate = this.selectedStartDate;
            this.selectedStartDate = end;
        } else {
            this.selectedEndDate = end;
        }

        this.isSelectingRange = false;
        this.isDateLocked = false;
        this.selectionTimeband = null;
        return true;
    }

    #getYearEndDateForTimeband(itemData, tb) {
        const d = itemData.date;
        if (tb === TIMEBANDS.YEAR) return new Date(d.getFullYear(), 11, 31);
        if (tb === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
        if (tb === TIMEBANDS.DAY) return clampToStartOfDay(d);
        return null;
    }

    #getMonthEndDateForTimeband(itemData, tb) {
        const d = itemData.date;
        if (tb === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
        if (tb === TIMEBANDS.DAY) return clampToStartOfDay(d);
        return null;
    }

    #getDayEndDateForTimeband(itemData, tb) {
        const d = itemData.date;
        if (tb === TIMEBANDS.DAY) return clampToStartOfDay(d);
        if (tb === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
        if (tb === TIMEBANDS.YEAR) return new Date(d.getFullYear(), 11, 31);
        return null;
    }

    /* Hover helpers */

    getHoverOverlayText(itemData) {
        if (!this.selectedStartDate) return null;

        const currentTimeband = this.datePicker.currentTimeband;
        const clicked = new Date(itemData.date);
        const selected = new Date(this.selectedStartDate);

        if (currentTimeband === TIMEBANDS.DAY) {
            return this.#getDayHoverText(clicked, selected);
        }
        if (currentTimeband === TIMEBANDS.MONTH) {
            return this.#getMonthHoverText(clicked, selected);
        }
        if (currentTimeband === TIMEBANDS.YEAR) {
            return this.#getYearHoverText(clicked, selected);
        }
        return null;
    }

    #getDayHoverText(clicked, selected) {
        if (this.datePicker.isSameDay(clicked, selected)) {
            if (!this.selectedEndDate) {
                return this.isDateLocked ? 'Click to unlock date' : 'Click to lock date';
            }
            return 'Click to clear selection';
        }

        if (this.isDateLocked && !this.selectedEndDate) {
            return 'Click to create range';
        }

        if (this.selectedEndDate) {
            return this.#getExistingRangeHoverText(clicked);
        }

        return null;
    }

    #getMonthHoverText(clicked, selected) {
        if (this.datePicker.isSameMonth(clicked, selected)) {
            if (!this.selectedEndDate) {
                return this.isDateLocked ? 'Click to unlock month' : 'Click to lock month';
            }
            return 'Click to clear selection';
        }

        if (this.isDateLocked && !this.selectedEndDate) {
            return 'Click to create range';
        }

        if (this.selectedEndDate) {
            return this.#getExistingRangeHoverText(clicked);
        }

        return null;
    }

    #getYearHoverText(clicked, selected) {
        if (this.datePicker.isSameYear(clicked, selected)) {
            if (!this.selectedEndDate) {
                return this.isDateLocked ? 'Click to unlock year' : 'Click to lock year';
            }
            return 'Click to clear selection';
        }

        if (this.isDateLocked && !this.selectedEndDate) {
            return 'Click to create range';
        }

        if (this.selectedEndDate) {
            return this.#getExistingRangeHoverText(clicked);
        }

        return null;
    }

    #getExistingRangeHoverText(clicked) {
        const start = new Date(this.selectedStartDate);
        const end = new Date(this.selectedEndDate);
        const timeband = this.datePicker.currentTimeband;

        // Check if clicking on the end boundary based on current timeband
        let isEndBoundary = false;
        if (timeband === TIMEBANDS.DAY) {
            isEndBoundary = this.datePicker.isSameDay(clicked, end);
        } else if (timeband === TIMEBANDS.MONTH) {
            isEndBoundary = this.datePicker.isSameMonth(clicked, end);
        } else if (timeband === TIMEBANDS.YEAR) {
            isEndBoundary = this.datePicker.isSameYear(clicked, end);
        }

        if (isEndBoundary) {
            return 'Click to clear selection';
        }
        if (clicked < start) {
            return 'Click to expand range backward';
        }
        if (clicked > end) {
            return 'Click to expand range forward';
        }
        return 'Click to adjust range start';
    }

    getHoverTooltipText(itemData) {
        if (!this.selectedStartDate || !this.isSelectingRange) return null;

        const timeband = this.datePicker.currentTimeband;
        let start = this.selectedStartDate;
        let end;

        if (timeband === TIMEBANDS.DAY) {
            end = clampToStartOfDay(itemData.date);
        } else if (timeband === TIMEBANDS.MONTH) {
            end = this.#getMonthEndDateForTooltip(itemData);
        } else if (timeband === TIMEBANDS.YEAR) {
            end = this.#getYearEndDateForTooltip(itemData);
        }

        if (!end) return null;

        if (end < start) {
            [start, end] = [end, start];
        }

        if (this.datePicker.isSameDay(start, end)) {
            return `Select: ${this.datePicker.formatDate(start)}`;
        }

        return `Select: ${this.datePicker.formatDate(start)} to ${this.datePicker.formatDate(end)}`;
    }

    #getMonthEndDateForTooltip(itemData) {
        const d = itemData.date;
        if (this.selectionTimeband === TIMEBANDS.DAY) {
            // Align to month start if user started in day view
            return new Date(d.getFullYear(), d.getMonth(), 1);
        }
        return new Date(d.getFullYear(), d.getMonth() + 1, 0);
    }

    #getYearEndDateForTooltip(itemData) {
        const d = itemData.date;
        if (this.selectionTimeband === TIMEBANDS.DAY ||
            this.selectionTimeband === TIMEBANDS.MONTH) {
            // Align to year start if started from a more granular band
            return new Date(d.getFullYear(), 0, 1);
        }
        return new Date(d.getFullYear(), 11, 31);
    }

    /* Comparators (delegate to DatePicker for consistency) */

    isSameDate(a, b) {
        return this.datePicker.isSameDay(a, b);
    }

    isSameTimebandDate(a, b, timeband) {
        if (timeband === TIMEBANDS.MONTH) return this.datePicker.isSameMonth(a, b);
        if (timeband === TIMEBANDS.YEAR) return this.datePicker.isSameYear(a, b);
        return this.datePicker.isSameDay(a, b);
    }
}

/** DatePicker core **/

class DatePicker {
    constructor(containerId, options = {}) {
        this.container = document.getElementById(containerId);
        if (!this.container) {
            throw new Error(`DatePicker: container with id "${containerId}" not found.`);
        }

        const normalizedStart = clampToStartOfDay(options.startDate || new Date());

        this.options = {
            daysToShow: 14,
            prefetchDays: 25,
            allowRangeSelection: true,
            allowMonthRangeSelection: true,
            allowYearRangeSelection: true,
            singleDateMode: false,
            dateFormat: 'YYYY-MM-DD',
            startDate: normalizedStart,
            initialTimeband: TIMEBANDS.DAY,
            transitionDuration: 400,
            transitionEffect: 'perspective',
            hoverInfoPosition: 'above',
            renderDayItem: null,
            renderMonthItem: null,
            renderYearItem: null,
            renderLeftIndicator: null,
            renderRightIndicator: null,
            renderHoverOverlay: null,
            ...options,
        };

        this.items = [];
        this.selectionManager = new SelectionManager(this);
        this.eventListeners = {};
        this.currentTimeband = this.options.initialTimeband;
        this.isTransitioning = false;

        this.hoverTooltip = null;
        this.hoverOverlay = null;

        // For potential virtual scrolling; currently unused but kept for compatibility
        this.elementPool = [];
        this.visibleElements = new Map();
        this.lastVisibleRange = { start: -1, end: -1 };
        this.virtualWrapper = null;

        this.timebandConfigs = this.#createTimebandConfigs();

        this.init();
    }

    #createTimebandConfigs() {
        return {
            [TIMEBANDS.DAY]: {
                itemWidth: 80,
                generateItems: () => this.#generateDays(),
                createItemElement: (data, index) => this.createDayElement(data, index),
                getItemData: (date) => this.createDayData(date),
                addToLeft: () => TimebandUtils.addItemsToDirection(this, 'left', TIMEBANDS.DAY),
                addToRight: () => TimebandUtils.addItemsToDirection(this, 'right', TIMEBANDS.DAY)
            },
            [TIMEBANDS.MONTH]: {
                itemWidth: 100,
                generateItems: () => this.#generateMonths(),
                createItemElement: (data, index) => this.createMonthElement(data, index),
                getItemData: (date) => this.createMonthData(date),
                addToLeft: () => TimebandUtils.addItemsToDirection(this, 'left', TIMEBANDS.MONTH),
                addToRight: () => TimebandUtils.addItemsToDirection(this, 'right', TIMEBANDS.MONTH)
            },
            [TIMEBANDS.YEAR]: {
                itemWidth: 120,
                generateItems: () => this.#generateYears(),
                createItemElement: (data, index) => this.createYearElement(data, index),
                getItemData: (date) => this.createYearData(date),
                addToLeft: () => TimebandUtils.addItemsToDirection(this, 'left', TIMEBANDS.YEAR),
                addToRight: () => TimebandUtils.addItemsToDirection(this, 'right', TIMEBANDS.YEAR)
            }
        };
    }

    /** Initialization **/

    init() {
        this.#createContainer();
        this.#generateInitialItems();
        this.render();
        this.#setupScrollListener();
        this.#setupDelegatedItemEvents();
    }

    #createContainer() {
        this.container.innerHTML = '';
        this.container.className = 'date-picker';

        this.scrollContainer = document.createElement('div');
        this.scrollContainer.className = `date-picker-container timeband-${this.currentTimeband}`;
        this.container.appendChild(this.scrollContainer);

        this.#createRangeIndicators();
        this.#createHoverInfo();
        this.#createHoverOverlay();
    }

    #generateInitialItems() {
        const config = this.timebandConfigs[this.currentTimeband];
        if (config && typeof config.generateItems === 'function') {
            config.generateItems();
        }
    }

    #generateDays() {
        const center = this.options.startDate || new Date();
        const total = this.options.daysToShow + (this.options.prefetchDays * 6);
        const start = new Date(center);
        start.setDate(center.getDate() - Math.floor(total / 2));

        this.items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(start);
            d.setDate(start.getDate() + i);
            this.items.push(this.createDayData(d));
        }
    }

    #generateMonths() {
        const center = this.options.startDate || new Date();
        const total = 24;
        const start = new Date(center.getFullYear(), center.getMonth() - Math.floor(total / 2), 1);

        this.items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
            this.items.push(this.createMonthData(d));
        }
    }

    #generateYears() {
        const center = this.options.startDate || new Date();
        const total = 20;
        const startYear = center.getFullYear() - Math.floor(total / 2);

        this.items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(startYear + i, 0, 1);
            this.items.push(this.createYearData(d));
        }
    }

    /** Data factories **/

    createDayData(date) {
        const now = new Date();
        return {
            date: clampToStartOfDay(date),
            element: null,
            isToday: areSameDay(date, now),
            type: TIMEBANDS.DAY
        };
    }

    createMonthData(date) {
        const now = new Date();
        return {
            date: new Date(date.getFullYear(), date.getMonth(), 1),
            element: null,
            isToday: areSameMonth(date, now),
            type: TIMEBANDS.MONTH
        };
    }

    createYearData(date) {
        const now = new Date();
        return {
            date: new Date(date.getFullYear(), 0, 1),
            element: null,
            isToday: areSameYear(date, now),
            type: TIMEBANDS.YEAR
        };
    }

    /** Rendering **/

    render() {
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return;

        this.scrollContainer.innerHTML = '';
        this.scrollContainer.className = `date-picker-container timeband-${this.currentTimeband}`;

        this.elementPool = [];
        this.visibleElements.clear();
        this.lastVisibleRange = { start: -1, end: -1 };
        this.itemByTime = new Map();

        const fragment = document.createDocumentFragment();
        this.items.forEach((itemData, index) => {
            const el = cfg.createItemElement(itemData, index);
            // Ensure common metadata for event delegation
            this.#prepareItemElement(el, itemData, index);
            itemData.element = el;
            fragment.appendChild(el);
        });
        this.scrollContainer.appendChild(fragment);

        this.scrollToCenter(true);
        this.requestSelectionUpdate();
    }

    /** Item elements **/

    // Small helpers to reduce duplication in element creation
    #maybeUseCustomRenderer(renderer, data, index) {
        if (!renderer) return null;
        const el = renderer(data, index, this);
        if (el) this.#prepareItemElement(el, data, index);
        return el || null;
    }

    #buildItemElement({ baseClass = 'timeband-item', today = false, children = [] }, itemData, index) {
        const el = document.createElement('div');
        el.className = baseClass;
        if (today) el.classList.add('today');

        for (const c of children) {
            const child = document.createElement('div');
            if (c.className) child.className = c.className;
            if (c.text != null) child.textContent = c.text;
            el.appendChild(child);
        }

        this.#prepareItemElement(el, itemData, index);
        return el;
    }

    createDayElement(dayData, index) {
        const custom = this.#maybeUseCustomRenderer(this.options.renderDayItem, dayData, index);
        if (custom) return custom;

        return this.#buildItemElement(
            {
                baseClass: 'date-day timeband-item',
                today: dayData.isToday,
                children: [
                    { className: 'day-name secondary-text', text: this.getDayName(dayData.date) },
                    { className: 'day-number primary-text', text: dayData.date.getDate() },
                    { className: 'month-year tertiary-text', text: this.getMonthYear(dayData.date) }
                ]
            },
            dayData,
            index
        );
    }

    createMonthElement(monthData, index) {
        const custom = this.#maybeUseCustomRenderer(this.options.renderMonthItem, monthData, index);
        if (custom) return custom;

        return this.#buildItemElement(
            {
                today: monthData.isToday,
                children: [
                    { className: 'secondary-text', text: monthData.date.getFullYear() },
                    { className: 'primary-text', text: this.getMonthName(monthData.date) }
                ]
            },
            monthData,
            index
        );
    }

    createYearElement(yearData, index) {
        const custom = this.#maybeUseCustomRenderer(this.options.renderYearItem, yearData, index);
        if (custom) return custom;

        return this.#buildItemElement(
            {
                today: yearData.isToday,
                children: [
                    { className: 'primary-text', text: yearData.date.getFullYear() }
                ]
            },
            yearData,
            index
        );
    }

    #prepareItemElement(el, itemData, index) {
        if (!el) return;
        if (!el.classList.contains('timeband-item')) {
            el.classList.add('timeband-item');
        }
        el.dataset.time = String(itemData.date.getTime());
        if (index != null) el.dataset.idx = String(index);
        if (!this.itemByTime) this.itemByTime = new Map();
        this.itemByTime.set(itemData.date.getTime(), itemData);
    }

    /** Click dispatch **/

    #setupDelegatedItemEvents() {
        // Store bound handlers for destroy()
        this._onClick = (e) => {
            const el = e.target && e.target.closest && e.target.closest('.timeband-item');
            if (!el || !this.scrollContainer.contains(el)) return;
            const idx = el.dataset && el.dataset.idx ? Number(el.dataset.idx) : NaN;
            let data = !Number.isNaN(idx) ? this.items[idx] : null;
            if (!data && el.dataset && el.dataset.time && this.itemByTime) {
                data = this.itemByTime.get(Number(el.dataset.time)) || null;
            }
            if (data) this.handleItemClick(data);
        };

        this._onMouseOver = (e) => {
            const el = e.target && e.target.closest && e.target.closest('.timeband-item');
            if (!el || !this.scrollContainer.contains(el)) return;
            const idx = el.dataset && el.dataset.idx ? Number(el.dataset.idx) : NaN;
            let data = !Number.isNaN(idx) ? this.items[idx] : null;
            if (!data && el.dataset && el.dataset.time && this.itemByTime) {
                data = this.itemByTime.get(Number(el.dataset.time)) || null;
            }
            if (data) this.handleItemHover(data, e);
        };

        this._onMouseOut = (e) => {
            const toEl = e.relatedTarget && (e.relatedTarget.closest ? e.relatedTarget.closest('.timeband-item') : null);
            const fromEl = e.target && e.target.closest && e.target.closest('.timeband-item');
            if (fromEl && (!toEl || !this.scrollContainer.contains(toEl))) {
                this.hideHoverInfo();
                this.hideHoverOverlay();
            }
        };

        this.scrollContainer.addEventListener('click', this._onClick, { passive: true });
        this.scrollContainer.addEventListener('mouseover', this._onMouseOver, { passive: true });
        this.scrollContainer.addEventListener('mouseout', this._onMouseOut, { passive: true });
    }

    handleItemClick(itemData) {
        const sm = this.selectionManager;

        if (sm.isSelectingRange &&
            sm.selectedStartDate &&
            sm.canCompleteRangeAtCurrentTimeband()) {
            this.completeRangeSelection(itemData);
            return;
        }

        if (sm.isSelectingRange &&
            sm.selectionTimeband &&
            sm.selectionTimeband !== this.currentTimeband) {
            this.transitionToTimebandForRangeCompletion(itemData);
            return;
        }

        if (this.currentTimeband === TIMEBANDS.MONTH) {
            this.handleMonthClick(itemData);
            return;
        }

        if (this.currentTimeband === TIMEBANDS.YEAR) {
            this.handleYearClick(itemData);
            return;
        }

        this.handleDayClick(itemData);
    }

    handleDayClick(itemData) {
        this.selectionManager.handleDayClick(itemData);
        this.emitSelectionChange();
    }

    handleMonthClick(itemData) {
        if (this.options.allowMonthRangeSelection || this.options.singleDateMode) {
            this.selectionManager.handleTimebandRangeSelection(itemData, TIMEBANDS.MONTH);
        } else {
            this.selectionManager.selectFullTimeband(itemData.date, TIMEBANDS.MONTH);
        }
        this.emitSelectionChange();
    }

    handleYearClick(itemData) {
        if (this.options.allowYearRangeSelection || this.options.singleDateMode) {
            this.selectionManager.handleTimebandRangeSelection(itemData, TIMEBANDS.YEAR);
        } else {
            this.selectionManager.selectFullTimeband(itemData.date, TIMEBANDS.YEAR);
        }
        this.emitSelectionChange();
    }

    completeRangeSelection(itemData) {
        if (this.selectionManager.completeRangeAtTimeband(itemData)) {
            this.requestSelectionUpdate();
            this.emitSelectionChange();
        }
    }

    emitSelectionChange() {
        this.requestSelectionUpdate();
        this.emit('selectionChange', this.selectionManager.getSelectedRange());
    }

    /** Wheel/timeband transitions **/

    handleTimebandTransition(itemData) {
        if (this.selectionManager.isSelectingRange && this.selectionManager.selectionTimeband) {
            // Do not auto-drill while user is mid-selection
            return;
        }

        if (this.currentTimeband === TIMEBANDS.YEAR) {
            const targetDate = new Date(itemData.date.getFullYear(), 0, 1);
            this.transitionToTimeband(TIMEBANDS.MONTH, targetDate);
        } else if (this.currentTimeband === TIMEBANDS.MONTH) {
            const d = itemData.date;
            const targetDate = new Date(d.getFullYear(), d.getMonth(), 1);
            this.transitionToTimeband(TIMEBANDS.DAY, targetDate);
        }
    }

    handleWheelTransition(deltaY, event) {
        if (this.isTransitioning) return;

        const mouseDate = this.getDateUnderMouse(event);
        const mousePos = this.getMousePositionInContainer(event);
        let targetDate;

        if (deltaY > 0) {
            // Zoom out
            if (this.currentTimeband === TIMEBANDS.DAY) {
                this.transitionToTimeband(TIMEBANDS.MONTH, mouseDate, mousePos);
            } else if (this.currentTimeband === TIMEBANDS.MONTH) {
                this.transitionToTimeband(TIMEBANDS.YEAR, mouseDate, mousePos);
            }
        } else {
            // Zoom in
            if (this.currentTimeband === TIMEBANDS.YEAR) {
                targetDate = new Date(mouseDate.getFullYear(), 0, 1);
                this.transitionToTimeband(TIMEBANDS.MONTH, targetDate, mousePos);
            } else if (this.currentTimeband === TIMEBANDS.MONTH) {
                targetDate = new Date(mouseDate.getFullYear(), mouseDate.getMonth(), 1);
                this.transitionToTimeband(TIMEBANDS.DAY, targetDate, mousePos);
            }
        }
    }

    // Perform one wheel step based on direction and current timeband
    #performWheelStep(dir, mouseDate, mousePos) {
        if (this.isTransitioning) return;
        const date = mouseDate || this.getCenterDate();
        const pos = (mousePos != null) ? mousePos : Math.floor(this.scrollContainer.clientWidth / 2);

        if (dir > 0) { // zoom out
            if (this.currentTimeband === TIMEBANDS.DAY) {
                this.transitionToTimeband(TIMEBANDS.MONTH, date, pos);
                return;
            }
            if (this.currentTimeband === TIMEBANDS.MONTH) {
                this.transitionToTimeband(TIMEBANDS.YEAR, date, pos);
                return;
            }
            // Already at max zoom out (YEAR)
            this._wheelChainCount = 0;
            this._wheelChainDir = 0;
        } else if (dir < 0) { // zoom in
            if (this.currentTimeband === TIMEBANDS.YEAR) {
                const target = new Date(date.getFullYear(), 0, 1);
                this.transitionToTimeband(TIMEBANDS.MONTH, target, pos);
                return;
            }
            if (this.currentTimeband === TIMEBANDS.MONTH) {
                const target = new Date(date.getFullYear(), date.getMonth(), 1);
                this.transitionToTimeband(TIMEBANDS.DAY, target, pos);
                return;
            }
            // Already at max zoom in (DAY)
            this._wheelChainCount = 0;
            this._wheelChainDir = 0;
        }
    }

    /** Position helpers **/

    getCenterDate() {
        const { scrollLeft, clientWidth } = this.scrollContainer;
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return this.options.startDate || new Date();

        const width = (cfg.itemWidth || 0) + 1;
        const centerPos = scrollLeft + (clientWidth / 2);
        const index = Math.floor(centerPos / width);

        if (index >= 0 && index < this.items.length) {
            return this.items[index].date;
        }

        return this.options.startDate || new Date();
    }

    getDateUnderMouse(event) {
        const rect = this.scrollContainer.getBoundingClientRect();
        const mouseX = event.clientX - rect.left;
        const { scrollLeft } = this.scrollContainer;
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return this.getCenterDate();

        const width = (cfg.itemWidth || 0) + 1;
        const position = scrollLeft + mouseX;
        const index = Math.floor(position / width);

        if (index >= 0 && index < this.items.length) {
            return this.items[index].date;
        }

        return this.getCenterDate();
    }

    getMousePositionInContainer(event) {
        const rect = this.scrollContainer.getBoundingClientRect();
        return event.clientX - rect.left;
    }

    /** Selection UI **/

    updateSelection() {
        const tb = this.currentTimeband;
        this.items.forEach(item => this.updateItemSelection(item, tb));
        this.updateRangeIndicators();
    }

    requestSelectionUpdate() {
        if (this._pendingSelRaf) return;
        this._pendingSelRaf = requestAnimationFrame(() => {
            this._pendingSelRaf = 0;
            this.updateSelection();
        });
    }

    updateItemSelection(itemData, timeband) {
        const el = itemData.element;
        if (!el) return;

        const sm = this.selectionManager;

        el.classList.remove(
            'selected',
            'in-range',
            'range-start',
            'range-end',
            'locked'
        );

        if (!sm.selectedStartDate) return;

        const itemStart = TimebandUtils.getItemRangeStart(itemData, timeband);
        const itemEnd = TimebandUtils.getItemRangeEnd(itemData, timeband);
        const selStart = sm.selectedStartDate;
        const selEnd = sm.selectedEndDate || sm.selectedStartDate;

        const overlaps =
            itemStart.getTime() <= selEnd.getTime() &&
            itemEnd.getTime() >= selStart.getTime();

        if (!overlaps) return;

        const containsStart =
            itemStart.getTime() <= selStart.getTime() &&
            itemEnd.getTime() >= selStart.getTime();
        const containsEnd =
            itemStart.getTime() <= selEnd.getTime() &&
            itemEnd.getTime() >= selEnd.getTime();
        const fullyInside =
            itemStart.getTime() >= selStart.getTime() &&
            itemEnd.getTime() <= selEnd.getTime();

        if (containsStart && containsEnd) {
            el.classList.add('selected', 'range-start', 'range-end');
            if (this.options.singleDateMode && sm.isDateLocked && !sm.selectedEndDate) {
                el.classList.add('locked');
            }
        } else if (containsStart) {
            el.classList.add('selected', 'range-start');
        } else if (containsEnd) {
            el.classList.add('selected', 'range-end');
        } else if (fullyInside) {
            el.classList.add('in-range');
        } else {
            el.classList.add('in-range');
        }
    }

    /** Scroll / infinite items **/

    #setupScrollListener() {
        this._scrollTimeout = null;
        this._wheelTimeout = null;
        this._lastWheelTime = 0;
        this._horizontalScrollActive = false;
        this._horizontalTimeout = null;
        this._touchStartX = 0;
        this._touchStartY = 0;
        this._touchLock = null; // 'x' | 'y' | null
        // Chain state to allow multiple zoom steps without pauses
        this._wheelChainDir = 0; // 1 = zoom out, -1 = zoom in
        this._wheelChainCount = 0; // queued steps to continue after transition
        this._wheelChainMouseDate = null;
        this._wheelChainMousePos = null;
        // Gesture aggregation to avoid skipping on a single mouse notch
        this._wheelGestureDir = 0;
        this._wheelGestureCount = 0;
        this._wheelGestureWindowUntil = 0;
        this._wheelGestureStart = 0;
        this._wheelGestureDelta = 0;

        this._onScroll = () => {
            this._horizontalScrollActive = true;
            clearTimeout(this._horizontalTimeout);
            this._horizontalTimeout = setTimeout(() => {
                this._horizontalScrollActive = false;
            }, 200);

            clearTimeout(this._scrollTimeout);
            this._scrollTimeout = setTimeout(() => {
                this.handleScroll();
                this.updateRangeIndicators();
                this.emit('viewChange');
            }, 50);
        };

        this._onWheel = (e) => {
            const now = Date.now();
            const vertical = Math.abs(e.deltaY) > Math.abs(e.deltaX) &&
                Math.abs(e.deltaY) > 15 &&
                Math.abs(e.deltaX) < 5;
            const horizontal = Math.abs(e.deltaX) > Math.abs(e.deltaY) &&
                Math.abs(e.deltaX) > 5;

            if (vertical) {
                e.preventDefault();
                // Stop both propagation and immediate propagation to ensure
                // no ancestor scroll handlers (including page) react.
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                e.stopPropagation();
                if (this._horizontalScrollActive) return;

                const dir = e.deltaY > 0 ? 1 : -1; // 1 = zoom out, -1 = zoom in
                const mouseDate = this.getDateUnderMouse(e);
                const mousePos = this.getMousePositionInContainer(e);

                if (this.isTransitioning) {
                    // Aggregate events within a short window before deciding to skip modes
                    const windowMs = 180;
                    if (this._wheelGestureDir !== dir || now > this._wheelGestureWindowUntil) {
                        this._wheelGestureDir = dir;
                        this._wheelGestureCount = 0;
                        this._wheelGestureStart = now;
                        this._wheelGestureDelta = 0;
                    }
                    this._wheelGestureWindowUntil = now + windowMs;
                    this._wheelGestureCount += 1;
                    this._wheelGestureDelta += Math.abs(e.deltaY);

                    // Consider device type
                    const isDiscreteDevice = e.deltaMode === 1 || e.deltaMode === 2; // lines/pages
                    // Only consider extra steps if the gesture persists beyond a minimal duration
                    const gestureDuration = now - this._wheelGestureStart;
                    let desired = 0;

                    if (isDiscreteDevice) {
                        // For mouse wheels with discrete notches: require multiple fast ticks to skip
                        if (gestureDuration >= 80) {
                            if (this._wheelGestureCount >= 4) desired = 2;
                            else if (this._wheelGestureCount >= 2) desired = 1;
                        }
                    } else {
                        // For trackpads/continuous devices: use higher thresholds to avoid accidental skips
                        if (gestureDuration >= 120) {
                            const byCount = (this._wheelGestureCount >= 12) ? 2 : (this._wheelGestureCount >= 6 ? 1 : 0);
                            const byDelta = (this._wheelGestureDelta >= 800) ? 2 : (this._wheelGestureDelta >= 400 ? 1 : 0);
                            desired = Math.max(byCount, byDelta);
                        }
                    }

                    if (desired > 0) {
                        this._wheelChainDir = dir;
                        // Keep up to two queued steps total
                        this._wheelChainCount = Math.min(desired, 2);
                        this._wheelChainMouseDate = mouseDate;
                        this._wheelChainMousePos = mousePos;
                    }
                    return;
                }

                // Start/refresh gesture window for the immediate step
                this._wheelGestureDir = dir;
                this._wheelGestureCount = 1;
                this._wheelGestureStart = now;
                this._wheelGestureDelta = Math.abs(e.deltaY);
                this._wheelGestureWindowUntil = now + 180;

                // Perform one immediate step; any further wheel events during the
                // transition will be queued and continued automatically
                this.#performWheelStep(dir, mouseDate, mousePos);
            } else if (horizontal) {
                // native horizontal scroll
            } else {
                e.preventDefault();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                e.stopPropagation();
            }
        };

        // Touch gesture handling to block page scroll while interacting
        this._onTouchStart = (e) => {
            if (!e.touches || e.touches.length === 0) return;
            const t = e.touches[0];
            this._touchStartX = t.clientX;
            this._touchStartY = t.clientY;
            this._touchLock = null;
        };

        this._onTouchMove = (e) => {
            if (!e.touches || e.touches.length === 0) return;
            const t = e.touches[0];
            const dx = Math.abs(t.clientX - this._touchStartX);
            const dy = Math.abs(t.clientY - this._touchStartY);
            if (!this._touchLock) {
                // Determine intent after a small threshold
                const threshold = 8; // px
                if (dx < threshold && dy < threshold) return;
                this._touchLock = dx > dy ? 'x' : 'y';
            }
            // If vertical or ambiguous, prevent page scroll
            if (this._touchLock === 'y') {
                e.preventDefault();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                e.stopPropagation();
            }
            // If horizontal, allow native horizontal scrolling of the container
        };

        this.scrollContainer.addEventListener('scroll', this._onScroll, { passive: true });
        // Use capture to intercept early and prevent page scroll leaks.
        this.scrollContainer.addEventListener('wheel', this._onWheel, { passive: false, capture: true });
        this.scrollContainer.addEventListener('touchstart', this._onTouchStart, { passive: true });
        this.scrollContainer.addEventListener('touchmove', this._onTouchMove, { passive: false, capture: true });
    }

    handleScroll() {
        const { scrollLeft, scrollWidth, clientWidth } = this.scrollContainer;
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return;

        const itemWidth = cfg.itemWidth || 0;
        const threshold = itemWidth * 5 || 400;

        if (scrollLeft < threshold) {
            cfg.addToLeft && cfg.addToLeft();
        }

        if (scrollLeft + clientWidth > scrollWidth - threshold) {
            cfg.addToRight && cfg.addToRight();
        }
    }

    scrollToCenter(instant = false) {
        const centerDate = this.options.startDate || new Date();
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return;

        const index = this.#findIndexForDate(centerDate, this.currentTimeband);
        const itemWidth = (cfg.itemWidth || 0) + 1;
        const containerWidth = this.scrollContainer.clientWidth;

        const target =
            index >= 0
                ? (index * itemWidth) - (containerWidth / 2) + (itemWidth / 2)
                : (this.scrollContainer.scrollWidth - containerWidth) / 2;

        this.#setScrollLeft(Math.max(0, target), instant);
    }

    #findIndexForDate(targetDate, timeband) {
        if (!targetDate) return -1;

        return this.items.findIndex(item =>
            isSameByTimeband(item.date, targetDate, timeband)
        );
    }

    #setScrollLeft(value, instant) {
        if (instant) {
            const prev = this.scrollContainer.style.scrollBehavior;
            this.scrollContainer.style.scrollBehavior = 'auto';
            this.scrollContainer.scrollLeft = value;
            setTimeout(() => {
                this.scrollContainer.style.scrollBehavior = prev || 'smooth';
            }, 50);
        } else {
            this.scrollContainer.scrollLeft = value;
        }
    }

    /** External selection API **/

    setSelectedRange(startDate, endDate = null) {
        this.selectionManager.setSelectedRange(startDate, endDate);
        // If a start date is provided, transition to DAY timeband and scroll to it
        if (startDate) {
            const targetDate = clampToStartOfDay(startDate);
            // Update the start date to ensure items are generated around the target
            this.options.startDate = targetDate;

            // If not already in DAY timeband, transition to it
            if (this.currentTimeband !== TIMEBANDS.DAY) {
                // Don't emit selection change yet - wait for transition to complete
                this.pendingSelectionEmit = true;
                this.transitionToTimeband(TIMEBANDS.DAY, targetDate);
            } else {
                // Already in DAY timeband, regenerate items around the target date and scroll
                this.#generateInitialItems();
                this.render();
                this.scrollToCenter(false);
                this.requestSelectionUpdate();
                this.emit('selectionChange', this.selectionManager.getSelectedRange());
            }
        } else {
            this.requestSelectionUpdate();
            this.emit('selectionChange', this.selectionManager.getSelectedRange());
        }
    }

    clearSelection() {
        this.selectionManager.clearSelection();
        this.requestSelectionUpdate();
        this.emit('selectionChange', { startDate: null, endDate: null });
    }

    getSelectedRange() {
        return this.selectionManager.getSelectedRange();
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

    /** Lifecycle **/

    destroy() {
        // Remove scroll/wheel listeners
        if (this.scrollContainer) {
            if (this._onScroll) this.scrollContainer.removeEventListener('scroll', this._onScroll);
            // Must match capture option used on addEventListener to successfully remove
            if (this._onWheel) this.scrollContainer.removeEventListener('wheel', this._onWheel, { capture: true });
            if (this._onClick) this.scrollContainer.removeEventListener('click', this._onClick);
            if (this._onMouseOver) this.scrollContainer.removeEventListener('mouseover', this._onMouseOver);
            if (this._onMouseOut) this.scrollContainer.removeEventListener('mouseout', this._onMouseOut);
            if (this._onTouchStart) this.scrollContainer.removeEventListener('touchstart', this._onTouchStart);
            if (this._onTouchMove) this.scrollContainer.removeEventListener('touchmove', this._onTouchMove, { capture: true });
        }

        // Clear timers and RAFs
        if (this._scrollTimeout) clearTimeout(this._scrollTimeout);
        if (this._wheelTimeout) clearTimeout(this._wheelTimeout);
        if (this._horizontalTimeout) clearTimeout(this._horizontalTimeout);
        if (this._pendingSelRaf) cancelAnimationFrame(this._pendingSelRaf);

        // Remove indicators and overlays from DOM
        if (this.leftIndicator && this.leftIndicator.parentNode) this.leftIndicator.parentNode.removeChild(this.leftIndicator);
        if (this.rightIndicator && this.rightIndicator.parentNode) this.rightIndicator.parentNode.removeChild(this.rightIndicator);
        if (this.hoverInfo && this.hoverInfo.parentNode) this.hoverInfo.parentNode.removeChild(this.hoverInfo);
        if (this.hoverOverlay && this.hoverOverlay.parentNode) this.hoverOverlay.parentNode.removeChild(this.hoverOverlay);

        // Null references to help GC
        this.items = [];
        this.itemByTime && this.itemByTime.clear();
        this.itemByTime = null;
        this.visibleElements && this.visibleElements.clear();
        this.visibleElements = null;
        this.elementPool = null;

        this._onScroll = null;
        this._onWheel = null;
        this._onClick = null;
        this._onMouseOver = null;
        this._onMouseOut = null;
        this._onTouchStart = null;
        this._onTouchMove = null;

        // Reset wheel chain state
        this._wheelChainDir = 0;
        this._wheelChainCount = 0;
        this._wheelChainMouseDate = null;
        this._wheelChainMousePos = null;
        this._wheelGestureDir = 0;
        this._wheelGestureCount = 0;
        this._wheelGestureWindowUntil = 0;
        this._wheelGestureStart = 0;
        this._wheelGestureDelta = 0;
    }

    /** Compare + format helpers **/

    isSameDay(a, b) {
        return areSameDay(a, b);
    }

    isSameMonth(a, b) {
        return areSameMonth(a, b);
    }

    isSameYear(a, b) {
        return areSameYear(a, b);
    }

    formatDate(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');

        switch (this.options.dateFormat) {
            case 'MM/DD/YYYY':
                return `${m}/${d}/${y}`;
            case 'DD/MM/YYYY':
                return `${d}/${m}/${y}`;
            case 'YYYY-MM-DD':
            default:
                return `${y}-${m}-${d}`;
        }
    }

    // Cached Intl formatters for i18n-friendly names
    #fmt = null;
    #ensureFormatters() {
        if (this.#fmt) return;
        const locale = this.options && this.options.locale ? this.options.locale : undefined;
        try {
            this.#fmt = {
                weekday: new Intl.DateTimeFormat(locale, { weekday: 'short' }),
                month: new Intl.DateTimeFormat(locale, { month: 'short' })
            };
        } catch (e) {
            // Fallback: leave #fmt null; we will use static arrays
            this.#fmt = null;
        }
    }

    getDayName(date) {
        this.#ensureFormatters();
        if (this.#fmt && this.#fmt.weekday) return this.#fmt.weekday.format(date);
        return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][date.getDay()];
    }

    getMonthYear(date) {
        const m = this.getMonthName(date);
        return `${m} ${date.getFullYear()}`;
    }

    getMonthName(date) {
        this.#ensureFormatters();
        if (this.#fmt && this.#fmt.month) return this.#fmt.month.format(date);
        return ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
            'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][date.getMonth()];
    }

    /** Timeband transitions **/

    transitionToTimeband(timeband, centerDate = null, alignPosition = null) {
        if (timeband === this.currentTimeband) {
            // Already at target timeband, just update if needed
            if (centerDate) {
                this.options.startDate = new Date(centerDate);
                this.scrollToCenter(false);
            }
            if (this.pendingSelectionEmit) {
                this.pendingSelectionEmit = false;
                this.requestSelectionUpdate();
                this.emit('selectionChange', this.selectionManager.getSelectedRange());
            }
            return;
        }

        if (this.isTransitioning) return;

        const cfg = this.timebandConfigs[timeband];
        if (!cfg) return;

        this.isTransitioning = true;
        this.scrollContainer.classList.add('transitioning');

        const from = TIMEBAND_ORDER[this.currentTimeband];
        const to = TIMEBAND_ORDER[timeband];

        this.scrollContainer.classList.toggle('forward', to > from);
        this.scrollContainer.classList.toggle('backward', to < from);
        this.scrollContainer.classList.toggle('zoom-in', to > from);
        this.scrollContainer.classList.toggle('zoom-out', to < from);

        if (centerDate) {
            this.options.startDate = new Date(centerDate);
        }

        this.pendingAlignPosition = alignPosition;

        const half = this.options.transitionDuration / 2;

        requestAnimationFrame(() => {
            setTimeout(() => {
                this.currentTimeband = timeband;
                this.#generateInitialItems();
                this.render();

                if (this.pendingAlignPosition != null) {
                    this.scrollToAlignPosition(this.options.startDate, this.pendingAlignPosition, true);
                } else {
                    this.scrollToCenter(true);
                }

                if (this.pendingRangeCompletion) {
                    this.completeRangeSelectionAfterTransition();
                }

                requestAnimationFrame(() => {
                    setTimeout(() => {
                        this.scrollContainer.classList.remove(
                            'transitioning',
                            'forward',
                            'backward',
                            'zoom-in',
                            'zoom-out'
                        );
                        this.isTransitioning = false;
                        this.pendingAlignPosition = null;

                        this.emit('timebandChange', {
                            timeband: this.currentTimeband,
                            centerDate: this.options.startDate
                        });

                        this.emit('viewChange');

                        this.requestSelectionUpdate();

                        // Emit pending selection change if setSelectedRange triggered this transition
                        if (this.pendingSelectionEmit) {
                            this.pendingSelectionEmit = false;
                            this.emit('selectionChange', this.selectionManager.getSelectedRange());
                        }

                        // If there are queued wheel steps, continue chaining without pause
                        this.#continueWheelChain();
                    }, half);
                });
            }, half);
        });
    }

    // Continue queued wheel zoom steps after a transition completes
    #continueWheelChain() {
        if (!this._wheelChainCount || !this._wheelChainDir) return;
        // Do not auto-drill while user is mid selection at a specific timeband
        if (this.selectionManager && this.selectionManager.isSelectingRange && this.selectionManager.selectionTimeband) {
            this._wheelChainCount = 0;
            this._wheelChainDir = 0;
            return;
        }

        if (this.isTransitioning) return;
        const date = this._wheelChainMouseDate || this.getCenterDate();
        const pos = (this._wheelChainMousePos != null) ? this._wheelChainMousePos : Math.floor(this.scrollContainer.clientWidth / 2);

        // Consume one step and perform. Remaining steps will be consumed on next end.
        this._wheelChainCount = Math.max(0, this._wheelChainCount - 1);
        this.#performWheelStep(this._wheelChainDir, date, pos);
        // If performWheelStep hits a boundary and doesn't transition, clear state
        if (!this.isTransitioning && this._wheelChainCount === 0) {
            this._wheelChainDir = 0;
            this._wheelChainMouseDate = null;
            this._wheelChainMousePos = null;
        }
    }

    getCurrentTimeband() {
        return this.currentTimeband;
    }

    setTimeband(timeband, centerDate = null) {
        this.transitionToTimeband(timeband, centerDate);
    }

    scrollToAlignPosition(targetDate, alignPosition, instant = false) {
        if (!targetDate && targetDate !== 0) return;

        const index = this.#findIndexForDate(targetDate, this.currentTimeband);
        if (index < 0) return;

        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return;

        const itemWidth = (cfg.itemWidth || 0) + 1;
        const center = (index * itemWidth) + (itemWidth / 2);
        const scrollPos = center - alignPosition;

        this.#setScrollLeft(Math.max(0, scrollPos), instant);
    }

    transitionToTimebandForRangeCompletion(itemData) {
        const sm = this.selectionManager;
        const startBand = sm.selectionTimeband;
        if (!startBand) return;

        let targetBand = this.currentTimeband;
        let targetDate = itemData.date;

        if (startBand === TIMEBANDS.DAY) {
            // Need to zoom into day view from month/year to complete
            if (this.currentTimeband === TIMEBANDS.MONTH) {
                targetBand = TIMEBANDS.DAY;
                targetDate = new Date(targetDate.getFullYear(), targetDate.getMonth(), 1);
            } else if (this.currentTimeband === TIMEBANDS.YEAR) {
                targetBand = TIMEBANDS.DAY;
                targetDate = new Date(targetDate.getFullYear(), 0, 1);
            }
        } else if (startBand === TIMEBANDS.MONTH) {
            if (this.currentTimeband === TIMEBANDS.YEAR) {
                targetBand = TIMEBANDS.MONTH;
                targetDate = new Date(targetDate.getFullYear(), 0, 1);
            }
        }

        this.pendingRangeCompletion = { itemData, originalTimeband: this.currentTimeband };
        this.transitionToTimeband(targetBand, targetDate);
    }

    completeRangeSelectionAfterTransition() {
        const sm = this.selectionManager;
        const pending = this.pendingRangeCompletion;
        if (!pending || !sm.selectionTimeband) {
            this.pendingRangeCompletion = null;
            return;
        }

        const { itemData, originalTimeband } = pending;
        let endDate = null;

        if (sm.selectionTimeband === TIMEBANDS.DAY) {
            if (originalTimeband === TIMEBANDS.MONTH) {
                endDate = new Date(itemData.date.getFullYear(), itemData.date.getMonth(), 1);
            } else if (originalTimeband === TIMEBANDS.YEAR) {
                endDate = new Date(itemData.date.getFullYear(), 0, 1);
            }
        } else if (sm.selectionTimeband === TIMEBANDS.MONTH) {
            if (originalTimeband === TIMEBANDS.YEAR) {
                endDate = new Date(itemData.date.getFullYear(), 0, 1);
            }
        }

        if (endDate) {
            if (endDate < sm.selectedStartDate) {
                sm.selectedEndDate = sm.selectedStartDate;
                sm.selectedStartDate = endDate;
            } else {
                sm.selectedEndDate = endDate;
            }

            sm.isSelectingRange = false;
            sm.selectionTimeband = null;
            this.requestSelectionUpdate();
            this.emit('selectionChange', sm.getSelectedRange());
        }

        this.pendingRangeCompletion = null;
    }

    /** Indicators + hover **/

    #createRangeIndicators() {
        if (this.options.renderLeftIndicator) {
            this.leftIndicator = this.options.renderLeftIndicator(this);
        } else {
            this.leftIndicator = document.createElement('div');
            this.leftIndicator.className = 'date-picker-range-indicator left';
            this.leftIndicator.innerHTML = '◀';
        }
        this.container.appendChild(this.leftIndicator);

        if (this.options.renderRightIndicator) {
            this.rightIndicator = this.options.renderRightIndicator(this);
        } else {
            this.rightIndicator = document.createElement('div');
            this.rightIndicator.className = 'date-picker-range-indicator right';
            this.rightIndicator.innerHTML = '▶';
        }
        this.container.appendChild(this.rightIndicator);
    }

    #createHoverInfo() {
        this.hoverInfo = document.createElement('div');
        this.hoverInfo.className = 'date-picker-hover-info';
        document.body.appendChild(this.hoverInfo);
    }

    #createHoverOverlay() {
        if (this.options.renderHoverOverlay) {
            this.hoverOverlay = this.options.renderHoverOverlay(this);
        } else {
            this.hoverOverlay = document.createElement('div');
            this.hoverOverlay.className = 'date-picker-hover-overlay';
        }
        document.body.appendChild(this.hoverOverlay);
    }

    handleItemHover(itemData, event) {
        // Single-date mode overlay text (lock/unlock/create range)
        if (this.options.singleDateMode && this.selectionManager.selectedStartDate) {
            const overlayText = this.selectionManager.getHoverOverlayText(itemData);
            if (overlayText) {
                this.showHoverOverlay(overlayText, event);
            } else {
                this.hideHoverOverlay();
            }
        }

        // Range preview tooltip
        if (!this.selectionManager.isSelectingRange || !this.selectionManager.selectedStartDate) {
            this.hideHoverInfo();
            return;
        }

        const tooltip = this.selectionManager.getHoverTooltipText(itemData);
        if (tooltip) {
            this.showHoverInfo(tooltip);
        } else {
            this.hideHoverInfo();
        }
    }

    showHoverOverlay(text, event) {
        if (!this.hoverOverlay) return;

        this.hoverOverlay.textContent = text;
        this.hoverOverlay.style.display = 'block';
        this.hoverOverlay.style.opacity = '1';

        this.hoverOverlay.style.left = `${event.clientX + 10}px`;
        this.hoverOverlay.style.top = `${event.clientY - 30}px`;
    }

    hideHoverOverlay() {
        if (!this.hoverOverlay) return;
        this.hoverOverlay.style.opacity = '0';
        setTimeout(() => {
            if (this.hoverOverlay) this.hoverOverlay.style.display = 'none';
        }, 200);
    }

    showHoverInfo(text) {
        if (!this.hoverInfo) return;
        const rect = this.container.getBoundingClientRect();
        const above = this.options.hoverInfoPosition === 'above';

        this.hoverInfo.textContent = text;
        this.hoverInfo.style.left = `${rect.left}px`;
        this.hoverInfo.style.width = `${rect.width}px`;
        this.hoverInfo.style.top = above
            ? `${rect.top - 40}px`
            : `${rect.bottom + 8}px`;
        this.hoverInfo.style.opacity = '1';
    }

    hideHoverInfo() {
        if (this.hoverInfo) {
            this.hoverInfo.style.opacity = '0';
        }
    }

    updateRangeIndicators() {
        const sm = this.selectionManager;
        if (!sm.selectedStartDate ||
            !this.leftIndicator ||
            !this.rightIndicator) {
            if (this.leftIndicator) this.leftIndicator.style.opacity = '0';
            if (this.rightIndicator) this.rightIndicator.style.opacity = '0';
            return;
        }

        const range = this.getVisibleDateRange();
        if (!range) {
            this.leftIndicator.style.opacity = '0';
            this.rightIndicator.style.opacity = '0';
            return;
        }

        const selectionStart = sm.selectedStartDate;
        const selectionEnd = sm.selectedEndDate || sm.selectedStartDate;

        const startsBefore = selectionStart < range.start;
        const endsAfter = selectionEnd > range.end;

        this.leftIndicator.style.opacity = startsBefore ? '1' : '0';
        this.rightIndicator.style.opacity = endsAfter ? '1' : '0';
    }

    getVisibleDateRange() {
        if (!this.items.length) return null;

        const { scrollLeft, clientWidth } = this.scrollContainer;
        const cfg = this.timebandConfigs[this.currentTimeband];
        if (!cfg) return null;

        const width = (cfg.itemWidth || 0) + 1;
        const firstIndex = Math.floor(scrollLeft / width);
        const lastIndex = Math.min(
            this.items.length - 1,
            Math.ceil((scrollLeft + clientWidth) / width)
        );

        if (firstIndex >= this.items.length || lastIndex < 0) return null;

        const firstItem = this.items[Math.max(0, firstIndex)];
        const lastItem = this.items[Math.max(0, lastIndex)];

        return {
            start: TimebandUtils.getItemRangeStart(firstItem, this.currentTimeband),
            end: TimebandUtils.getItemRangeEnd(lastItem, this.currentTimeband)
        };
    }

    isDateVisible(date) {
        if (!date) return false;
        
        const range = this.getVisibleDateRange();
        if (!range) return false;
        
        const targetDate = clampToStartOfDay(new Date(date));
        
        // Check if the date falls within the visible range based on current timeband
        if (this.currentTimeband === TIMEBANDS.DAY) {
            return targetDate >= range.start && targetDate <= range.end;
        } else if (this.currentTimeband === TIMEBANDS.MONTH) {
            const targetMonth = new Date(targetDate.getFullYear(), targetDate.getMonth(), 1);
            return targetMonth >= range.start && targetMonth <= range.end;
        } else if (this.currentTimeband === TIMEBANDS.YEAR) {
            const targetYear = new Date(targetDate.getFullYear(), 0, 1);
            return targetYear >= range.start && targetYear <= range.end;
        }
        
        return false;
    }

}

/** UMD-style export **/

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { DatePicker, SelectionManager, TimebandUtils };
} else if (typeof window !== 'undefined') {
    window.DatePicker = DatePicker;
    window.SelectionManager = SelectionManager;
    window.TimebandUtils = TimebandUtils;
}
