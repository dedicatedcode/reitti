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

        const maxDate = datePicker.options?.allowFutureDates ? undefined : new Date();
        const items = [];
        const startOffset = isLeft ? -count : 1;
        const endOffset = isLeft ? 0 : count + 1;

        for (let i = startOffset; i < endOffset; i++) {
            if (i === 0) continue;
            const date = addToTimeband(referenceDate, timeband, i);
            if (!date) continue;
            if (!isLeft && maxDate && date > maxDate) break;
            items.push(cfg.getItemData(date));
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
    SINGLE: 'single',    // One picker element selected (unlockable)
    LOCKED: 'locked',    // The selected element is fixed; another click starts a range
    RANGE: 'range'       // A user-drawn range between two anchors
};

const SELECTION_KINDS = {
    ELEMENT: 'element',  // A single picker element (its band span is the committed range)
    RANGE: 'range'       // A real multi-element range drawn by the user
};

class SelectionManager {
    #datePicker = null;
    #selection = null;
    #hoveredDate = null;

    constructor(datePicker) {
        this.#datePicker = datePicker;
    }

    get selection() { return this.#selection; }
    get selectedStartDate() { return this.#selection ? this.#selection.start : null; }
    get selectedEndDate() { return this.#selection ? this.#selection.end : null; }
    get selectionMode() {
        if (this.#selection?.kind === SELECTION_KINDS.RANGE) return SELECTION_MODES.RANGE;
        return this.isLocked ? SELECTION_MODES.LOCKED : SELECTION_MODES.SINGLE;
    }
    get selectionTimeband() { return this.#selection ? this.#selection.anchorBand : null; }
    get isLocked() {
        return !!this.#selection &&
            this.#selection.kind === SELECTION_KINDS.ELEMENT && this.#selection.locked;
    }
    get isRangeMode() { return this.selectionMode === SELECTION_MODES.RANGE; }
    get hoveredDate() { return this.#hoveredDate; }

    clearSelection() {
        this.#selection = null;
        this.#hoveredDate = null;
    }

    ensureSelection(defaultDate = null) {
        if (this.#selection) return this.#selection.start;
        const date = defaultDate || this.#datePicker.options.startDate || new Date();
        this.#selectElement(date, TIMEBANDS.DAY);
        return this.#selection.start;
    }

    setSelectedRange(startDate, endDate = null) {
        const start = clampToStartOfDay(startDate);
        const end = endDate ? clampToStartOfDay(endDate) : null;

        if (!start) {
            this.#selection = null;
            return;
        }

        if (!end || end.getTime() === start.getTime()) {
            this.#selectElement(start, TIMEBANDS.DAY);
            return;
        }

        const rangeStart = start < end ? start : end;
        const rangeEnd = start < end ? end : start;

        const anchorBand = this.#detectElementBand(rangeStart, rangeEnd);
        if (anchorBand) {
            this.#selectElement(rangeStart, anchorBand);
        } else {
            this.#setRangeRecord(rangeStart, rangeEnd, TIMEBANDS.DAY);
        }
    }

    #detectElementBand(start, end) {
        for (const band of [TIMEBANDS.DAY, TIMEBANDS.MONTH, TIMEBANDS.YEAR]) {
            const bandStart = getTimebandStart(start, band);
            const bandEnd = getTimebandEnd(start, band);
            if (bandStart.getTime() === start.getTime() &&
                bandEnd.getTime() === end.getTime()) {
                return band;
            }
        }
        return null;
    }

    getSelectedRange() {
        const fmt = (d) => d ? this.#datePicker.formatDate(d) : null;
        const sel = this.#selection;
        return {
            startDate: sel ? fmt(sel.start) : null,
            endDate: sel ? fmt(sel.end) : null,
            timeband: this.#datePicker.currentTimeband,
            mode: this.selectionMode,
            kind: sel ? sel.kind : null,
            locked: this.isLocked
        };
    }

    setHoveredDate(itemData) {
        if (!itemData?.date) {
            this.#hoveredDate = null;
            return;
        }
        this.#hoveredDate = normalizeDate(itemData.date);
    }

    handleClick(itemData, timeband) {
        const date = normalizeDate(itemData?.date);
        if (!date) return;

        const clickedStart = getTimebandStart(date, timeband);
        const clickedEnd = getTimebandEnd(date, timeband);
        if (!clickedStart || !clickedEnd) return;

        const sel = this.#selection;
        if (!sel) {
            this.#selectElement(date, timeband);
            return;
        }

        if (sel.kind === SELECTION_KINDS.ELEMENT) {
            this.#handleElementKindClick(sel, clickedStart, clickedEnd, date, timeband);
        } else {
            this.#handleRangeKindClick(sel, clickedStart, clickedEnd, date, timeband);
        }
    }

    #handleElementKindClick(sel, clickedStart, clickedEnd, date, timeband) {
        const isAnchorElement = sel.anchorBand === timeband &&
            clickedStart.getTime() === sel.start.getTime() &&
            clickedEnd.getTime() === sel.end.getTime();

        if (isAnchorElement) {
            if (this.#datePicker.options.singleDateMode) {
                sel.locked = !sel.locked;
            }
            return;
        }

        const clickedWithin = clickedStart >= sel.start && clickedEnd <= sel.end;

        if (this.#datePicker.options.singleDateMode && (!sel.locked || clickedWithin)) {
            this.#selectElement(date, timeband);
            return;
        }

        this.#setRangeRecord(
            sel.start <= clickedStart ? sel.start : clickedStart,
            sel.end >= clickedEnd ? sel.end : clickedEnd,
            timeband
        );
    }

    #handleRangeKindClick(sel, clickedStart, clickedEnd, date, timeband) {
        if (!this.#datePicker.options.singleDateMode) {
            // Classic multi-date flow: any click restarts a single selection.
            this.#selectElement(date, timeband);
            return;
        }

        const containsStart = clickedStart <= sel.start && clickedEnd >= sel.start;
        const containsEnd = clickedStart <= sel.end && clickedEnd >= sel.end;

        if (containsStart || containsEnd) {
            this.#selectElement(date, timeband);
            return;
        }

        if (clickedStart > sel.end) {
            this.#setRangeRecord(sel.start, clickedEnd, timeband);
        } else {
            // Strictly inside the range: move the start to the clicked element
            this.#setRangeRecord(clickedStart, sel.end, timeband);
        }
    }

    #selectElement(date, timeband) {
        const start = getTimebandStart(date, timeband);
        const end = getTimebandEnd(date, timeband);
        if (!start || !end) return;
        this.#selection = {
            kind: SELECTION_KINDS.ELEMENT,
            anchorBand: timeband,
            start,
            end,
            locked: false
        };
    }

    #setRangeRecord(start, end, timeband) {
        this.#selection = {
            kind: SELECTION_KINDS.RANGE,
            anchorBand: timeband,
            start,
            end,
            locked: false
        };
    }

    calculatePotentialRange(itemData) {
        const timeband = this.#datePicker.currentTimeband;
        const date = normalizeDate(itemData?.date);

        // Nothing to preview yet — the consumer hides the tooltip for null.
        if (!this.#selection || !date) return null;

        const clickedStart = getTimebandStart(date, timeband);
        const clickedEnd = getTimebandEnd(date, timeband);
        const sel = this.#selection;

        let start = sel.start;
        let end = sel.end;
        let mode = this.selectionMode;
        let kind = sel.kind;
        let locked = sel.locked;

        if (sel.kind === SELECTION_KINDS.ELEMENT) {
            const isAnchorElement = sel.anchorBand === timeband &&
                clickedStart.getTime() === sel.start.getTime() &&
                clickedEnd.getTime() === sel.end.getTime();

            if (isAnchorElement) {
                locked = !sel.locked;
                mode = locked ? SELECTION_MODES.LOCKED : SELECTION_MODES.SINGLE;
            } else {
                const clickedWithin = clickedStart >= sel.start && clickedEnd <= sel.end;
                const becomesRange = !this.#datePicker.options.singleDateMode ||
                    (sel.locked && !clickedWithin);

                if (becomesRange) {
                    start = sel.start <= clickedStart ? sel.start : clickedStart;
                    end = sel.end >= clickedEnd ? sel.end : clickedEnd;
                    mode = SELECTION_MODES.RANGE;
                    kind = SELECTION_KINDS.RANGE;
                    locked = false;
                } else {
                    start = clickedStart;
                    end = clickedEnd;
                    mode = SELECTION_MODES.SINGLE;
                    locked = false;
                }
            }
        } else {
            if (!this.#datePicker.options.singleDateMode) {
                // Classic multi-date flow: any click restarts a single selection.
                start = clickedStart;
                end = clickedEnd;
                mode = SELECTION_MODES.SINGLE;
                kind = SELECTION_KINDS.ELEMENT;
                locked = false;
            } else {
                const containsStart = clickedStart <= sel.start && clickedEnd >= sel.start;
                const containsEnd = clickedStart <= sel.end && clickedEnd >= sel.end;

                if (containsStart || containsEnd) {
                    start = clickedStart;
                    end = clickedEnd;
                    mode = SELECTION_MODES.SINGLE;
                    kind = SELECTION_KINDS.ELEMENT;
                    locked = false;
                } else if (clickedStart > sel.end) {
                    end = clickedEnd;
                } else {
                    start = clickedStart;
                }
            }
        }

        return {
            startDate: start,
            endDate: end,
            timeband,
            mode,
            kind,
            locked
        };
    }
}

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

    // Inputs mode state ('mode: "inputs"')
    #inputsStart = null;
    #inputsEnd = null;
    #inputsMaxDate = null;
    #inputsCommittedRange = null;
    #inputsToggle = null;
    #inputsEndWrap = null;
    #inputsExpanded = false;

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
            clickToUnlockMonth: 'Click to unlock month',
            clickToLockMonth: 'Click to lock month',
            clickToUnlockYear: 'Click to unlock year',
            clickToLockYear: 'Click to lock year',
            clickToClearSelection: 'Click to collapse range',
            clickToCollapseRange: 'Click to collapse range',
            clickToCreateRange: 'Click to create range',
            clickToExpandRangeBackward: 'Click to expand range backward',
            clickToExpandRangeForward: 'Click to expand range forward',
            clickToAdjustRangeStart: 'Click to adjust range start',
            select: 'Select',
            to: 'to',
            clear: 'Clear',
            showRange: 'Add end date',
            hideRange: 'Remove end date'
        };

        this.#options = {
            mode: 'strip',
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
            allowFutureDates: false,
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
        if (this.#options.mode === 'inputs') {
            this.#initInputsMode();
            return;
        }
        this.#createContainer();
        this.#generateInitialItems();
        this.render();
        this.#setupEventListeners();
        this.#selectionManager.ensureSelection();
        this.#requestSelectionUpdate();
    }

    #initInputsMode() {
        this.#container.innerHTML = '';
        this.#container.className = 'date-picker date-picker-inputs';

        const root = document.createElement('div');
        root.className = 'date-range-inputs';

        const startWrap = document.createElement('div');
        startWrap.className = 'datetime-picker date-range-input';
        startWrap.innerHTML =
            '<input type="date" class="date-input" aria-label="Start date">' +
            '<button type="button" class="picker-trigger"><i class="lni lni-calendar-days"></i></button>';

        const endWrap = document.createElement('div');
        endWrap.className = 'datetime-picker date-range-input date-range-end';
        endWrap.innerHTML =
            '<input type="date" class="date-input" aria-label="End date">' +
            '<button type="button" class="picker-trigger"><i class="lni lni-calendar-days"></i></button>';
        endWrap.classList.add('hidden');
        this.#inputsEndWrap = endWrap;

        const toggleBtn = document.createElement('button');
        toggleBtn.type = 'button';
        toggleBtn.className = 'btn bar-btn date-range-toggle';
        toggleBtn.setAttribute('aria-expanded', 'false');
        toggleBtn.title = this.#options.strings.showRange || 'Add end date';
        toggleBtn.innerHTML = '<i class="lni lni-plus"></i>';
        this.#inputsToggle = toggleBtn;

        const clearBtn = document.createElement('button');
        clearBtn.type = 'button';
        clearBtn.className = 'btn bar-btn date-range-clear';
        clearBtn.textContent = this.#options.strings.clear || 'Clear';

        root.appendChild(startWrap);
        root.appendChild(toggleBtn);
        root.appendChild(endWrap);
        root.appendChild(clearBtn);
        this.#container.appendChild(root);

        this.#inputsStart = new DateTimePicker(startWrap, {
            popupPlacement: 'top',
            locale: this.#options.locale
        });
        this.#inputsEnd = new DateTimePicker(endWrap, {
            popupPlacement: 'top',
            locale: this.#options.locale
        });

        toggleBtn.addEventListener('click', () => this.#toggleRangeInputs());
        clearBtn.addEventListener('click', () => this.#clearInputs());

        // Every picked date applies immediately — start, end or single date,
        // no matter whether the range toggle is expanded or collapsed.
        for (const input of root.querySelectorAll('.date-input')) {
            input.addEventListener('change', () => this.#commitInputs());
        }

        const today = this.#toYmd(clampToStartOfDay(new Date()));
        this.#setCommittedRange(today, today, false);
    }

    #toYmd(value) {
        if (!value) return null;
        if (value instanceof Date) {
            return isNaN(value.getTime()) ? null : this.formatDate(value);
        }
        const parsed = new Date(value + 'T00:00:00');
        return isNaN(parsed.getTime()) ? null : this.formatDate(parsed);
    }

    #effectiveMaxDate() {
        if (!this.#options.allowFutureDates) {
            return clampToStartOfDay(new Date());
        }
        return this.#inputsMaxDate;
    }

    #applyInputConstraints() {
        const max = this.#effectiveMaxDate();
        const maxYmd = max ? this.formatDate(max) : '';
        const startInput = this.#inputsStart.element.querySelector('.date-input');
        const endInput = this.#inputsEnd.element.querySelector('.date-input');
        startInput.max = maxYmd;
        endInput.max = maxYmd;
        this.#inputsStart.setMaxDate(max);
        this.#inputsEnd.setMaxDate(max);

        const startVal = startInput.value;
        endInput.min = startVal || '';
        this.#inputsEnd.setMinDate(startVal ? new Date(startVal + 'T00:00:00') : null);
    }

    #setCommittedRange(startDate, endDate, emit) {
        // Guard against redundant commits (e.g. duplicate change events):
        // re-setting the identical range must not re-trigger a GPS load.
        const unchanged = !!this.#inputsCommittedRange &&
            this.#inputsCommittedRange.startDate === startDate &&
            this.#inputsCommittedRange.endDate === endDate;
        this.#inputsCommittedRange = { startDate, endDate };
        this.#inputsStart.setDateSilent(new Date(startDate + 'T00:00:00'));
        this.#inputsEnd.setDateSilent(new Date(endDate + 'T00:00:00'));
        this.#applyInputConstraints();
        if (emit && !unchanged) {
            this.emit('selectionChange', { startDate, endDate, timeband: 'day' });
        }
    }

    #toggleRangeInputs() {
        this.#inputsExpanded = !this.#inputsExpanded;
        this.#applyInputsExpansion();
    }

    #applyInputsExpansion() {
        if (!this.#inputsToggle || !this.#inputsEndWrap) return;
        const strings = this.#options.strings;
        const icon = this.#inputsToggle.querySelector('i');
        if (this.#inputsExpanded) {
            this.#inputsEndWrap.classList.remove('hidden');
            if (icon) icon.className = 'lni lni-minus';
            this.#inputsToggle.title = strings.hideRange || 'Remove end date';
        } else {
            this.#inputsEndWrap.classList.add('hidden');
            if (icon) icon.className = 'lni lni-plus';
            this.#inputsToggle.title = strings.showRange || 'Add end date';
        }
        this.#inputsToggle.setAttribute('aria-expanded', String(this.#inputsExpanded));
    }

    #commitInputs() {
        const startVal = (this.#inputsStart.getValue() || '').split('T')[0];
        let endVal = (this.#inputsEnd.getValue() || '').split('T')[0];
        // Collapsed toggle = single-date selection: end always equals start.
        if (!this.#inputsExpanded) endVal = startVal;
        if (!startVal && !endVal) return;
        let start = startVal || endVal;
        let end = endVal || startVal;
        if (start > end) {
            const tmp = start;
            start = end;
            end = tmp;
        }
        this.#setCommittedRange(start, end, true);
    }

    #clearInputs() {
        this.#inputsExpanded = false;
        this.#applyInputsExpansion();
        const today = this.#toYmd(clampToStartOfDay(new Date()));
        this.#setCommittedRange(today, today, true);
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

        let start;
        if (this.#options.allowFutureDates) {
            start = new Date(center);
            start.setDate(center.getDate() - Math.floor(total / 2));
        } else {
            const rightEdge = new Date();
            start = new Date(rightEdge);
            start.setDate(rightEdge.getDate() - total + 1);
        }

        const maxDate = this.#options.allowFutureDates ? undefined : new Date();
        this.#items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(start);
            d.setDate(start.getDate() + i);
            if (maxDate && d > maxDate) break;
            this.#items.push(this.#createDayData(d));
        }
    }

    #generateMonths() {
        const center = this.#options.startDate || new Date();
        const total = this.#calculateInitialItemCount(TIMEBANDS.MONTH);

        let start;
        if (this.#options.allowFutureDates) {
            start = new Date(center.getFullYear(), center.getMonth() - Math.floor(total / 2), 1);
        } else {
            const now = new Date();
            const rightEdge = new Date(now.getFullYear(), now.getMonth(), 1);
            start = new Date(rightEdge.getFullYear(), rightEdge.getMonth() - total + 1, 1);
        }

        const maxDate = this.#options.allowFutureDates ? undefined : new Date();
        this.#items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
            if (maxDate && d > maxDate) break;
            this.#items.push(this.#createMonthData(d));
        }
    }

    #generateYears() {
        const center = this.#options.startDate || new Date();
        const total = this.#calculateInitialItemCount(TIMEBANDS.YEAR);

        let startYear;
        if (this.#options.allowFutureDates) {
            startYear = center.getFullYear() - Math.floor(total / 2);
        } else {
            const now = new Date();
            startYear = now.getFullYear() - total + 1;
        }

        const maxDate = this.#options.allowFutureDates ? undefined : new Date();
        this.#items = [];
        for (let i = 0; i < total; i++) {
            const d = new Date(startYear + i, 0, 1);
            if (maxDate && d > maxDate) break;
            this.#items.push(this.#createYearData(d));
        }
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
        if (!itemData?.date) return;

        const sm = this.#selectionManager;
        sm.handleClick(itemData, this.#currentTimeband);

        if (sm.isLocked && itemData) {
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
        const pos = mousePos || this.#scrollContainer.clientWidth / 2;

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
        const sel = sm.selection;

        el.classList.remove('selected', 'in-range', 'range-start', 'range-end', 'locked');
        el.title = '';

        if (!sel) return;

        const itemStart = TimebandUtils.getItemRangeStart(itemData, timeband);
        const itemEnd = TimebandUtils.getItemRangeEnd(itemData, timeband);
        if (!itemStart || !itemEnd) return;

        const overlaps = itemStart <= sel.end && itemEnd >= sel.start;

        if (sel.kind === SELECTION_KINDS.ELEMENT) {
            if (sel.anchorBand === timeband) {
                const isAnchorItem = itemStart.getTime() === sel.start.getTime() &&
                    itemEnd.getTime() === sel.end.getTime();
                if (isAnchorItem) {
                    el.classList.add('selected');
                    if (sel.locked) {
                        el.classList.add('locked');
                        el.title = this.#lockTitle('Unlock', sel.anchorBand);
                    } else {
                        el.title = this.#lockTitle('Lock', sel.anchorBand);
                    }
                } else if (sel.locked && !overlaps) {
                    el.title = this.#options.strings.clickToCreateRange || '';
                }
            } else if (overlaps) {
                // The element's span covers this band: highlight uniformly,
                // without range endpoints — this is an implied range, not a drawn one.
                el.classList.add('in-range');
            }
            return;
        }

        if (!overlaps) {
            el.title = itemEnd < sel.start
                ? (this.#options.strings.clickToExpandRangeBackward || '')
                : (this.#options.strings.clickToExpandRangeForward || '');
            return;
        }

        const containsStart = itemStart <= sel.start && itemEnd >= sel.start;
        const containsEnd = itemStart <= sel.end && itemEnd >= sel.end;

        if (containsStart) {
            el.classList.add('selected', 'range-start');
            el.title = this.#options.strings.clickToClearSelection || '';
        }
        if (containsEnd) {
            el.classList.add('selected', 'range-end');
            el.title = this.#options.strings.clickToClearSelection || '';
        }
        if (!containsStart && !containsEnd) {
            el.classList.add('in-range');
            el.title = this.#options.strings.clickToAdjustRangeStart || '';
        }
    }

    #lockTitle(action, band) {
        const strings = this.#options.strings || {};
        const keys = {
            [TIMEBANDS.DAY]: 'Date',
            [TIMEBANDS.MONTH]: 'Month',
            [TIMEBANDS.YEAR]: 'Year'
        };
        return strings[`clickTo${action}${keys[band] || 'Date'}`] || '';
    }

    scrollToCenter(instant = false) {
        const centerDate = this.#options.startDate || new Date();
        const cfg = this.#timebandConfigs[this.#currentTimeband];
        if (!cfg) return;

        const index = this.#findIndexForDate(centerDate, this.#currentTimeband);
        const itemWidth = (cfg.itemWidth || 0) + 1;
        const containerWidth = this.#scrollContainer.clientWidth;

        let target;
        if (this.#options.allowFutureDates) {
            target = index >= 0
                ? (index * itemWidth) - (containerWidth / 2) + (itemWidth / 2)
                : (this.#scrollContainer.scrollWidth - containerWidth) / 2;
        } else {
            target = index >= 0
                ? (index * itemWidth) - containerWidth + itemWidth
                : this.#scrollContainer.scrollWidth - containerWidth;
        }

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
        if (this.#options.mode === 'inputs') {
            const start = this.#toYmd(startDate) || this.#toYmd(clampToStartOfDay(new Date()));
            const end = endDate ? (this.#toYmd(endDate) || start) : start;
            // A real multi-day range auto-expands the end input; a single
            // date keeps the collapsed single-input look.
            this.#inputsExpanded = end !== start;
            this.#applyInputsExpansion();
            // Emit like the strip does — this is what triggers the initial
            // timeline/GPS load after the factory syncs the starting date.
            // The 30s auto-today timer is gated by isSelectedDateToday() in
            // index.html, so an unchanged sync cannot loop.
            this.#setCommittedRange(start < end ? start : end, start < end ? end : start, false);
            this.emit('selectionChange', { startDate: start, endDate: end, timeband: 'day' });
            return;
        }
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
        this.#selectionManager.clearSelection();
        this.#requestSelectionUpdate();
        this.emit('selectionChange', this.#selectionManager.getSelectedRange());
    }

    getSelectedRange() {
        if (this.#options.mode === 'inputs') {
            return this.#inputsCommittedRange
                ? { startDate: this.#inputsCommittedRange.startDate, endDate: this.#inputsCommittedRange.endDate }
                : null;
        }
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

        if (this.#options.mode === 'inputs') {
            const range = this.#inputsCommittedRange;
            if (!range) return false;
            const target = this.#toYmd(date);
            return !!target && target >= range.startDate && target <= range.endDate;
        }

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

    setAllowFutureDates(allow) {
        if (this.#options.mode === 'inputs') {
            this.#options.allowFutureDates = !!allow;
            this.#applyInputConstraints();
            return;
        }
        if (allow === this.#options.allowFutureDates) return;
        this.#options.allowFutureDates = allow;
        this.#generateInitialItems();
        this.render();
        this.#requestSelectionUpdate();
    }

    setMaxDate(date) {
        if (this.#options.mode === 'inputs') {
            this.#inputsMaxDate = date ? clampToStartOfDay(normalizeDate(date)) : null;
            this.#applyInputConstraints();
            return;
        }
        if (this.#options.allowFutureDates) return;
        const clamped = clampToStartOfDay(normalizeDate(date));
        if (clamped) {
            this.#generateInitialItems();
            this.render();
            this.#requestSelectionUpdate();
        }
    }

    destroy() {
        if (this.#inputsStart) {
            this.#inputsStart.destroy();
            this.#inputsStart = null;
        }
        if (this.#inputsEnd) {
            this.#inputsEnd.destroy();
            this.#inputsEnd = null;
        }
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
