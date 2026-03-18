package com.pos.exception;

public class PosRuntimeException extends RuntimeException {

    public PosRuntimeException(String message) {
        super(message);
    }

    public PosRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
