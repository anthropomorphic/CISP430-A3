package com.mdorst.exception;

public class SyntaxError extends Exception {
    public SyntaxError(String message) {
        super(message);
    }
    public SyntaxError() {}
}