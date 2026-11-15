package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.enums.PgCallResult;
import com.practicefintech.refundrelay.common.enums.RefundStatus;
import com.practicefintech.refundrelay.common.utils.PgClient;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 접수된 환불을 뒤에서 가상 결제대행사로 전달하는 워커.
 * 1단계의 목표는 "접수 API와 분리된 워커가 실제로 돌아간다"를 눈으로 확인하는 것뿐이다.
 *
 * 1단계에서 일부러 하지 않는 것
 * - 재시도가 없다. 결제대행사가 한 번이라도 실패하면 바로 FAILED다.
 * - 스레드 풀이 없다. 접수된 건을 한 건씩 차례로 처리하므로, 앞 건의 응답이 느리면
 *   뒤에 있는 다른 주문까지 모두 밀린다.
 *
 * 왜 다음 단계가 필요한가
 * - 2단계: 타임아웃과 재시도(최대 4회)를 넣고, 다른 주문을 동시에 처리하도록 스레드 풀을 쓴다.
 * - 3단계: 스레드 풀을 쓰면 같은 주문의 환불도 동시에 나가 버린다. 주문 단위로 묶어
 *   "앞 건이 끝나야 다음 건"을 지키게 만든다.
 */
@Component
@RequiredArgsConstructor
public class RefundWorker {

    private final RefundRepository repository;
    private final PgClient pgClient;

    @Scheduled(fixedDelayString = "${worker.tick-interval-ms}")
    public void tick() {
        for (Refund refund : repository.findByStatusOrderByRequestedAtAsc(RefundStatus.ACCEPTED)) {
            process(refund);
        }
    }

    private void process(Refund refund) {
        refund.markSending();
        repository.save(refund);

        PgCallResult result = pgClient.send(refund.getRefundId(), refund.getOrderId(), refund.getRefundAmount());

        if (result == PgCallResult.SUCCESS) {
            refund.markCompleted(Instant.now());
        } else {
            // 1단계는 재시도하지 않는다. 실패하면 그대로 끝이다.
            refund.markFailed(Instant.now());
        }
        repository.save(refund);
    }
}
