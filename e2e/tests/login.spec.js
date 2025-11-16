import {expect, test} from '@playwright/test';

test.describe('Login Page Tests', () => {
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
    });

    test('should load Login Page', async ({page}) => {
        // Check if the page loads with the expected title
        await expect(page).toHaveTitle('Reitti - Login');

        // Check if the main container is present
        await expect(page.locator('#username')).toBeVisible();
        await expect(page.locator('#password')).toBeVisible();


        await page.locator('#username').fill('admin');
        await page.locator('#password').fill('admin');

        await page.locator('button:has-text("Login")').click();

        await page.waitForLoadState('networkidle');
        await expect(page.locator('.navbar .nav-link.active')).toBeVisible();
    });
});
