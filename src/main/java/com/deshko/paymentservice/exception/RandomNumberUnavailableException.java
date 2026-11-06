package com.deshko.paymentservice.exception;

public class RandomNumberUnavailableException extends RuntimeException {
    public RandomNumberUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
