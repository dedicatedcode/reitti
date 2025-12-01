import {expect, test} from '@playwright/test';

test.describe('Date Picker Tests', () => {
    test.beforeEach(async ({page}) => {
        await page.goto('/');
        await page.waitForLoadState('networkidle');

        // Wait for fonts to load to ensure consistent rendering
        await page.waitForFunction(() => {
            return document.fonts.ready;
        });

        if (await page.title() === 'Setup - Reitti') {
            await page.locator('#password').fill('admin')
                .then(() => page.keyboard.press('Enter'));
        }
        await page.waitForLoadState('networkidle');
        await expect(page).toHaveTitle('Reitti - Login');

        // Check if the main container is present
        await expect(page.locator('#username')).toBeVisible();
        await expect(page.locator('#password')).toBeVisible();


        await page.locator('#username').fill('admin');
        await page.locator('#password').fill('admin');

        await page.locator('button:has-text("Login")').click();

        await page.waitForNavigation();
    });

    test('should select single date when startDate is given', async ({page}) => {
        await page.goto('/?startDate=2018-12-30')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        await expect(page.locator('.date-day.range-start .day-number')).toHaveText('30');
        await expect(page.locator('.date-day.range-start .month-year')).toHaveText('Dec 2018');
    });

    test('should select latest date when no date is given', async ({page}) => {
        await page.goto('/')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        await expect(page.locator('.date-day.range-start .day-number')).toHaveText('31');
        await expect(page.locator('.date-day.range-start .month-year')).toHaveText('Dec 2017');
    });

    test('should select date range', async ({page}) => {
        await page.goto('/?startDate=2018-12-31&endDate=2019-01-01')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        await expect(page.locator('.date-day.range-start .day-number')).toHaveText('31');
        await expect(page.locator('.date-day.range-start .month-year')).toHaveText('Dec 2018');
        await expect(page.locator('.date-day.range-end .day-number')).toHaveText('1');
        await expect(page.locator('.date-day.range-end .month-year')).toHaveText('Jan 2019');
    });


    test('end date for a day-selection should be the start day', async ({page}) => {
        await page.goto('/')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        await page.getByText('Fri29Dec').click();
        await expect(page).toHaveURL(/startDate=2017-12-29&endDate=2017-12-29/);
    });

    test('end date for a month-selection should be the last day of the month', async ({page}) => {
        await page.goto('/')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        // Move the mouse over the date picker
        await page.locator('#date-picker-container').hover();
        // Scroll one tick up to switch to month range
        await page.mouse.wheel(0, 100);
        // Select September 2017
        await page.getByText('2017Sep').click();
        await expect(page).toHaveURL(/startDate=2017-09-01&endDate=2017-09-30/);
    });

    test('end date for a year-selection should be the last day of the year', async ({page}) => {
        await page.goto('/')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        // Move the mouse over the date picker
        await page.locator('#date-picker-container').hover();
        // Scroll one tick up to switch to month range
        await page.mouse.wheel(0, 100);
        await expect(page.getByText('2017Sep')).toBeVisible();
        await page.waitForTimeout(1000);
        await page.locator('#date-picker-container').hover();

        await page.mouse.wheel(0, 100);
        await expect(page.getByText('2017Sep')).not.toBeVisible();

        // Select September 2017
        await page.locator('div').filter({ hasText: '2016' }).nth(2).click();
        await expect(page.locator('div').filter({ hasText: '2016' }).nth(2)).toContainClass('selected');
        await expect(page).toHaveURL(/startDate=2016-01-01&endDate=2016-12-31/);
    });

    test('should switch to auto-update mode', async ({page}) => {
        await page.goto('/')
        await page.getByTitle('Enter Auto-Update Mode', { exact: true }).click();
        await expect(page.locator('#auto-update-overlay')).toBeVisible();
        await expect(page.getByText('Sun31Dec')).not.toBeVisible();
        await expect(page.getByTitle('Leave Auto-Update Mode')).toBeVisible();
        await page.getByTitle('Leave Auto-Update Mode').click();
        await expect(page.locator('#auto-update-overlay')).not.toBeVisible();
        await expect(page.getByTitle('Leave Auto-Update Mode')).not.toBeVisible();

        await expect(page).toHaveURL(/startDate=2017-12-31&endDate=2017-12-31/);
    });



});
