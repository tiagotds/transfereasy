package br.com.tiagotds.transfereasy.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Releases all tasks at the same instant (a start latch) so they truly race, then waits for every one of them.
 * Any exception a task throws fails the caller; expected refusals must be caught inside the task.
 */
public final class Race {

    private Race() {
    }

    public static <T> List<T> run(int racers, IntFunction<Callable<T>> task) throws Exception {
        return run(IntStream.range(0, racers).mapToObj(task).toList());
    }

    public static <T> List<T> run(List<Callable<T>> tasks) throws Exception {
        var ready = new CountDownLatch(tasks.size());
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(tasks.size())) {
            var futures = new ArrayList<Future<T>>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "workers did not start");
            go.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
