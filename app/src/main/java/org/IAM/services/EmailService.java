package org.IAM.services;

public interface EmailService {
    void sendPasswordResetCode(String toEmail, String code);
}
