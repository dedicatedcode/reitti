import {expect, test} from '@playwright/test';

test.use({
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
});

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

    test('should load correct today when in a different timezone', async ({page}) => {
        await page.clock.setFixedTime(new Date('2017-02-02T00:30:00'));

        await page.getByRole('button', { name: 'Today' }).click()
        await expect(page.locator('div').filter({ hasText: 'Thu2Feb' }).nth(2)).toContainClass('selected');
        await expect(page).toHaveURL(/startDate=2017-02-02&endDate=2017-02-02/);
    })

    test('should load correct date when in a different timezone at start of day', async ({page}) => {
        await page.clock.setFixedTime(new Date('2017-02-02T00:30:00'));

        await page.getByText('Wed20Dec').click()
        await expect(page.locator('div').filter({ hasText: 'Wed20Dec' }).nth(2)).toContainClass('selected');
        await expect(page.getByText('Dec. 19 17:28 - 10:')).toBeVisible();
        await expect(page).toHaveURL(/startDate=2017-12-20&endDate=2017-12-20/);
    })

    test('should load correct date when in a different timezone at end of day', async ({page}) => {
        await page.clock.setFixedTime(new Date('2017-02-02T23:30:50'));

        await page.getByText('Wed20Dec').click()
        await expect(page.locator('div').filter({ hasText: 'Wed20Dec' }).nth(2)).toContainClass('selected');
        await expect(page.getByText('Dec. 19 17:28 - 10:')).toBeVisible();
        await expect(page).toHaveURL(/startDate=2017-12-20&endDate=2017-12-20/);
    })
});
