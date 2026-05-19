(function () {
    const gameId      = document.getElementById('game-id').value;
    const me          = document.getElementById('me').value;
    const startOrient = document.getElementById('orientation').value;
    const spectator   = (document.getElementById('spectator').value === 'true');
    const myColour    = startOrient;       // "white" or "black" — the side this user controls
    let   orientation = startOrient;       // visual orientation (can flip)

    const $status     = document.getElementById('status');
    const $promptBar  = document.getElementById('prompt-bar');
    const $moves      = document.getElementById('move-list');
    const $captTop    = document.getElementById('captured-top');
    const $captBottom = document.getElementById('captured-bottom');
    const $clockTop   = document.getElementById('clock-top');
    const $clockBot   = document.getElementById('clock-bottom');
    const $chatLog    = document.getElementById('chat-log');
    const $boardWrap  = $('#board-wrap');
    const $promo      = $('#promo-picker');

    const PIECE_SYMBOLS = {
        P: '♙', N: '♘', B: '♗', R: '♖', Q: '♕', K: '♔',
        p: '♟', n: '♞', b: '♝', r: '♜', q: '♛', k: '♚'
    };
    const PIECE_IMG = code => '/pieces/' + code.toLowerCase() + '.png';

    let lastView    = window.__initialView;
    let selectedSq  = null;       // square name of the currently-selected own piece
    let replayIndex = null;       // when non-null, the board is showing fenHistory[replayIndex]

    // ─────────────────────────────────────────────────────────────────────
    //  Chessboard.js
    // ─────────────────────────────────────────────────────────────────────

    const board = Chessboard('board', {
        draggable: !spectator,
        position: lastView ? fenBoardOnly(lastView.fen) : 'start',
        orientation,
        pieceTheme: piece => PIECE_IMG(piece),
        onDragStart, onDrop, onSnapEnd: redrawOverlays
    });

    // Forward DOM clicks on board squares to a click handler. chessboard.js
    // doesn't expose onClick, so we attach manually to .square-XX nodes after
    // each redraw via a delegated handler on the board wrapper.
    $(document).on('click', '#board .square-55d63', function (e) {
        if (spectator) return;
        const sq = $(this).data('square');
        if (sq) handleSquareClick(sq);
    });

    function onDragStart(source, piece) {
        if (spectator || !lastView) return false;
        if (replayIndex !== null) return false;
        if (lastView.stage !== 'ACTIVE') return false;
        if (!isMyTurn(lastView)) return false;
        const isWhitePiece = piece.startsWith('w');
        if (isWhitePiece !== (myColour === 'white')) return false;
        selectSquare(source);
    }

    async function onDrop(source, target, piece) {
        if (source === target) return 'snapback';
        clearSelection();
        await sendMove(source, target, piece);
    }

    async function handleSquareClick(sq) {
        if (!lastView || lastView.stage !== 'ACTIVE' || !isMyTurn(lastView)) return;
        if (replayIndex !== null) return;
        const pieceAtClicked = pieceOn(sq, lastView.fen);

        // If a piece is already selected, attempt the move; otherwise (re-)select.
        if (selectedSq) {
            // Same square ⇒ deselect.
            if (selectedSq === sq) { clearSelection(); return; }

            // Clicking another own piece switches selection.
            if (pieceAtClicked && pieceAtClicked.startsWith(myColour === 'white' ? 'w' : 'b')) {
                selectSquare(sq);
                return;
            }

            // Try the move.
            const from = selectedSq;
            clearSelection();
            const movingPiece = pieceOn(from, lastView.fen);
            await sendMove(from, sq, movingPiece);
            return;
        }

        // First click — must be on our own piece.
        if (pieceAtClicked && pieceAtClicked.startsWith(myColour === 'white' ? 'w' : 'b')) {
            selectSquare(sq);
        }
    }

    function selectSquare(sq) {
        selectedSq = sq;
        redrawOverlays();
    }

    function clearSelection() {
        selectedSq = null;
        redrawOverlays();
    }

    async function sendMove(from, to, piece) {
        let promo = null;
        const isWhitePawn = piece === 'wP';
        const isBlackPawn = piece === 'bP';
        if ((isWhitePawn && to[1] === '8') || (isBlackPawn && to[1] === '1')) {
            promo = await openPromotionPicker(to, isWhitePawn);
            if (!promo) {
                // Cancelled — snap the board back to authoritative state.
                if (lastView) board.position(fenBoardOnly(lastView.fen), false);
                redrawOverlays();
                return;
            }
        }
        const uci = from + to + (promo || '');
        stomp.publish({
            destination: '/app/game/' + gameId + '/move',
            body: JSON.stringify({ uci })
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Promotion picker (inline, lichess-style)
    // ─────────────────────────────────────────────────────────────────────

    function openPromotionPicker(targetSquare, whitePromotion) {
        return new Promise((resolve) => {
            const $sq = $('#board .square-' + targetSquare);
            const sqOffset = $sq.offset();
            const wrapOffset = $boardWrap.offset();
            const left = sqOffset.left - wrapOffset.left;
            const top  = sqOffset.top  - wrapOffset.top;
            const tile = $sq.width();

            const colour = whitePromotion ? 'w' : 'b';
            const pieces = ['q', 'n', 'r', 'b'];   // visual order on the popup
            $promo.empty()
                .css({ left: left + 'px', top: top + 'px',
                       width: tile + 'px' })
                .show();
            pieces.forEach(p => {
                const $b = $('<button>')
                    .attr('data-piece', p)
                    .css({ width: tile + 'px', height: tile + 'px' })
                    .append($('<img>').attr('src', PIECE_IMG(colour + p.toUpperCase())))
                    .on('click', () => { close(p); });
                $promo.append($b);
            });
            const $cancel = $('<button>')
                .addClass('promo-cancel')
                .css({ width: tile + 'px', height: '28px' })
                .text('×')
                .on('click', () => close(null));
            $promo.append($cancel);

            // Click outside closes the picker.
            const escape = (e) => {
                if (!$promo.get(0).contains(e.target)) close(null);
            };
            setTimeout(() => document.addEventListener('mousedown', escape), 0);

            function close(piece) {
                document.removeEventListener('mousedown', escape);
                $promo.hide().empty();
                resolve(piece);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Square overlays — last move, selection, legal targets, check, lava
    // ─────────────────────────────────────────────────────────────────────

    function clearOverlays() {
        $('#board .square-55d63').removeClass(
            'selected-square last-move-square check-square ' +
            'lava-square lava-warning-square legal-move-dot legal-move-capture');
    }

    function redrawOverlays() {
        clearOverlays();
        if (!lastView) return;

        // Last move highlight.
        if (lastView.lastMove && typeof lastView.lastMove === 'string'
                && lastView.lastMove.length >= 4) {
            const from = lastView.lastMove.slice(0, 2);
            const to   = lastView.lastMove.slice(2, 4);
            $('#board .square-' + from).addClass('last-move-square');
            $('#board .square-' + to  ).addClass('last-move-square');
        }

        // Check highlight.
        if (lastView.kingInCheckSquare) {
            $('#board .square-' + lastView.kingInCheckSquare).addClass('check-square');
        }

        // Lava — active and pending.
        (lastView.lavaSquares    || []).forEach(sq => $('#board .square-' + sq).addClass('lava-square'));
        (lastView.warningSquares || []).forEach(sq => $('#board .square-' + sq).addClass('lava-warning-square'));

        // Selection + legal targets.
        if (selectedSq) {
            $('#board .square-' + selectedSq).addClass('selected-square');
            const targets = (lastView.legalMoves || [])
                .filter(uci => uci.slice(0, 2) === selectedSq);
            targets.forEach(uci => {
                const tgt = uci.slice(2, 4);
                const captured = !!pieceOn(tgt, lastView.fen);
                // En passant: pawn captures sideways onto empty square — still mark as capture.
                const sourcePiece = pieceOn(selectedSq, lastView.fen);
                const isPawn = sourcePiece && sourcePiece[1] === 'P';
                const isDiagonal = selectedSq[0] !== tgt[0];
                const enPassant = isPawn && isDiagonal && !captured;
                $('#board .square-' + tgt)
                    .addClass(captured || enPassant ? 'legal-move-capture' : 'legal-move-dot');
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Side-pane rendering
    // ─────────────────────────────────────────────────────────────────────

    function isMyTurn(view) {
        if (!view) return false;
        return view.whiteToMove === (myColour === 'white');
    }

    function statusText(view) {
        const s = view.status;
        const mover = view.whiteToMove ? view.whiteName : view.blackName;
        if (view.stage === 'WAITING_FOR_OPPONENT') return 'Waiting for opponent to join…';
        if (view.stage === 'ABORTED') return 'Game aborted — opponent didn’t move in time.';
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
        const topIsBlack = orientation === 'white';
        const topName = topIsBlack ? view.blackName : view.whiteName;
        const botName = topIsBlack ? view.whiteName : view.blackName;
        $clockTop.querySelector('.name').textContent = topName ?? '—';
        $clockBot.querySelector('.name').textContent = botName ?? me;

        const topMs = topIsBlack ? view.blackMillisLeft : view.whiteMillisLeft;
        const botMs = topIsBlack ? view.whiteMillisLeft : view.blackMillisLeft;
        $clockTop.querySelector('.time').textContent = view.unlimitedTime ? '∞' : fmt(topMs);
        $clockBot.querySelector('.time').textContent = view.unlimitedTime ? '∞' : fmt(botMs);

        const topToMove = topIsBlack ? !view.whiteToMove : view.whiteToMove;
        $clockTop.classList.toggle('active',  topToMove && view.stage === 'ACTIVE');
        $clockBot.classList.toggle('active', !topToMove && view.stage === 'ACTIVE');
    }

    function renderMoves(view) {
        $moves.innerHTML = '';
        const moves = view.sanHistory && view.sanHistory.length
                ? view.sanHistory : (view.moveHistory || []);
        const totalPlies = moves.length;
        for (let i = 0; i < moves.length; i += 2) {
            const row = document.createElement('div');
            row.className = 'move-row';
            const num = (i / 2) + 1;
            const numCell  = document.createElement('span'); numCell.className = 'num'; numCell.textContent = num + '.';
            const w = document.createElement('span'); w.className = 'ply'; w.textContent = moves[i] || '';
            const b = document.createElement('span'); b.className = 'ply'; b.textContent = moves[i + 1] || '';
            const whitePly = i + 1;       // fenHistory index after this move
            const blackPly = i + 2;
            if (moves[i])     w.dataset.ply = whitePly;
            if (moves[i + 1]) b.dataset.ply = blackPly;
            w.addEventListener('click', () => onPlyClick(whitePly, totalPlies));
            b.addEventListener('click', () => onPlyClick(blackPly, totalPlies));
            row.appendChild(numCell); row.appendChild(w); row.appendChild(b);
            $moves.appendChild(row);
        }
        // Highlight the currently-viewed ply, if any.
        const activePly = replayIndex !== null ? replayIndex : totalPlies;
        $moves.querySelectorAll('.ply').forEach(el => {
            if (parseInt(el.dataset.ply || '-1', 10) === activePly) el.classList.add('active');
        });
        $moves.scrollTop = $moves.scrollHeight;
    }

    function onPlyClick(plyIndex, totalPlies) {
        // Clicking the latest ply (or beyond) resumes live play.
        if (plyIndex >= totalPlies) {
            exitReplay();
            return;
        }
        if (!lastView || !lastView.fenHistory || plyIndex < 0
                || plyIndex >= lastView.fenHistory.length) return;
        replayIndex = plyIndex;
        clearSelection();
        board.position(fenBoardOnly(lastView.fenHistory[plyIndex]), true);
        renderMoves(lastView);
        renderHistoryBanner(lastView);
        clearOverlays();
        const uci = (lastView.moveHistory || [])[plyIndex - 1];
        if (uci && uci.length >= 4) {
            $('#board .square-' + uci.slice(0, 2)).addClass('last-move-square');
            $('#board .square-' + uci.slice(2, 4)).addClass('last-move-square');
        }
    }

    function exitReplay() {
        if (replayIndex === null) return;
        replayIndex = null;
        if (lastView) {
            board.position(fenBoardOnly(lastView.fen), true);
            renderMoves(lastView);
            renderHistoryBanner(lastView);
            redrawOverlays();
        }
    }

    function renderHistoryBanner(view) {
        const $banner = document.getElementById('history-banner');
        if (replayIndex === null) {
            if ($banner) $banner.remove();
            return;
        }
        const total = (view.sanHistory || view.moveHistory || []).length;
        let banner = $banner;
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'history-banner';
            banner.className = 'history-banner';
            $promptBar.insertAdjacentElement('afterend', banner);
        }
        banner.innerHTML = `Replay — move ${replayIndex} of ${total}.
            <button class="btn primary" id="live-btn">Back to live</button>`;
        document.getElementById('live-btn').onclick = exitReplay;
    }

    function renderCaptured(view) {
        // Top = opponent's captures (i.e. pieces of MY colour they took).
        const myCapt  = orientation === 'white' ? view.capturedByWhite : view.capturedByBlack;
        const oppCapt = orientation === 'white' ? view.capturedByBlack : view.capturedByWhite;
        $captTop.textContent    = oppCapt.map(p => PIECE_SYMBOLS[p] || p).join(' ');
        $captBottom.textContent = myCapt.map(p => PIECE_SYMBOLS[p] || p).join(' ');
    }

    function renderActions(view) {
        const finished = view.stage === 'FINISHED' || view.stage === 'ABORTED'
                || (view.status !== 'ACTIVE' && view.status !== 'CHECK'
                    && view.status !== 'AWAITING_PROMOTION');
        const iAmInGame = view.whiteName === me || view.blackName === me;
        const oppIsBot  = orientation === 'white' ? view.blackIsBot : view.whiteIsBot;

        document.getElementById('btn-resign').disabled = !iAmInGame || finished;
        document.getElementById('btn-draw').disabled   = !iAmInGame || finished || oppIsBot;
        document.getElementById('btn-undo').disabled   = !iAmInGame || finished || view.moveHistory.length === 0;

        // ── Game-over panel: rematch flow + lobby ────────────────────────
        if (finished && iAmInGame) {
            if (view.rematchOfferBy && view.rematchOfferBy !== me) {
                showPrompt(`<strong>${view.rematchOfferBy}</strong> wants a rematch.`, [
                    { id: 'rematch-accept',  cls: 'btn primary', label: 'Accept',        action: 'rematch/offer'   },
                    { id: 'rematch-decline', cls: 'btn',         label: 'Decline',       action: 'rematch/decline' },
                    { id: 'btn-lobby',       cls: 'btn',         label: 'Back to lobby', action: 'lobby'           },
                ]);
            } else if (view.rematchOfferBy === me) {
                showPrompt('Rematch offer sent — waiting for opponent…', [
                    { id: 'rematch-cancel', cls: 'btn',         label: 'Cancel offer',  action: 'rematch/decline' },
                    { id: 'btn-lobby',      cls: 'btn',         label: 'Back to lobby', action: 'lobby'           },
                ]);
            } else {
                showPrompt('Game over.', [
                    { id: 'btn-rematch', cls: 'btn primary', label: 'Rematch',       action: 'rematch/offer' },
                    { id: 'btn-lobby',   cls: 'btn',         label: 'Back to lobby', action: 'lobby'         },
                ]);
            }
        } else if (view.drawOfferBy && view.drawOfferBy !== me && !finished) {
            showPrompt(`<strong>${view.drawOfferBy}</strong> offers a draw.`, [
                { id: 'accept-draw',  cls: 'btn primary', label: 'Accept',  action: 'draw/offer'  },
                { id: 'decline-draw', cls: 'btn',         label: 'Decline', action: 'draw/decline' },
            ]);
        } else if (view.drawOfferBy === me && !finished) {
            showPrompt('Draw offer sent.', [
                { id: 'decline-draw', cls: 'btn', label: 'Cancel', action: 'draw/decline' },
            ]);
        } else if (view.undoRequestBy && view.undoRequestBy !== me && !finished) {
            showPrompt(`<strong>${view.undoRequestBy}</strong> requests an undo.`, [
                { id: 'accept-undo',  cls: 'btn primary', label: 'Accept',  action: 'undo/accept'  },
                { id: 'decline-undo', cls: 'btn',         label: 'Decline', action: 'undo/decline' },
            ]);
        } else if (view.undoRequestBy === me && !finished) {
            showPrompt('Undo request sent.', [
                { id: 'decline-undo', cls: 'btn', label: 'Cancel', action: 'undo/decline' },
            ]);
        } else {
            $promptBar.style.display = 'none';
            $promptBar.innerHTML = '';
        }
    }

    function showPrompt(html, buttons) {
        $promptBar.style.display = '';
        $promptBar.innerHTML = html + ' ' +
            buttons.map(b => `<button class="${b.cls}" id="${b.id}">${b.label}</button>`).join(' ');
        buttons.forEach(b => {
            document.getElementById(b.id).onclick = () => promptAction(b.action);
        });
    }

    async function promptAction(action) {
        if (action === 'lobby') {
            window.location.href = '/lobby';
            return;
        }
        // All other actions are STOMP messages to /app/game/{id}/{action}.
        send(action);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Sounds
    // ─────────────────────────────────────────────────────────────────────

    const sounds = {
        move:      new Audio('/sounds/move.wav'),
        capture:   new Audio('/sounds/capture.wav'),
        castle:    new Audio('/sounds/castle.wav'),
        check:     new Audio('/sounds/check.wav'),
        checkmate: new Audio('/sounds/checkmate.wav'),
        draw:      new Audio('/sounds/draw.wav'),
        start:     new Audio('/sounds/start.wav'),
    };
    let soundEnabled = localStorage.getItem('pc.sound') !== 'off';

    function playSound(name) {
        if (!name || !soundEnabled) return;
        const a = sounds[name];
        if (a) { a.currentTime = 0; a.play().catch(() => {}); }
    }

    function refreshSoundButton() {
        const b = document.getElementById('btn-sound');
        if (!b) return;
        b.textContent = soundEnabled ? '🔊' : '🔇';
        b.title = soundEnabled ? 'Mute sounds' : 'Unmute sounds';
        b.setAttribute('aria-label', b.title);
    }
    document.getElementById('btn-sound')?.addEventListener('click', () => {
        soundEnabled = !soundEnabled;
        localStorage.setItem('pc.sound', soundEnabled ? 'on' : 'off');
        refreshSoundButton();
        if (soundEnabled) playSound('move');     // audible feedback
    });
    refreshSoundButton();

    // ─────────────────────────────────────────────────────────────────────
    //  FEN helpers
    // ─────────────────────────────────────────────────────────────────────

    function fenBoardOnly(fen) {
        return (fen || '').split(' ')[0];
    }

    /** Returns "wP" / "bN" / null for a square name (e.g. "e4"). */
    function pieceOn(sq, fen) {
        const board = fenBoardOnly(fen);
        const file = sq.charCodeAt(0) - 'a'.charCodeAt(0);
        const rank = parseInt(sq[1], 10);             // 1..8
        const row  = 8 - rank;                         // 0 = top
        const ranks = board.split('/');
        if (row < 0 || row > 7) return null;
        const rowStr = ranks[row];
        let col = 0;
        for (const ch of rowStr) {
            if (/\d/.test(ch)) { col += parseInt(ch, 10); }
            else {
                if (col === file) {
                    return (ch === ch.toUpperCase() ? 'w' : 'b') + ch.toUpperCase();
                }
                col += 1;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Apply a view (main render loop)
    // ─────────────────────────────────────────────────────────────────────

    function applyView(view) {
        const previousStage = lastView?.stage;
        lastView = view;
        // Rematch finalised: server points us to the fresh game — both seats redirect.
        if (view.rematchToGameId) {
            window.location.href = '/game/' + encodeURIComponent(view.rematchToGameId);
            return;
        }
        // If the user is viewing history, leave the board alone — they'll click
        // "Back to live" (or the latest move) to resume.
        if (replayIndex === null) {
            board.position(fenBoardOnly(view.fen), false);
        }

        // Clear stale selection once the position changes (turn flipped, etc.).
        if (selectedSq) {
            const stillOwnPiece = pieceOn(selectedSq, view.fen);
            if (!stillOwnPiece || !stillOwnPiece.startsWith(myColour === 'white' ? 'w' : 'b')) {
                selectedSq = null;
            }
            if (!isMyTurn(view)) selectedSq = null;
        }

        $status.textContent = statusText(view);
        renderClocks(view);
        renderMoves(view);
        renderCaptured(view);
        renderActions(view);
        renderHistoryBanner(view);
        if (replayIndex === null) redrawOverlays();

        if (previousStage === 'WAITING_FOR_OPPONENT' && view.stage === 'ACTIVE') {
            playSound('start');
        }
        playSound(view.soundEvent);
    }

    if (lastView) applyView(lastView);

    // Local clock ticker — drains the active clock between server pushes.
    setInterval(() => {
        if (!lastView || lastView.stage !== 'ACTIVE' || lastView.unlimitedTime) return;
        const now = performance.now();
        const dt  = now - (lastView.__lastLocal ?? now);
        lastView.__lastLocal = now;
        if (lastView.whiteToMove) lastView.whiteMillisLeft = Math.max(0, lastView.whiteMillisLeft - dt);
        else                      lastView.blackMillisLeft = Math.max(0, lastView.blackMillisLeft - dt);
        renderClocks(lastView);
    }, 250);

    // ─────────────────────────────────────────────────────────────────────
    //  STOMP
    // ─────────────────────────────────────────────────────────────────────

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
            if (lastView) board.position(fenBoardOnly(lastView.fen), true);
        });
        // Personal redirect — used by the server for rematch acceptance.
        stomp.subscribe('/user/queue/redirect', msg => {
            const r = JSON.parse(msg.body);
            if (r.gameId && r.gameId !== gameId) {
                window.location.href = '/game/' + encodeURIComponent(r.gameId);
            }
        });
    };
    stomp.activate();

    // ─────────────────────────────────────────────────────────────────────
    //  Action buttons
    // ─────────────────────────────────────────────────────────────────────

    document.getElementById('btn-resign').onclick = () => {
        if (confirm('Resign this game?')) send('resign');
    };
    document.getElementById('btn-draw').onclick = () => send('draw/offer');
    document.getElementById('btn-undo').onclick = () => send('undo/request');

    document.getElementById('btn-flip').onclick = () => {
        orientation = (orientation === 'white') ? 'black' : 'white';
        board.orientation(orientation);
        if (lastView) {
            renderClocks(lastView);
            renderCaptured(lastView);
            redrawOverlays();
        }
    };

    document.getElementById('btn-pgn').onclick = async () => {
        try {
            const res = await fetch('/api/play/pgn/' + encodeURIComponent(gameId));
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const text = await res.text();
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
                flashPrompt('PGN copied to clipboard.');
            } else {
                // Fallback: open in a new window for manual copy.
                const w = window.open('', '_blank');
                w.document.write('<pre>' + escapeHtml(text) + '</pre>');
            }
        } catch (e) {
            flashPrompt('PGN failed: ' + e.message);
        }
    };

    function flashPrompt(text) {
        $promptBar.style.display = '';
        $promptBar.textContent = text;
        setTimeout(() => {
            if ($promptBar.textContent === text) {
                $promptBar.style.display = 'none';
                $promptBar.textContent = '';
            }
        }, 2500);
    }

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
