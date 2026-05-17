package org.pocketchess.online.game;

import org.pocketchess.core.ai.difficulty.AIDifficulty;

/**
 * A seat at the table. Either a human (referenced by display name) or a bot
 * playing at a fixed difficulty.
 */
public record Player(String name, boolean bot, AIDifficulty difficulty) {

    public static Player human(String name) {
        return new Player(name, false, null);
    }

    public static Player bot(AIDifficulty difficulty) {
        return new Player("Bot — " + difficulty.getDisplayName(), true, difficulty);
    }
}
