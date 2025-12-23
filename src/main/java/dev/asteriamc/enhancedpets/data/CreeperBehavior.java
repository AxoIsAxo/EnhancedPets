package dev.asteriamc.enhancedpets.data;

/**
 * Defines how a pet reacts to Creepers.
 */
public enum CreeperBehavior {
    /**
     * Pet attacks creepers only if the owner attacks one first.
     * This mimics vanilla wolf behavior.
     */
    NEUTRAL,

    /**
     * Pet actively runs away from creepers if within 3 blocks.
     * Will not target creepers under any circumstance (except explicit targeting).
     * NOTE: Has no effect on Cats since creepers naturally flee from cats.
     */
    FLEE,

    /**
     * Pet completely ignores creepers - won't attack them, won't flee from them.
     * Creepers are invisible to the pet's AI.
     */
    IGNORE
}
