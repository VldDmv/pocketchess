package org.pocketchess.core.general;

import org.pocketchess.core.game.moveanalyze.AttackChecker;
import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.game.moveanalyze.MaterialChecker;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;

/**
 * Chess rules engine - implements all chess rules.
 * RESPONSIBILITIES:
 * - Move validation
 * - Check detection
 * - Checkmate/Stalemate detection
 * - Castling validation
 * - Insufficient material check (automatic draw)
 */
public class RuleEngine implements ChessRules {
    private final AttackChecker attackChecker;
    private final MaterialChecker materialChecker;

    public RuleEngine() {
        this.attackChecker = new AttackChecker();
        this.materialChecker = new MaterialChecker();
    }

    /**
     * Checks if a move is legal according to chess rules.
     */
    @Override
    public boolean isMoveLegal(Board board, Spot start, Spot end) {
        Piece sourcePiece = start.getPiece();
        if (sourcePiece == null || !sourcePiece.canMove(board, start, end)) {
            return false;
        }

        return isKingSafeAfterMove(board, start, end, sourcePiece);
    }

    /**
     * Checks if a king is currently in check.
     */
    @Override
    public boolean isKingInCheck(Board board, boolean isWhite) {
        Spot kingSpot = findKing(board, isWhite);
        if (kingSpot == null) {
            return false;
        }
        return isSquareUnderAttack(board, kingSpot, !isWhite);
    }

    /**
     * Checks if a square is under attack by a specific color.
     */
    @Override
    public boolean isSquareUnderAttack(Board board, Spot spot, boolean isAttackerWhite) {
        return attackChecker.isSquareUnderAttack(board, spot, isAttackerWhite);
    }

    /**
     * Finds the king of specified color on the board.
     */
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

    /**
     * Validates castling move.
     * Castling is legal if:
     * 1. King hasn't moved
     * 2. King is not in check
     * 3. Rook hasn't moved
     * 4. No pieces between king and rook
     * 5. King doesn't pass through or land on attacked square
     */
    @Override
    public boolean isCastlingMoveLegal(Board board, Spot start, Spot end) {
        Piece king = start.getPiece();
        if (!(king instanceof King) || ((King) king).hasMoved() || isKingInCheck(board, king.isWhite())) {
            return false;
        }

        return canCastleThroughSquares(board, start, end, king);
    }

    /**
     * Checks if the current player has any legal moves.
     */
    @Override
    public boolean hasLegalMoves(Board board, boolean isWhite) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Spot startSpot = board.getBox(i, j);
                if (startSpot.getPiece() != null && startSpot.getPiece().isWhite() == isWhite) {
                    if (hasAnyLegalMove(board, startSpot)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isInsufficientMaterial(Board board) {
        return materialChecker.isInsufficientMaterial(board);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Simulates a move to check if king becomes safe.
     */
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
     * Validates that king can safely castle through squares.
     */
    private boolean canCastleThroughSquares(Board board, Spot start, Spot end, Piece king) {
        int direction = (end.getY() - start.getY()) > 0 ? 1 : -1;

        for (int i = 1; i <= 2; i++) {
            Spot throughSpot = board.getBox(start.getX(), start.getY() + i * direction);
            if (i < 2 && throughSpot.getPiece() != null) {
                return false; // Path blocked
            }
            if (isSquareUnderAttack(board, throughSpot, !king.isWhite())) {
                return false; // Square under attack
            }
        }

        return king.canMove(board, start, end);
    }

    /**
     * Checks if a piece has at least one legal move.
     */
    private boolean hasAnyLegalMove(Board board, Spot startSpot) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                if (isMoveLegal(board, startSpot, board.getBox(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }
}