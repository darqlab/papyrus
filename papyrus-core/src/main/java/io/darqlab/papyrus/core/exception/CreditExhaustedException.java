package io.darqlab.papyrus.core.exception;

public class CreditExhaustedException extends RuntimeException {

    public CreditExhaustedException(String message) {
        super(message);
    }

    public CreditExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
