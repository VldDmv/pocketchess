package org.pocketchess.core.gamemode;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;

import java.util.*;

/**
 * Manages the "Floor is Lava" game mode.
 *
 * CYCLE (by half-moves):
 *  - Move 0 (start):   W1 generated → shown as BLUE warning
 *  - Move 3:           W1 → RED lava (pieces removed), W2 generated → BLUE warning
 *  - Move 6:           W1 disappears, W2 → RED lava, W3 → BLUE warning
 *  - Move 9:           W2 disappears, W3 → RED lava, W4 → BLUE ...
 *
 * Rules:
 *  - Pieces cannot move TO or THROUGH lava squares
 *  - If lava activates on an occupied square, the piece is removed
 *  - Kings are never targeted by lava
 *  - First EARLY_GAME_HALF_MOVES: lava only on empty center squares
 */
public class LavaManager {

    /** How many half-moves between lava changes (= 3 full moves) */
    public static final int LAVA_INTERVAL = 6;

    /** Lava squares per interval – always 2 */
    private static final int LAVA_COUNT = 2;

    /** First N half-moves: prefer empty center squares (= first 10 full moves) */
    private static final int EARLY_GAME_HALF_MOVES = 20;

    /** Rows/cols considered "center" for early game */
    private static final int CENTER_MIN = 2;
    private static final int CENTER_MAX = 5;

    // Current red lava squares (encoded as row*8+col)
    private Set<Integer> lavaSquares   = new HashSet<>();
    // Blue warning squares (will become lava next interval)
    private Set<Integer> warningSquares = new HashSet<>();

    private final Random random;
    private boolean enabled;

    // ─────────────────────────────────────────────────────────
    //  Snapshot storage (for undo support)
    //
    //  Uses an internal counter that increments/decrements independently
    //  of historyManager - immune to off-by-one errors from callback order.
    // ─────────────────────────────────────────────────────────

    private static class LavaStateSnapshot {
        final Set<Integer> lavaSquares;
        final Set<Integer> warningSquares;
        /** pos→piece for every piece eaten by lava on this half-move. */
        final Map<Integer, Piece> eatenPieces = new LinkedHashMap<>();

        LavaStateSnapshot(Set<Integer> lava, Set<Integer> warnings) {
            this.lavaSquares    = new HashSet<>(lava);
            this.warningSquares = new HashSet<>(warnings);
        }
    }

    /** Sorted map: key = internal counter value at time of save */
    private final TreeMap<Integer, LavaStateSnapshot> snapshotMap = new TreeMap<>();
    private int snapshotCounter = 0;

    // ─────────────────────────────────────────────────────────
    //  Construction / copying
    // ─────────────────────────────────────────────────────────

    public LavaManager() {
        this.random  = new Random();
        this.enabled = false;
    }

    /** Deep-copy constructor used by AI game copies */
    public LavaManager(LavaManager other) {
        this.random        = new Random();
        this.enabled       = other.enabled;
        this.lavaSquares   = new HashSet<>(other.lavaSquares);
        this.warningSquares = new HashSet<>(other.warningSquares);
    }

    // ─────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────

    /** Enable lava mode and generate the first batch of warning squares */
    public void enable(Board board) {
        this.enabled = true;
        this.lavaSquares.clear();
        this.warningSquares = generateLavaSet(board, 0);
        this.snapshotMap.clear();
        this.snapshotCounter = 0;
    }

    public void disable() {
        this.enabled = false;
        this.lavaSquares.clear();
        this.warningSquares.clear();
        this.snapshotMap.clear();
        this.snapshotCounter = 0;
    }

    public boolean isEnabled() { return enabled; }

    // ─────────────────────────────────────────────────────────
    //  Move callback
    // ─────────────────────────────────────────────────────────

    /**
     * Called after each half-move is fully committed.
     *
     * @param board          current board (AFTER the move, pieces still on squares
     *                       that are about to become lava)
     * @param totalHalfMoves total half-moves played including the one just made
     * @return encoded positions (row*8+col) where lava just activated
     *         (caller must remove any pieces there)
     */
    public List<Integer> onMoveCompleted(Board board, int totalHalfMoves) {
        if (!enabled || totalHalfMoves <= 0 || totalHalfMoves % LAVA_INTERVAL != 0) {
            return Collections.emptyList();
        }

        // Warning → lava
        lavaSquares = new HashSet<>(warningSquares);

        // Generate next batch of warnings (uses updated lavaSquares)
        warningSquares = generateLavaSet(board, totalHalfMoves);

        return new ArrayList<>(lavaSquares);
    }

    // ─────────────────────────────────────────────────────────
    //  Queries used by renderer & move generator
    // ─────────────────────────────────────────────────────────

    public boolean isLava(int row, int col) {
        return enabled && lavaSquares.contains(encode(row, col));
    }

    public boolean isWarning(int row, int col) {
        return enabled && warningSquares.contains(encode(row, col));
    }

    public Set<Integer> getLavaSquares()    { return Collections.unmodifiableSet(lavaSquares); }
    public Set<Integer> getWarningSquares() { return Collections.unmodifiableSet(warningSquares); }

    // ─────────────────────────────────────────────────────────
    //  Undo / snapshot support
    // ─────────────────────────────────────────────────────────

    /**
     * Saves the current lava state keyed by an internal counter.
     * Returns the key (pass it to {@link #recordEatenPiece}).
     */
    public int saveSnapshot() {
        snapshotCounter++;
        snapshotMap.put(snapshotCounter, new LavaStateSnapshot(lavaSquares, warningSquares));
        return snapshotCounter;
    }

    /**
     * Restores lava visual state (squares) for the most recent half-move and
     * removes that snapshot from the map.  Call once per undone half-move.
     */
    public void popLatestSnapshot() {
        if (snapshotMap.isEmpty()) return;
        int key = snapshotMap.lastKey();
        LavaStateSnapshot snap = snapshotMap.remove(key);
        snapshotCounter = key - 1;
        lavaSquares    = new HashSet<>(snap.lavaSquares);
        warningSquares = new HashSet<>(snap.warningSquares);
    }

    /**
     * Records a piece eaten by lava at {@code encodedPos} for the given snapshot key.
     */
    public void recordEatenPiece(int snapshotKey, int encodedPos, Piece piece) {
        LavaStateSnapshot snap = snapshotMap.get(snapshotKey);
        if (snap != null) {
            snap.eatenPieces.put(encodedPos, piece);
        }
    }

    /**
     * Called after chess-history undo to re-apply lava piece removals.
     *
     * The chess history restores the board to the state it was in BEFORE lava
     * fired on any given move, effectively resurrecting lava-killed pieces.
     * This method fixes that by:
     *   1. Removing ALL previously tracked lava kills from the captured lists.
     *   2. For every lava wave whose snapshot key is ≤ {@code upToSnapshotKey},
     *      putting the piece back to null on the board and adding it to the
     *      correct captured list.
     *
     * @param board           current board (just restored by history undo)
     * @param upToSnapshotKey only re-apply waves with key ≤ this value
     *                        (== snapshotCounter after popping undone moves)
     * @param whiteCaptured   white's captured-pieces list (maintained by moveExecutor)
     * @param blackCaptured   black's captured-pieces list
     */
    public void reapplyEatenPieces(Board board, int upToSnapshotKey,
                                   List<Piece> whiteCaptured, List<Piece> blackCaptured) {
        // Clean slate: remove all lava-kill entries from captured lists
        for (LavaStateSnapshot snap : snapshotMap.values()) {
            for (Piece p : snap.eatenPieces.values()) {
                whiteCaptured.remove(p);
                blackCaptured.remove(p);
            }
        }

        // Re-apply lava kills for waves whose key <= upToSnapshotKey.
        //
        // IMPORTANT: historyNavigationService restores the board from Board snapshots,
        // creating NEW piece objects every time — object identity (==) is useless here.
        // We compare by CLASS + COLOR instead:
        //   • same class + same color → it's "our" dead piece being restored → kill it again
        //   • different class or color → a different piece moved to that square → leave it alone
        //
        // Edge case: if another piece of the exact same type+color independently moved to a
        // lava-eaten square after the original kill, we would incorrectly kill it on undo.
        // This is extremely rare and acceptable given the alternative (never killing anything).
        for (Map.Entry<Integer, LavaStateSnapshot> entry : snapshotMap.entrySet()) {
            if (entry.getKey() > upToSnapshotKey) continue;

            for (Map.Entry<Integer, Piece> eaten : entry.getValue().eatenPieces.entrySet()) {
                int[] pos  = decode(eaten.getKey());
                Piece p    = eaten.getValue();
                Piece curr = board.getBox(pos[0], pos[1]).getPiece();

                boolean sameTypeAndColor = (curr != null)
                        && (curr.getClass() == p.getClass())
                        && (curr.isWhite() == p.isWhite());

                if (sameTypeAndColor) {
                    board.getBox(pos[0], pos[1]).setPiece(null);
                }

                if (p.isWhite()) blackCaptured.add(p);
                else             whiteCaptured.add(p);
            }
        }
    }

    /**
     * Restores lava visual state to what it was at {@code key}, without removing
     * any snapshots. Used by goToMove to jump to arbitrary history positions.
     */
    public void restoreToSnapshot(int key) {
        LavaStateSnapshot snap = snapshotMap.get(key);
        if (snap != null) {
            lavaSquares    = new HashSet<>(snap.lavaSquares);
            warningSquares = new HashSet<>(snap.warningSquares);
        } else {
            lavaSquares    = new HashSet<>();
            warningSquares = new HashSet<>();
        }
    }

    /** Clears all snapshots – called on full game reset. */
    public void clearSnapshots() {
        snapshotMap.clear();
        snapshotCounter = 0;
    }

    public int getSnapshotCounter() { return snapshotCounter; }

    // ─────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────

    private Set<Integer> generateLavaSet(Board board, int totalHalfMoves) {
        boolean earlyGame = (totalHalfMoves < EARLY_GAME_HALF_MOVES);

        List<Integer> candidates = getCandidateSquares(board, earlyGame);
        if (candidates.isEmpty() && earlyGame) {
            candidates = getCandidateSquares(board, false);
        }

        Collections.shuffle(candidates, random);

        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < Math.min(LAVA_COUNT, candidates.size()); i++) {
            result.add(candidates.get(i));
        }
        return result;
    }

    private List<Integer> getCandidateSquares(Board board, boolean earlyGame) {
        List<Integer> candidates = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int enc = encode(r, c);

                // Never overlap current lava or current warnings
                if (lavaSquares.contains(enc))   continue;
                if (warningSquares.contains(enc)) continue;

                Piece piece = board.getBox(r, c).getPiece();

                // Kings are always safe
                if (piece instanceof King) continue;

                if (earlyGame) {
                    // Only empty squares in the central 4×4 zone
                    boolean isCenter = (r >= CENTER_MIN && r <= CENTER_MAX)
                            && (c >= CENTER_MIN && c <= CENTER_MAX);
                    if (isCenter && piece == null) {
                        candidates.add(enc);
                    }
                } else {
                    // Any non-king square (pieces can be eaten in late game)
                    candidates.add(enc);
                }
            }
        }
        return candidates;
    }

    // ─────────────────────────────────────────────────────────
    //  Coordinate encoding
    // ─────────────────────────────────────────────────────────

    public static int encode(int row, int col) { return row * 8 + col; }

    public static int[] decode(int encoded) {
        return new int[]{ encoded / 8, encoded % 8 };
    }
}