package org.pocketchess.core.game.status;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for executing moves and managing captured pieces.
 */
public class GameMoveExecutor {
    private final Board board;
    private final List<Piece> whiteCapturedPieces = new ArrayList<>();
    private final List<Piece> blackCapturedPieces = new ArrayList<>();

    public GameMoveExecutor(Board board) {
        this.board = board;
    }


    public Move createAndExecuteMove(Spot startSpot, Spot endSpot, boolean isCastling, int currentHalfMoves) {
        Move move = new Move();
        Piece sourcePiece = startSpot.getPiece();

        move.start = startSpot;
        move.end = endSpot;
        move.pieceMoved = sourcePiece;
        move.pieceKilled = endSpot.getPiece();
        move.enPassantTargetBeforeMove = board.getEnPassantTargetSquare();
        move.wasCastlingMove = isCastling;

        // Save information about the first move for castling
        if (sourcePiece instanceof King) {
            move.wasFirstMoveForPiece = !((King) sourcePiece).hasMoved();
        } else if (sourcePiece instanceof Rook) {
            move.wasFirstMoveForPiece = !((Rook) sourcePiece).hasMoved();
        }

        // Handling of en passant
        Piece capturedPiece = endSpot.getPiece();
        if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            capturedPiece = capturedPawnSpot.getPiece();
            move.pieceKilled = capturedPiece;
            capturedPawnSpot.setPiece(null);
        }

        if (capturedPiece != null) {
            if (capturedPiece.isWhite()) {
                blackCapturedPieces.add(capturedPiece);
            } else {
                whiteCapturedPieces.add(capturedPiece);
            }
        }


        endSpot.setPiece(sourcePiece);
        startSpot.setPiece(null);


        if (isCastling) {
            handleRookMovementForCastling(startSpot.getX(), startSpot.getY(), endSpot.getY());
        }
        if (sourcePiece instanceof King) {
            ((King) sourcePiece).setHasMoved(true);
        }
        if (sourcePiece instanceof Rook) {
            ((Rook) sourcePiece).setHasMoved(true);
        }

        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY()));
        } else {
            board.setEnPassantTargetSquare(null);
        }

        int newHalfMoves;
        if (sourcePiece instanceof Pawn || capturedPiece != null) {
            newHalfMoves = 0;
        } else {
            newHalfMoves = currentHalfMoves + 1;
        }
        move.halfMovesAfterMove = newHalfMoves;

        return move;
    }

    /**
     * Replays the progress of navigating through history
     */
    public void replayMove(Move move) {
        Spot startSpot = board.getBox(move.start.getX(), move.start.getY());
        Spot endSpot = board.getBox(move.end.getX(), move.end.getY());
        Piece sourcePiece = startSpot.getPiece();

        if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            Piece capturedPiece = capturedPawnSpot.getPiece();
            if (capturedPiece != null) {
                (capturedPiece.isWhite() ? blackCapturedPieces : whiteCapturedPieces).add(capturedPiece);
            }
            capturedPawnSpot.setPiece(null);
        } else if (endSpot.getPiece() != null) {
            Piece capturedPiece = endSpot.getPiece();
            (capturedPiece.isWhite() ? blackCapturedPieces : whiteCapturedPieces).add(capturedPiece);
        }


        endSpot.setPiece(sourcePiece);
        startSpot.setPiece(null);


        if (move.promotedTo != null) {
            endSpot.setPiece(move.promotedTo);
        }


        if (move.wasCastlingMove) {
            handleRookMovementForCastling(startSpot.getX(), startSpot.getY(), endSpot.getY());
        }


        if (sourcePiece instanceof King) {
            ((King) sourcePiece).setHasMoved(true);
        }
        if (sourcePiece instanceof Rook) {
            ((Rook) sourcePiece).setHasMoved(true);
        }


        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY()));
        } else {
            board.setEnPassantTargetSquare(null);
        }
    }

    private void handleRookMovementForCastling(int startX, int startY, int endY) {
        int rookY = (endY - startY) > 0 ? 7 : 0;
        int rookNewY = (endY - startY) > 0 ? 5 : 3;
        Spot rookStartSpot = board.getBox(startX, rookY);
        Spot rookEndSpot = board.getBox(startX, rookNewY);
        Piece rook = rookStartSpot.getPiece();

        if (rook instanceof Rook) {
            rookEndSpot.setPiece(rook);
            rookStartSpot.setPiece(null);
            ((Rook) rook).setHasMoved(true);
        }
    }

    public void clearCapturedPieces() {
        whiteCapturedPieces.clear();
        blackCapturedPieces.clear();
    }

    public List<Piece> getWhiteCapturedPieces() {
        return whiteCapturedPieces;
    }

    public List<Piece> getBlackCapturedPieces() {
        return blackCapturedPieces;
    }
}