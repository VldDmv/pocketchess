(function () {
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
    const me = document.querySelector('.topbar .who')?.textContent ?? '';

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
        if (!fd.has('unlimited')) o.unlimited = false; else o.unlimited = true;
        if (o.minutes) o.minutes = parseInt(o.minutes, 10);
        if (o.increment) o.increment = parseInt(o.increment, 10);
        return o;
    }

    function go(gameId) {
        window.location.href = '/game/' + encodeURIComponent(gameId);
    }

    document.getElementById('bot-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            const r = await postJson('/api/play/bot', readForm(e.target));
            go(r.gameId);
        } catch (err) { alert(err.message); }
    });

    document.getElementById('open-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            const r = await postJson('/api/play/open', readForm(e.target));
            go(r.gameId);
        } catch (err) { alert(err.message); }
    });

    document.getElementById('quick-match').addEventListener('click', async () => {
        const form = document.getElementById('open-form');
        try {
            const r = await postJson('/api/play/quick', readForm(form));
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
    document.querySelectorAll('.join-btn').forEach(joinHandler);

    // Live updates
    const tbody = document.querySelector('#games-table tbody');
    const counter = document.getElementById('lobby-count');

    function render(entries) {
        tbody.innerHTML = '';
        if (!entries || entries.length === 0) {
            const tr = document.createElement('tr');
            tr.className = 'empty';
            tr.innerHTML = '<td colspan="5">No open games yet — create one above.</td>';
            tbody.appendChild(tr);
        } else {
            entries.forEach(e => {
                const tr = document.createElement('tr');
                tr.dataset.id = e.gameId;
                const tc = e.unlimitedTime ? 'unlimited'
                    : Math.round(e.baseTimeSeconds / 60) + ' min + ' + e.incrementSeconds + ' s';
                tr.innerHTML = `
                    <td>${escapeHtml(e.creatorName)}</td>
                    <td>${e.creatorColour}</td>
                    <td>${e.variant}</td>
                    <td>${tc}</td>
                    <td><button class="btn join-btn" ${e.creatorName === me ? 'disabled' : ''}>${e.creatorName === me ? 'Your game' : 'Join'}</button></td>`;
                tbody.appendChild(tr);
            });
            tbody.querySelectorAll('.join-btn').forEach(joinHandler);
        }
        counter.textContent = (entries?.length ?? 0) + ' open';
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
    };
    client.activate();
})();
