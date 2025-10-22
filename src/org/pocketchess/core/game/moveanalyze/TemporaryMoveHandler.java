package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

/**
 * Time progress handler for AI
 */
public class TemporaryMoveHandler {
    private final Board board;
    private final GamePositionTracker positionTracker;
    private Spot enPassantTargetBeforeNullMove;

    public TemporaryMoveHandler(Board board, GamePositionTracker positionTracker) {
        this.board = board;
        this.positionTracker = positionTracker;
    }

    /**
     * Performs a temporary move on the board
     * Handles all types of moves:
     * - Normal move
     * - Capture (including en passant)
     * - Castling
     * - Pawn promotion
     */
    public boolean makeTemporaryMove(Move move, boolean isWhiteTurn) {
        Spot startSpot = move.start;
        Spot endSpot = move.end;
        Piece sourcePiece = move.pieceMoved;

        if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            move.pieceKilled = capturedPawnSpot.getPiece();
            capturedPawnSpot.setPiece(null);
        }

        endSpot.setPiece(sourcePiece);
        startSpot.setPiece(null);


        if (move.promotedTo != null) {
            endSpot.setPiece(move.promotedTo);
        }

        if (move.wasCastlingMove) {
            handleCastlingRook(startSpot, endSpot);
        }


        updatePieceState(sourcePiece);

        updateEnPassantTarget(sourcePiece, startSpot, endSpot);

        isWhiteTurn = !isWhiteTurn;
        positionTracker.recordPosition(board, isWhiteTurn);
        return isWhiteTurn;
    }

    /**
     * Cancels a temporary move
     * Restores the board to its state before the move
     */
    public boolean undoTemporaryMove(Move move, boolean isWhiteTurn) {

        positionTracker.removeLastPosition(board, isWhiteTurn);
        isWhiteTurn = !isWhiteTurn;

        Spot startSpot = move.start;
        Spot endSpot = move.end;

        startSpot.setPiece(move.pieceMoved);
        endSpot.setPiece(move.pieceKilled);

        if (move.wasCastlingMove) {
            undoCastlingRook(startSpot, endSpot);
        }

        if (move.pieceMoved instanceof Pawn && endSpot == move.enPassantTargetBeforeMove) {
            endSpot.setPiece(null);
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            capturedPawnSpot.setPiece(move.pieceKilled);
        }

        if (move.wasFirstMoveForPiece) {
            restorePieceState(move.pieceMoved);
        }

        board.setEnPassantTargetSquare(move.enPassantTargetBeforeMove);
        return isWhiteTurn;
    }

    /**
     * Performs a "null move" skipping a move
     * Used for null move pruning in AI
     */
    public boolean makeNullMove(boolean isWhiteTurn) {
        this.enPassantTargetBeforeNullMove = board.getEnPassantTargetSquare();
        board.setEnPassantTargetSquare(null);
        isWhiteTurn = !isWhiteTurn;
        return isWhiteTurn;
    }

    /**
     * Cancels the "null move"
     */
    public boolean undoNullMove(boolean isWhiteTurn) {
        isWhiteTurn = !isWhiteTurn;
        board.setEnPassantTargetSquare(this.enPassantTargetBeforeNullMove);
        return isWhiteTurn;
    }


    /**
     * Moves the rook when castling
     */
    private void handleCastlingRook(Spot startSpot, Spot endSpot) {
        int rookY = (endSpot.getY() > startSpot.getY()) ? 7 : 0;
        int rookNewY = (endSpot.getY() > startSpot.getY()) ? 5 : 3;
        Spot rookStart = board.getBox(startSpot.getX(), rookY);
        Spot rookEnd = board.getBox(startSpot.getX(), rookNewY);
        rookEnd.setPiece(rookStart.getPiece());
        rookStart.setPiece(null);
    }

    /**
     * Cancels the rook's move when castling
     */
    private void undoCastlingRook(Spot startSpot, Spot endSpot) {
        int rookY = (endSpot.getY() > startSpot.getY()) ? 7 : 0;
        int rookNewY = (endSpot.getY() > startSpot.getY()) ? 5 : 3;
        Spot rookStart = board.getBox(startSpot.getX(), rookY);
        Spot rookEnd = board.getBox(startSpot.getX(), rookNewY);
        rookStart.setPiece(rookEnd.getPiece());
        rookEnd.setPiece(null);
    }

    /**
     * Sets "moved" flag for the king and rook
     */
    private void updatePieceState(Piece piece) {
        if (piece instanceof King) {
            ((King) piece).setHasMoved(true);
        }
        if (piece instanceof Rook) {
            ((Rook) piece).setHasMoved(true);
        }
    }

    /**
     * Restores "not moved" flag for king and rook
     */
    private void restorePieceState(Piece piece) {
        if (piece instanceof King) {
            ((King) piece).setHasMoved(false);
        }
        if (piece instanceof Rook) {
            ((Rook) piece).setHasMoved(false);
        }
    }

    /**
     * Updates the en passant field after a pawn moves 2 squares
     */
    private void updateEnPassantTarget(Piece sourcePiece, Spot startSpot, Spot endSpot) {
        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(
                    board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY())
            );
        } else {
            board.setEnPassantTargetSquare(null);
        }
    }
}