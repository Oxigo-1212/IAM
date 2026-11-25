package org.IAM.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(
        JavaMailSender mailSender,
        @Value("${app.mail.from:noreply@example.com}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your password reset code");
        message.setText(
            "Use this 6-digit code to reset your password: " + code
                + "\n\nThis code expires in 10 minutes. If you did not request a reset, ignore this email."
        );
        mailSender.send(message);
    }
}
