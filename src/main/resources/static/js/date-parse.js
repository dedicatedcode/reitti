/**
 * DateJumpParser — pure, DOM-free date expression parser for the type-to-jump
 * feature (see #1195). Consumes a context object built by date-jump.js from
 * window.userSettings.selectedLocale and window.locale (localized month names
 * are server-rendered — this parser never re-derives locale data).
 *
 * Grammar, in documented precedence:
 *   0. Range:            "side - side" (spaced hyphen) or "side to side"
 *                        (localized word, e.g. "bis") — each side parses as
 *                        any expression below; result spans both sides
 *                        (auto-swapped when side 2 is earlier).
 *   1. Relative words:   "today" / "yesterday" (localized via ctx.words)
 *   2. Month names:      "Aug" / "Aug." / "August" (localized short+full)
 *                        → month in the current year
 *      Month + year:     "Aug 2026" → explicit month
 *   3. Bare digits:      1–3 digits = incomplete (could grow to any form)
 *                        "2026" → year
 *   4. ISO date:         "2026-08-31"
 *   5. Separated numerics: '.' implies D.M.Y (European), '/' follows the
 *      locale ('en-US' → M/D/Y, otherwise D/M/Y):
 *        "31.08.2026" / "08/31/2026" → exact date (4-digit year required)
 *        "31.8" / "8.10." / "8/10"   → day-month in the current year
 *        "8/2026" / "8.2026"         → explicit month
 *
 * Ambiguity rule for two-part numeric input ("08/10"): the separator decides
 * the convention ('.' is always day-first), and for '/' the locale decides.
 *
 * Returns one of:
 *   {status:'ok', startDate:'YYYY-MM-DD', endDate:'YYYY-MM-DD'}   — Enter commits
 *   {status:'incomplete', alternatives:[{startDate,endDate},…]}   — prefix of something
 *   {status:'invalid', alternatives:[]}                           — unrecognized
 */
class DateJumpParser {

    static EN_MONTHS = [
        { short: 'jan', full: 'january' }, { short: 'feb', full: 'february' },
        { short: 'mar', full: 'march' }, { short: 'apr', full: 'april' },
        { short: 'may', full: 'may' }, { short: 'jun', full: 'june' },
        { short: 'jul', full: 'july' }, { short: 'aug', full: 'august' },
        { short: 'sep', full: 'september' }, { short: 'oct', full: 'october' },
        { short: 'nov', full: 'november' }, { short: 'dec', full: 'december' }
    ];

    static parse(buffer, ctx, impliedYear) {
        const context = ctx || {};
        const input = (buffer || '').trim();
        const now = new Date();
        if (!input) return DateJumpParser._incomplete([]);

        // 0. Range expressions: two expressions separated by a spaced hyphen
        //    or a localized "to" word. Both sides must resolve (close-ended);
        //    an incomplete side keeps the whole thing incomplete while typing.
        const rangeParts = DateJumpParser._splitRange(buffer, context);
        if (rangeParts) {
            let left = DateJumpParser.parse(rangeParts[0], context, impliedYear);
            let right = DateJumpParser.parse(rangeParts[1], context, impliedYear);
            if (left.status === 'invalid' || right.status === 'invalid') {
                return DateJumpParser._invalid();
            }
            if (left.status !== 'ok' || right.status !== 'ok') {
                return DateJumpParser._incomplete([].concat(left.alternatives || [], right.alternatives || []));
            }
            // Year inheritance: an explicit year on one side applies to a
            // year-less other side ("Aug - Okt 2025" → both 2025).
            const yearOf = (side) => parseInt(side.startDate.substring(0, 4), 10);
            if (left.yearExplicit === false && right.yearExplicit !== false) {
                left = DateJumpParser.parse(rangeParts[0], context, yearOf(right));
            } else if (right.yearExplicit === false && left.yearExplicit !== false) {
                right = DateJumpParser.parse(rangeParts[1], context, yearOf(left));
            }
            const startDate = left.startDate <= right.startDate ? left.startDate : right.startDate;
            const endDate = right.endDate >= left.endDate ? right.endDate : left.endDate;
            return DateJumpParser._ok(startDate, endDate);
        }

        const lower = input.toLowerCase();

        if (DateJumpParser._matchesWord(lower, context.words && context.words.today)) {
            const ymd = DateJumpParser._ymd(now);
            return DateJumpParser._ok(ymd, ymd);
        }
        if (DateJumpParser._matchesWord(lower, context.words && context.words.yesterday)) {
            const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
            const ymd = DateJumpParser._ymd(d);
            return DateJumpParser._ok(ymd, ymd);
        }

        const spaceParts = input.split(/\s+/);
        if (spaceParts.length === 2) {
            const month = DateJumpParser._matchMonth(spaceParts[0], context.months);
            if (!month) return DateJumpParser._invalid();
            const year = spaceParts[1];
            if (/^\d{4}$/.test(year)) {
                if (month.complete) return DateJumpParser._monthRange(month.index, parseInt(year, 10));
                return DateJumpParser._incomplete([DateJumpParser._monthRangeBestEffort(month.index, parseInt(year, 10))]);
            }
            if (/^\d{1,3}$/.test(year)) {
                return DateJumpParser._incomplete(
                    month.complete ? [DateJumpParser._monthRange(month.index, now.getFullYear())] : []);
            }
            return DateJumpParser._invalid();
        }
        if (spaceParts.length === 1) {
            const month = DateJumpParser._matchMonth(input, context.months);
            if (month) {
                if (month.complete) {
                    return DateJumpParser._monthRange(month.index, impliedYear || now.getFullYear(), false);
                }
                return DateJumpParser._incomplete(
                    [DateJumpParser._monthRange(month.index, impliedYear || now.getFullYear(), false)]);
            }
        }

        if (/^\d+$/.test(input)) {
            if (input.length <= 3) return DateJumpParser._incomplete([]);
            if (input.length === 4) return DateJumpParser._yearRange(parseInt(input, 10));
            return DateJumpParser._invalid();
        }

        const iso = input.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
        if (iso) {
            const ymd = DateJumpParser._validateYmd(parseInt(iso[1], 10), parseInt(iso[2], 10), parseInt(iso[3], 10));
            return ymd ? DateJumpParser._ok(ymd, ymd) : DateJumpParser._invalid();
        }

        if (input.includes('.') || input.includes('/')) {
            const sep = input.includes('.') ? '.' : '/';
            const segments = input.split(sep);
            const trailingEmpty = segments[segments.length - 1] === '';
            const strict = trailingEmpty ? segments.slice(0, -1) : segments;
            if (strict.some(s => !/^\d{1,4}$/.test(s))) return DateJumpParser._invalid();

            if (trailingEmpty && segments.length === 3) {
                return DateJumpParser._dayMonth(segments[0], segments[1], sep, context, now, impliedYear);
            }
            if (!trailingEmpty && segments.length === 2) {
                if (segments[1].length === 4) {
                    return DateJumpParser._monthRange(parseInt(segments[0], 10) - 1, parseInt(segments[1], 10));
                }
                return DateJumpParser._dayMonth(segments[0], segments[1], sep, context, now, impliedYear);
            }
            if (!trailingEmpty && segments.length === 3) {
                return DateJumpParser._fullDate(segments[0], segments[1], segments[2], sep, context);
            }
            return DateJumpParser._invalid();
        }

        return DateJumpParser._invalid();
    }

    /**
     * Splits a range expression into its two sides. Separators: a hyphen with
     * spaces on BOTH sides (so ISO dates never collide) or a localized "to"
     * word surrounded by spaces. Open-ended attempts ("date -" or "- date")
     * are recognized as ranges with one empty side so they stay incomplete
     * (close-ended policy) instead of degrading to invalid. Returns null when
     * the input is not a range attempt.
     */
    static _splitRange(rawInput, context) {
        const input = String(rawInput == null ? '' : rawInput);
        const separators = [' - '];
        const rangeWords = (context.words && context.words.range) || [];
        for (const word of rangeWords) {
            const w = String(word).trim().toLowerCase();
            if (w) separators.push(' ' + w + ' ');
        }
        for (const sep of separators) {
            const parts = input.split(sep);
            if (parts.length === 2) {
                return [parts[0].trim(), parts[1].trim()];
            }
        }
        if (input.startsWith('- ')) return ['', input.slice(2).trim()];
        if (input.endsWith(' -')) return [input.slice(0, -2).trim(), ''];
        return null;
    }

    static _matchesWord(lower, words) {
        return !!words && words.some(w => String(w).toLowerCase() === lower);
    }

    static _monthEntries(context) {
        if (context && context.months && context.months.length === 12) {
            return context.months.map(m => ({
                names: [String(m.short || '').toLowerCase().replace(/\.$/, ''), String(m.full || '').toLowerCase()]
            }));
        }
        return DateJumpParser.EN_MONTHS;
    }

    static _matchMonth(text, months) {
        const cleaned = String(text).toLowerCase().replace(/\.$/, '');
        if (!cleaned) return null;
        const entries = DateJumpParser._monthEntries({ months });
        for (let i = 0; i < entries.length; i++) {
            for (const name of entries[i].names) {
                if (name && name === cleaned) return { index: i, complete: true };
            }
        }
        for (let i = 0; i < entries.length; i++) {
            for (const name of entries[i].names) {
                if (name && name.startsWith(cleaned)) return { index: i, complete: false };
            }
        }
        return null;
    }

    /**
     * '.' always means day-first (European convention); for '/' the locale
     * decides ('en-US' is month-first, everything else day-first).
     */
    static _isDayFirst(sep, locale) {
        if (sep === '.') return true;
        return String(locale || '').toLowerCase() !== 'en-us';
    }

    static _dayMonth(a, b, sep, context, now, impliedYear) {
        const dayFirst = DateJumpParser._isDayFirst(sep, context.locale);
        const day = parseInt(dayFirst ? a : b, 10);
        const month = parseInt(dayFirst ? b : a, 10);
        const ymd = DateJumpParser._validateYmd(impliedYear || now.getFullYear(), month, day);
        return ymd ? DateJumpParser._ok(ymd, ymd, false) : DateJumpParser._invalid();
    }

    static _fullDate(a, b, c, sep, context) {
        if (c.length !== 4) return DateJumpParser._invalid();
        const dayFirst = DateJumpParser._isDayFirst(sep, context.locale);
        const day = parseInt(dayFirst ? a : b, 10);
        const month = parseInt(dayFirst ? b : a, 10);
        const ymd = DateJumpParser._validateYmd(parseInt(c, 10), month, day);
        return ymd ? DateJumpParser._ok(ymd, ymd) : DateJumpParser._invalid();
    }

    static _validateYmd(year, month, day) {
        if (!(month >= 1 && month <= 12) || !(day >= 1 && day <= 31)) return null;
        const dt = new Date(year, month - 1, day);
        if (dt.getFullYear() !== year || dt.getMonth() !== month - 1 || dt.getDate() !== day) return null;
        return DateJumpParser._ymd(dt);
    }

    static _monthRange(monthIndex0, year, yearExplicit = true) {
        const start = new Date(year, monthIndex0, 1);
        const end = new Date(year, monthIndex0 + 1, 0);
        return DateJumpParser._ok(DateJumpParser._ymd(start), DateJumpParser._ymd(end), yearExplicit);
    }

    static _monthRangeBestEffort(monthIndex0, year) {
        try {
            return DateJumpParser._monthRange(monthIndex0, year);
        } catch (_) {
            return { startDate: null, endDate: null };
        }
    }

    static _yearRange(year) {
        if (year < 1 || year > 9999) return DateJumpParser._invalid();
        return DateJumpParser._ok(`${year}-01-01`, `${year}-12-31`);
    }

    static _ymd(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    }

    /**
     * `yearExplicit` marks whether the INPUT contained a year (or is inherently
     * pinned, like "today"); range parsing uses it to inherit an explicit year
     * from one side into a year-less other side.
     */
    static _ok(startDate, endDate, yearExplicit = true) {
        return { status: 'ok', startDate, endDate, alternatives: [], yearExplicit };
    }

    static _incomplete(alternatives) {
        return { status: 'incomplete', alternatives: alternatives || [] };
    }

    static _invalid() {
        return { status: 'invalid', alternatives: [] };
    }
}

/* Exports */

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { DateJumpParser };
} else if (typeof window !== 'undefined') {
    window.DateJumpParser = DateJumpParser;
}
