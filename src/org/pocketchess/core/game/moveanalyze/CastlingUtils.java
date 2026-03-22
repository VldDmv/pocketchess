package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Rook;

/**
 * Shared castling helpers used by GameMoveExecutor and TemporaryMoveHandler.
 *
 * Centralising here removes the duplicated findCastlingRookCol that previously
 * lived in both classes and was fixed independently (causing drift).
 */
public final class CastlingUtils {

    private CastlingUtils() {}

    /**
     * Scans from (row, kingOrigCol + direction) for the first unmoved
     * friendly Rook, skipping over the King if it has already been placed
     * on its destination square.
     *
     * @param board       current board state
     * @param row         rank being castled on (0 or 7)
     * @param kingOrigCol king's ORIGINAL column (before the king moved)
     * @param direction   +1 for kingside, -1 for queenside
     * @return column of the rook, or -1 if not found
     */
    public static int findCastlingRookCol(Board board, int row,
                                          int kingOrigCol, int direction) {
        for (int c = kingOrigCol + direction; c >= 0 && c < 8; c += direction) {
            Piece p = board.getBox(row, c).getPiece();
            if (p instanceof Rook && !((Rook) p).hasMoved()) return c;
            if (p != null && !(p instanceof King)) break;
        }
        return -1;
    }

    /**
     * Returns the rook's destination file after castling.
     *
     * @param kingDestCol king destination column (6 = kingside, 2 = queenside)
     * @return rook destination column (5 = f-file, 3 = d-file)
     */
    public static int rookDestCol(int kingDestCol) {
        return (kingDestCol == 6) ? 5 : 3;
    }

    /**
     * Returns the castling direction based on the king's destination.
     *
     * @param kingDestCol king destination column (6 = kingside, 2 = queenside)
     * @return +1 for kingside, -1 for queenside
     */
    public static int castlingDirection(int kingDestCol) {
        return (kingDestCol == 6) ? 1 : -1;
    }
}