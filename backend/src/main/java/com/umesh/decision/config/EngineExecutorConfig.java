package com.umesh.decision.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounded worker pool for the concurrent Monte Carlo engine. The project targets Java 17 (see
 * {@code pom.xml}), so virtual threads / structured concurrency (stable only from Java 21) are not
 * available here; this is a classic, correctly-bounded {@link ThreadPoolExecutor} instead.
 *
 * <p>Deliberately not {@code @Async}: Spring's {@code @Async} is fire-and-forget by default and
 * gives no backpressure, no bounded queue, and no natural place to apply a timeout or cancel
 * in-flight work — all of which the resumable Monte Carlo engine needs (see
 * {@code com.umesh.decision.engine.MonteCarloEngine}).
 *
 * <p>Sizing: core = max pool size = CPU count by default (CPU-bound work gets no benefit from
 * extra threads beyond the core count; a fixed pool also means {@code allowCoreThreadTimeOut} is
 * unnecessary). The work queue is bounded ({@link ArrayBlockingQueue}) rather than unbounded,
 * specifically so that submitting far more batches than the pool can execute cannot exhaust
 * memory. Backpressure is provided via {@link ThreadPoolExecutor.CallerRunsPolicy}: once the queue
 * is full, the thread trying to submit more batches (the engine's own orchestrator thread) runs
 * the batch itself instead of queuing it — this naturally throttles submission to the pool's real
 * throughput without failing the request or needing a separate semaphore.
 */
@Configuration
public class EngineExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor monteCarloExecutor(AppProperties appProperties) {
        AppProperties.Simulation config = appProperties.simulation();
        int workerThreads = resolveWorkerThreads(config);
        int queueCapacity = config != null && config.executorQueueCapacity() != null
                ? config.executorQueueCapacity()
                : workerThreads * 4;

        ThreadFactory threadFactory = namedThreadFactory("monte-carlo-worker-");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    public static int resolveWorkerThreads(AppProperties.Simulation config) {
        if (config != null && config.workerThreads() != null && config.workerThreads() > 0) {
            return config.workerThreads();
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
