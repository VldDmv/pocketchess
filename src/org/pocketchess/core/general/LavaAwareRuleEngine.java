package org.pocketchess.core.general;

import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorator around {@link RuleEngine} that makes every rule check lava-aware.
 *
 * Strategy:
 *  - Before delegating to the real engine, place a temporary {@link Pawn} of the
 *    DEFENDER's colour on every empty lava square.  AttackChecker stops
 *    sliding rays at any occupied square, so those blockers cut off all lines of
 *    sight that would cross lava.
 *  - For {@link #isMoveLegal} also check that (a) the destination is not a lava
 *    square and (b) sliding pieces don't jump over a lava square.
 *  - When lava is disabled the delegate is called directly (zero overhead).
 *
 * Because this class IS the {@link ChessRules} instance handed to every
 * sub-system (PlayerMoveService, GameStatusManager, FastMoveGenerator …),
 * all rule checks are automatically lava-aware with no further changes needed
 * elsewhere.
 */
public class LavaAwareRuleEngine implements ChessRules {
    private boolean isAISimulation = false;
    private final ChessRules  delegate;
    private final LavaManager lavaManager;
    private final Board       board;

    private static final Piece BLOCKER_W = new Pawn(true);
    private static final Piece BLOCKER_B = new Pawn(false);

    private final List<Spot> activeBlockers = new ArrayList<>();

    public LavaAwareRuleEngine(ChessRules delegate, LavaManager lavaManager, Board board) {
        this.delegate    = delegate;
        this.lavaManager = lavaManager;
        this.board       = board;
    }
    public void setAISimulation(boolean isAISimulation) {
        this.isAISimulation = isAISimulation;
    }
    // ── ChessRules delegation ─────────────────────────────────────────────────

    @Override
    public boolean isMoveLegal(Board b, Spot start, Spot end) {
        if (!lavaManager.isEnabled()) return delegate.isMoveLegal(b, start, end);

        // Safety net: capturing a king should never be legal
        if (end.getPiece() instanceof King) return false;

        int er = end.getX(), ec = end.getY();

        // 1. Cannot move TO a lava square
        if (lavaManager.isLava(er, ec)) return false;

        // 2. Sliding pieces cannot pass THROUGH a lava square
        Piece piece = start.getPiece();
        if (piece instanceof Queen || piece instanceof Rook || piece instanceof Bishop) {
            if (rayBlockedByLava(start.getX(), start.getY(), er, ec)) return false;
        }

        // 3. Install blockers so the engine's king-safety simulation sees lava walls
        boolean defIsWhite = (piece != null) && piece.isWhite();
        install(defIsWhite);
        boolean result = delegate.isMoveLegal(b, start, end);
        uninstall();
        return result;
    }

    @Override
    public boolean isKingInCheck(Board b, boolean isWhite) {
        if (!lavaManager.isEnabled()) return delegate.isKingInCheck(b, isWhite);
        install(isWhite);               // defender = isWhite
        boolean result = delegate.isKingInCheck(b, isWhite);
        uninstall();
        return result;
    }

    @Override
    public boolean isSquareUnderAttack(Board b, Spot spot, boolean isAttackerWhite) {
        if (!lavaManager.isEnabled()) return delegate.isSquareUnderAttack(b, spot, isAttackerWhite);
        install(!isAttackerWhite);      // defender = !isAttackerWhite
        boolean result = delegate.isSquareUnderAttack(b, spot, isAttackerWhite);
        uninstall();
        return result;
    }

    @Override
    public boolean isCastlingMoveLegal(Board b, Spot start, Spot end) {
        if (!lavaManager.isEnabled()) return delegate.isCastlingMoveLegal(b, start, end);
        Piece piece = start.getPiece();
        install((piece != null) && piece.isWhite());
        boolean result = delegate.isCastlingMoveLegal(b, start, end);
        uninstall();
        return result;
    }

    /**
     * Overrides the default to also filter out moves whose destination is lava
     * or whose ray crosses lava — the delegate's own hasLegalMoves uses its own
     * isMoveLegal which doesn't know about lava destinations/rays.
     */
    @Override
    public boolean hasLegalMoves(Board b, boolean isWhite) {
        if (!lavaManager.isEnabled()) return delegate.hasLegalMoves(b, isWhite);

        install(isWhite);

        boolean found = false;
        outer:
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Spot start = b.getBox(r, c);
                Piece p    = start.getPiece();
                if (p == null || p.isWhite() != isWhite
                        || p == BLOCKER_W || p == BLOCKER_B) continue;

                for (int er = 0; er < 8; er++) {
                    for (int ec = 0; ec < 8; ec++) {
                        // Lava-specific filters (destination + ray)
                        if (lavaManager.isLava(er, ec)) continue;
                        if ((p instanceof Queen || p instanceof Rook || p instanceof Bishop)
                                && rayBlockedByLava(r, c, er, ec)) continue;

                        // Delegate for normal chess legality (blockers are on board)
                        if (delegate.isMoveLegal(b, start, b.getBox(er, ec))) {
                            found = true;
                            break outer;
                        }
                    }
                }
            }
        }

        uninstall();
        return found;
    }

    @Override
    public boolean isInsufficientMaterial(Board b) {
        return delegate.isInsufficientMaterial(b);   // lava-independent
    }

    @Override
    public Spot findKing(Board b, boolean isWhite) {
        return delegate.findKing(b, isWhite);         // lava-independent
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
        if (isAISimulation) {
            for (int encoded : lavaManager.getWarningSquares()) {
                int[] pos = LavaManager.decode(encoded);
                Spot s = board.getBox(pos[0], pos[1]);
                if (s.getPiece() == null) {
                    s.setPiece(blocker);
                    activeBlockers.add(s);
                }
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
        boolean ortho    = (dr == 0 || dc == 0);
        boolean diagonal = (Math.abs(dr) == Math.abs(dc));
        if (!ortho && !diagonal) return false;
        if (dr == 0 && dc == 0)  return false;

        int stepR = Integer.signum(dr), stepC = Integer.signum(dc);
        int steps = Math.max(Math.abs(dr), Math.abs(dc));
        for (int i = 1; i < steps; i++) {
            if (lavaManager.isLava(sr + i * stepR, sc + i * stepC)) return true;
        }
        return false;
    }
}