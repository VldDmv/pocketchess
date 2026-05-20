(function () {
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
    const me = document.querySelector('.topbar .who')?.textContent ?? '';
    const filter = document.getElementById('category-filter');

    let latestEntries = null;   // last server payload, before filtering

    function jsonHeaders() {
        return { 'Content-Type': 'application/json', [csrfHeader]: csrfToken };
    }

    async function postJson(url, body) {
        const res = await fetch(url, {
            method: 'POST', headers: jsonHeaders(), body: JSON.stringify(body)
        });
        if (!res.ok) {
            const msg = await res.text();
            throw new Error(msg || ('HTTP ' + res.status));
        }
        return res.json();
    }

    function readForm(form) {
        const fd = new FormData(form);
        const o = {};
        fd.forEach((v, k) => { o[k] = v; });

        const preset = o.preset || '300+0';
        let baseSeconds, increment, unlimited = false;
        if (preset === 'unlimited') {
            unlimited = true;
            baseSeconds = 300;
            increment = 0;
        } else if (preset === 'custom') {
            baseSeconds = parseInt(o.customSeconds || '300', 10);
            increment   = parseInt(o.customIncrement || '0', 10);
        } else {
            const [b, i] = preset.split('+');
            baseSeconds = parseInt(b, 10);
            increment   = parseInt(i, 10);
        }
        delete o.preset; delete o.customSeconds; delete o.customIncrement;
        o.baseSeconds = baseSeconds;
        o.increment   = increment;
        o.unlimited   = unlimited;
        return o;
    }

    // Show / hide custom-tc inputs.
    document.querySelectorAll('.preset').forEach(sel => {
        sel.addEventListener('change', () => {
            const form = sel.closest('form');
            const show = sel.value === 'custom';
            form.querySelectorAll('.custom-tc').forEach(el => {
                el.style.display = show ? '' : 'none';
            });
        });
    });

    function go(gameId) {
        window.location.href = '/game/' + encodeURIComponent(gameId);
    }

    // Buttons are type="button" so the form can't submit natively while the
    // listener is still attaching — that used to GET-navigate to a URL with
    // form data in the query string before the JS handler bound.
    document.getElementById('bot-start')?.addEventListener('click', async () => {
        try {
            const r = await postJson('/api/play/bot',
                readForm(document.getElementById('bot-form')));
            go(r.gameId);
        } catch (err) { alert(err.message); }
    });
    document.getElementById('open-create')?.addEventListener('click', async () => {
        try {
            const r = await postJson('/api/play/open',
                readForm(document.getElementById('open-form')));
            go(r.gameId);
        } catch (err) { alert(err.message); }
    });

    function joinHandler(btn) {
        btn.addEventListener('click', async () => {
            const id = btn.closest('tr').dataset.id;
            try {
                const params = new URLSearchParams({ gameId: id });
                const res = await fetch('/api/play/join?' + params, {
                    method: 'POST', headers: { [csrfHeader]: csrfToken }
                });
                if (!res.ok) throw new Error(await res.text());
                const r = await res.json();
                go(r.gameId);
            } catch (err) { alert(err.message); }
        });
    }
    function cancelHandler(btn) {
        btn.addEventListener('click', async () => {
            if (!confirm('Cancel your open challenge?')) return;
            try {
                const res = await fetch('/api/play/cancel', {
                    method: 'POST', headers: { [csrfHeader]: csrfToken }
                });
                if (!res.ok) throw new Error(await res.text());
                // The lobby will refresh via /topic/lobby; nothing to do.
            } catch (err) { alert(err.message); }
        });
    }
    document.querySelectorAll('.join-btn').forEach(joinHandler);
    document.querySelectorAll('.cancel-btn').forEach(cancelHandler);

    // Live updates
    const tbody = document.querySelector('#games-table tbody');
    const counter = document.getElementById('lobby-count');

    function render(entries) {
        latestEntries = entries;
        const filterValue = filter ? filter.value : 'ALL';
        const filtered = (entries || []).filter(e =>
            filterValue === 'ALL' || e.category === filterValue);
        // Sort: my row first, then by category bucket (server already sorted
        // by category — we just bubble mine up).
        filtered.sort((a, b) => {
            const am = a.creatorName === me ? 0 : 1;
            const bm = b.creatorName === me ? 0 : 1;
            return am - bm;
        });

        tbody.innerHTML = '';
        if (filtered.length === 0) {
            const tr = document.createElement('tr');
            tr.className = 'empty';
            tr.innerHTML = '<td colspan="6">No open games — create one above.</td>';
            tbody.appendChild(tr);
        } else {
            filtered.forEach(e => {
                const tr = document.createElement('tr');
                tr.dataset.id = e.gameId;
                tr.dataset.category = e.category;
                if (e.creatorName === me) tr.className = 'mine';
                const tc = formatTc(e);
                const mine = e.creatorName === me;
                const actions = mine
                    ? `<a class="btn primary" href="/game/${e.gameId}">Resume</a>
                       <button class="btn cancel-btn">Cancel</button>`
                    : `<button class="btn join-btn">Join</button>`;
                tr.innerHTML = `
                    <td>${escapeHtml(e.creatorName)}</td>
                    <td>${e.creatorColour}</td>
                    <td>${e.variant}</td>
                    <td>${tc}</td>
                    <td><span class="category-pill cat-${e.category}">${e.category}</span></td>
                    <td>${actions}</td>`;
                tbody.appendChild(tr);
            });
            tbody.querySelectorAll('.join-btn').forEach(joinHandler);
            tbody.querySelectorAll('.cancel-btn').forEach(cancelHandler);
        }
        counter.textContent = ((entries || []).length) + ' open';
    }

    if (filter) {
        filter.addEventListener('change', async () => {
            // If no STOMP push has arrived yet, pull the current list once so
            // the filter has something to operate on instead of wiping the
            // Thymeleaf-rendered table to an empty state.
            if (latestEntries === null) {
                try {
                    const res = await fetch('/api/play/lobby');
                    if (res.ok) latestEntries = await res.json();
                    else latestEntries = [];
                } catch (_) { latestEntries = []; }
            }
            render(latestEntries);
        });
    }

    function formatTc(e) {
        if (e.unlimitedTime) return 'unlimited';
        const base = e.baseTimeSeconds;
        const inc = e.incrementSeconds;
        const baseStr = base < 60
                ? base + ' s'
                : (base % 60 === 0 ? (base / 60) + ' min' : (base / 60).toFixed(1) + ' min');
        return inc > 0 ? `${baseStr} + ${inc} s` : baseStr;
    }

    function escapeHtml(s) {
        return s.replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 3000
    });
    client.onConnect = () => {
        client.subscribe('/topic/lobby', msg => render(JSON.parse(msg.body)));
        // Server pushes here when someone joins your challenge or accepts a rematch.
        client.subscribe('/user/queue/redirect', msg => {
            const r = JSON.parse(msg.body);
            if (r.gameId) go(r.gameId);
        });
    };
    client.activate();
})();
