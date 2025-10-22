package org.pocketchess.core.ai;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.Random;

/**
 * Opening Book Manager - Manages the use of opening theory.
 * BEHAVIOR BY LEVEL:
 * - EASY: Doesn't use a book (to be weaker)
 * - MEDIUM: Uses a book, but sometimes (30%) ignores it for variety
 * - HARD: Always follows a book when possible
 */
public class OpeningBookManager {
    private final AIDifficulty difficulty;
    private final Random random;
    private final OpeningBook openingBook;

    public OpeningBookManager(AIDifficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.openingBook = new OpeningBook();
        this.random = random;
    }

    /**
     * Attempts to find a move from the opening book for the current position.
     */
    public Move getBookMove(Game game) {
        // Easy level does not use opening book
        if (difficulty == AIDifficulty.EASY) return null;

        // Convert the position to FEN for searching in the book
        String fen = org.pocketchess.core.game.utils.FenUtils.generateFEN(
                game.getBoard(),
                game.isWhiteTurn()
        );

        //looking for a move in the book
        String moveStr = openingBook.getBookMove(fen);

        if (moveStr != null) {
            Move bookMove = openingBook.parseMove(moveStr, game);
            if (bookMove != null) {
                // HARD always follows the book
                // MEDIUM sometimes follows, sometimes ignores
                if (difficulty == AIDifficulty.HARD || random.nextDouble() > 0.3) {
                    return bookMove;
                }
            }
        }

        // The move was not found in the book or not followed
        return null;
    }
}