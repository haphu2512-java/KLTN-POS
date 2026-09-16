package com.pos.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
