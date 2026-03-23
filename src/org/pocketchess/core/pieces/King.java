package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

public class King extends Piece {
    private boolean hasMoved = false;

    public King(boolean isWhite) {
        super(isWhite);
    }

    public King(boolean isWhite, boolean hasMoved) {
        super(isWhite);
        this.hasMoved = hasMoved;
    }

    public boolean hasMoved() { return hasMoved; }

    public void setHasMoved(boolean hasMoved) { this.hasMoved = hasMoved; }

    @Override
    public boolean canMove(Board board, Spot start, Spot end) {
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());

        // ── Castling ──────────────────────────────────────────────────────────
        boolean isCastlingIntent = (x == 0) && !this.hasMoved() &&
                (end.getY() == 6 || end.getY() == 2 ||
                        (end.getPiece() instanceof Rook && end.getPiece().isWhite() == this.isWhite()));

        if (isCastlingIntent) {
            return isCastlingMove(board, start, end);
        }

        // ── Normal one-square move ────────────────────────────────────────────
        if (x <= 1 && y <= 1) {
            if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;
            return true;
        }
        return false;
    }

    private boolean isCastlingMove(Board board, Spot start, Spot end) {
        if (this.hasMoved) return false;

        int kingDest;
        if (end.getPiece() instanceof Rook && end.getPiece().isWhite() == this.isWhite()) {
            // Case (b): end is the rook — infer destination from which side it's on
            kingDest = (end.getY() > start.getY()) ? 6 : 2;
        } else {
            // Case (a): end IS the king's destination (g1 or c1)
            kingDest = end.getY();
        }

        // Rook is always to the right for kingside (dest=6), left for queenside (dest=2)
        int direction = (kingDest == 6) ? 1 : -1;

        for (int c = start.getY() + direction; c >= 0 && c < 8; c += direction) {
            Piece p = board.getBox(start.getX(), c).getPiece();
            if (p == null) continue;

            if (p instanceof Rook && p.isWhite() == this.isWhite() && !((Rook) p).hasMoved()) {

                // King's path must be clear (excluding king's own square)
                int loK = Math.min(start.getY(), kingDest);
                int hiK = Math.max(start.getY(), kingDest);
                for (int i = loK; i <= hiK; i++) {
                    if (i == start.getY()) continue;
                    Piece blocker = board.getBox(start.getX(), i).getPiece();
                    if (blocker != null && blocker != p) return false;
                }

                // Rook's path must be clear (excluding rook's own square,
                // king's square, and king's destination)
                int rookDest = (direction > 0) ? 5 : 3;
                int loR = Math.min(c, rookDest);
                int hiR = Math.max(c, rookDest);
                for (int i = loR; i <= hiR; i++) {
                    if (i == c || i == start.getY() || i == kingDest) continue;
                    Piece blocker = board.getBox(start.getX(), i).getPiece();
                    if (blocker != null && blocker != p) return false;
                }
                return true;
            }
            break;
        }
        return false;
    }
}