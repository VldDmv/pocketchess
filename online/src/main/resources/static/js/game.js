(function () {
    const gameId = document.getElementById('game-id').value;
    const me = document.getElementById('me').value;
    const orientation = document.getElementById('orientation').value;

    const $status = document.getElementById('status');
    const $promptBar = document.getElementById('prompt-bar');
    const $moves = document.getElementById('move-list');
    const $captTop = document.getElementById('captured-top');
    const $captBottom = document.getElementById('captured-bottom');
    const $clockTop = document.getElementById('clock-top');
    const $clockBot = document.getElementById('clock-bottom');
    const $chatLog = document.getElementById('chat-log');

    const promoOverlay = document.getElementById('promotion-overlay');
    let promoResolver = null;

    promoOverlay.querySelectorAll('button[data-piece]').forEach(b => {
        b.addEventListener('click', () => {
            promoOverlay.style.display = 'none';
            promoResolver?.(b.dataset.piece);
            promoResolver = null;
        });
    });

    function pickPromotion() {
        promoOverlay.style.display = 'flex';
        return new Promise(res => { promoResolver = res; });
    }

    let lastView = window.__initialView;

    function isMyTurn(view) {
        if (!view) return false;
        const myColour = orientation;     // "white" or "black"
        return view.whiteToMove === (myColour === 'white');
    }

    function statusText(view) {
        const s = view.status;
        const mover = view.whiteToMove ? view.whiteName : view.blackName;
        if (view.stage === 'WAITING_FOR_OPPONENT') return 'Waiting for opponent to join…';
        switch (s) {
            case 'ACTIVE':  return `${mover ?? '?'} to move`;
            case 'CHECK':   return `${mover ?? '?'} to move — check!`;
            case 'AWAITING_PROMOTION': return 'Awaiting promotion';
            case 'WHITE_WIN': return 'Checkmate — White wins';
            case 'BLACK_WIN': return 'Checkmate — Black wins';
            case 'WHITE_WIN_ON_TIME': return 'Black flagged — White wins';
            case 'BLACK_WIN_ON_TIME': return 'White flagged — Black wins';
            case 'WHITE_WINS_BY_RESIGNATION': return 'Black resigned — White wins';
            case 'BLACK_WINS_BY_RESIGNATION': return 'White resigned — Black wins';
            case 'STALEMATE': return 'Stalemate';
            case 'DRAW_THREEFOLD_REPETITION': return 'Draw — threefold repetition';
            case 'DRAW_50_MOVES': return 'Draw — fifty-move rule';
            case 'DRAW_AGREED': return 'Draw — agreed';
            case 'DRAW_INSUFFICIENT_MATERIAL': return 'Draw — insufficient material';
            default: return s;
        }
    }

    function fmt(ms) {
        if (ms == null || ms > 9999999999) return '∞';
        if (ms < 0) ms = 0;
        const totalSec = Math.ceil(ms / 1000);
        const m = Math.floor(totalSec / 60);
        const s = totalSec % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    function renderClocks(view) {
        const myTopIsOpp = true; // top clock = opponent
        const topName = orientation === 'white' ? view.blackName : view.whiteName;
        const botName = orientation === 'white' ? view.whiteName : view.blackName;
        $clockTop.querySelector('.name').textContent = topName ?? '—';
        $clockBot.querySelector('.name').textContent = botName ?? me;

        const topMs = orientation === 'white' ? view.blackMillisLeft : view.whiteMillisLeft;
        const botMs = orientation === 'white' ? view.whiteMillisLeft : view.blackMillisLeft;
        $clockTop.querySelector('.time').textContent = view.unlimitedTime ? '∞' : fmt(topMs);
        $clockBot.querySelector('.time').textContent = view.unlimitedTime ? '∞' : fmt(botMs);

        const myMove = isMyTurn(view);
        $clockBot.classList.toggle('active', myMove && view.stage === 'ACTIVE');
        $clockTop.classList.toggle('active', !myMove && view.stage === 'ACTIVE');
    }

    function renderMoves(view) {
        $moves.innerHTML = '';
        const moves = view.moveHistory || [];
        for (let i = 0; i < moves.length; i += 2) {
            const row = document.createElement('div');
            row.className = 'move-row';
            const num = (i / 2) + 1;
            row.innerHTML = `<span class="num">${num}.</span><span>${moves[i] || ''}</span><span>${moves[i+1] || ''}</span>`;
            $moves.appendChild(row);
        }
        $moves.scrollTop = $moves.scrollHeight;
    }

    const PIECE_SYMBOLS = {
        P: '♙', N: '♘', B: '♗', R: '♖', Q: '♕', K: '♔',
        p: '♟', n: '♞', b: '♝', r: '♜', q: '♛', k: '♚'
    };
    function renderCaptured(view) {
        // Top = opponent's captures (i.e. pieces of MY colour they took).
        // Bottom = my captures.
        const myCapt   = orientation === 'white' ? view.capturedByWhite : view.capturedByBlack;
        const oppCapt  = orientation === 'white' ? view.capturedByBlack : view.capturedByWhite;
        $captTop.textContent = oppCapt.map(p => PIECE_SYMBOLS[p] || p).join(' ');
        $captBottom.textContent = myCapt.map(p => PIECE_SYMBOLS[p] || p).join(' ');
    }

    function renderActions(view) {
        const finished = view.stage === 'FINISHED' || (view.status !== 'ACTIVE' && view.status !== 'CHECK');
        const iAmInGame = view.whiteName === me || view.blackName === me;
        const opp = orientation === 'white' ? view.blackName : view.whiteName;
        const oppIsBot = orientation === 'white' ? view.blackIsBot : view.whiteIsBot;

        document.getElementById('btn-resign').disabled = !iAmInGame || finished;
        document.getElementById('btn-draw').disabled = !iAmInGame || finished || oppIsBot;
        document.getElementById('btn-undo').disabled = !iAmInGame || finished || view.moveHistory.length === 0;

        if (view.drawOfferBy && view.drawOfferBy !== me && !finished) {
            $promptBar.style.display = '';
            $promptBar.innerHTML = `<strong>${view.drawOfferBy}</strong> offers a draw.
                <button class="btn primary" id="accept-draw">Accept</button>
                <button class="btn" id="decline-draw">Decline</button>`;
            document.getElementById('accept-draw').onclick = () => send('draw/offer');
            document.getElementById('decline-draw').onclick = () => send('draw/decline');
        } else if (view.drawOfferBy === me && !finished) {
            $promptBar.style.display = '';
            $promptBar.innerHTML = `Draw offer sent.
                <button class="btn" id="decline-draw">Cancel</button>`;
            document.getElementById('decline-draw').onclick = () => send('draw/decline');
        } else if (view.undoRequestBy && view.undoRequestBy !== me && !finished) {
            $promptBar.style.display = '';
            $promptBar.innerHTML = `<strong>${view.undoRequestBy}</strong> requests an undo.
                <button class="btn primary" id="accept-undo">Accept</button>
                <button class="btn" id="decline-undo">Decline</button>`;
            document.getElementById('accept-undo').onclick = () => send('undo/accept');
            document.getElementById('decline-undo').onclick = () => send('undo/decline');
        } else if (view.undoRequestBy === me && !finished) {
            $promptBar.style.display = '';
            $promptBar.innerHTML = `Undo request sent.
                <button class="btn" id="decline-undo">Cancel</button>`;
            document.getElementById('decline-undo').onclick = () => send('undo/decline');
        } else {
            $promptBar.style.display = 'none';
            $promptBar.innerHTML = '';
        }
    }

    const sounds = {
        move:      new Audio('/sounds/move.wav'),
        capture:   new Audio('/sounds/capture.wav'),
        castle:    new Audio('/sounds/castle.wav'),
        check:     new Audio('/sounds/check.wav'),
        checkmate: new Audio('/sounds/checkmate.wav'),
        draw:      new Audio('/sounds/draw.wav'),
        start:     new Audio('/sounds/start.wav'),
    };
    function playSound(name) {
        if (!name) return;
        const a = sounds[name];
        if (a) { a.currentTime = 0; a.play().catch(() => {}); }
    }

    // Chessboard.js setup
    const board = Chessboard('board', {
        draggable: !window.__spectator,
        position: lastView?.fen ?? 'start',
        orientation,
        pieceTheme: piece => '/pieces/' + piece.toLowerCase() + '.png',
        onDragStart: (source, piece) => {
            if (window.__spectator) return false;
            if (!lastView) return false;
            if (lastView.stage !== 'ACTIVE') return false;
            if (!isMyTurn(lastView)) return false;
            const isWhitePiece = piece.startsWith('w');
            if (isWhitePiece !== (orientation === 'white')) return false;
        },
        onDrop: async (source, target, piece) => {
            if (source === target) return 'snapback';
            let promo = null;
            const isWhitePawn = piece === 'wP';
            const isBlackPawn = piece === 'bP';
            if (isWhitePawn && target[1] === '8') promo = await pickPromotion();
            else if (isBlackPawn && target[1] === '1') promo = await pickPromotion();
            const uci = source + target + (promo || '');
            stomp.publish({
                destination: '/app/game/' + gameId + '/move',
                body: JSON.stringify({ uci })
            });
            // Don't snapback here — the server's broadcast (or rejection error) will
            // realign the board if the move was illegal.
        }
    });

    function applyView(view) {
        lastView = view;
        // Snap the board to the authoritative position
        board.position(view.fen, false);
        $status.textContent = statusText(view);
        renderClocks(view);
        renderMoves(view);
        renderCaptured(view);
        renderActions(view);
        playSound(view.soundEvent);
    }

    // Initial render
    if (lastView) applyView(lastView);

    // Local clock ticker — decrements the active clock between server pushes
    setInterval(() => {
        if (!lastView || lastView.stage !== 'ACTIVE' || lastView.unlimitedTime) return;
        // We approximate by repainting the existing values — the server pushes
        // the source of truth on every move, but between moves we let the
        // active clock drain visually.
        const now = performance.now();
        const dt = now - (lastView.__lastLocal ?? now);
        lastView.__lastLocal = now;
        if (lastView.whiteToMove) lastView.whiteMillisLeft = Math.max(0, lastView.whiteMillisLeft - dt);
        else lastView.blackMillisLeft = Math.max(0, lastView.blackMillisLeft - dt);
        renderClocks(lastView);
    }, 250);

    // STOMP
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const stomp = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 3000,
        connectHeaders: csrfToken ? { 'X-CSRF-TOKEN': csrfToken } : {}
    });

    function send(action) {
        stomp.publish({
            destination: '/app/game/' + gameId + '/' + action,
            body: '{}'
        });
    }

    stomp.onConnect = () => {
        stomp.subscribe('/topic/game/' + gameId, msg => applyView(JSON.parse(msg.body)));
        stomp.subscribe('/topic/game/' + gameId + '/chat', msg => {
            const c = JSON.parse(msg.body);
            const div = document.createElement('div');
            div.innerHTML = `<span class="from">${escapeHtml(c.from)}</span>: ${escapeHtml(c.text)}`;
            $chatLog.appendChild(div);
            $chatLog.scrollTop = $chatLog.scrollHeight;
        });
        stomp.subscribe('/user/queue/game/' + gameId + '/errors', msg => {
            const e = JSON.parse(msg.body);
            $status.textContent = '⚠ ' + e.reason;
            // refresh board to server truth
            if (lastView) board.position(lastView.fen, true);
        });
    };
    stomp.activate();

    document.getElementById('btn-resign').onclick = () => {
        if (confirm('Resign this game?')) send('resign');
    };
    document.getElementById('btn-draw').onclick = () => send('draw/offer');
    document.getElementById('btn-undo').onclick = () => send('undo/request');

    const chatForm = document.getElementById('chat-form');
    if (chatForm) {
        chatForm.addEventListener('submit', e => {
            e.preventDefault();
            const input = document.getElementById('chat-text');
            const text = input.value.trim();
            if (!text) return;
            stomp.publish({
                destination: '/app/game/' + gameId + '/chat',
                body: JSON.stringify({ text })
            });
            input.value = '';
        });
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }
})();
