package com.practicefintech.portfolio.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 에러 응답 공통 형식.
 *
 * <p>1단계에는 이 형식으로 바꿔 주는 곳이 아직 없다. {@code ApiException}과
 * {@code GlobalExceptionHandler}가 없어서, 잘못된 요청은 Spring 기본 500 응답으로 나간다.
 * 2단계에서 두 클래스를 만들어 400, 404, 503을 이 형식으로 내려 준다.
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
