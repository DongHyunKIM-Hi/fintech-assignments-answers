package com.practicefintech.portfolio.common;

/** 에러 응답 공통 형식. */
public record ErrorResponse(String code, String message) {
}
