package com.umesh.decision.engine;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation signal checked by {@link MonteCarloEngine} between waves of batches. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public static CancellationToken none() {
        return new CancellationToken();
    }
}
