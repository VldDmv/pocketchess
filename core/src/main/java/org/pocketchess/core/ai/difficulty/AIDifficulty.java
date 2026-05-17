package org.pocketchess.core.ai.difficulty;

/**
 * AI difficulty levels with settings for each level.
 * Each level determines:
 * - Search depth (how many moves ahead the AI calculates)
 * - Quiescence search depth (additional analysis of tactical positions)
 * - Error probability (how often the AI makes suboptimal moves)
 * - Simplified evaluation (whether to use a simple or full evaluation function)
 */
public enum AIDifficulty {
    /**
     * Easy level - for beginners
     * - Search depth: 2 ply moves
     * - Quiescence depth: 2
     * - Error probability: 30%
     * - Uses simplified evaluation (material and piece positions only)
     * - Estimated rating: 800-1200 ELO
     */
    EASY("Easy", 2, 2, 0.30, true),

    /**
     * Intermediate level - for experienced players
     * - Search depth: 4 ply moves
     * - Quiescence depth: 3
     * - Error rate: 15%
     * - Uses the full rating function
     * - Estimated rating: 1200-1600 ELO
     */
    MEDIUM("Medium", 4, 3, 0.15, false),

    /**
     * Hard level - for experienced players
     * - Search depth: 5 ply moves
     * - Quiescence depth: 4
     * - Error probability: 0%
     * - Uses the full evaluation function + advanced concepts
     * - Estimated rating: 1800-2400 ELO
     */
    HARD("Hard", 5, 4, 0.0, false);

    private final String displayName;
    private final int searchDepth;          // Basic search depth
    private final int quiescenceDepth;      // Depth for tactical analysis
    private final double mistakeProbability; // Probability of intentional error
    private final boolean simplifiedEvaluation; // Whether to use simplified evaluation

    AIDifficulty(String displayName, int searchDepth, int quiescenceDepth,
                 double mistakeProbability, boolean simplifiedEvaluation) {
        this.displayName = displayName;
        this.searchDepth = searchDepth;
        this.quiescenceDepth = quiescenceDepth;
        this.mistakeProbability = mistakeProbability;
        this.simplifiedEvaluation = simplifiedEvaluation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    public int getQuiescenceDepth() {
        return quiescenceDepth;
    }

    public double getMistakeProbability() {
        return mistakeProbability;
    }

    public boolean isSimplifiedEvaluation() {
        return simplifiedEvaluation;
    }

}