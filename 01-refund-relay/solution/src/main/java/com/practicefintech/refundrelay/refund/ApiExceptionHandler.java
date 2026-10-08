package com.practicefintech.refundrelay.refund;

import com.practicefintech.refundrelay.refund.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 입력 검증 실패를 계약이 정한 400 INVALID_REQUEST 형식으로 통일한다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage(), null));
    }

    /**
     * 정상 경로에서는 orderId 락이 막아 주므로 거의 발생하지 않지만, 혹시 유니크 제약을
     * 뚫고 들어온 경우 "이미 존재하는 요청"으로 안전하게 처리한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInsert(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("REFUND_ID_CONFLICT", "동시 요청으로 이미 접수되었습니다. 다시 조회해 주세요.", null));
    }
}
