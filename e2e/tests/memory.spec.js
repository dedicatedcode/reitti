import {expect, test} from '@playwright/test';

test.describe('Memory Tests', () => {
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

        await page.waitForLoadState('networkidle');
    });

    test('Should create Memory', async ({page}) => {
        await page.goto('/')
        await page.locator('#nav-memories').click();
        await expect(page.getByRole('button', {name: 'Create Memory'})).toBeVisible();
        await page.getByRole('button',  { name: 'Create Memory' }).click();
        await page.getByRole('textbox', { name: 'Title *' }).click();
        await page.getByRole('textbox', { name: 'Title *' }).fill('Test Memory');
        await page.getByRole('textbox', { name: 'Description' }).click();
        await page.getByRole('textbox', { name: 'Description' }).fill('One fine description');
        await page.getByRole('textbox', { name: 'Start Date *' }).fill('2017-03-22');
        await page.getByRole('textbox', { name: 'End Date *' }).fill('2017-03-23');
        await page.getByRole('button', { name: 'Create Memory' }).click();
        await expect(page.getByText('Test Memory')).toBeVisible();
        await page.getByText('One fine description').click();
        await page.getByText('March 21, 2017 - March 23, 2017').click();
    });

    test('Should edit Memory', async ({page}) => {
        await page.goto('/')
        await page.locator('#nav-memories').click();
        await expect(page.getByRole('button', {name: 'Create Memory'})).toBeVisible();
        await page.getByRole('button', { name: 'Create Memory' }).click();
        await page.getByRole('textbox', { name: 'Title *' }).click();
        await page.getByRole('textbox', { name: 'Title *' }).fill('Test');
        await page.getByRole('textbox', { name: 'Title *' }).press('Tab');
        await page.getByRole('textbox', { name: 'Description' }).press('Tab');
        await page.getByRole('textbox', { name: 'Start Date *' }).fill('2017-03-23');
        await page.getByRole('textbox', { name: 'Start Date *' }).press('Tab');
        await page.getByRole('textbox', { name: 'Start Date *' }).press('Tab');
        await page.getByRole('textbox', { name: 'End Date *' }).fill('2017-03-24');
        await page.getByRole('button', { name: 'Create Memory' }).click();
        await page.getByRole('button', { name: 'Edit' }).click();
        await expect(page.getByRole('button', { name: 'Save Changes' })).toBeVisible();
        await expect(page.getByRole('link', { name: 'Cancel' })).toBeVisible();
        await expect(page.getByRole('textbox', { name: 'Title *' })).toHaveValue('Test');
        await expect(page.getByRole('textbox', { name: 'Start Date *' })).toHaveValue('2017-03-22');
        await expect(page.getByRole('textbox', { name: 'End Date *' })).toHaveValue('2017-03-24');
        await expect(page.getByRole('radio', { name: 'Map' })).toBeChecked();
        await expect(page.getByRole('radio', { name: 'Image' })).not.toBeChecked();
        await page.getByText('Image', { exact: true }).click();
    });

});