package com.cosmicsmp.core.task;

/** Why a {@link TimedEffect} ended. Effects use it to decide what to persist or restore. */
public enum EndReason {
    /** Ran its full duration or finished on its own. */
    COMPLETED,
    /** Stopped early by gameplay (dismount, target lost, recast...). */
    CANCELLED,
    /** A participant left the server - persistent timed state must be kept for resuming. */
    QUIT,
    /** A participant died. */
    DEATH,
    /** Plugin disable / server stop - restore everything immediately. */
    SHUTDOWN
}
