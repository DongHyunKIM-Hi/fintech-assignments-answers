package com.practicefintech.coffee.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/** 여러 작업을 최대한 같은 순간에 시작시켜 결과(성공 또는 예외)를 모은다. */
public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    public static <T> List<Result<T>> run(int count, IntFunction<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(count, 32));
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<Result<T>>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.await();
                try {
                    return new Result<>(task.apply(index), null);
                } catch (RuntimeException e) {
                    return new Result<T>(null, e);
                }
            }));
        }
        ready.countDown();
        List<Result<T>> results = new ArrayList<>();
        for (Future<Result<T>> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();
        return results;
    }

    public record Result<T>(T value, RuntimeException error) {

        public boolean success() {
            return error == null;
        }
    }
}
