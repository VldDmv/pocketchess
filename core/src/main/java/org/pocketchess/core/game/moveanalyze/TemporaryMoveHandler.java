package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.moveanalyze.CastlingUtils;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

/**
 * Temporary move handler for AI search.
 */
public class TemporaryMoveHandler {
    private final Board board;
    private final GamePositionTracker positionTracker;
    private Spot enPassantTargetBeforeNullMove;

    public TemporaryMoveHandler(Board board, GamePositionTracker positionTracker) {
        this.board            = board;
        this.positionTracker  = positionTracker;
    }

    public boolean makeTemporaryMove(Move move, boolean isWhiteTurn) {
        Spot  startSpot   = move.start;
        Spot  endSpot     = move.end;
        Piece sourcePiece = move.pieceMoved;

        // En-passant
        if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            move.pieceKilled = capturedPawnSpot.getPiece();
            capturedPawnSpot.setPiece(null);
        }

        int kingOrigCol = startSpot.getY();
        int kingDestCol = endSpot.getY();

        if (move.wasCastlingMove) {
            executeCastlingAtomic(startSpot.getX(), kingOrigCol, kingDestCol, sourcePiece, move);
        } else {
            endSpot.setPiece(sourcePiece);
            if (startSpot != endSpot) startSpot.setPiece(null);
            if (move.promotedTo != null) endSpot.setPiece(move.promotedTo);
        }

        updatePieceState(sourcePiece);
        updateEnPassantTarget(sourcePiece, startSpot, endSpot);

        isWhiteTurn = !isWhiteTurn;
        positionTracker.recordPosition(board, isWhiteTurn);
        return isWhiteTurn;
    }

    public boolean undoTemporaryMove(Move move, boolean isWhiteTurn) {
        positionTracker.removeLastPosition(board, isWhiteTurn);
        isWhiteTurn = !isWhiteTurn;

        Spot startSpot = move.start;
        Spot endSpot   = move.end;

        startSpot.setPiece(move.pieceMoved);
        endSpot.setPiece(move.pieceKilled);

        if (move.wasCastlingMove) undoCastlingRook(startSpot, endSpot, move);

        if (move.pieceMoved instanceof Pawn && endSpot == move.enPassantTargetBeforeMove) {
            endSpot.setPiece(null);
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            capturedPawnSpot.setPiece(move.pieceKilled);
        }

        if (move.wasFirstMoveForPiece) restorePieceState(move.pieceMoved);

        board.setEnPassantTargetSquare(move.enPassantTargetBeforeMove);
        return isWhiteTurn;
    }

    public boolean makeNullMove(boolean isWhiteTurn) {
        this.enPassantTargetBeforeNullMove = board.getEnPassantTargetSquare();
        board.setEnPassantTargetSquare(null);
        return !isWhiteTurn;
    }

    public boolean undoNullMove(boolean isWhiteTurn) {
        board.setEnPassantTargetSquare(this.enPassantTargetBeforeNullMove);
        return !isWhiteTurn;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Castling helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Atomic Chess960 castling for AI search.
     * Finds rook before touching the board, clears both sources, places both pieces.
     * Saves rookFromCol in move for undo.
     */
    private void executeCastlingAtomic(int row, int kingOrigCol, int kingDestCol,
                                       Piece king, Move move) {
        int direction = CastlingUtils.castlingDirection(kingDestCol);
        int rookNewY  = CastlingUtils.rookDestCol(kingDestCol);

        int rookFromY = CastlingUtils.findCastlingRookCol(board, row, kingOrigCol, direction);
        Piece rook = (rookFromY != -1) ? board.getBox(row, rookFromY).getPiece() : null;
        move.chess960RookFromCol = rookFromY;

        board.getBox(row, kingOrigCol).setPiece(null);
        if (rookFromY != -1 && rookFromY != kingOrigCol) board.getBox(row, rookFromY).setPiece(null);

        board.getBox(row, kingDestCol).setPiece(king);
        if (rook instanceof Rook) {
            board.getBox(row, rookNewY).setPiece(rook);
            ((Rook) rook).setHasMoved(true);
        }
    }

    /**
     * Restores king and rook to pre-castle positions.
     * undoTemporaryMove already restores the king via startSpot/endSpot,
     * so here we only restore the rook.
     */
    private void undoCastlingRook(Spot startSpot, Spot endSpot, Move move) {
        int kingDestCol = endSpot.getY();
        int rookNewY    = CastlingUtils.rookDestCol(kingDestCol);

        int rookFromY = move.chess960RookFromCol != -1
                ? move.chess960RookFromCol
                : (kingDestCol == 6 ? 7 : 0);  // standard fallback

        Spot rookOrigSpot = board.getBox(startSpot.getX(), rookFromY);
        Spot rookCurSpot  = board.getBox(startSpot.getX(), rookNewY);

        rookOrigSpot.setPiece(rookCurSpot.getPiece());
        if (rookFromY != rookNewY) rookCurSpot.setPiece(null);
    }

    private void updatePieceState(Piece piece) {
        if (piece instanceof King) ((King) piece).setHasMoved(true);
        if (piece instanceof Rook) ((Rook) piece).setHasMoved(true);
    }

    private void restorePieceState(Piece piece) {
        if (piece instanceof King) ((King) piece).setHasMoved(false);
        if (piece instanceof Rook) ((Rook) piece).setHasMoved(false);
    }

    private void updateEnPassantTarget(Piece sourcePiece, Spot startSpot, Spot endSpot) {
        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(
                    board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY()));
        } else {
            board.setEnPassantTargetSquare(null);
        }
    }
}