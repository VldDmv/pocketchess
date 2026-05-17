package org.pocketchess.core.general;

import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorator around {@link RuleEngine} that makes every rule check lava-aware.
 */
public class LavaAwareRuleEngine implements ChessRules {
    private final ChessRules delegate;
    private final LavaManager lavaManager;
    private final Board board;

    private static final Piece BLOCKER_W = new Pawn(true);
    private static final Piece BLOCKER_B = new Pawn(false);

    private final List<Spot> activeBlockers = new ArrayList<>();

    public LavaAwareRuleEngine(ChessRules delegate, LavaManager lavaManager, Board board) {
        this.delegate = delegate;
        this.lavaManager = lavaManager;
        this.board = board;
    }

    public void setAISimulation() {
    }

    // ── ChessRules delegation ─────────────────────────────────────────────────

    @Override
    public boolean isMoveLegal(Board b, Spot start, Spot end) {
        if (!lavaManager.isEnabled()) return delegate.isMoveLegal(b, start, end);

        if (end.getPiece() instanceof King) return false;

        int er = end.getX(), ec = end.getY();

        if (lavaManager.isLava(er, ec)) return false;

        Piece piece = start.getPiece();
        if (piece instanceof Queen || piece instanceof Rook || piece instanceof Bishop) {
            if (rayBlockedByLava(start.getX(), start.getY(), er, ec)) return false;
        }


        boolean defIsWhite = (piece != null) && piece.isWhite();
        install(defIsWhite);
        try {
            return delegate.isMoveLegal(b, start, end);
        } finally {
            uninstall();
        }
    }

    @Override
    public boolean isKingInCheck(Board b, boolean isWhite) {
        if (!lavaManager.isEnabled()) return delegate.isKingInCheck(b, isWhite);
        install(isWhite);
        try {
            return delegate.isKingInCheck(b, isWhite);
        } finally {
            uninstall();
        }
    }

    @Override
    public boolean isSquareUnderAttack(Board b, Spot spot, boolean isAttackerWhite) {
        if (!lavaManager.isEnabled()) return delegate.isSquareUnderAttack(b, spot, isAttackerWhite);
        install(!isAttackerWhite);
        try {
            return delegate.isSquareUnderAttack(b, spot, isAttackerWhite);
        } finally {
            uninstall();
        }
    }

    @Override
    public boolean isCastlingMoveLegal(Board b, Spot start, Spot end) {
        if (!lavaManager.isEnabled()) return delegate.isCastlingMoveLegal(b, start, end);
        Piece piece = start.getPiece();
        install((piece != null) && piece.isWhite());
        try {
            return delegate.isCastlingMoveLegal(b, start, end);
        } finally {
            uninstall();
        }
    }

    @Override
    public boolean hasLegalMoves(Board b, boolean isWhite) {
        if (!lavaManager.isEnabled()) return delegate.hasLegalMoves(b, isWhite);

        install(isWhite);
        try {
            boolean found = false;
            outer:
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    Spot start = b.getBox(r, c);
                    Piece p = start.getPiece();
                    if (p == null || p.isWhite() != isWhite
                            || p == BLOCKER_W || p == BLOCKER_B) continue;

                    for (int er = 0; er < 8; er++) {
                        for (int ec = 0; ec < 8; ec++) {
                            if (lavaManager.isLava(er, ec)) continue;
                            if ((p instanceof Queen || p instanceof Rook || p instanceof Bishop)
                                    && rayBlockedByLava(r, c, er, ec)) continue;

                            if (delegate.isMoveLegal(b, start, b.getBox(er, ec))) {
                                found = true;
                                break outer;
                            }
                        }
                    }
                }
            }
            return found;
        } finally {
            uninstall();
        }
    }

    @Override
    public boolean isInsufficientMaterial(Board b) {
        return delegate.isInsufficientMaterial(b);
    }

    @Override
    public Spot findKing(Board b, boolean isWhite) {
        return delegate.findKing(b, isWhite);
    }

    // ── Blocker management ────────────────────────────────────────────────────

    private void install(boolean defenderIsWhite) {
        Piece blocker = defenderIsWhite ? BLOCKER_W : BLOCKER_B;
        for (int encoded : lavaManager.getLavaSquares()) {
            int[] pos = LavaManager.decode(encoded);
            Spot s = board.getBox(pos[0], pos[1]);
            if (s.getPiece() == null) {
                s.setPiece(blocker);
                activeBlockers.add(s);
            }
        }
    }

    private void uninstall() {
        for (Spot s : activeBlockers) {
            Piece p = s.getPiece();
            if (p == BLOCKER_W || p == BLOCKER_B) s.setPiece(null);
        }
        activeBlockers.clear();
    }

    // ── Ray helper ────────────────────────────────────────────────────────────

    private boolean rayBlockedByLava(int sr, int sc, int er, int ec) {
        int dr = er - sr, dc = ec - sc;
        boolean ortho = (dr == 0 || dc == 0);
        boolean diagonal = (Math.abs(dr) == Math.abs(dc));
        if (!ortho && !diagonal) return false;
        if (dr == 0 && dc == 0) return false;

        int stepR = Integer.signum(dr), stepC = Integer.signum(dc);
        int steps = Math.max(Math.abs(dr), Math.abs(dc));
        for (int i = 1; i < steps; i++) {
            if (lavaManager.isLava(sr + i * stepR, sc + i * stepC)) return true;
        }
        return false;
    }
}