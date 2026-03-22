package org.pocketchess.core.game.model;

public record TimeControl(int baseTimeSeconds, int incrementSeconds) {

    public static final TimeControl UNLIMITED = new TimeControl(Integer.MAX_VALUE, 0);

    public boolean isUnlimited() {
        return this.baseTimeSeconds == Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
        if (isUnlimited()) {
            return "Unlimited";
        }
        int minutes = baseTimeSeconds / 60;
        if (incrementSeconds == 0) {
            return String.format("%d min", minutes);
        }
        return String.format("%d min + %d sec", minutes, incrementSeconds);
    }
}