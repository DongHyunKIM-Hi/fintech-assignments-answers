package com.practicefintech.paymentlimit.common.exception;

import com.practicefintech.paymentlimit.common.enums.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** API 명세의 에러 응답 형식({@link ErrorResponse})으로 예외를 일관되게 변환합니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage(), e.getPaymentId()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "요청 형식이 올바르지 않습니다."));
    }

    /**
     * 없는 경로(404)나 지원하지 않는 메서드·미디어 타입(405, 415)처럼 Spring이 상태 코드를 정해 둔 예외는
     * 그 코드를 그대로 쓰고, 나머지는 500으로 응답한다. 둘 다 명세의 {code, message} 형식을 따른다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "지원하지 않는 요청입니다."));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
