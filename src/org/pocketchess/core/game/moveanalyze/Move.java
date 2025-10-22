package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;

public class Move {
    public int halfMovesAfterMove;
    public Spot start;
    public Spot end;
    public Piece pieceMoved;
    public Piece pieceKilled;
    public boolean wasFirstMoveForPiece;
    public boolean wasCastlingMove = false;
    public Spot enPassantTargetBeforeMove;
    public Piece promotedTo = null;
    public long whiteTimeMillisAfterMove;
    public long blackTimeMillisAfterMove;
    public GameStatus statusAfterMove;

    public Move() {
        this.statusAfterMove = GameStatus.ACTIVE;
    }

    public Move(Spot start, Spot end, Piece pieceMoved, Piece pieceKilled, boolean wasCastling, boolean wasFirstMove,
                Spot enPassant, int halfMoves, long whiteTime, long blackTime) {
        this.start = start;
        this.end = end;
        this.pieceMoved = pieceMoved;
        this.pieceKilled = pieceKilled;
        this.wasCastlingMove = wasCastling;
        this.wasFirstMoveForPiece = wasFirstMove;
        this.enPassantTargetBeforeMove = enPassant;
        this.halfMovesAfterMove = halfMoves;
        this.whiteTimeMillisAfterMove = whiteTime;
        this.blackTimeMillisAfterMove = blackTime;
        this.statusAfterMove = GameStatus.ACTIVE;
    }
}

