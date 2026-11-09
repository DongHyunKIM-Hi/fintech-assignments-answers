package com.practicefintech.refundrelay.common.exception;

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
     * 정상 경로에서는 orderId 락이 먼저 막으므로 거의 발생하지 않는다.
     * 유일 제약 위반이 발생하면 이미 존재하는 요청으로 처리한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInsert(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("REFUND_ID_CONFLICT", "동시 요청으로 이미 접수되었습니다. 다시 조회해 주세요.", null));
    }

    /**
     * 없는 경로(404)나 지원하지 않는 메서드·미디어 타입(405, 415)처럼 Spring이 상태 코드를 정해 둔 예외는
     * 그 코드를 그대로 쓰고, 나머지는 500으로 응답한다. 둘 다 5-1의 {code, message} 형식을 따른다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(new ErrorResponse("INVALID_REQUEST", "지원하지 않는 요청입니다.", null));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", null));
    }
}
