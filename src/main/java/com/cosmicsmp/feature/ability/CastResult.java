package com.cosmicsmp.feature.ability;

public enum CastResult {
    /** Cast happened - the cooldown starts. */
    SUCCESS,
    /** Nothing valid to aim at - no cooldown. */
    NO_TARGET,
    /** Cast not possible right now (already active, blocked area...) - no cooldown. */
    BLOCKED
}
