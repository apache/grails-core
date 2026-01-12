(function () {
    function compare(a, b) {
        return a < b ? -1 : a > b ? 1 : 0;
    }

    function parseVersionParts(v) {
        const cleaned = String(v || '').trim().replace(/[^0-9.]+/g, '.');
        if (!cleaned) return [];
        return cleaned.split('.').filter(Boolean).map((s) => Number(s) || 0);
    }

    function compareVersions(a, b) {
        const pa = parseVersionParts(a);
        const pb = parseVersionParts(b);
        const n = Math.max(pa.length, pb.length);
        for (let i = 0; i < n; i++) {
            const da = pa[i] ?? 0;
            const db = pb[i] ?? 0;
            if (da !== db) return da < db ? -1 : 1;
        }
        const sa = String(a || '').toLowerCase();
        const sb = String(b || '').toLowerCase();
        return compare(sa, sb);
    }

    function getValue(tr, key) {
        if (key === 'name') return (tr.dataset.name || '').toLowerCase();
        if (key === 'version') return (tr.dataset.version || '');
        if (key === 'order') return Number(tr.dataset.order || '0');
        return '';
    }

    function sortTableBy(table, key, dir) {
        const tbody = table.tBodies[0];
        if (!tbody) return;

        const rows = Array.from(tbody.querySelectorAll('tr'));
        rows.sort((ra, rb) => {
            if (key === 'version') {
                return compareVersions(getValue(ra, key), getValue(rb, key)) * dir;
            }
            return compare(getValue(ra, key), getValue(rb, key)) * dir;
        });

        const frag = document.createDocumentFragment();
        rows.forEach((r) => frag.appendChild(r));
        tbody.appendChild(frag);
    }

    function setSortIndicator(table, activeTh, ariaSortValue, dataSortDirValue) {
        table.querySelectorAll('th.sortable, th[data-sort-key]').forEach((th) => {
            if (th !== activeTh) th.removeAttribute('data-sort-dir');
        });

        if (dataSortDirValue) {
            activeTh.setAttribute('data-sort-dir', dataSortDirValue);
        } else {
            activeTh.removeAttribute('data-sort-dir');
        }

        table.querySelectorAll('th.sortable, th[data-sort-key]').forEach((th) => {
            th.removeAttribute('aria-sort');
        });
        activeTh.setAttribute('aria-sort', ariaSortValue);
    }

    function initSortableTable(table) {
        const headers = Array.from(
            table.querySelectorAll('th.sortable[data-sort-key], th[data-sort-key]')
        );
        const state = { key: null, dir: 1 };

        function activateSort(th) {
            const key = th.getAttribute('data-sort-key');
            if (!key) return;

            state.dir = state.key === key ? state.dir * -1 : 1;
            state.key = key;

            const ariaSort = state.dir === 1 ? 'ascending' : 'descending';
            const dataSortDir = state.dir === 1 ? 'asc' : 'desc';

            setSortIndicator(table, th, ariaSort, dataSortDir);
            sortTableBy(table, key, state.dir);
        }

        headers.forEach((th) => {
            th.style.cursor = 'pointer';

            th.addEventListener('click', () => activateSort(th));

            // Keyboard support: Enter / Space should sort like a click.
            th.addEventListener('keydown', (e) => {
                const k = e.key;
                if (k === 'Enter' || k === ' ' || k === 'Spacebar') {
                    e.preventDefault();
                    activateSort(th);
                }
            });
        });

        // Initial load: show ascending indicator on Name (and sort to match).
        const defaultKey = 'name';
        const defaultTh = headers.find((th) => (th.getAttribute('data-sort-key') || '') === defaultKey);
        if (defaultTh) {
            state.key = defaultKey;
            state.dir = 1;
            setSortIndicator(table, defaultTh, 'ascending', 'asc');
            sortTableBy(table, defaultKey, state.dir);
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('table[data-sortable]').forEach(initSortableTable);
    });
})();