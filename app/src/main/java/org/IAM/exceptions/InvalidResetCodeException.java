package org.IAM.exceptions;

public class InvalidResetCodeException extends RuntimeException {
    public InvalidResetCodeException(String message) {
        super(message);
    }
}
