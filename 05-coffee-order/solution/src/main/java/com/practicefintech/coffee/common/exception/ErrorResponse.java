package com.practicefintech.coffee.common.exception;

import com.practicefintech.coffee.common.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message);
    }
}
