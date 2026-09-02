(function () {
    'use strict';
    if (typeof window === 'undefined' || window.__dateJumpInitialized) return;
    window.__dateJumpInitialized = true;

    const TRIGGER_CHARS = /^[a-zA-Z0-9.\-/ ]$/;
    const EDITABLE_SELECTOR = 'input, textarea, select, [contenteditable="true"]';
    const MONTH_KEYS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

    const uiMode = window.userSettings && window.userSettings.uiMode;
    if (uiMode === 'SHARED_LIVE_MODE_ONLY' || uiMode === 'VIEW_MEMORIES') return;

    let session = null;
    let overlay = null;
    let bufferEl = null;
    let previewEl = null;

    function isEditableTarget(target) {
        return !!(target && target.closest && target.closest(EDITABLE_SELECTOR));
    }

    function isOverlayOpen() {
        return document.querySelectorAll('.overlay:not(.hidden)').length > 0;
    }

    function isLiveModeActive() {
        const liveOverlay = document.getElementById('auto-update-overlay');
        return !!(liveOverlay && liveOverlay.classList.contains('visible'));
    }

    function buildParserContext() {
        const locale = (window.userSettings && window.userSettings.selectedLocale) || navigator.language || 'en';
        const longFormatter = new Intl.DateTimeFormat(locale, { month: 'long' });
        const months = MONTH_KEYS.map((key, i) => {
            const short = (window.locale && window.locale.months && window.locale.months[i])
                || t('datepicker.months.' + key);
            let full = short;
            try {
                full = longFormatter.format(new Date(2026, i, 1));
            } catch (_) {
                // keep short as full fallback
            }
            return { short, full };
        });
        const words = {
            today: [t('datepicker.today'), 'today', 'heute'],
            yesterday: [t('datepicker.yesterday'), 'yesterday', 'gestern'],
            range: [t('datepicker.to'), 'to', 'bis']
        };
        return { locale, months, words };
    }

    function buildHintTokens() {
        const tokens = ['31.08.2026', '2026-08-31', '08/31/2026', 'Aug 2026', 'Aug - Oct 2026', '2026',
            t('datepicker.today'), t('datepicker.yesterday')];
        return tokens.map(token =>
            '<span class="date-jump-hint-token">' + token + '</span>').join('');
    }

    function ensureOverlay() {
        if (overlay) return;
        overlay = document.createElement('div');
        overlay.id = 'date-jump-overlay';
        overlay.className = 'date-jump-overlay hidden';
        overlay.setAttribute('aria-live', 'polite');
        overlay.innerHTML =
            '<div class="date-jump-input-row">' +
            '  <span class="date-jump-label">' + t('datejump.jump-to-date') + '</span>' +
            '  <input type="text" id="date-jump-buffer" class="date-jump-buffer" ' +
            '         autocomplete="off" spellcheck="false" aria-label="' + t('datejump.jump-to-date') + '">' +
            '</div>' +
            '<div id="date-jump-preview" class="date-jump-preview"></div>' +
            '<div class="date-jump-hints">' +
            '  <span class="date-jump-hints-label">' + t('datejump.hint.label') + '</span>' +
            '  <div class="date-jump-hint-tokens">' + buildHintTokens() + '</div>' +
            '</div>';
        document.body.appendChild(overlay);
        bufferEl = overlay.querySelector('#date-jump-buffer');
        previewEl = overlay.querySelector('#date-jump-preview');
        overlay.addEventListener('click', (e) => e.stopPropagation());
        // Touch devices have no hardware keyboard: the buffer doubles as a real
        // input so focusing it opens the virtual keyboard. Desktop commits run
        // through the window keydown handler; this sync covers soft keyboards.
        bufferEl.addEventListener('input', () => {
            if (session) {
                session.buffer = bufferEl.value;
                renderSession();
            }
        });
    }

    function formatDate(dateYmd) {
        try {
            return new Intl.DateTimeFormat(window.userSettings && window.userSettings.selectedLocale,
                { dateStyle: 'medium' }).format(new Date(dateYmd + 'T00:00:00'));
        } catch (_) {
            return dateYmd;
        }
    }

    function formatRange(range) {
        if (!range || !range.startDate) return '';
        if (range.startDate === range.endDate) return formatDate(range.startDate);
        return formatDate(range.startDate) + ' ' + t('datepicker.to') + ' ' + formatDate(range.endDate);
    }

    function renderSession() {
        ensureOverlay();
        overlay.classList.remove('hidden');
        // Only write when different — setting .value would move the caret
        // while the user edits mid-string.
        if (bufferEl.value !== session.buffer) bufferEl.value = session.buffer;
        const result = window.DateJumpParser.parse(session.buffer, buildParserContext());
        session.result = result.status === 'ok' || result.status === 'incomplete' ? result : { status: 'invalid', alternatives: [] };

        const display = session.override && session.override.startDate
            ? { status: 'ok', startDate: session.override.startDate, endDate: session.override.endDate }
            : session.result;

        overlay.classList.remove('is-valid', 'is-invalid');
        if (display.status === 'ok') {
            overlay.classList.add('is-valid');
            previewEl.innerHTML = t('datejump.understood') + ' <strong>' + formatRange(display) + '</strong>';
        } else if (display.status === 'incomplete' && display.alternatives.length && display.alternatives[0].startDate) {
            previewEl.innerHTML = t('datejump.understood') + ' <strong>' + formatRange(display.alternatives[0]) + '…</strong>';
        } else if (display.status === 'incomplete') {
            previewEl.textContent = t('datejump.keep-typing');
        } else {
            overlay.classList.add('is-invalid');
            previewEl.textContent = t('datejump.unrecognized');
        }
    }

    function startSession(initialBuffer) {
        session = { buffer: initialBuffer || '', result: null, override: null, selected: 0 };
        renderSession();
        // Focus opens the virtual keyboard on touch devices; on desktop the
        // window-level keydown capture keeps handling every key.
        if (bufferEl) bufferEl.focus({ preventScroll: true });
    }

    function cancelSession() {
        session = null;
        if (overlay) overlay.classList.add('hidden');
    }

    function commitSession() {
        const result = session && session.result;
        cancelSession();
        if (result && result.status === 'ok') {
            document.body.dispatchEvent(new CustomEvent('dateJump:commit', {
                detail: { startDate: result.startDate, endDate: result.endDate }
            }));
        }
    }

    function cycleInterpretation() {
        if (!session || !session.result) return;
        const alternatives = (session.result.alternatives || []).filter(a => a && a.startDate);
        if (!alternatives.length) return;
        session.selected = ((session.selected || 0) + 1) % (alternatives.length + 1);
        session.override = session.selected === 0 ? null : alternatives[session.selected - 1];
        renderSession();
    }

    function onKeyDown(e) {
        if (e.isComposing || e.keyCode === 229) return; // IME composition

        if (e.ctrlKey || e.metaKey || e.altKey) {
            if (session) cancelSession();
            return; // cancel-but-pass-through
        }

        const key = e.key;

        if (e.repeat && !(session && key === 'Backspace')) {
            if (session) cancelSession();
            return;
        }

        if (session) {
            if (key === 'Escape') {
                cancelSession();
                e.preventDefault();
                e.stopPropagation();
                return;
            }
            if (key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                if (session.result && session.result.status === 'ok') commitSession();
                return; // unrecognized → Enter does nothing
            }
            if (key === 'Tab') {
                e.preventDefault();
                e.stopPropagation();
                cycleInterpretation();
                return;
            }
            if (key === 'Backspace') {
                e.preventDefault();
                e.stopPropagation();
                session.buffer = session.buffer.slice(0, -1);
                if (!session.buffer) cancelSession();
                else renderSession();
                return;
            }
            if (key.length === 1 && TRIGGER_CHARS.test(key)) {
                e.preventDefault();
                e.stopPropagation();
                session.buffer += key;
                renderSession();
            }
            return;
        }

        // No active session — check the start gates.
        if (isLiveModeActive()) return;
        if (isOverlayOpen()) return;
        if (isEditableTarget(e.target)) return;
        if (key.length === 1 && TRIGGER_CHARS.test(key)) {
            e.preventDefault();
            e.stopPropagation();
            startSession(key);
        }
    }

    document.addEventListener('click', (e) => {
        if (session && overlay && !overlay.contains(e.target)) {
            cancelSession();
        }
    });

    window.addEventListener('blur', () => {
        if (session) cancelSession();
    });

    window.addEventListener('keydown', onKeyDown, true);

    window.DateJump = {
        open() {
            if (!session && !isLiveModeActive() && !isOverlayOpen()) {
                startSession('');
            }
        }
    };
})();
