package org.IAM.exceptions;

public class InvalidPasswordChangeException extends RuntimeException {
    public InvalidPasswordChangeException(String message) {
        super(message);
    }
}
