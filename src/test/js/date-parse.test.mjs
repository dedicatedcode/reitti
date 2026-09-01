/**
 * Standalone unit tests for DateJumpParser (no build step, no DOM needed).
 * Run with:  node src/test/js/date-parse.test.mjs
 */
import { createRequire } from 'module';
import assert from 'assert';

const require = createRequire(import.meta.url);
const { DateJumpParser } = require('../../main/resources/static/js/date-parse.js');

const DE_MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const EN_MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

const ctx = (locale) => ({
    locale,
    months: (locale && locale.startsWith('de') ? DE_MONTHS : EN_MONTHS).map(full => ({
        short: full.substring(0, 3), full
    })),
    words: { today: ['today', 'heute'], yesterday: ['yesterday', 'gestern'], range: ['to', 'bis'] }
});

const YEAR = new Date().getFullYear();

let passed = 0;
function test(name, fn) {
    try {
        fn();
        passed++;
    } catch (err) {
        console.error(`FAIL: ${name}\n  ${err.message}`);
        process.exitCode = 1;
    }
}

const ok = (r, start, end) => {
    assert.strictEqual(r.status, 'ok', `expected ok, got ${JSON.stringify(r)}`);
    assert.strictEqual(r.startDate, start);
    assert.strictEqual(r.endDate, end);
};
const incomplete = (r) => assert.strictEqual(r.status, 'incomplete', `expected incomplete, got ${JSON.stringify(r)}`);
const invalid = (r) => assert.strictEqual(r.status, 'invalid', `expected invalid, got ${JSON.stringify(r)}`);

// --- ISO dates ---
test('ISO exact date', () => ok(DateJumpParser.parse('2026-08-31', ctx('en-US')), '2026-08-31', '2026-08-31'));
test('ISO single digits', () => ok(DateJumpParser.parse('2026-3-5', ctx('en-US')), '2026-03-05', '2026-03-05'));

// --- Dotted (day-first) numeric dates ---
test('D.M.Y full date', () => ok(DateJumpParser.parse('31.08.2026', ctx('de-DE')), '2026-08-31', '2026-08-31'));
test('D.M.Y short month', () => ok(DateJumpParser.parse('31.8.2026', ctx('de-DE')), '2026-08-31', '2026-08-31'));
test('D.M. trailing separator', () => ok(DateJumpParser.parse('31.8.', ctx('de-DE')), `${YEAR}-08-31`, `${YEAR}-08-31`));
test('D.M two parts', () => ok(DateJumpParser.parse('31.8', ctx('de-DE')), `${YEAR}-08-31`, `${YEAR}-08-31`));
test('invalid calendar day 31.02', () => invalid(DateJumpParser.parse('31.02.2026', ctx('de-DE'))));
test('invalid month 13', () => invalid(DateJumpParser.parse('13.13.2026', ctx('de-DE'))));
test('leap year 29.02.2024', () => ok(DateJumpParser.parse('29.02.2024', ctx('de-DE')), '2024-02-29', '2024-02-29'));
test('non-leap year 29.02.2025', () => invalid(DateJumpParser.parse('29.02.2025', ctx('de-DE'))));
test('month range dotted', () => ok(DateJumpParser.parse('8.2026', ctx('de-DE')), '2026-08-01', '2026-08-31'));

// --- Slashed dates, locale-aware ---
test('US M/D/Y', () => ok(DateJumpParser.parse('08/31/2026', ctx('en-US')), '2026-08-31', '2026-08-31'));
test('GB D/M/Y', () => ok(DateJumpParser.parse('31/08/2026', ctx('en-GB')), '2026-08-31', '2026-08-31'));
test('08/10 ambiguity en-US is Aug 10', () => ok(DateJumpParser.parse('08/10', ctx('en-US')), `${YEAR}-08-10`, `${YEAR}-08-10`));
test('08/10 ambiguity en-GB is Oct 8', () => ok(DateJumpParser.parse('08/10', ctx('en-GB')), `${YEAR}-10-08`, `${YEAR}-10-08`));
test('M/YYYY slashed', () => ok(DateJumpParser.parse('8/2026', ctx('en-US')), '2026-08-01', '2026-08-31'));

// --- Month names ---
test('short month with dot', () => ok(DateJumpParser.parse('Aug.', ctx('en-US')), `${YEAR}-08-01`, `${YEAR}-08-31`));
test('short month', () => ok(DateJumpParser.parse('Aug', ctx('en-US')), `${YEAR}-08-01`, `${YEAR}-08-31`));
test('full month', () => ok(DateJumpParser.parse('August', ctx('en-US')), `${YEAR}-08-01`, `${YEAR}-08-31`));
test('localized full month de', () => ok(DateJumpParser.parse('August', ctx('de-DE')), `${YEAR}-08-01`, `${YEAR}-08-31`));
test('localized short prefix', () => ok(DateJumpParser.parse('Mär', ctx('de-DE')), `${YEAR}-03-01`, `${YEAR}-03-31`));
test('month name prefix incomplete', () => incomplete(DateJumpParser.parse('augu', ctx('en-US'))));
test('month + year', () => ok(DateJumpParser.parse('Aug 2026', ctx('en-US')), '2026-08-01', '2026-08-31'));
test('month + partial year incomplete', () => incomplete(DateJumpParser.parse('Aug 20', ctx('en-US'))));

// --- Bare digits ---
test('bare year', () => ok(DateJumpParser.parse('2026', ctx('en-US')), '2026-01-01', '2026-12-31'));
test('1-3 digits incomplete', () => incomplete(DateJumpParser.parse('202', ctx('en-US'))));
test('5 digits invalid', () => invalid(DateJumpParser.parse('20265', ctx('en-US'))));

// --- Relative words ---
test('today', () => {
    const t = DateJumpParser._ymd(new Date());
    ok(DateJumpParser.parse('today', ctx('en-US')), t, t);
});
test('localized heute', () => {
    const t = DateJumpParser._ymd(new Date());
    ok(DateJumpParser.parse('heute', ctx('de-DE')), t, t);
});
test('yesterday', () => {
    const y = new Date();
    y.setDate(y.getDate() - 1);
    const t = DateJumpParser._ymd(y);
    ok(DateJumpParser.parse('yesterday', ctx('en-US')), t, t);
});

// --- Garbage / empty ---
test('empty incomplete', () => incomplete(DateJumpParser.parse('', ctx('en-US'))));
test('garbage invalid', () => invalid(DateJumpParser.parse('hello world!', ctx('en-US'))));
test('whitespace incomplete', () => incomplete(DateJumpParser.parse('   ', ctx('en-US'))));
test('garbage segments invalid', () => invalid(DateJumpParser.parse('12.ab.34', ctx('en-US'))));

// --- Range expressions ---
test('date range with spaced hyphen', () =>
    ok(DateJumpParser.parse('01.03.2025 - 04.04.2025', ctx('de-DE')), '2025-03-01', '2025-04-04'));
test('ISO date range', () =>
    ok(DateJumpParser.parse('2025-03-01 - 2025-04-04', ctx('en-US')), '2025-03-01', '2025-04-04'));
test('date range with word separator', () =>
    ok(DateJumpParser.parse('01.03.2025 to 04.04.2025', ctx('en-US')), '2025-03-01', '2025-04-04'));
test('date range with german bis', () =>
    ok(DateJumpParser.parse('01.03.2025 bis 04.04.2025', ctx('de-DE')), '2025-03-01', '2025-04-04'));
test('swapped range auto-swaps', () =>
    ok(DateJumpParser.parse('04.04.2025 - 01.03.2025', ctx('de-DE')), '2025-03-01', '2025-04-04'));
test('year to year', () =>
    ok(DateJumpParser.parse('2025 - 2026', ctx('en-US')), '2025-01-01', '2026-12-31'));
test('month to month', () =>
    ok(DateJumpParser.parse('Mar - Jun', ctx('en-US')), `${YEAR}-03-01`, `${YEAR}-06-30`));
test('month to month with year', () =>
    ok(DateJumpParser.parse('Aug - Oct 2026', ctx('en-US')), '2026-08-01', '2026-10-31'));
test('incomplete second side stays incomplete', () =>
    incomplete(DateJumpParser.parse('01.03.2025 - ', ctx('de-DE'))));
test('incomplete first side stays incomplete', () =>
    incomplete(DateJumpParser.parse('- 04.04.2025', ctx('de-DE'))));
test('three parts invalid', () =>
    invalid(DateJumpParser.parse('01.03.2025 - 02.03.2025 - 03.03.2025', ctx('de-DE'))));
test('ISO dates not broken by range check', () =>
    ok(DateJumpParser.parse('2026-08-31', ctx('en-US')), '2026-08-31', '2026-08-31'));
test('year-less side inherits explicit year from other side', () =>
    ok(DateJumpParser.parse('Aug - Okt 2025', ctx('de-DE')), '2025-08-01', '2025-10-31'));
test('year-less day-month inherits explicit year', () =>
    ok(DateJumpParser.parse('01.03. - 04.04.2025', ctx('de-DE')), '2025-03-01', '2025-04-04'));
test('explicit year on left applies to year-less right', () =>
    ok(DateJumpParser.parse('01.03.2025 - 04.04.', ctx('de-DE')), '2025-03-01', '2025-04-04'));
test('both sides year-less keep current year', () =>
    ok(DateJumpParser.parse('Mar - Jun', ctx('en-US')), `${YEAR}-03-01`, `${YEAR}-06-30`));

// eslint-disable-next-line no-console
console.log(`${passed} parser tests passed${process.exitCode ? ' (with failures)' : ''}`);
