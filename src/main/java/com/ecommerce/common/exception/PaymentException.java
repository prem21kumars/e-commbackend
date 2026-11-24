package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends BaseException {
    public PaymentException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "PAYMENT_ERROR");
    }

    public PaymentException(String message, String errorCode) {
        super(message, HttpStatus.PAYMENT_REQUIRED, errorCode);
    }

    public PaymentException(String message, Throwable cause, String errorCode) {
        super(message, cause, HttpStatus.PAYMENT_REQUIRED, errorCode);
    }
}

