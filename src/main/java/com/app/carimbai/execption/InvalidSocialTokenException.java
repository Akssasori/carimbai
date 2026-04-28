package com.app.carimbai.execption;

public class InvalidSocialTokenException extends RuntimeException {
    public InvalidSocialTokenException(String message) {
        super(message);
    }
}
