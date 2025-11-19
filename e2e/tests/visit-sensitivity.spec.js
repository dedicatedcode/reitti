import {expect, test} from '@playwright/test';

test.describe('Visit Sensitivity Tests', () => {
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

    test('should not duplicate edit form', async ({page}) => {
        await page.locator('#nav-settings').click();
        await page.getByRole('link', {name: 'Visit Sensitivity'}).click();
        await page.getByRole('button', {name: 'Edit'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await page.getByRole('button', {name: 'Simple'}).click();
        await page.getByRole('button', {name: 'Edit'}).click();
        await expect(page.locator('#edit-form')).toBeVisible(); //this will fail if the edit form is duplicated
    });

    test('should display simple edit form', async ({page}) => {
        await page.locator('#nav-settings').click();
        await page.getByRole('link', {name: 'Visit Sensitivity'}).click();
        await page.getByRole('button', {name: 'Edit'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await page.getByRole('button', {name: 'Simple'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
    });

    test('should display advanced edit form', async ({page}) => {
        await page.locator('#nav-settings').click();
        await page.getByRole('link', {name: 'Visit Sensitivity'}).click();
        await page.getByRole('button', {name: 'Edit'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await page.getByRole('button', {name: 'Advanced'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await expect(page.getByText('Visit Detection')).toBeVisible();
    });

    test('should display recalculation advise', async ({page}) => {
        await page.locator('#nav-settings').click();
        await page.getByRole('link', {name: 'Visit Sensitivity'}).click();
        await page.getByRole('button', {name: 'Edit'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await page.getByRole('button', {name: 'Advanced'}).click();
        await expect(page.locator('#edit-form')).toBeVisible();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.locator('#visit-sensitivity-section')).toContainText('Recalculation Advised');
        await page.getByRole('button', { name: 'Dismiss' }).click();
        await expect(page.locator('#visit-sensitivity-section')).toContainText('Recalculation advice dismissed.');
    });
});