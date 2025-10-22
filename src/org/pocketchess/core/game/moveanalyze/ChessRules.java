package org.pocketchess.core.game.moveanalyze;


import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.Spot;


public interface ChessRules {

    boolean isMoveLegal(Board board, Spot start, Spot end);

    boolean isKingInCheck(Board board, boolean isWhite);

    boolean isSquareUnderAttack(Board board, Spot spot, boolean isAttackerWhite);

    Spot findKing(Board board, boolean isWhite);

    boolean isCastlingMoveLegal(Board board, Spot start, Spot end);

    boolean hasLegalMoves(Board board, boolean isWhite);

    boolean isInsufficientMaterial(Board board);
}