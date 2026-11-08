package com.app.coreshortener.exception;

public class ShortCodeConflictException extends RuntimeException {
    public ShortCodeConflictException(String message) {
        super(message);
    }
}

