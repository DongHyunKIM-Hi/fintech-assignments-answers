package com.practicefintech.coffee.common.lock;

import com.practicefintech.coffee.common.enums.ErrorCode;
import com.practicefintech.coffee.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 분산 락을 잡은 상태에서 트랜잭션을 실행한다.
 *
 * 서버가 여러 대이면 synchronized 같은 JVM 락은 다른 서버의 요청을 막지 못한다.
 * 그래서 모든 서버가 함께 보는 Redis에 락을 둔다.
 *
 * 순서가 중요하다. 락 획득, 트랜잭션 시작, 커밋, 락 해제 순서로 진행한다.
 * 커밋 전에 락을 풀면, 다음 요청이 아직 커밋되지 않은 이전 잔액을 읽을 수 있다.
 * 그래서 @Transactional 메서드 안에서 락을 잡지 않고, 락 안에서 TransactionTemplate으로 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${coffee.lock-wait-millis}")
    private long waitMillis;

    public <T> T executeInTransaction(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock("lock:" + key);
        boolean acquired = false;
        try {
            // 임대 시간을 정하지 않으면 Redisson이 작업이 끝날 때까지 락 만료를 자동으로 늘린다.
            // 서버가 죽으면 자동 연장이 멈추고 30초 뒤 락이 풀린다.
            acquired = lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.REQUEST_BUSY,
                        "같은 사용자의 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            }
            return transactionTemplate.execute(status -> action.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락을 기다리는 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
