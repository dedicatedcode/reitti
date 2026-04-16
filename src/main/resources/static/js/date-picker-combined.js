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

// Base numbers - will be multiplied by container width factor
const BASE_ITEMS_TO_ADD = {
    [TIMEBANDS.DAY]: 1.5,    // 1.5x container width worth of items
    [TIMEBANDS.MONTH]: 2.0,  // 2x container width
    [TIMEBANDS.YEAR]: 2.0    // 2x container width
};

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

const DRAG_CONFIG = {
    threshold: 5,
    momentumMultiplier: 0.3,
    friction: 0.95,
    minVelocity: 0.5
};

function normalizeDate(date) {
    if (!date) return null;
    const d = date instanceof Date ? new Date(date) : new Date(date);
    return isNaN(d.getTime()) ? null : d;
}

function clampToStartOfDay(date) {
    const d = normalizeDate(date);
    if (!d) return null;

    if (typeof date === 'string' && !date.includes('T')) {
        return new Date(date + 'T00:00:00');
    }

    d.setHours(0, 0, 0, 0);
    return d;
}

function isSameByGranularity(a, b, timeband) {
    if (!a || !b) return false;
    const da = normalizeDate(a);
    const db = normalizeDate(b);
    if (!da || !db) return false;

    switch (timeband) {
        case TIMEBANDS.YEAR:
            return da.getFullYear() === db.getFullYear();
        case TIMEBANDS.MONTH:
            return da.getFullYear() === db.getFullYear() &&
                da.getMonth() === db.getMonth();
        case TIMEBANDS.DAY:
        default:
            return da.getFullYear() === db.getFullYear() &&
                da.getMonth() === db.getMonth() &&
                da.getDate() === db.getDate();
    }
}

const areSameDay = (a, b) => isSameByGranularity(a, b, TIMEBANDS.DAY);
const areSameMonth = (a, b) => isSameByGranularity(a, b, TIMEBANDS.MONTH);
const areSameYear = (a, b) => isSameByGranularity(a, b, TIMEBANDS.YEAR);

function getTimebandStart(date, timeband) {
    const d = normalizeDate(date);
    if (!d) return null;

    switch (timeband) {
        case TIMEBANDS.MONTH:
            return new Date(d.getFullYear(), d.getMonth(), 1);
        case TIMEBANDS.YEAR:
            return new Date(d.getFullYear(), 0, 1);
        case TIMEBANDS.DAY:
        default:
            return clampToStartOfDay(d);
    }
}

function getTimebandEnd(date, timeband) {
    const d = normalizeDate(date);
    if (!d) return null;

    switch (timeband) {
        case TIMEBANDS.MONTH:
            return new Date(d.getFullYear(), d.getMonth() + 1, 0);
        case TIMEBANDS.YEAR:
            return new Date(d.getFullYear(), 11, 31);
        case TIMEBANDS.DAY:
        default:
            return clampToStartOfDay(d);
    }
}

function addToTimeband(date, timeband, offset) {
    const d = normalizeDate(date);
    if (!d) return null;

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

/** Timeband utilities **/

class TimebandUtils {
    /**
     * Calculate how many items to add based on container width
     * This ensures we always add enough items for at least 2 screen widths of scrolling
     */
    static getItemsToAddCount(timeband, containerWidth, itemWidth) {
        const multiplier = BASE_ITEMS_TO_ADD[timeband] || 1.5;
        const effectiveWidth = containerWidth || 1000;
        const effectiveItemWidth = itemWidth || 100;

        // Calculate items needed to fill multiplier * container width
        const itemsNeeded = Math.ceil((effectiveWidth * multiplier) / effectiveItemWidth);

        // Ensure minimum values for safety
        const minimums = {
            [TIMEBANDS.DAY]: 25,
            [TIMEBANDS.MONTH]: 20,
            [TIMEBANDS.YEAR]: 15
        };

        return Math.max(itemsNeeded, minimums[timeband] || 15);
    }

    static addItemsToDirection(datePicker, direction, timeband) {
        const isLeft = direction === 'left';
        const { items, scrollContainer, timebandConfigs } = datePicker;

        if (!items?.length || !scrollContainer) return;

        const config = timebandConfigs[timeband];
        if (!config) return;

        const referenceDate = isLeft ? items[0].date : items[items.length - 1].date;
        const previousScrollLeft = scrollContainer.scrollLeft;
        const previousBehavior = scrollContainer.style.scrollBehavior;

        scrollContainer.style.scrollBehavior = 'auto';

        const itemWidth = (config.itemWidth || 0) + 1;
        const containerWidth = scrollContainer.clientWidth;
        const count = this.getItemsToAddCount(timeband, containerWidth, itemWidth);
        const estimatedAddedWidth = itemWidth * count;

        const newItems = this.generateNewItems(
            datePicker,
            referenceDate,
            timeband,
            count,
            isLeft
        );

        if (newItems.length === 0) return;

        datePicker.items = isLeft
            ? [...newItems, ...items]
            : [...items, ...newItems];

        const fragment = document.createDocumentFragment();
        newItems.forEach((itemData) => {
            const el = config.createItemElement(itemData, 0);
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

            setTimeout(() => {
                scrollContainer.style.scrollBehavior = previousBehavior || '';
            }, 50);

            this.updateElementIndices(datePicker);
        });
    }

    static updateElementIndices(datePicker) {
        const { scrollContainer } = datePicker;
        if (!scrollContainer) return;

        scrollContainer.querySelectorAll('.timeband-item').forEach((el, index) => {
            el.dataset.idx = String(index);
        });
    }

    static generateNewItems(datePicker, referenceDate, timeband, count, isLeft) {
        const { timebandConfigs } = datePicker;
        const cfg = timebandConfigs[timeband];
        if (!cfg?.getItemData) return [];

        const items = [];
        const startOffset = isLeft ? -count : 1;
        const endOffset = isLeft ? 0 : count + 1;

        for (let i = startOffset; i < endOffset; i++) {
            if (i === 0) continue;
            const date = addToTimeband(referenceDate, timeband, i);
            if (date) items.push(cfg.getItemData(date));
        }

        return items;
    }

    static getItemRangeStart(itemData, timeband) {
        return getTimebandStart(itemData?.date, timeband);
    }

    static getItemRangeEnd(itemData, timeband) {
        return getTimebandEnd(itemData?.date, timeband);
    }
}
const SELECTION_MODES = {
    SINGLE: 'single',    // Initial state, single date selected
    LOCKED: 'locked',    // Date is locked, waiting for second click to create range
    RANGE: 'range'       // Range is being selected or already selected
};

class SelectionManager {
    #datePicker = null;
    #selectedStartDate = null;
    #selectedEndDate = null;
    #selectionMode = SELECTION_MODES.SINGLE;
    #selectionTimeband = null;
    #hoveredDate = null;

    constructor(datePicker) {
        this.#datePicker = datePicker;
    }

    get selectedStartDate() { return this.#selectedStartDate; }
    get selectedEndDate() { return this.#selectedEndDate; }
    get selectionMode() { return this.#selectionMode; }
    get selectionTimeband() { return this.#selectionTimeband; }
    get isLocked() { return this.#selectionMode === SELECTION_MODES.LOCKED; }
    get isRangeMode() { return this.#selectionMode === SELECTION_MODES.RANGE; }
    get hoveredDate() { return this.#hoveredDate; }

    clearSelection(preserveLast = true) {
        if (preserveLast && this.#selectedStartDate) {
            this.#selectedStartDate = null;
            this.#selectedEndDate = null;
            this.#selectionMode = SELECTION_MODES.SINGLE;
            this.#selectionTimeband = this.#datePicker.currentTimeband;
        } else {
            this.#selectedStartDate = null;
            this.#selectedEndDate = null;
            this.#selectionMode = SELECTION_MODES.SINGLE;
            this.#selectionTimeband = null;
        }
        this.#hoveredDate = null;
    }

    ensureSelection(defaultDate = null) {
        if (!this.#selectedStartDate) {
            const date = defaultDate || this.#datePicker.options.startDate || new Date();
            this.#selectedStartDate = clampToStartOfDay(date);
            this.#selectedEndDate = null;
            this.#selectionMode = SELECTION_MODES.SINGLE;
            this.#selectionTimeband = this.#datePicker.currentTimeband;
        }
        return this.#selectedStartDate;
    }

    setSelectedRange(startDate, endDate = null) {
        this.#selectedStartDate = startDate ? clampToStartOfDay(startDate) : null;
        this.#selectedEndDate = endDate ? clampToStartOfDay(endDate) : null;

        if (startDate && endDate) {
            this.#selectionMode = SELECTION_MODES.RANGE;
        } else if (startDate) {
            this.#selectionMode = SELECTION_MODES.SINGLE;
        } else {
            this.#selectionMode = SELECTION_MODES.SINGLE;
        }
        this.#selectionTimeband = null;
    }

    getSelectedRange() {
        const fmt = (d) => d ? this.#datePicker.formatDate(d) : null;
        return {
            startDate: fmt(this.#selectedStartDate),
            endDate: fmt(this.#selectedEndDate),
            timeband: this.#datePicker.currentTimeband,
            mode: this.#selectionMode
        };
    }

    setHoveredDate(itemData) {
        if (!itemData?.date) {
            this.#hoveredDate = null;
            return;
        }
        this.#hoveredDate = normalizeDate(itemData.date);
    }

    isSoleSelectedDate(date, timeband) {
        if (!this.#selectedStartDate) return false;

        if (this.#selectedEndDate) {
            const start = getTimebandStart(this.#selectedStartDate, timeband);
            const end = getTimebandEnd(this.#selectedStartDate, timeband);

            const isFullStart = this.#isSameTimeband(this.#selectedStartDate, start, timeband);
            const isFullEnd = this.#isSameTimeband(this.#selectedEndDate, end, timeband);

            if (!isFullStart || !isFullEnd) return false;
        }

        // Check if clicked element is within the selected range
        const clickedStart = getTimebandStart(date, timeband);
        const clickedEnd = getTimebandEnd(date, timeband);
        const selStart = getTimebandStart(this.#selectedStartDate, timeband);
        const selEnd = getTimebandEnd(this.#selectedEndDate || this.#selectedStartDate, timeband);

        return clickedStart >= selStart && clickedEnd <= selEnd;
    }

    handleClick(itemData, timeband) {
        const { options } = this.#datePicker;
        const clicked = getTimebandStart(itemData.date, timeband);
        if (!clicked) return;

        if (!options.singleDateMode) {
            this.#handleMultiDateModeClick(clicked, timeband);
            return;
        }

        // Single date mode
        this.#handleSingleDateModeClick(clicked, timeband);
    }

    #isFullTimebandSelected(clicked, timeband) {
        if (!this.#selectedStartDate || !this.#selectedEndDate) {
            return false;
        }

        const clickedStart = getTimebandStart(clicked, timeband);
        const clickedEnd = getTimebandEnd(clicked, timeband);

        const selectedStart = getTimebandStart(this.#selectedStartDate, timeband);
        const selectedEnd = getTimebandEnd(this.#selectedEndDate, timeband);

        return this.#isSameTimeband(clickedStart, selectedStart, timeband) &&
            this.#isSameTimeband(clickedEnd, selectedEnd, timeband);
    }

    #handleSingleDateModeClick(clicked, timeband) {
        const isSameElement = this.isSoleSelectedDate(clicked, timeband);

        if (isSameElement) {
            const isFullTimeband = this.#isFullTimebandSelected(clicked, timeband);

            if (isFullTimeband) {
                // Full timeband is selected - toggle lock
                if (this.#selectionMode === SELECTION_MODES.LOCKED) {
                    this.#selectionMode = SELECTION_MODES.SINGLE;
                } else {
                    this.#selectionMode = SELECTION_MODES.LOCKED;
                    this.#selectionTimeband = timeband;
                }
            } else {
                // Not full timeband - expand to full timeband
                this.#selectFullTimeband(clicked, timeband);
            }
            return;
        }

        // Clicked on different element
        if (this.#selectionMode === SELECTION_MODES.LOCKED) {
            // LOCKED mode: create range
            this.#createRangeFromLocked(clicked, timeband);
        } else if (this.#selectionMode === SELECTION_MODES.RANGE) {
            // RANGE mode: adjust range
            this.#handleRangeAdjustment(clicked, timeband);
        } else {
            // SINGLE mode: expand to full timeband
            this.#selectFullTimeband(clicked, timeband);
        }
    }

    #handleMultiDateModeClick(clicked, timeband) {
        if (!this.#selectedStartDate) {
            this.#selectSingle(clicked, timeband);
            return;
        }

        if (this.#selectedStartDate && this.#selectedEndDate) {
            this.#selectSingle(clicked, timeband);
            return;
        }

        // Complete the range
        this.#completeRange(clicked, timeband);
    }

    #selectSingle(date, timeband) {
        this.#selectedStartDate = normalizeDate(date);
        this.#selectedEndDate = null;
        this.#selectionMode = SELECTION_MODES.SINGLE;
        this.#selectionTimeband = timeband;
    }

    #selectFullTimeband(date, timeband) {
        this.#selectedStartDate = getTimebandStart(date, timeband);
        this.#selectedEndDate = getTimebandEnd(date, timeband);
        this.#selectionMode = SELECTION_MODES.SINGLE;
        this.#selectionTimeband = null;
    }

    #createRangeFromLocked(clicked, timeband) {
        const clickedEnd = getTimebandEnd(clicked, timeband);

        if (clicked < this.#selectedStartDate) {
            this.#selectedEndDate = getTimebandEnd(this.#selectedStartDate, timeband);
            this.#selectedStartDate = clicked;
        } else {
            this.#selectedEndDate = clickedEnd;
        }

        this.#selectionMode = SELECTION_MODES.RANGE;
        this.#selectionTimeband = null;
    }

    #handleRangeAdjustment(clicked, timeband) {
        const start = getTimebandStart(this.#selectedStartDate, timeband);
        const end = getTimebandStart(this.#selectedEndDate, timeband);

        if (this.#isSameTimeband(clicked, start, timeband)) {
            this.#selectedEndDate = null;
            this.#selectionMode = SELECTION_MODES.SINGLE;
            this.#selectionTimeband = null;
            return;
        }

        if (this.#isSameTimeband(clicked, end, timeband)) {
            this.#selectedStartDate = normalizeDate(clicked);
            this.#selectedEndDate = null;
            this.#selectionMode = SELECTION_MODES.SINGLE;
            this.#selectionTimeband = timeband;
            return;
        }

        if (clicked < start) {
            this.#selectedStartDate = clicked;
        }
        else if (clicked > end) {
            this.#selectedEndDate = getTimebandEnd(clicked, timeband);
        }
        else {
            this.#selectedStartDate = normalizeDate(clicked);
        }
    }

    #completeRange(clicked, timeband) {
        const normalizedStart = getTimebandStart(this.#selectedStartDate, timeband);

        if (clicked < normalizedStart) {
            this.#selectedEndDate = getTimebandEnd(normalizedStart, timeband);
            this.#selectedStartDate = clicked;
        } else {
            this.#selectedEndDate = getTimebandEnd(clicked, timeband);
        }

        this.#selectionMode = SELECTION_MODES.RANGE;
        this.#selectionTimeband = null;
    }

    #isSameTimeband(a, b, timeband) {
        return isSameByGranularity(a, b, timeband);
    }

    canCompleteRangeAtCurrentTimeband() {
        if (this.#selectionMode !== SELECTION_MODES.RANGE || !this.#selectedStartDate || !this.#selectionTimeband) {
            return false;
        }
        const current = this.#datePicker.currentTimeband;
        return TIMEBAND_ORDER >= TIMEBAND_ORDER;
    }

    completeRangeAtTimeband(itemData) {
        const tb = this.#selectionTimeband;
        if (!tb) return false;

        const end = this.#getEndDateForTimeband(itemData, tb, this.#datePicker.currentTimeband);
        if (!end) return false;

        if (end < this.#selectedStartDate) {
            this.#selectedEndDate = this.#selectedStartDate;
            this.#selectedStartDate = end;
        } else {
            this.#selectedEndDate = end;
        }

        this.#selectionMode = SELECTION_MODES.RANGE;
        this.#selectionTimeband = null;
        return true;
    }

    #getEndDateForTimeband(itemData, selectionBand, currentBand) {
        const d = itemData?.date;
        if (!d) return null;

        if (selectionBand === TIMEBANDS.YEAR) {
            if (currentBand === TIMEBANDS.YEAR) return new Date(d.getFullYear(), 11, 31);
            if (currentBand === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
            if (currentBand === TIMEBANDS.DAY) return clampToStartOfDay(d);
        }
        if (selectionBand === TIMEBANDS.MONTH) {
            if (currentBand === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
            if (currentBand === TIMEBANDS.DAY) return clampToStartOfDay(d);
        }
        if (selectionBand === TIMEBANDS.DAY) {
            if (currentBand === TIMEBANDS.DAY) return clampToStartOfDay(d);
            if (currentBand === TIMEBANDS.MONTH) return new Date(d.getFullYear(), d.getMonth() + 1, 0);
            if (currentBand === TIMEBANDS.YEAR) return new Date(d.getFullYear(), 11, 31);
        }
        return null;
    }

    calculatePotentialRange(itemData) {
        if (!this.#selectedStartDate) {
            return {
                startDate: null,
                endDate: null,
                timeband: this.#datePicker.currentTimeband,
                mode: this.#selectionMode
            };
        }

        const hovered = normalizeDate(itemData?.date);
        const timeband = this.#datePicker.currentTimeband;
        let start = this.#selectedStartDate;
        let end = null;

        if ((this.#selectionMode === SELECTION_MODES.RANGE || this.selectionMode === SELECTION_MODES.LOCKED) && this.#selectedEndDate) {
            // Calculate what the range would be based on where user hovers
            const rangeStart = getTimebandStart(this.#selectedStartDate, timeband);
            const rangeEnd = getTimebandStart(this.#selectedEndDate, timeband);

            if (hovered < rangeStart) {
                // Hovering before the range - extend start
                start = getTimebandStart(hovered, timeband);
                end = normalizeDate(this.#selectedEndDate);
            } else if (hovered > rangeEnd) {
                // Hovering after the range - extend end
                end = getTimebandEnd(hovered, timeband);
            } else {
                // Hovering inside the range - adjust start
                start = getTimebandStart(hovered, timeband);
                end = normalizeDate(this.#selectedEndDate);
            }
        } else if (this.#selectionMode === SELECTION_MODES.SINGLE) {
            start = getTimebandStart(hovered, timeband);
            end = getTimebandEnd(hovered, timeband);
        }

        if (end && end < start) [end, start] = [ start, end ];

        return {
            startDate: start,
            endDate: end,
            timeband: timeband,
            mode: this.#selectionMode
        };
    }

    #getTooltipEndDate(itemData, timeband) {
        const d = itemData?.date;
        if (!d) return null;

        if (timeband === TIMEBANDS.DAY) {
            return clampToStartOfDay(d);
        }
        if (timeband === TIMEBANDS.MONTH) {
            return this.#datePicker.currentTimeband === TIMEBANDS.DAY
                ? new Date(d.getFullYear(), d.getMonth(), 1)
                : new Date(d.getFullYear(), d.getMonth() + 1, 0);
        }
        return (timeband === TIMEBANDS.YEAR &&
            this.#datePicker.currentTimeband === TIMEBANDS.YEAR)
            ? new Date(d.getFullYear(), 11, 31)
            : new Date(d.getFullYear(), 0, 1);
    }
}

/** DatePicker core **/

class DatePicker {
    #container = null;
    #scrollContainer = null;
    #options = {};
    #items = [];
    #selectionManager = null;
    #eventListeners = {};
    #currentTimeband = TIMEBANDS.DAY;
    #isTransitioning = false;
    #itemByTime = new Map();
    #timebandConfigs = null;

    #leftIndicator = null;
    #rightIndicator = null;

    #boundHandlers = {};

    #scrollTimeout = null;
    #horizontalTimeout = null;
    #pendingSelRaf = null;

    #dragState = {
        isActive: false,
        startX: 0,
        startY: 0,
        scrollLeft: 0,
        scrollTop: 0,
        hasMoved: false,
        lastX: 0,
        lastTime: 0,
        velocity: 0
    };

    #momentumRaf = null;
    #isMomentumActive = false;

    #wheelChain = { dir: 0, count: 0, mouseDate: null, mousePos: null };
    #wheelGesture = { dir: 0, count: 0, start: 0, delta: 0, windowUntil: 0 };
    #touchState = { startX: 0, startY: 0, lock: null };
    #formatters = null;
    #pendingSelectionEmit = false;
    #pendingRangeCompletion = null;
    #pendingAlignPosition = null;
    #horizontalScrollActive = false;
    #dragClickPrevented = false;
    #isAddingItems = false;
    #lastHoveredItem = null;

    constructor(containerId, options = {}) {
        this.#container = document.getElementById(containerId);
        if (!this.#container) {
            throw new Error(`DatePicker: container "${containerId}" not found.`);
        }

        this.#initOptions(options);
        this.#selectionManager = new SelectionManager(this);
        this.#timebandConfigs = this.#createTimebandConfigs();

        this.#init();
    }

    #initOptions(options) {
        const normalizedStart = clampToStartOfDay(options.startDate || new Date());

        const defaultStrings = {
            clickToUnlockDate: 'Click to unlock date',
            clickToLockDate: 'Click to lock date',
            clickToClearSelection: 'Click to collapse range',
            clickToCollapseRange: 'Click to collapse range',
            clickToCreateRange: 'Click to create range',
            clickToExpandRangeBackward: 'Click to expand range backward',
            clickToExpandRangeForward: 'Click to expand range forward',
            clickToAdjustRangeStart: 'Click to adjust range start',
            select: 'Select',
            to: 'to'
        };

        this.#options = {
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
            locale: undefined,
            enableDrag: true,
            dragCursor: 'grab',
            draggingCursor: 'grabbing',
            momentumScrolling: true,
            strings: { ...defaultStrings, ...options.strings },
            onHoverRange: null,
            ...options
        };

        this.#options.startDate = normalizedStart;
    }

    get container() { return this.#container; }
    get scrollContainer() { return this.#scrollContainer; }
    get items() { return this.#items; }
    set items(val) { this.#items = val; }
    get timebandConfigs() { return this.#timebandConfigs; }
    get currentTimeband() { return this.#currentTimeband; }
    get options() { return this.#options; }
    get itemByTime() { return this.#itemByTime; }
    get selectionManager() { return this.#selectionManager; }
    get leftIndicator() { return this.#leftIndicator; }
    get rightIndicator() { return this.#rightIndicator; }

    #createTimebandConfigs() {
        const createConfig = (timeband, itemWidth, generateItems, createItemElement, getItemData) => ({
            itemWidth,
            generateItems,
            createItemElement,
            getItemData,
            addToLeft: () => TimebandUtils.addItemsToDirection(this, 'left', timeband),
            addToRight: () => TimebandUtils.addItemsToDirection(this, 'right', timeband)
        });

        return {
            [TIMEBANDS.DAY]: createConfig(
                TIMEBANDS.DAY, 80,
                () => this.#generateDays(),
                (data, index) => this.#createDayElement(data, index),
                (date) => this.#createDayData(date)
            ),
            [TIMEBANDS.MONTH]: createConfig(
                TIMEBANDS.MONTH, 100,
                () => this.#generateMonths(),
                (data, index) => this.#createMonthElement(data, index),
                (date) => this.#createMonthData(date)
            ),
            [TIMEBANDS.YEAR]: createConfig(
                TIMEBANDS.YEAR, 120,
                () => this.#generateYears(),
                (data, index) => this.#createYearElement(data, index),
                (date) => this.#createYearData(date)
            )
        };
    }

    #init() {
        this.#createContainer();
        this.#generateInitialItems();
        this.render();
        this.#setupEventListeners();
        this.#selectionManager.ensureSelection();
        this.#requestSelectionUpdate();
    }

    #createContainer() {
        this.#container.innerHTML = '';
        this.#container.className = 'date-picker';

        this.#scrollContainer = document.createElement('div');
        this.#scrollContainer.className = `date-picker-container timeband-${this.#currentTimeband}`;

        if (this.#options.enableDrag) {
            this.#scrollContainer.style.cursor = this.#options.dragCursor;
        }

        this.#container.appendChild(this.#scrollContainer);

        this.#createRangeIndicators();
    }

    #generateInitialItems() {
        const config = this.#timebandConfigs[this.#currentTimeband];
        config?.generateItems?.();
    }

    /**
     * Calculate initial item count based on container width
     * Ensures we have enough items for 3x the container width
     */
    #calculateInitialItemCount(timeband) {
        const containerWidth = this.#scrollContainer?.clientWidth ||
            this.#container?.clientWidth || 1000;

        const cfg = this.#timebandConfigs[timeband];
        const itemWidth = (cfg?.itemWidth || 100) + 1;

        // Generate 3x container width worth of items initially
        return Math.ceil((containerWidth * 3) / itemWidth);
    }

    #generateDays() {
        const center = this.#options.startDate || new Date();
        const total = this.#calculateInitialItemCount(TIMEBANDS.DAY);
        const start = new Date(center);
        start.setDate(center.getDate() - Math.floor(total / 2));

        this.#items = Array.from({ length: total }, (_, i) => {
            const d = new Date(start);
            d.setDate(start.getDate() + i);
            return this.#createDayData(d);
        });
    }

    #generateMonths() {
        const center = this.#options.startDate || new Date();
        const total = this.#calculateInitialItemCount(TIMEBANDS.MONTH);
        const start = new Date(center.getFullYear(), center.getMonth() - Math.floor(total / 2), 1);

        this.#items = Array.from({ length: total }, (_, i) => {
            const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
            return this.#createMonthData(d);
        });
    }

    #generateYears() {
        const center = this.#options.startDate || new Date();
        const total = this.#calculateInitialItemCount(TIMEBANDS.YEAR);
        const startYear = center.getFullYear() - Math.floor(total / 2);

        this.#items = Array.from({ length: total }, (_, i) => {
            const d = new Date(startYear + i, 0, 1);
            return this.#createYearData(d);
        });
    }

    #createDayData(date) {
        return {
            date: clampToStartOfDay(date),
            element: null,
            isToday: areSameDay(date, new Date()),
            type: TIMEBANDS.DAY
        };
    }

    #createMonthData(date) {
        return {
            date: new Date(date.getFullYear(), date.getMonth(), 1),
            element: null,
            isToday: areSameMonth(date, new Date()),
            type: TIMEBANDS.MONTH
        };
    }

    #createYearData(date) {
        return {
            date: new Date(date.getFullYear(), 0, 1),
            element: null,
            isToday: areSameYear(date, new Date()),
            type: TIMEBANDS.YEAR
        };
    }

    render() {
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return;

        this.#scrollContainer.innerHTML = '';
        this.#scrollContainer.className = `date-picker-container timeband-${this.#currentTimeband}`;

        if (this.#options.enableDrag) {
            this.#scrollContainer.style.cursor = this.#options.dragCursor;
        }

        this.#itemByTime.clear();

        const fragment = document.createDocumentFragment();
        this.#items.forEach((itemData, index) => {
            const el = cfg.createItemElement(itemData, index);
            this.#prepareItemElement(el, itemData, index);
            itemData.element = el;
            fragment.appendChild(el);
        });

        this.#scrollContainer.appendChild(fragment);
        this.scrollToCenter(true);
        this.#requestSelectionUpdate();
    }

    #createDayElement(dayData, index) {
        const custom = this.#tryCustomRenderer('renderDayItem', dayData, index);
        if (custom) return custom;

        return this.#buildItemElement({
            baseClass: 'date-day timeband-item',
            today: dayData.isToday,
            children: [
                { className: 'day-name secondary-text', text: this.getDayName(dayData.date) },
                { className: 'day-number primary-text', text: dayData.date.getDate() },
                { className: 'month-year tertiary-text', text: this.getMonthYear(dayData.date) }
            ]
        }, dayData, index);
    }

    #createMonthElement(monthData, index) {
        const custom = this.#tryCustomRenderer('renderMonthItem', monthData, index);
        if (custom) return custom;

        return this.#buildItemElement({
            today: monthData.isToday,
            children: [
                { className: 'secondary-text', text: monthData.date.getFullYear() },
                { className: 'primary-text', text: this.getMonthName(monthData.date) }
            ]
        }, monthData, index);
    }

    #createYearElement(yearData, index) {
        const custom = this.#tryCustomRenderer('renderYearItem', yearData, index);
        if (custom) return custom;

        return this.#buildItemElement({
            today: yearData.isToday,
            children: [
                { className: 'primary-text', text: yearData.date.getFullYear() }
            ]
        }, yearData, index);
    }

    #tryCustomRenderer(optionKey, data, index) {
        const renderer = this.#options[optionKey];
        if (!renderer) return null;

        const el = renderer(data, index, this);
        if (el) this.#prepareItemElement(el, data, index);
        return el;
    }

    #buildItemElement({ baseClass = 'timeband-item', today = false, children = [] }, itemData, index) {
        const el = document.createElement('div');
        el.className = baseClass;
        if (today) el.classList.add('today');

        children.forEach(({ className, text }) => {
            const child = document.createElement('div');
            if (className) child.className = className;
            if (text != null) child.textContent = text;
            el.appendChild(child);
        });

        this.#prepareItemElement(el, itemData, index);
        return el;
    }

    #prepareItemElement(el, itemData, index) {
        if (!el || !itemData?.date) return;

        el.classList.add('timeband-item');
        el.dataset.time = String(itemData.date.getTime());
        if (index != null) el.dataset.idx = String(index);
        this.#itemByTime.set(itemData.date.getTime(), itemData);
    }

    #setupEventListeners() {
        this.#boundHandlers = {
            onClick: (e) => this.#handleDelegatedClick(e),
            onMouseOver: (e) => this.#handleDelegatedHover(e),
            onMouseOut: (e) => this.#handleMouseOut(e),
            onScroll: () => this.#handleScrollEvent(),
            onWheel: (e) => this.#handleWheel(e),
            onMouseDown: (e) => this.#handleMouseDown(e),
            onMouseMove: (e) => this.#handleMouseMove(e),
            onMouseUp: (e) => this.#handleMouseUp(e),
            onTouchStart: (e) => this.#handleTouchStart(e),
            onTouchMove: (e) => this.#handleTouchMove(e),
            onTouchEnd: (e) => this.#handleTouchEnd(e)
        };

        const sc = this.#scrollContainer;
        sc.addEventListener('click', this.#boundHandlers.onClick, { passive: true });
        sc.addEventListener('mouseover', this.#boundHandlers.onMouseOver, { passive: true });
        sc.addEventListener('mouseout', this.#boundHandlers.onMouseOut, { passive: true });
        sc.addEventListener('scroll', this.#boundHandlers.onScroll, { passive: true });
        sc.addEventListener('wheel', this.#boundHandlers.onWheel, { passive: false, capture: true });

        if (this.#options.enableDrag) {
            sc.addEventListener('mousedown', this.#boundHandlers.onMouseDown, { passive: true });
        }

        sc.addEventListener('touchstart', this.#boundHandlers.onTouchStart, { passive: true });
        sc.addEventListener('touchmove', this.#boundHandlers.onTouchMove, { passive: false, capture: true });
        sc.addEventListener('touchend', this.#boundHandlers.onTouchEnd, { passive: true });

        if (this.#options.enableDrag) {
            window.addEventListener('mousemove', this.#boundHandlers.onMouseMove, { passive: true });
            window.addEventListener('mouseup', this.#boundHandlers.onMouseUp, { passive: true });
        }
    }

    /* Mouse drag implementation */

    #handleMouseDown(e) {
        if (e.button !== 0) return;
        if (e.target.closest('button, a, input, select, textarea')) return;

        this.#stopMomentum();

        const sc = this.#scrollContainer;

        this.#dragState = {
            isActive: true,
            startX: e.clientX,
            startY: e.clientY,
            scrollLeft: sc.scrollLeft,
            scrollTop: sc.scrollTop,
            hasMoved: false,
            lastX: e.clientX,
            lastTime: performance.now(),
            velocity: 0
        };

        sc.style.cursor = this.#options.draggingCursor;
        sc.style.userSelect = 'none';

        e.preventDefault();
    }

    #handleMouseMove(e) {
        if (!this.#dragState.isActive) return;

        const { startX, scrollLeft } = this.#dragState;
        const dx = e.clientX - startX;
        const distance = Math.abs(dx);

        if (!this.#dragState.hasMoved && distance > DRAG_CONFIG.threshold) {
            this.#dragState.hasMoved = true;
            this.#stopMomentum();
        }

        if (this.#dragState.hasMoved) {
            const sc = this.#scrollContainer;

            const now = performance.now();
            const dt = now - this.#dragState.lastTime;
            if (dt > 0) {
                const dxSinceLast = e.clientX - this.#dragState.lastX;
                this.#dragState.velocity = dxSinceLast / dt;
            }
            this.#dragState.lastX = e.clientX;
            this.#dragState.lastTime = now;

            sc.scrollLeft = scrollLeft - dx;

            e.preventDefault();
        }
    }

    #handleMouseUp(e) {
        if (!this.#dragState.isActive) return;

        const hadMoved = this.#dragState.hasMoved;
        const sc = this.#scrollContainer;

        sc.style.cursor = this.#options.dragCursor;
        sc.style.userSelect = '';

        this.#dragState.isActive = false;

        if (this.#options.momentumScrolling && hadMoved) {
            this.#startMomentum();
        } else {
            this.#scheduleReplenishCheck();
        }

        if (hadMoved) {
            this.#dragClickPrevented = true;
            requestAnimationFrame(() => {
                this.#dragClickPrevented = false;
            });
        }
    }

    /* Momentum scrolling */

    #startMomentum() {
        this.#stopMomentum();

        let velocity = this.#dragState.velocity * DRAG_CONFIG.momentumMultiplier * 16;

        if (Math.abs(velocity) < DRAG_CONFIG.minVelocity) {
            this.#scheduleReplenishCheck();
            return;
        }

        const sc = this.#scrollContainer;

        this.#isMomentumActive = true;

        let lastScrollLeft = sc.scrollLeft;

        const animate = () => {
            const newScrollLeft = sc.scrollLeft - velocity;
            sc.scrollLeft = newScrollLeft;

            if (sc.scrollLeft === lastScrollLeft) {
                this.#momentumRaf = null;
                this.#isMomentumActive = false;
                this.#scheduleReplenishCheck();
                return;
            }

            lastScrollLeft = sc.scrollLeft;
            velocity *= DRAG_CONFIG.friction;

            if (Math.abs(velocity) >= DRAG_CONFIG.minVelocity) {
                this.#momentumRaf = requestAnimationFrame(animate);
            } else {
                this.#momentumRaf = null;
                this.#isMomentumActive = false;
                this.#scheduleReplenishCheck();
            }
        };

        this.#momentumRaf = requestAnimationFrame(animate);
    }

    #stopMomentum() {
        if (this.#momentumRaf) {
            cancelAnimationFrame(this.#momentumRaf);
            this.#momentumRaf = null;
        }
        this.#isMomentumActive = false;
    }

    #scheduleReplenishCheck() {
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                this.#checkAndReplenishItems();
            });
        });
    }

    /**
     * Check if we need more items and add them
     * Uses dynamic threshold based on container width
     */
    #checkAndReplenishItems() {
        if (this.#isAddingItems) return;

        const sc = this.#scrollContainer;
        if (!sc) return;

        const { scrollLeft, scrollWidth, clientWidth } = sc;
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return;

        const itemWidth = (cfg.itemWidth || 100) + 1;

        // Dynamic threshold: 50% of container width
        // This ensures we add items well before user runs out
        const threshold = Math.max(clientWidth * 0.5, itemWidth * 5);

        const needsLeft = scrollLeft < threshold;
        const needsRight = scrollLeft + clientWidth > scrollWidth - threshold;

        if (needsLeft || needsRight) {
            this.#isAddingItems = true;

            if (needsLeft) {
                cfg.addToLeft?.();
            }
            if (needsRight) {
                cfg.addToRight?.();
            }

            // Wait longer for DOM to settle before checking again
            setTimeout(() => {
                this.#isAddingItems = false;
                this.#scheduleReplenishCheck();
            }, 150);
        }
    }

    #getItemDataFromEvent(e) {
        const el = e.target?.closest?.('.timeband-item');
        if (!el || !this.#scrollContainer.contains(el)) return null;

        const idx = el.dataset.idx ? Number(el.dataset.idx) : NaN;
        if (!isNaN(idx) && idx >= 0 && idx < this.#items.length) {
            return this.#items[idx];
        }

        const time = el.dataset.time ? Number(el.dataset.time) : NaN;
        if (!isNaN(time)) {
            return this.#itemByTime.get(time) || null;
        }

        return null;
    }

    #handleDelegatedClick(e) {
        if (this.#dragClickPrevented || this.#dragState.hasMoved) {
            e.stopPropagation();
            e.preventDefault();
            this.#dragState.hasMoved = false;
            return;
        }

        const data = this.#getItemDataFromEvent(e);
        if (data) this.handleItemClick(data);
    }

    #handleDelegatedHover(e) {
        const data = this.#getItemDataFromEvent(e);
        if (data) {
            this.handleItemHover(data, e);
        }
    }

    #handleMouseOut(e) {
        const toEl = e.relatedTarget?.closest?.('.timeband-item');
        const fromEl = e.target?.closest?.('.timeband-item');

        if (fromEl && (!toEl || !this.#scrollContainer.contains(toEl))) {
            this.#selectionManager.setHoveredDate(null);
            this.#lastHoveredItem = null;
            if (this.#options.onHoverRange) {
                this.#options.onHoverRange(null, this, null);
            }
        }
    }

    handleItemClick(itemData) {
        const sm = this.#selectionManager;

        if (sm.selectionMode === SELECTION_MODES.RANGE && sm.selectedStartDate) {
            if (sm.canCompleteRangeAtCurrentTimeband()) {
                return this.#completeRangeSelection(itemData);
            }
            if (sm.selectionTimeband && sm.selectionTimeband !== this.#currentTimeband) {
                return this.#transitionForRangeCompletion(itemData);
            }
        }

        sm.handleClick(itemData, this.#currentTimeband);

        if (sm.selectionMode === SELECTION_MODES.LOCKED && itemData) {
            this.handleItemHover(itemData, {
                clientX: this.#scrollContainer.getBoundingClientRect().left + this.#scrollContainer.scrollLeft + (itemData.element?.offsetLeft || 0),
                clientY: this.#scrollContainer.getBoundingClientRect().top
            });
        }

        this.#emitSelectionChange();
    }

    handleItemHover(itemData, event) {
        // Track the hovered date for tooltip updates
        this.#selectionManager.setHoveredDate(itemData);

        const sm = this.#selectionManager;

        // Only notify app when the hovered item actually changes
        if (this.#options.onHoverRange && this.#lastHoveredItem !== itemData) {
            this.#lastHoveredItem = itemData;
            const potentialRange = sm.calculatePotentialRange(itemData);
            this.#options.onHoverRange(potentialRange, this, itemData);
        }
    }

    #completeRangeSelection(itemData) {
        if (this.#selectionManager.completeRangeAtTimeband(itemData)) {
            this.#requestSelectionUpdate();
            this.#emitSelectionChange();
        }
    }

    #emitSelectionChange() {
        this.#requestSelectionUpdate();
        this.emit('selectionChange', this.#selectionManager.getSelectedRange());
    }

    #handleScrollEvent() {
        this.#horizontalScrollActive = true;
        clearTimeout(this.#horizontalTimeout);
        this.#horizontalTimeout = setTimeout(() => {
            this.#horizontalScrollActive = false;
        }, 200);

        clearTimeout(this.#scrollTimeout);
        this.#scrollTimeout = setTimeout(() => {
            if (!this.#dragState.isActive && !this.#isMomentumActive) {
                this.#checkAndReplenishItems();
            }
            this.updateRangeIndicators();
            this.emit('viewChange');
        }, 50);
    }

    #handleWheel(e) {
        const now = Date.now();
        const isVertical = Math.abs(e.deltaY) > Math.abs(e.deltaX) && Math.abs(e.deltaY) > 15;

        if (!isVertical) {
            if (Math.abs(e.deltaX) <= 5 && Math.abs(e.deltaY) > 0) {
                e.preventDefault();
                e.stopPropagation();
            }
            return;
        }

        e.preventDefault();
        e.stopImmediatePropagation?.();
        e.stopPropagation();

        if (this.#horizontalScrollActive) return;

        const dir = e.deltaY > 0 ? 1 : -1;
        const mouseDate = this.getDateUnderMouse(e);
        const mousePos = this.getMousePositionInContainer(e);

        if (this.#isTransitioning) {
            this.#aggregateWheelGesture(dir, Math.abs(e.deltaY), now, mouseDate, mousePos);
            return;
        }

        this.#resetWheelGesture(dir, Math.abs(e.deltaY), now);
        this.#performWheelStep(dir, mouseDate, mousePos);
    }

    #aggregateWheelGesture(dir, delta, now, mouseDate, mousePos) {
        const windowMs = 180;

        if (this.#wheelGesture.dir !== dir || now > this.#wheelGesture.windowUntil) {
            this.#wheelGesture = { dir, count: 0, start: now, delta: 0, windowUntil: 0 };
        }

        this.#wheelGesture.windowUntil = now + windowMs;
        this.#wheelGesture.count++;
        this.#wheelGesture.delta += delta;

        const duration = now - this.#wheelGesture.start;
        const desired = this.#calculateDesiredSteps(duration);

        if (desired > 0) {
            this.#wheelChain = { dir, count: Math.min(desired, 2), mouseDate, mousePos };
        }
    }

    #calculateDesiredSteps(duration) {
        const { count, delta } = this.#wheelGesture;
        if (duration < 120) return 0;
        if (count >= 12 || delta >= 800) return 2;
        if (count >= 6 || delta >= 400) return 1;
        return 0;
    }

    #resetWheelGesture(dir, delta, now) {
        this.#wheelGesture = { dir, count: 1, start: now, delta, windowUntil: now + 180 };
    }

    #performWheelStep(dir, mouseDate, mousePos) {
        if (this.#isTransitioning) return;

        const date = mouseDate || this.getCenterDate();
        const pos = mousePos ?? this.#scrollContainer.clientWidth / 2;

        if (this.#selectionManager.isSelectingRange && this.#selectionManager.selectionTimeband) {
            this.#wheelChain = { dir: 0, count: 0, mouseDate: null, mousePos: null };
            return;
        }

        const transitions = {
            [TIMEBANDS.DAY]: { out: TIMEBANDS.MONTH },
            [TIMEBANDS.MONTH]: { out: TIMEBANDS.YEAR, in: TIMEBANDS.DAY },
            [TIMEBANDS.YEAR]: { in: TIMEBANDS.MONTH }
        };

        const t = transitions[this.#currentTimeband];
        if (dir > 0 && t.out) {
            this.transitionToTimeband(t.out, date, pos);
        } else if (dir < 0 && t.in) {
            const target = t.in === TIMEBANDS.DAY
                ? new Date(date.getFullYear(), date.getMonth(), 1)
                : new Date(date.getFullYear(), 0, 1);
            this.transitionToTimeband(t.in, target, pos);
        } else {
            this.#wheelChain = { dir: 0, count: 0, mouseDate: null, mousePos: null };
        }
    }

    #handleTouchStart(e) {
        if (!e.touches?.length) return;

        this.#stopMomentum();

        const t = e.touches[0];
        this.#touchState = { startX: t.clientX, startY: t.clientY, lock: null };

        this.#dragState = {
            isActive: true,
            startX: t.clientX,
            startY: t.clientY,
            scrollLeft: this.#scrollContainer.scrollLeft,
            scrollTop: this.#scrollContainer.scrollTop,
            hasMoved: false,
            lastX: t.clientX,
            lastTime: performance.now(),
            velocity: 0
        };
    }

    #handleTouchMove(e) {
        if (!e.touches?.length) return;
        const t = e.touches[0];
        const dx = Math.abs(t.clientX - this.#touchState.startX);
        const dy = Math.abs(t.clientY - this.#touchState.startY);

        if (!this.#touchState.lock) {
            const threshold = 8;
            if (dx >= threshold || dy >= threshold) {
                this.#touchState.lock = dx > dy ? 'x' : 'y';

                if (this.#touchState.lock === 'x') {
                    this.#dragState.hasMoved = true;
                }
            }
        }

        if (this.#touchState.lock === 'y') {
            e.preventDefault();
            e.stopImmediatePropagation?.();
            e.stopPropagation();
        } else if (this.#touchState.lock === 'x' && this.#options.enableDrag) {
            const { startX, scrollLeft } = this.#dragState;
            const movedX = t.clientX - startX;

            const now = performance.now();
            const dt = now - this.#dragState.lastTime;
            if (dt > 0) {
                const dxSinceLast = t.clientX - this.#dragState.lastX;
                this.#dragState.velocity = dxSinceLast / dt;
            }
            this.#dragState.lastX = t.clientX;
            this.#dragState.lastTime = now;

            this.#scrollContainer.scrollLeft = scrollLeft - movedX;
            e.preventDefault();
        }
    }

    #handleTouchEnd(e) {
        const wasDraggingX = this.#touchState.lock === 'x' && this.#dragState.hasMoved;

        if (this.#touchState.lock === 'y') {
            const touch = e.changedTouches[0];
            const deltaY = touch.clientY - this.#touchState.startY;

            if (Math.abs(deltaY) > 50) {
                const dir = deltaY > 0 ? 1 : -1;
                this.#performWheelStep(dir, this.getCenterDate(), this.#scrollContainer.clientWidth / 2);
            }
        }

        this.#touchState.lock = null;
        this.#dragState.isActive = false;

        if (wasDraggingX) {
            if (this.#options.momentumScrolling) {
                this.#startMomentum();
            } else {
                this.#scheduleReplenishCheck();
            }
        }
    }

    getCenterDate() {
        const { scrollLeft, clientWidth } = this.#scrollContainer;
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return this.#options.startDate || new Date();

        const width = (cfg.itemWidth || 0) + 1;
        const index = Math.floor((scrollLeft + clientWidth / 2) / width);

        return (index >= 0 && index < this.#items.length)
            ? this.#items[index].date
            : this.#options.startDate || new Date();
    }

    getDateUnderMouse(event) {
        const rect = this.#scrollContainer.getBoundingClientRect();
        const mouseX = event.clientX - rect.left;
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return this.getCenterDate();

        const width = (cfg.itemWidth || 0) + 1;
        const index = Math.floor((this.#scrollContainer.scrollLeft + mouseX) / width);

        return (index >= 0 && index < this.#items.length)
            ? this.#items[index].date
            : this.getCenterDate();
    }

    getMousePositionInContainer(event) {
        return event.clientX - this.#scrollContainer.getBoundingClientRect().left;
    }

    #requestSelectionUpdate() {
        if (this.#pendingSelRaf) return;
        this.#pendingSelRaf = requestAnimationFrame(() => {
            this.#pendingSelRaf = 0;
            this.updateSelection();
        });
    }

    updateSelection() {
        const tb = this.#currentTimeband;
        this.#items.forEach(item => this.#updateItemSelection(item, tb));
        this.updateRangeIndicators();
    }

    #updateItemSelection(itemData, timeband) {
        const el = itemData.element;
        if (!el) return;

        const sm = this.#selectionManager;

        el.classList.remove('selected', 'in-range', 'range-start', 'range-end', 'locked');

        if (!sm.selectedStartDate) return;

        const itemStart = TimebandUtils.getItemRangeStart(itemData, timeband);
        const itemEnd = TimebandUtils.getItemRangeEnd(itemData, timeband);
        const selStart = sm.selectedStartDate;
        const selEnd = sm.selectedEndDate || sm.selectedStartDate;

        if (!itemStart || !itemEnd) return;

        const overlaps = itemStart <= selEnd && itemEnd >= selStart;
        if (!overlaps) return;

        const containsStart = itemStart <= selStart && itemEnd >= selStart;
        const containsEnd = itemStart <= selEnd && itemEnd >= selEnd;

        if (containsStart && containsEnd) {
            el.classList.add('selected', 'range-start', 'range-end');
            if (this.#options.singleDateMode && sm.selectionMode === SELECTION_MODES.LOCKED && !sm.selectedEndDate) {
                el.classList.add('locked');
            }
        } else if (containsStart) {
            el.classList.add('selected', 'range-start');
        } else if (containsEnd) {
            el.classList.add('selected', 'range-end');
        } else {
            el.classList.add('in-range');
        }
    }
    scrollToCenter(instant = false) {
        const centerDate = this.#options.startDate || new Date();
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return;

        const index = this.#findIndexForDate(centerDate, this.#currentTimeband);
        const itemWidth = (cfg.itemWidth || 0) + 1;
        const containerWidth = this.#scrollContainer.clientWidth;

        const target = index >= 0
            ? (index * itemWidth) - (containerWidth / 2) + (itemWidth / 2)
            : (this.#scrollContainer.scrollWidth - containerWidth) / 2;

        this.#setScrollLeft(Math.max(0, target), instant);
    }

    #findIndexForDate(targetDate, timeband) {
        if (!targetDate) return -1;
        return this.#items.findIndex(item =>
            isSameByGranularity(item.date, targetDate, timeband)
        );
    }

    #setScrollLeft(value, instant) {
        if (instant) {
            const prev = this.#scrollContainer.style.scrollBehavior;
            this.#scrollContainer.style.scrollBehavior = 'auto';
            this.#scrollContainer.scrollLeft = value;
            setTimeout(() => {
                this.#scrollContainer.style.scrollBehavior = prev || '';
            }, 50);
        } else {
            this.#scrollContainer.scrollLeft = value;
        }
    }

    scrollToAlignPosition(targetDate, alignPosition, instant = false) {
        if (!targetDate) return;

        const index = this.#findIndexForDate(targetDate, this.#currentTimeband);
        if (index < 0) return;

        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return;

        const itemWidth = (cfg.itemWidth || 0) + 1;
        const center = (index * itemWidth) + (itemWidth / 2);

        this.#setScrollLeft(Math.max(0, center - alignPosition), instant);
    }

    /* External API */

    setSelectedRange(startDate, endDate = null) {
        this.#selectionManager.setSelectedRange(startDate, endDate);

        if (startDate) {
            const targetDate = clampToStartOfDay(startDate);
            this.#options.startDate = targetDate;

            if (this.#currentTimeband !== TIMEBANDS.DAY) {
                this.#pendingSelectionEmit = true;
                this.transitionToTimeband(TIMEBANDS.DAY, targetDate);
            } else {
                this.#generateInitialItems();
                this.render();
                this.scrollToCenter(false);
                this.#requestSelectionUpdate();
                this.emit('selectionChange', this.#selectionManager.getSelectedRange());
            }
        } else {
            this.#selectionManager.ensureSelection();
            this.#requestSelectionUpdate();
            this.emit('selectionChange', this.#selectionManager.getSelectedRange());
        }
    }

    clearSelection() {
        this.#selectionManager.clearSelection(true);
        this.#requestSelectionUpdate();
        this.emit('selectionChange', this.#selectionManager.getSelectedRange());
    }

    getSelectedRange() {
        return this.#selectionManager.getSelectedRange();
    }

    on(event, callback) {
        (this.#eventListeners[event] ??= []).push(callback);
    }

    off(event, callback) {
        if (!this.#eventListeners[event]) return;
        this.#eventListeners[event] = this.#eventListeners[event].filter(cb => cb !== callback);
    }

    emit(event, data) {
        this.#eventListeners[event]?.forEach(cb => cb(data));
    }

    transitionToTimeband(timeband, centerDate = null, alignPosition = null) {
        if (timeband === this.#currentTimeband) {
            if (centerDate) {
                this.#options.startDate = new Date(centerDate);
                this.scrollToCenter(false);
            }
            this.#flushPendingSelectionEmit();
            return;
        }

        if (this.#isTransitioning) return;

        const cfg = this.#timebandConfigs[timeband];
        if (!cfg) return;

        this.#isTransitioning = true;
        this.#pendingAlignPosition = alignPosition;

        const from = TIMEBAND_ORDER[this.#currentTimeband];
        const to = TIMEBAND_ORDER[timeband];

        this.#scrollContainer.classList.add('transitioning');
        this.#scrollContainer.classList.toggle('forward', to > from);
        this.#scrollContainer.classList.toggle('backward', to < from);
        this.#scrollContainer.classList.toggle('zoom-in', to > from);
        this.#scrollContainer.classList.toggle('zoom-out', to < from);

        if (centerDate) {
            this.#options.startDate = new Date(centerDate);
        }

        const half = this.#options.transitionDuration / 2;

        requestAnimationFrame(() => {
            setTimeout(() => {
                this.#currentTimeband = timeband;
                this.#generateInitialItems();
                this.render();

                if (this.#pendingAlignPosition != null) {
                    this.scrollToAlignPosition(this.#options.startDate, this.#pendingAlignPosition, true);
                } else {
                    this.scrollToCenter(true);
                }

                if (this.#pendingRangeCompletion) {
                    this.#completeRangeAfterTransition();
                }

                requestAnimationFrame(() => {
                    setTimeout(() => {
                        this.#scrollContainer.classList.remove(
                            'transitioning', 'forward', 'backward', 'zoom-in', 'zoom-out'
                        );
                        this.#isTransitioning = false;
                        this.#pendingAlignPosition = null;

                        this.emit('timebandChange', {
                            timeband: this.#currentTimeband,
                            centerDate: this.#options.startDate
                        });
                        this.emit('viewChange');
                        this.#requestSelectionUpdate();
                        this.#flushPendingSelectionEmit();
                        this.#continueWheelChain();
                    }, half);
                });
            }, half);
        });
    }

    #flushPendingSelectionEmit() {
        if (this.#pendingSelectionEmit) {
            this.#pendingSelectionEmit = false;
            this.#requestSelectionUpdate();
            this.emit('selectionChange', this.#selectionManager.getSelectedRange());
        }
    }

    #continueWheelChain() {
        if (!this.#wheelChain.count || !this.#wheelChain.dir) return;
        if (this.#isTransitioning) return;

        const { dir, mouseDate, mousePos } = this.#wheelChain;
        this.#wheelChain.count = Math.max(0, this.#wheelChain.count - 1);

        this.#performWheelStep(dir, mouseDate, mousePos);

        if (!this.#isTransitioning && this.#wheelChain.count === 0) {
            this.#wheelChain = { dir: 0, count: 0, mouseDate: null, mousePos: null };
        }
    }

    #transitionForRangeCompletion(itemData) {
        const sm = this.#selectionManager;
        const startBand = sm.selectionTimeband;
        if (!startBand) return;

        let targetBand = this.#currentTimeband;
        let targetDate = itemData.date;

        if (startBand === TIMEBANDS.DAY) {
            if (this.#currentTimeband === TIMEBANDS.MONTH) {
                targetBand = TIMEBANDS.DAY;
                targetDate = new Date(targetDate.getFullYear(), targetDate.getMonth(), 1);
            } else if (this.#currentTimeband === TIMEBANDS.YEAR) {
                targetBand = TIMEBANDS.DAY;
                targetDate = new Date(targetDate.getFullYear(), 0, 1);
            }
        } else if (startBand === TIMEBANDS.MONTH && this.#currentTimeband === TIMEBANDS.YEAR) {
            targetBand = TIMEBANDS.MONTH;
            targetDate = new Date(targetDate.getFullYear(), 0, 1);
        }

        this.#pendingRangeCompletion = { itemData, originalTimeband: this.#currentTimeband };
        this.transitionToTimeband(targetBand, targetDate);
    }

    #completeRangeAfterTransition() {
        const pending = this.#pendingRangeCompletion;
        const sm = this.#selectionManager;

        if (!pending || !sm.selectionTimeband) {
            this.#pendingRangeCompletion = null;
            return;
        }

        const { itemData, originalTimeband } = pending;
        let endDate = null;

        if (sm.selectionTimeband === TIMEBANDS.DAY) {
            if (originalTimeband === TIMEBANDS.MONTH) {
                endDate = new Date(itemData.date.getFullYear(), itemData.date.getMonth() + 1, 0);
            } else if (originalTimeband === TIMEBANDS.YEAR) {
                endDate = new Date(itemData.date.getFullYear(), 11, 31);
            }
        } else if (sm.selectionTimeband === TIMEBANDS.MONTH && originalTimeband === TIMEBANDS.YEAR) {
            endDate = new Date(itemData.date.getFullYear(), 11, 31);
        }

        if (endDate) {
            sm.setSelectedRange(
                endDate < sm.selectedStartDate ? endDate : sm.selectedStartDate,
                endDate < sm.selectedStartDate ? sm.selectedStartDate : endDate
            );
            this.#requestSelectionUpdate();
            this.emit('selectionChange', sm.getSelectedRange());
        }

        this.#pendingRangeCompletion = null;
    }

    #createRangeIndicators() {
        this.#leftIndicator = this.#options.renderLeftIndicator?.(this) ??
            this.#createIndicator('left', '◀');
        this.#rightIndicator = this.#options.renderRightIndicator?.(this) ??
            this.#createIndicator('right', '▶');

        this.#container.appendChild(this.#leftIndicator);
        this.#container.appendChild(this.#rightIndicator);
    }

    #createIndicator(direction, symbol) {
        const el = document.createElement('div');
        el.className = `date-picker-range-indicator ${direction}`;
        el.innerHTML = symbol;
        return el;
    }

    updateRangeIndicators() {
        const sm = this.#selectionManager;

        if (!sm.selectedStartDate) {
            this.#leftIndicator?.style && (this.#leftIndicator.style.opacity = '0');
            this.#rightIndicator?.style && (this.#rightIndicator.style.opacity = '0');
            return;
        }

        const range = this.getVisibleDateRange();
        if (!range) {
            this.#leftIndicator && (this.#leftIndicator.style.opacity = '0');
            this.#rightIndicator && (this.#rightIndicator.style.opacity = '0');
            return;
        }

        const selStart = sm.selectedStartDate;
        const selEnd = sm.selectedEndDate || sm.selectedStartDate;

        if (this.#leftIndicator) {
            this.#leftIndicator.style.opacity = selStart < range.start ? '1' : '0';
        }
        if (this.#rightIndicator) {
            this.#rightIndicator.style.opacity = selEnd > range.end ? '1' : '0';
        }
    }

    getVisibleDateRange() {
        if (!this.#items.length) return null;

        const { scrollLeft, clientWidth } = this.#scrollContainer;
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return null;

        const width = (cfg.itemWidth || 0) + 1;
        const firstIndex = Math.max(0, Math.floor(scrollLeft / width));
        const lastIndex = Math.min(
            this.#items.length - 1,
            Math.ceil((scrollLeft + clientWidth) / width)
        );

        if (firstIndex >= this.#items.length || lastIndex < 0) return null;

        return {
            start: TimebandUtils.getItemRangeStart(this.#items[firstIndex], this.#currentTimeband),
            end: TimebandUtils.getItemRangeEnd(this.#items[lastIndex], this.#currentTimeband)
        };
    }

    isDateVisible(date) {
        if (!date) return false;

        const range = this.getVisibleDateRange();
        if (!range) return false;

        const target = clampToStartOfDay(date);
        return target >= range.start && target <= range.end;
    }

    formatDate(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');

        switch (this.#options.dateFormat) {
            case 'MM/DD/YYYY': return `${m}/${d}/${y}`;
            case 'DD/MM/YYYY': return `${d}/${m}/${y}`;
            default: return `${y}-${m}-${d}`;
        }
    }

    #ensureFormatters() {
        if (this.#formatters) return;

        try {
            const locale = this.#options.locale;
            this.#formatters = {
                weekday: new Intl.DateTimeFormat(locale, { weekday: 'short' }),
                month: new Intl.DateTimeFormat(locale, { month: 'short' })
            };
        } catch {
            this.#formatters = null;
        }
    }

    getDayName(date) {
        if (window.locale?.days) return window.locale.days[date.getDay()];

        this.#ensureFormatters();
        if (this.#formatters?.weekday) return this.#formatters.weekday.format(date);

        return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][date.getDay()];
    }

    getMonthName(date) {
        if (window.locale?.months) return window.locale.months[date.getMonth()];

        this.#ensureFormatters();
        if (this.#formatters?.month) return this.#formatters.month.format(date);

        return ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
            'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][date.getMonth()];
    }

    getMonthYear(date) {
        return `${this.getMonthName(date)} ${date.getFullYear()}`;
    }

    isSameDay = areSameDay;
    isSameMonth = areSameMonth;
    isSameYear = areSameYear;

    destroy() {
        this.#stopMomentum();

        if (this.#scrollContainer) {
            Object.entries(this.#boundHandlers).forEach(([key, handler]) => {
                const eventMap = {
                    onClick: 'click',
                    onMouseOver: 'mouseover',
                    onMouseOut: 'mouseout',
                    onScroll: 'scroll',
                    onWheel: 'wheel',
                    onMouseDown: 'mousedown',
                    onTouchStart: 'touchstart',
                    onTouchMove: 'touchmove',
                    onTouchEnd: 'touchend'
                };

                const event = eventMap[key];
                if (!event) return;

                const options = ['wheel', 'touchmove'].includes(event)
                    ? { capture: true }
                    : { passive: true };

                this.#scrollContainer.removeEventListener(event, handler, options);
            });
        }

        if (this.#options.enableDrag) {
            window.removeEventListener('mousemove', this.#boundHandlers.onMouseMove);
            window.removeEventListener('mouseup', this.#boundHandlers.onMouseUp);
        }

        clearTimeout(this.#scrollTimeout);
        clearTimeout(this.#horizontalTimeout);
        cancelAnimationFrame(this.#pendingSelRaf);

        [this.#leftIndicator, this.#rightIndicator]
            .forEach(el => el?.parentNode?.removeChild(el));

        this.#items = [];
        this.#itemByTime.clear();
        this.#boundHandlers = {};
        this.#eventListeners = {};
    }
}

/* Exports */

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { DatePicker, SelectionManager, TimebandUtils };
} else if (typeof window !== 'undefined') {
    window.DatePicker = DatePicker;
    window.SelectionManager = SelectionManager;
    window.TimebandUtils = TimebandUtils;
}
