import {expect, test} from '@playwright/test';

test.describe('Settings - Transportation Mode Tests', () => {
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

    test('should add and delete transportation mode', async ({page}) => {
        await page.locator('#nav-settings').click();
        await page.getByRole('link', { name: 'Transportation Modes' }).click();
        await page.getByLabel('Transportation Mode').selectOption('AIRPLANE');
        await page.getByRole('spinbutton', { name: 'Max Speed (km/h)' }).click();
        await page.getByRole('spinbutton', { name: 'Max Speed (km/h)' }).fill('1000');
        await page.getByRole('button', { name: 'Add Mode' }).click();
        await expect(page.getByRole('cell', { name: 'Airplane' })).toBeVisible();
        await expect(page.getByRole('cell', { name: '1000.0' }).getByPlaceholder('No limit')).toHaveValue('1000.0');
        await expect(page.locator('div').filter({ hasText: 'Transportation mode added' }).nth(4)).toBeVisible();
        page.once('dialog', dialog => {
            console.log(`Dialog message: ${dialog.message()}`);
            dialog.accept().catch(() => {});
        });
        await page.getByRole('row', { name: 'Airplane 1000.0 Delete' }).getByRole('button').click();
        await expect(page.locator('#transportation-modes')).toContainText('Transportation mode deleted successfully');

    });

});