package org.pocketchess.core.ai;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Transposition table - a cache of chess position evaluations.
 * Store the evaluation, depth, and best move for each encountered position.
 * Zobrist hashing:
 * - Each piece on each square is assigned a random 64-bit number.
 * - Position hash = XOR of all the numbers of pieces on the board.
 * - XOR allows for quick hash updates during moves.
 */
public class TranspositionTable {
    private static final int MAX_SIZE = 2_000_000;
    private final ConcurrentHashMap<Long, Entry> table = new ConcurrentHashMap<>();

    /**
     * Entry in the transposition table.
     */
    public static class Entry {
        public final int depth;
        public final int score;
        public final EntryType type;
        public final Move bestMove;

        /**
         * Record types:
         * EXACT - exact estimate
         * ALPHA - lower bound (>=)
         * BETA - upper bound (<=)
         */
        public enum EntryType {EXACT, ALPHA, BETA}

        public Entry(int depth, int score, EntryType type, Move bestMove) {
            this.depth = depth;
            this.score = score;
            this.type = type;
            this.bestMove = bestMove;
        }
    }
    // Zobrist hashing tables - initialized once when the class is loaded
    private static final long[][][] ZOBRIST_TABLE = new long[8][8][12];
    private static final long ZOBRIST_BLACK_TO_MOVE;
    private static final long[] ZOBRIST_CASTLING = new long[4];
    private static final long[] ZOBRIST_EN_PASSANT = new long[8];

    static {
        // Generate random numbers for Zobrist hashing
        Random rand = new Random(12345L);

        // For each figure on each field
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                for (int k = 0; k < 12; k++)
                    ZOBRIST_TABLE[i][j][k] = rand.nextLong();

        ZOBRIST_BLACK_TO_MOVE = rand.nextLong();

        for (int i = 0; i < 4; i++)
            ZOBRIST_CASTLING[i] = rand.nextLong();


        for (int i = 0; i < 8; i++)
            ZOBRIST_EN_PASSANT[i] = rand.nextLong();
    }

    /**
     * Stores the position's estimate in the table.
     * Replacement occurs only if:
     * - The position is not yet in the table
     * - OR the new depth >= the old depth
     * This ensures that deeper estimates are not overwritten by shallower ones.
     */
    public void put(Game game, int depth, int score, Entry.EntryType type, Move bestMove) {
        if (table.size() > MAX_SIZE) table.clear();

        long hash = computeHash(game);
        Entry existing = table.get(hash);


        if (existing == null || existing.depth <= depth) {
            table.put(hash, new Entry(depth, score, type, bestMove));
        }
    }

    /**
     Gets the saved position score.
     */
    public Entry get(Game game) {
        return table.get(computeHash(game));
    }

    /**
    Clear all table
     */
    public void clear() {
        table.clear();
    }

    /**
     * Calculates the Zobrist hash of the position.
     * The hash takes into account:
     * - The position of all pieces
     * - Whose turn it is
     * - Castling rights
     * - The possibility of capturing en passant
     */
    private static long computeHash(Game game) {
        long hash = 0;
        Board board = game.getBoard();

        // XOR for each piece on the board
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = board.getBox(r, c).getPiece();
                if (piece != null) {
                    hash ^= ZOBRIST_TABLE[r][c][getPieceIndex(piece)];
                }
            }
        }

        if (!game.isWhiteTurn()) {
            hash ^= ZOBRIST_BLACK_TO_MOVE;
        }

        if (game.canCastle(true, true)) hash ^= ZOBRIST_CASTLING[0];   // W О-О
        if (game.canCastle(true, false)) hash ^= ZOBRIST_CASTLING[1];  // W О-О-О
        if (game.canCastle(false, true)) hash ^= ZOBRIST_CASTLING[2];  // B О-О
        if (game.canCastle(false, false)) hash ^= ZOBRIST_CASTLING[3]; // B О-О-О

        // En passant
        Spot enPassant = board.getEnPassantTargetSquare();
        if (enPassant != null) {
            hash ^= ZOBRIST_EN_PASSANT[enPassant.getY()];
        }

        return hash;
    }

    /**
     * Converts a piece to an index for the Zobrist table.
     * Indexes:
     * 0-5: White pieces (Pawn, Knight, Bishop, Rook, Queen, King)
     * 6-11: Black pieces (ditto)
     */
    private static int getPieceIndex(Piece piece) {
        int offset = piece.isWhite() ? 0 : 6;
        if (piece instanceof Pawn) return offset;
        if (piece instanceof Knight) return 1 + offset;
        if (piece instanceof Bishop) return 2 + offset;
        if (piece instanceof Rook) return 3 + offset;
        if (piece instanceof Queen) return 4 + offset;
        if (piece instanceof King) return 5 + offset;
        return 0;
    }
}