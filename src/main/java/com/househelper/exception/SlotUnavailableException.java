package com.househelper.exception;

public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(String message) {
        super(message);
    }

    public SlotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
