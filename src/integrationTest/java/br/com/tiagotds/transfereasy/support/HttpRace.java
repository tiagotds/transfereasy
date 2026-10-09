package br.com.tiagotds.transfereasy.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/** Fires {@code racers} requests released by one start latch, on virtual threads, and collects the results. */
public final class HttpRace {

    private HttpRace() {
    }

    public static <T> List<T> run(int racers, IntFunction<Callable<T>> task) throws Exception {
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < racers; i++) {
                var call = task.apply(i);
                futures.add(executor.submit(() -> {
                    go.await();
                    return call.call();
                }));
            }
            go.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
