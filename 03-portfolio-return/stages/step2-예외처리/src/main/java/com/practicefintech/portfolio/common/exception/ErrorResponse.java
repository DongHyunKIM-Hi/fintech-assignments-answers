package com.practicefintech.portfolio.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 에러 응답 공통 형식. */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
