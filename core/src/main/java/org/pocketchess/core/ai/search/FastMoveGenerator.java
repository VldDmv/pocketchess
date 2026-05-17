package org.pocketchess.core.ai.search;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast move generator for chess pieces.
 * Lava-aware: pieces cannot move to or slide through active lava squares.
 */
public class FastMoveGenerator {

    private static final int[][] KNIGHT_MOVES    = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
    private static final int[][] BISHOP_DIRECTIONS = {{-1,-1},{-1,1},{1,-1},{1,1}};
    private static final int[][] ROOK_DIRECTIONS  = {{-1,0},{1,0},{0,-1},{0,1}};
    private static final int[][] QUEEN_DIRECTIONS = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
    private static final int[][] KING_MOVES       = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};

    // ─────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────

    public List<Move> generateMoves(Game game) {
        List<Move> pseudoLegal = new ArrayList<>(128);
        generatePseudoLegalMoves(game, pseudoLegal);

        List<Move> legal = new ArrayList<>(pseudoLegal.size());
        for (Move move : pseudoLegal) {
            game.makeTemporaryMove(move);
            if (!game.isKingInCheck(!game.isWhiteTurn())) {
                legal.add(move);
            }
            game.undoTemporaryMove(move);
        }
        return legal;
    }

    // ─────────────────────────────────────────────────────────
    //  Lava helper
    // ─────────────────────────────────────────────────────────

    /**
     * Returns true if the square is impassable for AI move generation.
     *
     * In AI search copies (where the game has no real postMoveCallback) we
     * treat WARNING squares identically to active lava.  This prevents the
     * generator from producing moves that land on squares which will burn on
     * the next lava interval — moves the evaluator would have to score as
     * suicidal anyway.
     *
     * The check {@code game.isLavaMode()} gates everything so there is zero
     * overhead when lava is disabled.
     */
    private boolean isLava(Game game, int row, int col) {
        if (!game.isLavaMode()) return false;
        LavaManager lm = game.getLavaManager();
        return lm.isLava(row, col) || lm.isWarning(row, col);
    }

    // ─────────────────────────────────────────────────────────
    //  Move factory helpers
    // ─────────────────────────────────────────────────────────

    private Move createMove(Game game, Spot start, Spot end, Piece moved, Piece killed) {
        boolean wasFirstMove = false;
        if (moved instanceof King) wasFirstMove = !((King) moved).hasMoved();
        if (moved instanceof Rook) wasFirstMove = !((Rook) moved).hasMoved();
        return new Move(start, end, moved, killed, false, wasFirstMove,
                game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
    }

    private Move createPromotionMove(Game game, Spot start, Spot end, Piece captured, Piece promotedTo) {
        Move move = createMove(game, start, end, start.getPiece(), captured);
        move.promotedTo = promotedTo;
        return move;
    }

    // ─────────────────────────────────────────────────────────
    //  Pseudo-legal generation dispatcher
    // ─────────────────────────────────────────────────────────

    private void generatePseudoLegalMoves(Game game, List<Move> moves) {
        Board board = game.getBoard();
        boolean isWhite = game.isWhiteTurn();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = board.getBox(r, c).getPiece();
                if (piece != null && piece.isWhite() == isWhite) {
                    if      (piece instanceof Pawn)   generatePawnMoves(game, r, c, piece, moves);
                    else if (piece instanceof Knight) generateKnightMoves(game, r, c, piece, moves);
                    else if (piece instanceof Bishop) generateSlidingMoves(game, r, c, piece, BISHOP_DIRECTIONS, moves);
                    else if (piece instanceof Rook)   generateSlidingMoves(game, r, c, piece, ROOK_DIRECTIONS, moves);
                    else if (piece instanceof Queen)  generateSlidingMoves(game, r, c, piece, QUEEN_DIRECTIONS, moves);
                    else if (piece instanceof King)   generateKingMoves(game, r, c, piece, moves);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Per-piece generators
    // ─────────────────────────────────────────────────────────

    /**
     * Sliding pieces (Bishop, Rook, Queen).
     * Lava squares block the ray: cannot move to or past them.
     */
    private void generateSlidingMoves(Game game, int r, int c, Piece piece,
                                      int[][] directions, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        for (int[] dir : directions) {
            int tr = r + dir[0];
            int tc = c + dir[1];

            while (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {

                // Lava completely blocks the ray (cannot enter or pass through)
                if (isLava(game, tr, tc)) break;

                Spot end = game.getBoard().getBox(tr, tc);
                Piece targetPiece = end.getPiece();

                if (targetPiece == null) {
                    moves.add(createMove(game, start, end, piece, null));
                } else {
                    if (targetPiece.isWhite() != piece.isWhite()) {
                        moves.add(createMove(game, start, end, piece, targetPiece));
                    }
                    break; // friendly piece or captured enemy – stop ray
                }

                tr += dir[0];
                tc += dir[1];
            }
        }
    }

    /** Knights jump, but cannot land on lava. */
    private void generateKnightMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        for (int[] mv : KNIGHT_MOVES) {
            int tr = r + mv[0];
            int tc = c + mv[1];

            if (tr < 0 || tr > 7 || tc < 0 || tc > 7) continue;
            if (isLava(game, tr, tc)) continue;          // cannot land on lava

            Spot end = game.getBoard().getBox(tr, tc);
            Piece targetPiece = end.getPiece();

            if (targetPiece == null || targetPiece.isWhite() != piece.isWhite()) {
                moves.add(createMove(game, start, end, piece, targetPiece));
            }
        }
    }

    /** King moves one square; castling squares must also be lava-free. */
    private void generateKingMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        // Normal one-square moves
        for (int[] mv : KING_MOVES) {
            int tr = r + mv[0];
            int tc = c + mv[1];

            if (tr < 0 || tr > 7 || tc < 0 || tc > 7) continue;
            if (isLava(game, tr, tc)) continue;          // cannot step on lava

            Spot end = game.getBoard().getBox(tr, tc);
            Piece targetPiece = end.getPiece();

            if (targetPiece == null || targetPiece.isWhite() != piece.isWhite()) {
                moves.add(createMove(game, start, end, piece, targetPiece));
            }
        }

        // Castling (only if king hasn't moved)
        if (!(piece instanceof King) || ((King) piece).hasMoved()) return;
        if (game.isKingInCheck(piece.isWhite())) return;

        // ── Kingside (O-O): destination = col 6 (g-file) ────────────────────
        if (c != 6) {
            Spot ksDestSpot = game.getBoard().getBox(r, 6);
            if (game.getRuleEngine().isCastlingMoveLegal(game.getBoard(), start, ksDestSpot)
                    && !isLava(game, r, 6)) {
                // Find the rook so we can store its column for Chess960 undo
                int ksRookCol = -1;
                for (int sc = c + 1; sc < 8; sc++) {
                    Piece p = game.getBoard().getBox(r, sc).getPiece();
                    if (p instanceof Rook && p.isWhite() == piece.isWhite()
                            && !((Rook) p).hasMoved()) { ksRookCol = sc; break; }
                    if (p != null) break;
                }
                Move castleMove = new Move(start, ksDestSpot, piece, null, true, true,
                        game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
                castleMove.chess960RookFromCol = ksRookCol;
                moves.add(castleMove);
            }
        }

        // ── Queenside (O-O-O): destination = col 2 (c-file) ─────────────────
        if (c != 2) {
            Spot qsDestSpot = game.getBoard().getBox(r, 2);
            if (game.getRuleEngine().isCastlingMoveLegal(game.getBoard(), start, qsDestSpot)
                    && !isLava(game, r, 2)) {
                int qsRookCol = -1;
                for (int sc = c - 1; sc >= 0; sc--) {
                    Piece p = game.getBoard().getBox(r, sc).getPiece();
                    if (p instanceof Rook && p.isWhite() == piece.isWhite()
                            && !((Rook) p).hasMoved()) { qsRookCol = sc; break; }
                    if (p != null) break;
                }
                Move castleMove = new Move(start, qsDestSpot, piece, null, true, true,
                        game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
                castleMove.chess960RookFromCol = qsRookCol;
                moves.add(castleMove);
            }
        }
    }

    /** Pawn moves; cannot advance to or capture on lava squares. */
    private void generatePawnMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Board board = game.getBoard();
        Spot start = board.getBox(r, c);

        int direction    = piece.isWhite() ? -1 : 1;
        int startRow     = piece.isWhite() ?  6 : 1;
        int promotionRow = piece.isWhite() ?  0 : 7;

        // ── One step forward ────────────────────────────────────────────────
        int oneStep = r + direction;
        if (oneStep >= 0 && oneStep < 8
                && board.getBox(oneStep, c).getPiece() == null
                && !isLava(game, oneStep, c)) {                // cannot step on lava

            Spot end = board.getBox(oneStep, c);
            if (oneStep == promotionRow) {
                addPromotionMoves(game, start, end, null, moves);
            } else {
                moves.add(createMove(game, start, end, piece, null));
            }

            // ── Two steps from start ─────────────────────────────────────────
            if (r == startRow) {
                int twoSteps = r + 2 * direction;
                if (!isLava(game, twoSteps, c) && board.getBox(twoSteps, c).getPiece() == null) {
                    moves.add(createMove(game, start, board.getBox(twoSteps, c), piece, null));
                }
            }
        }

        // ── Diagonal captures ───────────────────────────────────────────────
        int[] captureCols = {c - 1, c + 1};
        for (int tc : captureCols) {
            if (tc < 0 || tc > 7 || oneStep < 0 || oneStep > 7) continue;
            if (isLava(game, oneStep, tc)) continue;            // cannot capture on lava

            Spot end         = board.getBox(oneStep, tc);
            Piece targetPiece = end.getPiece();

            // Normal capture
            if (targetPiece != null && targetPiece.isWhite() != piece.isWhite()) {
                if (oneStep == promotionRow) {
                    addPromotionMoves(game, start, end, targetPiece, moves);
                } else {
                    moves.add(createMove(game, start, end, piece, targetPiece));
                }
            }
            // En-passant
            if (end == board.getEnPassantTargetSquare()) {
                Spot capturedPawnSpot = board.getBox(r, tc);
                moves.add(createMove(game, start, end, piece, capturedPawnSpot.getPiece()));
            }
        }
    }

    private void addPromotionMoves(Game game, Spot start, Spot end, Piece captured, List<Move> moves) {
        boolean isWhite = start.getPiece().isWhite();
        moves.add(createPromotionMove(game, start, end, captured, new Queen(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Rook(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Bishop(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Knight(isWhite)));
    }
}