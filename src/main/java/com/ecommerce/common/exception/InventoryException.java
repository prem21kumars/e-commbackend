package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class InventoryException extends BaseException {
    public InventoryException(String message) {
        super(message, HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY");
    }

    public InventoryException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}

