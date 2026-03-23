package org.pocketchess.core.game.status;

import org.pocketchess.core.game.moveanalyze.CastlingUtils;
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

    public Move createAndExecuteMove(Spot startSpot, Spot endSpot,
                                     boolean isCastling, int currentHalfMoves) {
        Move move = new Move();
        Piece sourcePiece = startSpot.getPiece();

        move.start = startSpot;
        move.end = endSpot;
        move.pieceMoved = sourcePiece;
        move.enPassantTargetBeforeMove = board.getEnPassantTargetSquare();
        move.wasCastlingMove = isCastling;

        if (sourcePiece instanceof King) {
            move.wasFirstMoveForPiece = !((King) sourcePiece).hasMoved();
        } else if (sourcePiece instanceof Rook) {
            move.wasFirstMoveForPiece = !((Rook) sourcePiece).hasMoved();
        }

        Piece capturedPiece = null;

        if (isCastling) {
            move.pieceKilled = null;
        } else {
            if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
                Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
                capturedPiece = capturedPawnSpot.getPiece();
                move.pieceKilled = capturedPiece;
                capturedPawnSpot.setPiece(null);
            } else {
                capturedPiece = endSpot.getPiece();
                move.pieceKilled = capturedPiece;
            }

            if (capturedPiece != null) {
                if (capturedPiece.isWhite()) blackCapturedPieces.add(capturedPiece);
                else whiteCapturedPieces.add(capturedPiece);
            }
        }

        int kingOrigCol = startSpot.getY();
        int kingDestCol = endSpot.getY();

        if (isCastling) {
            executeCastlingAtomic(startSpot.getX(), kingOrigCol, kingDestCol, sourcePiece);
        } else {
            endSpot.setPiece(sourcePiece);
            startSpot.setPiece(null);
        }

        if (sourcePiece instanceof King) ((King) sourcePiece).setHasMoved(true);
        if (sourcePiece instanceof Rook) ((Rook) sourcePiece).setHasMoved(true);

        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(
                    board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY()));
        } else {
            board.setEnPassantTargetSquare(null);
        }

        int newHalfMoves = (sourcePiece instanceof Pawn || capturedPiece != null)
                ? 0
                : currentHalfMoves + 1;
        move.halfMovesAfterMove = newHalfMoves;

        return move;
    }

    public void replayMove(Move move) {
        Spot startSpot = board.getBox(move.start.getX(), move.start.getY());
        Spot endSpot   = board.getBox(move.end.getX(),   move.end.getY());
        Piece sourcePiece = startSpot.getPiece();

        // ── En-passant capture ────────────────────────────────────────────────
        if (sourcePiece instanceof Pawn && endSpot == board.getEnPassantTargetSquare()) {
            Spot capturedPawnSpot = board.getBox(startSpot.getX(), endSpot.getY());
            Piece capturedPiece = capturedPawnSpot.getPiece();
            if (capturedPiece != null) {
                (capturedPiece.isWhite() ? blackCapturedPieces : whiteCapturedPieces)
                        .add(capturedPiece);
            }
            capturedPawnSpot.setPiece(null);

        } else if (!move.wasCastlingMove && endSpot.getPiece() != null) {
            Piece capturedPiece = endSpot.getPiece();
            (capturedPiece.isWhite() ? blackCapturedPieces : whiteCapturedPieces)
                    .add(capturedPiece);
        }

        int replayKingOrigCol = startSpot.getY();
        int replayKingDestCol = endSpot.getY();

        if (move.wasCastlingMove) {
            executeCastlingAtomic(startSpot.getX(), replayKingOrigCol,
                    replayKingDestCol, sourcePiece);
        } else {
            // ── FIX: move the piece first, THEN apply promotion ───────────────
            // Previously: promotedTo was set BEFORE sourcePiece was placed,
            // then endSpot.setPiece(sourcePiece) overwrote the queen with the pawn.
            endSpot.setPiece(sourcePiece);
            startSpot.setPiece(null);

            // Apply promotion AFTER placing the pawn on the destination square.
            if (move.promotedTo != null) {
                endSpot.setPiece(move.promotedTo);
            }
        }

        if (sourcePiece instanceof King) ((King) sourcePiece).setHasMoved(true);
        if (sourcePiece instanceof Rook) ((Rook) sourcePiece).setHasMoved(true);

        if (sourcePiece instanceof Pawn && Math.abs(startSpot.getX() - endSpot.getX()) == 2) {
            board.setEnPassantTargetSquare(
                    board.getBox((startSpot.getX() + endSpot.getX()) / 2, startSpot.getY()));
        } else {
            board.setEnPassantTargetSquare(null);
        }
    }

    private void executeCastlingAtomic(int row, int kingOrigCol,
                                       int kingDestCol, Piece king) {
        int direction = CastlingUtils.castlingDirection(kingDestCol);
        int rookNewY  = CastlingUtils.rookDestCol(kingDestCol);

        int rookY = CastlingUtils.findCastlingRookCol(board, row, kingOrigCol, direction);
        Piece rook = (rookY != -1) ? board.getBox(row, rookY).getPiece() : null;

        board.getBox(row, kingOrigCol).setPiece(null);
        if (rookY != -1 && rookY != kingOrigCol) board.getBox(row, rookY).setPiece(null);

        board.getBox(row, kingDestCol).setPiece(king);
        if (rook instanceof Rook) {
            board.getBox(row, rookNewY).setPiece(rook);
            ((Rook) rook).setHasMoved(true);
        }
    }

    public void clearCapturedPieces() {
        whiteCapturedPieces.clear();
        blackCapturedPieces.clear();
    }

    public List<Piece> getWhiteCapturedPieces() { return whiteCapturedPieces; }
    public List<Piece> getBlackCapturedPieces() { return blackCapturedPieces; }
}