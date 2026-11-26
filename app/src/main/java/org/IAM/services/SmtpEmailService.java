package org.IAM.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
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
        try {
            mailSender.send(message);
            log.info("Password reset email dispatched");
        } catch (MailException ex) {
            log.error("Failed to dispatch password reset email", ex);
            throw ex;
        }
    }
}
