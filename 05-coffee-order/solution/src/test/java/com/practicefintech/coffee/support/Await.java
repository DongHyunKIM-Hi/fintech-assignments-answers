package com.practicefintech.coffee.support;

import java.util.function.BooleanSupplier;

/** 비동기로 처리되는 결과를 최대 timeoutMillis 동안 기다린다. */
public final class Await {

    private Await() {
    }

    public static void until(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("제한 시간 안에 조건을 만족하지 못했습니다.");
    }
}
