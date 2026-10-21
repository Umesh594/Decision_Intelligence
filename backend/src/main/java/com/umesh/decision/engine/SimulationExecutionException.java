package com.umesh.decision.engine;

/** Wraps a failure of one or more Monte Carlo batch tasks so callers get a single typed exception. */
public class SimulationExecutionException extends RuntimeException {
    public SimulationExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
