class MapStyleSettingsPage {
    constructor(root) {
        this.root = root;
        this.editingMapStyleId = null;

        this.activeStyleSelect = this.root.querySelector('#settings-map-style-select');
        this.list = this.root.querySelector('#custom-map-style-list');
        this.form = this.root.querySelector('#custom-map-style-form');
        this.formTitle = this.root.querySelector('#custom-map-style-form-title');
        this.error = this.root.querySelector('#custom-map-style-error');

        this.setupListeners();
        this.refresh();
        this.syncFormMode();
    }

    setupListeners() {
        this.activeStyleSelect.addEventListener('change', (event) => {
            this.setActiveStyle(event.target.value);
        });

        this.root.querySelector('#add-map-style-btn').addEventListener('click', () => {
            this.openForm();
        });

        this.root.querySelector('#cancel-map-style-btn').addEventListener('click', () => {
            this.closeForm();
        });

        this.root.querySelectorAll('input[name="custom-map-style-map-type"], input[name="custom-map-style-style-input"], input[name="custom-map-style-raster-input"]')
            .forEach(input => input.addEventListener('change', () => this.syncFormMode()));

        this.form.addEventListener('submit', (event) => {
            event.preventDefault();
            this.saveForm();
        });

        this.list.addEventListener('click', (event) => {
            const button = event.target.closest('[data-map-style-action]');
            if (!button) return;

            const action = button.dataset.mapStyleAction;
            const styleId = button.dataset.mapStyleId;
            if (action === 'edit') {
                const style = MapRenderer.getCustomMapStyles().find(item => item.id === styleId);
                if (style) {
                    this.openForm(style);
                }
            } else if (action === 'delete') {
                this.deleteStyle(styleId);
            }
        });
    }

    refresh() {
        this.refreshActiveStyleSelect();
        this.renderCustomStyleList();
    }

    refreshActiveStyleSelect() {
        const styles = MapRenderer.getMapStyles();
        const storedStyleId = MapRenderer.getActiveMapStyleId();
        const selectedStyleId = styles.some(style => style.id === storedStyleId)
            ? storedStyleId
            : MapRenderer.getDefaultMapStyleId();

        this.activeStyleSelect.innerHTML = styles
            .map(style => `<option value="${this.escapeHtml(style.id)}"${style.id === selectedStyleId ? ' selected' : ''}>${this.escapeHtml(style.label)}</option>`)
            .join('');
        window.reittiActiveMapStyleId = selectedStyleId;
    }

    renderCustomStyleList() {
        const styles = MapRenderer.getCustomMapStyles().filter(style => style.editable !== false);
        if (!styles.length) {
            this.list.innerHTML = `<div class="custom-map-style-empty">${t('map.settings.dialog.map-styles.empty')}</div>`;
            return;
        }

        this.list.innerHTML = styles.map(style => {
            const typeLabel = style.mapType === 'raster'
                ? t('map.settings.dialog.map-styles.map-type.raster')
                : t('map.settings.dialog.map-styles.map-type.vector');
            const inputLabel = style.mapType === 'raster'
                ? (style.rasterSourceInputType === 'tilejson'
                    ? t('map.settings.dialog.map-styles.source-input.tilejson')
                    : t('map.settings.dialog.map-styles.source-input.tile-template'))
                : (style.styleInputType === 'json'
                    ? t('map.settings.dialog.map-styles.style-input.json')
                    : t('map.settings.dialog.map-styles.style-input.url'));
            const sharedLabel = style.shared ? ` · ${this.escapeHtml(t('map.settings.dialog.map-styles.shared-badge'))}` : '';

            return `
                <div class="custom-map-style-item">
                    <div class="custom-map-style-item-main">
                        <div class="custom-map-style-item-name">${this.escapeHtml(style.label)}</div>
                        <div class="custom-map-style-item-meta">${this.escapeHtml(typeLabel)} · ${this.escapeHtml(inputLabel)}${sharedLabel}</div>
                    </div>
                    <div class="custom-map-style-item-actions">
                        <button type="button" class="btn settings-icon-btn" data-map-style-action="edit" data-map-style-id="${this.escapeHtml(style.id)}" title="${t('map.settings.dialog.map-styles.edit')}">
                            <i class="lni lni-pencil-1"></i>
                        </button>
                        <button type="button" class="btn settings-icon-btn danger" data-map-style-action="delete" data-map-style-id="${this.escapeHtml(style.id)}" title="${t('map.settings.dialog.map-styles.remove')}">
                            <i class="lni lni-trash-3"></i>
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    openForm(style = null) {
        this.editingMapStyleId = style?.id || null;
        this.setError('');
        this.form.reset();

        this.formTitle.textContent = style
            ? t('map.settings.dialog.map-styles.edit-title')
            : t('map.settings.dialog.map-styles.add-title');

        const mapType = style?.mapType || 'vector';
        const styleInputType = style?.styleInputType || (String(style?.styleInput || '').trim().startsWith('{') ? 'json' : 'url');
        const rasterSourceInputType = style?.rasterSourceInputType || (style?.dataSource?.tileJsonUrl ? 'tilejson' : 'tile_template');

        this.setRadioValue('custom-map-style-map-type', mapType);
        this.setRadioValue('custom-map-style-style-input', styleInputType);
        this.setRadioValue('custom-map-style-raster-input', rasterSourceInputType);

        this.root.querySelector('#custom-map-style-name').value = style?.label || '';
        const sharedInput = this.root.querySelector('#custom-map-style-shared');
        if (sharedInput) {
            sharedInput.checked = !!style?.shared;
        }
        this.root.querySelector('#custom-map-style-url').value = mapType === 'vector' && styleInputType === 'url' ? (style?.styleInput || style?.styleUrl || '') : '';
        this.root.querySelector('#custom-map-style-json').value = mapType === 'vector' && styleInputType === 'json' ? (style?.styleInput || '') : '';
        this.root.querySelector('#custom-map-style-tilejson-url').value = style?.dataSource?.tileJsonUrl || '';
        this.root.querySelector('#custom-map-style-tile-template').value = style?.dataSource?.tileUrlTemplate || '';
        this.root.querySelector('#custom-map-style-attribution').value = rasterSourceInputType === 'tile_template' ? (style?.dataSource?.attribution || '') : '';
        this.root.querySelector('#custom-map-style-raster-attribution-override').value = rasterSourceInputType === 'tilejson' ? (style?.dataSource?.attribution || '') : '';
        this.root.querySelector('#custom-map-style-minzoom').value = style?.dataSource?.minzoom ?? '';
        this.root.querySelector('#custom-map-style-maxzoom').value = style?.dataSource?.maxzoom ?? '';
        this.root.querySelector('#custom-map-style-tile-size').value = style?.dataSource?.tileSize ?? '';
        this.root.querySelector('#custom-map-style-scheme').value = style?.dataSource?.scheme || '';

        const options = style?.vectorOptions || {};
        this.root.querySelector('#custom-map-style-attribution-override').value = options.attributionOverride || '';
        this.root.querySelector('#custom-map-style-glyphs-url').value = options.glyphsUrlOverride || '';
        this.root.querySelector('#custom-map-style-sprite-url').value = options.spriteUrlOverride || '';

        this.syncFormMode();
        this.form.classList.remove('hidden');
        this.root.querySelector('#custom-map-style-name').focus();
    }

    closeForm() {
        this.editingMapStyleId = null;
        this.setError('');
        this.form.reset();
        this.syncFormMode();
        this.form.classList.add('hidden');
    }

    syncFormMode() {
        const mapType = this.radioValue('custom-map-style-map-type') || 'vector';
        const styleInputType = this.radioValue('custom-map-style-style-input') || 'url';
        const rasterSourceInputType = this.radioValue('custom-map-style-raster-input') || 'tile_template';

        this.root.querySelectorAll('[data-map-style-panel]').forEach(panel => {
            panel.classList.toggle('hidden', panel.dataset.mapStylePanel !== mapType);
        });
        this.root.querySelector('#custom-map-style-url').closest('.form-group').classList.toggle('hidden', styleInputType !== 'url');
        this.root.querySelector('[data-style-json-group]').classList.toggle('hidden', styleInputType !== 'json');
        this.root.querySelectorAll('[data-raster-template-group]').forEach(group => {
            group.classList.toggle('hidden', rasterSourceInputType !== 'tile_template');
        });
        this.root.querySelectorAll('[data-raster-tilejson-group]').forEach(group => {
            group.classList.toggle('hidden', rasterSourceInputType !== 'tilejson');
        });
    }

    async saveForm() {
        const payload = this.buildPayload();
        const validationError = this.validatePayload(payload);
        if (validationError) {
            this.setError(validationError);
            return;
        }

        try {
            const response = await fetch(`${window.contextPath || ''}/settings/map-styles/api`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || t('map.settings.dialog.map-styles.error-save-status', [response.status]));
            }
            this.applySettings(await response.json());
            this.closeForm();
            this.refresh();
        } catch (error) {
            console.warn('Unable to save custom map style:', error);
            this.setError(error.message || t('map.settings.dialog.map-styles.error-save'));
        }
    }

    buildPayload() {
        const mapType = this.radioValue('custom-map-style-map-type') || 'vector';
        const styleInputType = this.radioValue('custom-map-style-style-input') || 'url';
        const rasterSourceInputType = this.radioValue('custom-map-style-raster-input') || 'tile_template';
        const rasterAttribution = rasterSourceInputType === 'tilejson'
            ? this.value('#custom-map-style-raster-attribution-override')
            : this.value('#custom-map-style-attribution');

        return {
            id: this.editingMapStyleId,
            label: this.value('#custom-map-style-name'),
            mapType,
            styleInputType,
            rasterSourceInputType,
            shared: !!window.reittiIsAdmin && !!this.root.querySelector('#custom-map-style-shared')?.checked,
            styleInput: mapType === 'vector'
                ? (styleInputType === 'json' ? this.value('#custom-map-style-json') : this.value('#custom-map-style-url'))
                : '',
            dataSource: {
                type: mapType === 'raster' ? 'raster' : 'vector',
                tileJsonUrl: mapType === 'raster' && rasterSourceInputType === 'tilejson' ? this.value('#custom-map-style-tilejson-url') : '',
                tileUrlTemplate: mapType === 'raster' && rasterSourceInputType === 'tile_template' ? this.value('#custom-map-style-tile-template') : '',
                attribution: mapType === 'raster' ? rasterAttribution : '',
                minzoom: mapType === 'raster' ? this.optionalNumberValue('#custom-map-style-minzoom') : null,
                maxzoom: mapType === 'raster' ? this.optionalNumberValue('#custom-map-style-maxzoom') : null,
                tileSize: mapType === 'raster' ? this.optionalNumberValue('#custom-map-style-tile-size') : null,
                scheme: mapType === 'raster' ? this.value('#custom-map-style-scheme') : ''
            },
            vectorOptions: {
                attributionOverride: mapType === 'vector' ? this.value('#custom-map-style-attribution-override') : '',
                glyphsUrlOverride: mapType === 'vector' ? this.value('#custom-map-style-glyphs-url') : '',
                spriteUrlOverride: mapType === 'vector' ? this.value('#custom-map-style-sprite-url') : ''
            }
        };
    }

    validatePayload(payload) {
        if (!payload.label) {
            return t('map.settings.dialog.map-styles.error-name-required');
        }
        if (!['vector', 'raster'].includes(payload.mapType)) {
            return t('map.settings.dialog.map-styles.error-map-type');
        }

        if (payload.mapType === 'vector') {
            return this.validateVectorPayload(payload);
        }
        return this.validateRasterPayload(payload);
    }

    validateVectorPayload(payload) {
        if (payload.styleInputType === 'url' && !payload.styleInput) {
            return t('map.settings.dialog.map-styles.error-style-url-required');
        }
        if (payload.styleInputType === 'json') {
            if (!payload.styleInput) {
                return t('map.settings.dialog.map-styles.error-style-json-required');
            }
            try {
                JSON.parse(payload.styleInput);
            } catch (error) {
                return t('map.settings.dialog.map-styles.error-json');
            }
        }

        return '';
    }

    validateRasterPayload(payload) {
        const source = payload.dataSource;
        if (payload.rasterSourceInputType === 'tile_template') {
            if (!source.tileUrlTemplate) {
                return t('map.settings.dialog.map-styles.error-tile-template-required');
            }
            if (!['{z}', '{x}', '{y}'].every(part => source.tileUrlTemplate.includes(part))) {
                return t('map.settings.dialog.map-styles.error-tile-template-placeholders');
            }
        } else if (!source.tileJsonUrl) {
            return t('map.settings.dialog.map-styles.error-tilejson-required');
        }

        if (source.minzoom !== null && source.maxzoom !== null && source.minzoom >= source.maxzoom) {
            return t('map.settings.dialog.map-styles.error-zoom-order');
        }
        if ((source.minzoom !== null && !Number.isFinite(source.minzoom)) || (source.maxzoom !== null && !Number.isFinite(source.maxzoom))) {
            return t('map.settings.dialog.map-styles.error-zoom-number');
        }
        if (source.tileSize !== null && ![256, 512].includes(source.tileSize)) {
            return t('map.settings.dialog.map-styles.error-tile-size');
        }
        if (source.scheme && !['xyz', 'tms'].includes(source.scheme)) {
            return t('map.settings.dialog.map-styles.error-scheme');
        }
        return '';
    }

    async deleteStyle(styleId) {
        if (!styleId || !confirm(t('map.settings.dialog.map-styles.remove-confirm'))) return;
        const id = this.resolveCustomId(styleId);
        if (!id) return;

        try {
            const response = await fetch(`${window.contextPath || ''}/settings/map-styles/api/${id}`, {
                method: 'DELETE'
            });
            if (!response.ok) {
                throw new Error(`Deleting map style failed with ${response.status}`);
            }
            await this.reloadSettings();
            this.closeForm();
        } catch (error) {
            console.warn('Unable to delete custom map style:', error);
            this.setError(t('map.settings.dialog.map-styles.error-delete'));
        }
    }

    async setActiveStyle(styleId) {
        try {
            const response = await fetch(`${window.contextPath || ''}/settings/map-styles/api/active`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({activeStyleId: styleId})
            });
            if (!response.ok) {
                throw new Error(`Saving active map style failed with ${response.status}`);
            }
            this.applySettings(await response.json());
            this.refresh();
        } catch (error) {
            console.warn('Unable to save active map style:', error);
            this.setError(t('map.settings.dialog.map-styles.error-active'));
        }
    }

    async reloadSettings() {
        const response = await fetch(`${window.contextPath || ''}/settings/map-styles/api`);
        if (!response.ok) {
            throw new Error(`Loading map styles failed with ${response.status}`);
        }
        this.applySettings(await response.json());
        this.refresh();
    }

    applySettings(settings) {
        window.reittiCustomMapStyles = Array.isArray(settings.customStyles) ? settings.customStyles : [];
        window.reittiActiveMapStyleId = settings.activeStyleId || MapRenderer.getDefaultMapStyleId();
        window.localStorage?.setItem('mapStyleId', window.reittiActiveMapStyleId);
        MapRenderer.dispatchMapStylesChanged?.(window.reittiActiveMapStyleId);
    }

    resolveCustomId(styleId) {
        if (!styleId?.startsWith('custom-')) return null;
        return styleId.substring('custom-'.length);
    }

    setError(message) {
        this.error.textContent = message || '';
        this.error.classList.toggle('visible', !!message);
    }

    setRadioValue(name, value) {
        const input = this.root.querySelector(`input[name="${name}"][value="${value}"]`);
        if (input) {
            input.checked = true;
        }
    }

    radioValue(name) {
        return this.root.querySelector(`input[name="${name}"]:checked`)?.value || '';
    }

    value(selector) {
        return this.root.querySelector(selector)?.value.trim() || '';
    }

    optionalNumberValue(selector) {
        const rawValue = this.value(selector);
        if (rawValue === '') return null;
        const number = Number(rawValue);
        return Number.isFinite(number) ? number : NaN;
    }

    escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, character => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[character]));
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('map-style-settings-page');
    if (root) {
        new MapStyleSettingsPage(root);
    }
});
