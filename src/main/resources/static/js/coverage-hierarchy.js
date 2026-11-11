(function () {
    'use strict';

    function scrollSelectedIntoView() {
        const columns = document.getElementById('hierarchy-columns');
        if (!columns) {
            return;
        }
        const selected = columns.querySelector('.hierarchy-item.selected');
        if (selected) {
            selected.scrollIntoView({block: 'nearest', inline: 'nearest'});
        }
        columns.scrollLeft = columns.scrollWidth;
    }

    document.body.addEventListener('htmx:afterSwap', function (event) {
        if (!event.detail || !event.detail.target || event.detail.target.id !== 'coverage-ui') {
            return;
        }
        scrollSelectedIntoView();
    });

    document.addEventListener('DOMContentLoaded', scrollSelectedIntoView);
})();
