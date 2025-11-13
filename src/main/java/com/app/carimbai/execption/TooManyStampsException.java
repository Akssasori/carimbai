package com.app.carimbai.execption;

public class TooManyStampsException extends RuntimeException{
    public TooManyStampsException(String message) {
        super(message);
    }
}
