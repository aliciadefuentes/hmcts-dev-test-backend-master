package uk.gov.hmcts.reform.dev.exceptions;

public class DuplicateTaskNumberException extends RuntimeException {
    public DuplicateTaskNumberException(String message) {
        super(message);
    }

    public DuplicateTaskNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
