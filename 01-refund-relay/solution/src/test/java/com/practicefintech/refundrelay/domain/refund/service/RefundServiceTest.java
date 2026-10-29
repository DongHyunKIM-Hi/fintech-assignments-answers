package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.enums.RefundStatus;
import com.practicefintech.refundrelay.common.exception.ErrorResponse;
import com.practicefintech.refundrelay.domain.refund.model.request.RefundRequest;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundDuplicateResponse;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 접수 단계 규칙(지원자 README 5-2) 검증.
 * 중복 응답, 충돌, 한도 계산, 거절 건 재요청을 확인한다. (시나리오 S2, S3, S5)
 */
@DataJpaTest
class RefundServiceTest {

    @Autowired
    RefundRepository repository;

    RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(repository);
    }

    @Test
    @DisplayName("S2: 같은 ID, 같은 내용으로 다시 요청하면 200과 duplicate:true, 새로 저장하지 않는다")
    void duplicateRequestReturnsExisting() {
        assertEquals(202, service.accept(request("R-1", "ORD-1", 10_000, 3_000)).getStatusCode().value());

        ResponseEntity<?> second = service.accept(request("R-1", "ORD-1", 10_000, 3_000));

        assertEquals(200, second.getStatusCode().value());
        RefundDuplicateResponse body = assertInstanceOf(RefundDuplicateResponse.class, second.getBody());
        assertTrue(body.isDuplicate());
        assertEquals("ACCEPTED", body.getStatus());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("S3: 같은 ID, 다른 내용으로 요청하면 409이고 기존 건은 바뀌지 않는다")
    void conflictingRequestReturns409() {
        service.accept(request("R-1", "ORD-1", 10_000, 3_000));

        ResponseEntity<?> conflict = service.accept(request("R-1", "ORD-1", 10_000, 9_999));

        assertEquals(409, conflict.getStatusCode().value());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, conflict.getBody());
        assertEquals("REFUND_ID_CONFLICT", body.getCode());
        assertEquals(3_000, repository.findByRefundId("R-1").orElseThrow().getRefundAmount());
    }

    @Test
    @DisplayName("S5: 한도를 넘는 요청은 422로 거절하고 REJECTED 상태와 사유를 저장한다")
    void overLimitIsRejectedAndRecorded() {
        service.accept(request("R-1", "ORD-1", 10_000, 7_000));

        ResponseEntity<?> rejected = service.accept(request("R-2", "ORD-1", 10_000, 4_000));

        assertEquals(422, rejected.getStatusCode().value());
        Refund saved = repository.findByRefundId("R-2").orElseThrow();
        assertEquals(RefundStatus.REJECTED, saved.getStatus());
        assertEquals("REFUND_LIMIT_EXCEEDED", saved.getFailureReason().name());
        assertNull(saved.getNextAttemptAt(), "거절 건은 처리 대상이 아니어야 한다");
    }

    @Test
    @DisplayName("거절(REJECTED) 건의 금액은 한도 합계에서 빠지고, 같은 ID로 다시 보내면 기존 결과를 돌려준다")
    void rejectedAmountIsExcludedFromLimit() {
        service.accept(request("R-1", "ORD-1", 10_000, 7_000));
        service.accept(request("R-2", "ORD-1", 10_000, 4_000)); // 422로 거절됨

        // 거절된 4,000원이 합계에 들어 있지 않으므로 3,000원은 접수된다 (7,000 + 3,000 = 10,000)
        assertEquals(202, service.accept(request("R-3", "ORD-1", 10_000, 3_000)).getStatusCode().value());

        ResponseEntity<?> again = service.accept(request("R-2", "ORD-1", 10_000, 4_000));
        assertEquals(200, again.getStatusCode().value());
        RefundDuplicateResponse body = assertInstanceOf(RefundDuplicateResponse.class, again.getBody());
        assertEquals("REJECTED", body.getStatus());
        assertTrue(body.isDuplicate());
    }

    private RefundRequest request(String refundId, String orderId, long paidAmount, long refundAmount) {
        RefundRequest request = new RefundRequest();
        ReflectionTestUtils.setField(request, "refundId", refundId);
        ReflectionTestUtils.setField(request, "orderId", orderId);
        ReflectionTestUtils.setField(request, "paidAmount", paidAmount);
        ReflectionTestUtils.setField(request, "refundAmount", refundAmount);
        return request;
    }
}
