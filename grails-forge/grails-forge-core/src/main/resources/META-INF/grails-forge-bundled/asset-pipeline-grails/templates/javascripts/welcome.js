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
        return (tr.dataset[key] || '').toLowerCase();
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

        // Initial load: show the ascending indicator on the default column; the
        // server already renders rows in that order.
        const defaultKey = table.getAttribute('data-sort-default') || 'name';
        const defaultTh = headers.find((th) => (th.getAttribute('data-sort-key') || '') === defaultKey);
        if (defaultTh) {
            state.key = defaultKey;
            state.dir = 1;
            setSortIndicator(table, defaultTh, 'ascending', 'asc');
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('table[data-sortable]').forEach(initSortableTable);
    });
})();
(function () {
    function applyFilter(input) {
        const list = document.querySelector(input.getAttribute('data-filter-list') || '');
        if (!list) return;

        const query = input.value.trim().toLowerCase();
        let visible = 0;

        list.querySelectorAll('[data-name]').forEach((el) => {
            const show = !query || (el.getAttribute('data-name') || '').toLowerCase().includes(query);
            el.classList.toggle('d-none', !show);
            if (show) visible++;
        });

        // Collapse groups (e.g. controller namespaces) left with no visible entries.
        list.querySelectorAll('[data-filter-group]').forEach((group) => {
            group.classList.toggle('d-none', !group.querySelector('[data-name]:not(.d-none)'));
        });

        const empty = document.querySelector(input.getAttribute('data-filter-empty') || '');
        if (empty) empty.classList.toggle('d-none', visible > 0);

        // Surface an engaged filter on its toggle button while the menu is closed.
        const dropdown = input.closest('.dropdown');
        const toggle = dropdown && dropdown.querySelector('[data-bs-toggle="dropdown"]');
        if (toggle) toggle.classList.toggle('active', query.length > 0);
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('input[data-filter-list]').forEach((input) => {
            input.addEventListener('input', () => applyFilter(input));
            // 'search' fires when the field's built-in clear control is used.
            input.addEventListener('search', () => applyFilter(input));

            const dropdown = input.closest('.dropdown');
            if (dropdown) {
                dropdown.addEventListener('shown.bs.dropdown', () => input.focus());
            }
        });
    });
})();

// "Jump to id" control in the Available Controllers list: send the visitor to
// /controller/show/{id}. The form submits to /controller/show?id=… on its own,
// so this only upgrades a working control to the cleaner path-style URL.
(function () {
    function jumpToId(form) {
        const input = form.querySelector('.show-jump-input');
        const base = form.getAttribute('data-show-base');
        const id = input && input.value.trim();
        if (!base || !id) return false;

        window.location.assign(base.replace(/\/+$/, '') + '/' + encodeURIComponent(id));
        return true;
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('form.show-jump').forEach((form) => {
            // With JS in play we own the submit: navigate on a real id, no-op on empty.
            // The plain GET fallback only runs when this listener never attaches.
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                jumpToId(form);
            });

            const input = form.querySelector('.show-jump-input');
            if (!input) return;

            // Offer a fuller example hint while focused, restore the terse label on blur.
            const idle = input.getAttribute('placeholder') || '';
            const hint = input.getAttribute('data-focus-placeholder');
            if (hint) {
                input.addEventListener('focus', () => { input.placeholder = hint; });
                input.addEventListener('blur', () => { input.placeholder = idle; });
            }
        });
    });
})();

// Switchable cards: one server-rendered panel per type inside a
// data-switch-scope container. Every type-scoped element (title span, count
// badge, filter input, panel) carries data-switch-for, so switching only
// toggles visibility within its own card and no localized text ever lives in
// this script. Multiple switchable cards on one page stay independent.
(function () {
    function activate(scope, type) {
        scope.querySelectorAll('[data-switch-for]').forEach((el) => {
            el.classList.toggle('d-none', el.getAttribute('data-switch-for') !== type);
        });

        scope.querySelectorAll('[data-switch-type]').forEach((btn) => {
            const active = btn.getAttribute('data-switch-type') === type;
            btn.classList.toggle('active', active);
            btn.setAttribute('aria-pressed', String(active));
        });

        // Resync the shared filter toggle's engaged indicator (and the newly
        // shown list) with the filter state the visible input still holds.
        const input = scope.querySelector('input.filter-input[data-switch-for="' + type + '"]');
        if (input) input.dispatchEvent(new Event('input'));
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('[data-switch-scope]').forEach((scope) => {
            scope.querySelectorAll('[data-switch-type]').forEach((btn) => {
                btn.addEventListener('click', () => activate(scope, btn.getAttribute('data-switch-type')));
            });
        });

        // Jump links (e.g. the artefact-count rows) land on the card their
        // anchor names, already switched to their type. The plain anchor stays
        // as the no-JS fallback.
        document.querySelectorAll('a[data-switch-jump]').forEach((el) => {
            el.addEventListener('click', (e) => {
                const card = document.querySelector(el.getAttribute('href') || '');
                if (!card) return;
                e.preventDefault();
                activate(card, el.getAttribute('data-switch-jump'));
                card.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        });
    });
})();

// Server round-trips (the URL-mappings resolve/clear, the domain-counts
// toggle) land on their card's fragment, but the browser anchors before late
// layout settles; re-align once the page has fully loaded so the card tops
// the viewport. The fragment alone remains the no-JS fallback.
(function () {
    window.addEventListener('load', () => {
        const hash = window.location.hash;
        if (hash === '#runtime-beans' || hash === '#available-artefacts') {
            const card = document.querySelector(hash);
            if (card) card.scrollIntoView({ block: 'start' });
        }
    });
})();


// The resolver's parameters field applies to POST calls only - GET requests
// carry parameters in the URL itself - so it follows the method selection.
// The server renders its initial visibility from the resolved method.
(function () {
    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('form.mapping-resolve').forEach((form) => {
            const method = form.querySelector('select[name="resolveMethod"]');
            const params = form.querySelector('.resolve-params');
            if (!method || !params) return;
            const sync = () => params.classList.toggle('d-none', method.value !== 'POST');
            method.addEventListener('change', sync);
            sync();
        });
    });
})();
