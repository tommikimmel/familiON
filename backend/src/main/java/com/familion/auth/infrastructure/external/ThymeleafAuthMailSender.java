package com.familion.auth.infrastructure.external;

import com.familion.auth.application.AuthMailSender;
import com.familion.auth.infrastructure.config.AuthMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class ThymeleafAuthMailSender implements AuthMailSender {

    public static final String SUBJECT_VERIFY_EMAIL = "Verifica tu correo en FamiliON";
    public static final String SUBJECT_TWO_FACTOR = "Tu código de acceso a FamiliON";
    public static final String SUBJECT_PASSWORD_RESET = "Restablecer tu contraseña de FamiliON";
    public static final String SUBJECT_PASSWORD_CHANGED = "Tu contraseña de FamiliON cambió";

    private static final Logger log = LoggerFactory.getLogger(ThymeleafAuthMailSender.class);
    private static final Locale MAIL_LOCALE = Locale.forLanguageTag("es");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthMailProperties mailProperties;

    public ThymeleafAuthMailSender(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AuthMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;
    }

    @Override
    public void sendVerificationEmail(String to, String fullName, String code) {
        sendMultipart(
                to,
                SUBJECT_VERIFY_EMAIL,
                "verify-email",
                Map.of("fullName", safeName(fullName), "code", code, "expiresInHours", 24));
    }

    @Override
    public void sendTwoFactorCodeEmail(String to, String fullName, String code) {
        sendMultipart(
                to,
                SUBJECT_TWO_FACTOR,
                "two-factor-code",
                Map.of("fullName", safeName(fullName), "code", code, "expiresInMinutes", 10));
    }

    @Override
    @Async
    public void sendPasswordResetEmailAsync(String to, String fullName, String resetUrl) {
        sendMultipart(
                to,
                SUBJECT_PASSWORD_RESET,
                "password-reset",
                Map.of(
                        "fullName", safeName(fullName),
                        "resetUrl", resetUrl,
                        "expiresInMinutes", 20));
    }

    @Override
    public void sendPasswordChangedEmail(String to, String fullName) {
        sendMultipart(
                to,
                SUBJECT_PASSWORD_CHANGED,
                "password-changed",
                Map.of("fullName", safeName(fullName)));
    }

    private void sendMultipart(
            String to, String subject, String templateName, Map<String, Object> variables) {
        Objects.requireNonNull(to, "Recipient email must not be null");
        Context context = new Context(MAIL_LOCALE);
        context.setVariables(variables);

        String htmlBody = templateEngine.process("mail/" + templateName, context);
        String textBody = templateEngine.process("mail/" + templateName + ".txt", context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.mailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(mimeMessage);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build email message for " + templateName, ex);
        } catch (MailException ex) {
            log.error("Failed to send '{}' email to {}: {}", templateName, to, ex.getMessage());
        }
    }

    private static String safeName(String fullName) {
        return (fullName == null || fullName.isBlank()) ? "usuario" : fullName.trim();
    }
}
