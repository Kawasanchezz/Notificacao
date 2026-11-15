package com.taskscheduler.exception;

public final class Exceptions {
    private Exceptions() {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    public static class ExternalServiceException extends RuntimeException {
        public ExternalServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
