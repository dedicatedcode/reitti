(function () {
    'use strict';

    function scrollRightmostColumnIntoView() {
        const columns = document.getElementById('hierarchy-columns');
        if (!columns) {
            return;
        }
        const lastColumn = columns.lastElementChild;
        if (!lastColumn) {
            return;
        }

        const containerRect = columns.getBoundingClientRect();
        const lastRect = lastColumn.getBoundingClientRect();
        const padding = 8;
        const fullyVisible = lastRect.left >= containerRect.left + padding
            && lastRect.right <= containerRect.right - padding;
        if (fullyVisible) {
            return;
        }

        const target = lastRect.right - containerRect.width + columns.scrollLeft;
        columns.scrollTo({left: Math.max(0, target), behavior: 'auto'});
    }

    let columnScrollPositions = [];

    function columnItemContainers() {
        const columns = document.getElementById('hierarchy-columns');
        return columns ? Array.from(columns.querySelectorAll('.hierarchy-column-items')) : [];
    }

    function captureColumnScroll() {
        columnScrollPositions = columnItemContainers().map(items => items.scrollTop);
    }

    function restoreColumnScroll() {
        const containers = columnItemContainers();
        containers.forEach((items, index) => {
            if (index < columnScrollPositions.length) {
                items.scrollTop = columnScrollPositions[index];
            }
        });
        columnScrollPositions = [];
    }

    function ensureSelectedVisible() {
        columnItemContainers().forEach(items => {
            const selected = items.querySelector('.hierarchy-item.selected');
            if (!selected) {
                return;
            }
            const containerRect = items.getBoundingClientRect();
            const itemRect = selected.getBoundingClientRect();
            if (itemRect.top < containerRect.top) {
                items.scrollTop -= containerRect.top - itemRect.top;
            } else if (itemRect.bottom > containerRect.bottom) {
                items.scrollTop += itemRect.bottom - containerRect.bottom;
            }
        });
    }

    function searchInput() {
        return document.getElementById('coverage-search-input');
    }

    function searchResults() {
        return document.getElementById('coverage-search-results');
    }

    function resultItems() {
        const results = searchResults();
        return results ? Array.from(results.querySelectorAll('.coverage-search-result')) : [];
    }

    function closeResults() {
        const results = searchResults();
        if (results) {
            results.innerHTML = '';
        }
        const input = searchInput();
        if (input) {
            input.setAttribute('aria-expanded', 'false');
        }
    }

    function moveSelection(offset) {
        const items = resultItems();
        if (items.length === 0) {
            return;
        }
        const currentIndex = items.findIndex(item => item.classList.contains('active'));
        let nextIndex = currentIndex + offset;
        if (currentIndex === -1) {
            nextIndex = offset > 0 ? 0 : items.length - 1;
        }
        nextIndex = Math.max(0, Math.min(items.length - 1, nextIndex));
        items.forEach(item => item.classList.remove('active'));
        items[nextIndex].classList.add('active');
        items[nextIndex].scrollIntoView({block: 'nearest'});
    }

    document.body.addEventListener('htmx:beforeSwap', function (event) {
        if (!event.detail || !event.detail.target) {
            return;
        }
        if (event.detail.target.id === 'coverage-ui') {
            captureColumnScroll();
        }
    });

    document.body.addEventListener('htmx:afterSwap', function (event) {
        if (!event.detail || !event.detail.target) {
            return;
        }
        if (event.detail.target.id === 'coverage-ui') {
            closeResults();
            restoreColumnScroll();
            ensureSelectedVisible();
            scrollRightmostColumnIntoView();
        }
    });

    document.addEventListener('keydown', function (event) {
        const input = searchInput();
        if (!input || document.activeElement !== input) {
            return;
        }
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            moveSelection(1);
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            moveSelection(-1);
        } else if (event.key === 'Enter') {
            const active = resultItems().find(item => item.classList.contains('active'));
            if (active) {
                event.preventDefault();
                active.click();
            }
        } else if (event.key === 'Escape') {
            closeResults();
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        ensureSelectedVisible();
        scrollRightmostColumnIntoView();
    });
})();
