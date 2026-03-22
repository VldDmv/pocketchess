package org.pocketchess.core.general;

import org.pocketchess.core.game.moveanalyze.AttackChecker;
import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.game.moveanalyze.MaterialChecker;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Rook;
import org.pocketchess.core.pieces.Spot;

/**
 * Chess rules engine.
 *
 * Chess960 change: {@link #canCastleThroughSquares} now allows a friendly
 * unmoved rook to occupy squares on the king's transit path (Chess960 castling
 * positions where the rook is between the king and its destination).
 */
public class RuleEngine implements ChessRules {
    private final AttackChecker  attackChecker;
    private final MaterialChecker materialChecker;

    public RuleEngine() {
        this.attackChecker   = new AttackChecker();
        this.materialChecker = new MaterialChecker();
    }

    @Override
    public boolean isMoveLegal(Board board, Spot start, Spot end) {
        Piece sourcePiece = start.getPiece();
        if (sourcePiece == null || !sourcePiece.canMove(board, start, end)) {
            return false;
        }
        return isKingSafeAfterMove(board, start, end, sourcePiece);
    }

    @Override
    public boolean isKingInCheck(Board board, boolean isWhite) {
        Spot kingSpot = findKing(board, isWhite);
        if (kingSpot == null) return false;
        return isSquareUnderAttack(board, kingSpot, !isWhite);
    }

    @Override
    public boolean isSquareUnderAttack(Board board, Spot spot, boolean isAttackerWhite) {
        return attackChecker.isSquareUnderAttack(board, spot, isAttackerWhite);
    }

    @Override
    public Spot findKing(Board board, boolean isWhite) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Spot spot = board.getBox(i, j);
                if (spot.getPiece() instanceof King && spot.getPiece().isWhite() == isWhite) {
                    return spot;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isCastlingMoveLegal(Board board, Spot start, Spot end) {
        Piece king = start.getPiece();
        if (!(king instanceof King) || ((King) king).hasMoved()
                || isKingInCheck(board, king.isWhite())) {
            return false;
        }
        return canCastleThroughSquares(board, start, end, king);
    }

    @Override
    public boolean hasLegalMoves(Board board, boolean isWhite) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Spot startSpot = board.getBox(i, j);
                if (startSpot.getPiece() != null
                        && startSpot.getPiece().isWhite() == isWhite) {
                    if (hasAnyLegalMove(board, startSpot)) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isInsufficientMaterial(Board board) {
        return materialChecker.isInsufficientMaterial(board);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isKingSafeAfterMove(Board board, Spot start, Spot end, Piece sourcePiece) {
        Piece capturedPiece = end.getPiece();
        end.setPiece(sourcePiece);
        start.setPiece(null);

        boolean isKingSafe = !isKingInCheck(board, sourcePiece.isWhite());

        start.setPiece(sourcePiece);
        end.setPiece(capturedPiece);
        return isKingSafe;
    }

    /**
     * Validates that the king does not pass through or land on an attacked
     * square during castling, and that the transit path is clear.
     *
     * Chess960 fix: a friendly unmoved rook may occupy any of the two
     * intermediate squares (start+1, start+2).  In standard chess the rook
     * is always beyond the king's destination so this never triggers; in
     * Chess960 the rook can be exactly 1 or 2 squares away.
     */
    private boolean canCastleThroughSquares(Board board, Spot start, Spot end, Piece king) {
        int direction = (end.getY() - start.getY()) > 0 ? 1 : -1;

        for (int i = 1; i <= 2; i++) {
            int col = start.getY() + i * direction;
            if (col < 0 || col > 7) return false;
            Spot throughSpot = board.getBox(start.getX(), col);
            Piece p          = throughSpot.getPiece();

            // The square must be empty, UNLESS it is the castling rook
            // (an unmoved friendly Rook).  This handles Chess960 positions
            // where the rook sits between the king and the king's destination.
            if (i < 2 && p != null) {
                boolean isCastlingRook = (p instanceof Rook)
                        && p.isWhite() == king.isWhite()
                        && !((Rook) p).hasMoved();
                if (!isCastlingRook) return false; // genuinely blocked
            }

            // King must not pass through a square under attack
            if (isSquareUnderAttack(board, throughSpot, !king.isWhite())) {
                return false;
            }
        }

        return king.canMove(board, start, end);
    }

    private boolean hasAnyLegalMove(Board board, Spot startSpot) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                if (isMoveLegal(board, startSpot, board.getBox(x, y))) return true;
            }
        }
        return false;
    }
}