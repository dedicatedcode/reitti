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
        await page.goto('/?start-date=2018-12-30')
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
        await page.goto('/?start-date=2018-12-31&end-date=2019-01-01')
        await expect(page.locator('.date-day.range-start')).toBeVisible();
        await expect(page.locator('.date-day.range-start .day-number')).toHaveText('31');
        await expect(page.locator('.date-day.range-start .month-year')).toHaveText('Dec 2018');
        await expect(page.locator('.date-day.range-end .day-number')).toHaveText('1');
        await expect(page.locator('.date-day.range-end .month-year')).toHaveText('Jan 2019');
    });

});