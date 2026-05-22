class Autocomplete {
    /**
     * @param {HTMLInputElement} input
     * @param {Object} options
     * @param {string} options.url            - JSON endpoint (GET, ?query= parameter)
     * @param {number} options.minLength      - minimum characters before fetching
     * @param {number} options.delay          - debounce delay in ms
     * @param {string|HTMLElement} options.container  - suggestions container (id, selector ref, or element)
     * @param {function(string): string} options.renderItem - returns HTML for a single suggestion
     * @param {function(string): void} options.onSelect - called when a suggestion is chosen
     */
    constructor(input, options = {}) {
        this.input = input;

        // --- URL ---
        this.url = options.url || input.dataset.autocompleteUrl || '';

        // --- minLength ---
        if (options.minLength !== undefined) {
            this.minLength = options.minLength;
        } else {
            const ml = input.dataset.autocompleteMinLength;
            this.minLength = ml ? parseInt(ml, 10) : 2;
        }

        // --- delay ---
        if (options.delay !== undefined) {
            this.delay = options.delay;
        } else {
            const d = input.dataset.autocompleteDelay;
            this.delay = d ? parseInt(d, 10) : 300;
        }

        // --- renderItem (default returns a simple <div>) ---
        this.renderItem = options.renderItem || (item => `<div class="suggestion-item">${item}</div>`);

        // --- onSelect (default writes into the input) ---
        this.onSelect = options.onSelect || (item => {
            this.input.value = item;
        });

        this.tagMode = options.tagMode || input.dataset.autocompleteTagMode === 'true';

        // --- container resolution ---
        const containerOpt = options.container || input.dataset.autocompleteContainer;
        if (containerOpt) {
            if (typeof containerOpt === 'string') {
                this.container = document.querySelector(containerOpt);
            } else if (containerOpt instanceof HTMLElement) {
                this.container = containerOpt;
            }
        }
        if (!this.container) {
            // fallback – try next sibling with class 'suggestions-dropdown'
            const sibling = input.nextElementSibling;
            if (sibling && sibling.classList.contains('suggestions-dropdown')) {
                this.container = sibling;
            } else {
                // create one
                const dd = document.createElement('div');
                dd.className = 'suggestions-dropdown';
                input.parentNode.insertBefore(dd, input.nextElementSibling);
                this.container = dd;
            }
        }

        this.timer = null;
        this.activeIndex = -1;
        this.loading = false;

        this.input.addEventListener('input', () => this.handleInput());
        this.input.addEventListener('keydown', (e) => this.handleKeyDown(e));
        this.input.addEventListener('blur', () => {
            setTimeout(() => this.hideSuggestions(), 200);
        });
    }

    handleInput() {
        const query = this.input.value.trim();
        if (query.length < this.minLength) {
            this.hideSuggestions();
            return;
        }
        if (this.timer) clearTimeout(this.timer);
        this.timer = setTimeout(async () => {
            await this.fetchSuggestions(query);
        }, this.delay);
    }

    async fetchSuggestions(query) {
        this.loading = true;
        try {
            const resp = await fetch(`${this.url}?query=${encodeURIComponent(query)}`);
            if (!resp.ok) {
                this.hideSuggestions();
                return;
            }
            const data = await resp.json();   // assume array of strings
            this.renderSuggestions(data);
        } catch (e) {
            this.hideSuggestions();
        } finally {
            this.loading = false;
        }
    }

    renderSuggestions(items) {
        this.container.innerHTML = '';
        if (!items || items.length === 0) {
            this.hideSuggestions();
            return;
        }
        const fragment = document.createDocumentFragment();
        items.forEach((item, idx) => {
            const wrapper = document.createElement('div');
            wrapper.innerHTML = this.renderItem(item);
            const node = wrapper.firstElementChild;
            if (node) {
                node.classList.add('suggestion-item');
                node.dataset.autocompleteValue = item;  // store original suggestion string
                node.addEventListener('mousedown', (e) => {
                    e.preventDefault(); // prevent blur before select
                    this.selectSuggestion(item);
                });
                fragment.appendChild(node);
            }
        });
        this.container.appendChild(fragment);
        this.activeIndex = -1;
    }

    selectSuggestion(item) {
        this.onSelect(item);
        this.hideSuggestions();
    }

    hideSuggestions() {
        if (this.container) {
            this.container.innerHTML = '';
        }
    }

    handleKeyDown(event) {
        const items = this.container ? this.container.querySelectorAll('.suggestion-item') : [];

        // Tag‑mode: commit raw input on Enter/Space when no active item is highlighted
        if ((event.key === 'Enter' || event.key === ' ') && this.tagMode) {
            const value = this.input.value.trim();
            if (value !== '' && (this.activeIndex === -1 || items.length === 0)) {
                event.preventDefault();
                this.selectSuggestion(value);
                return;
            }
        }

        // Don't react to arrows if there are no suggestions
        if (items.length === 0) return;

        if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.activeIndex = Math.min(this.activeIndex + 1, items.length - 1);
            this.updateActiveItem(items);
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.activeIndex = Math.max(this.activeIndex - 1, 0);
            this.updateActiveItem(items);
        } else if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            if (this.activeIndex >= 0 && this.activeIndex < items.length) {
                const activeNode = items[this.activeIndex];
                const value = activeNode ? activeNode.dataset.autocompleteValue : undefined;
                if (value) this.selectSuggestion(value);
            }
        }  else if (event.key === 'Escape') {
            if (items.length > 0) {
                event.preventDefault();
                event.stopPropagation();
                this.hideSuggestions();
            }
        }
    }

    updateActiveItem(items) {
        items.forEach((el, idx) => {
            if (idx === this.activeIndex) {
                el.classList.add('active');
            } else {
                el.classList.remove('active');
            }
        });
    }
}
