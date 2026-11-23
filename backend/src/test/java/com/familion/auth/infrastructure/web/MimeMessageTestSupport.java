package com.familion.auth.infrastructure.web;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MimeMessageTestSupport {

    private static final Pattern SIX_DIGIT_PATTERN = Pattern.compile("(?m)^(\\d{6})$");
    private static final Pattern RESET_URL_PATTERN =
            Pattern.compile("(https?://[^\\s]+/auth/reset-password\\?token=([A-Za-z0-9_-]+))");

    private MimeMessageTestSupport() {}

    static String extractTextBody(MimeMessage message) throws Exception {
        return extractPartByType(message, "text/plain");
    }

    static String extractHtmlBody(MimeMessage message) throws Exception {
        return extractPartByType(message, "text/html");
    }

    static String extractSixDigitCode(MimeMessage message) throws Exception {
        String text = extractTextBody(message);
        Matcher matcher = SIX_DIGIT_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No 6-digit code found in email body:\n" + text);
        }
        return matcher.group(1);
    }

    static String extractResetUrl(MimeMessage message) throws Exception {
        String text = extractTextBody(message);
        Matcher matcher = RESET_URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No password reset URL found in email body:\n" + text);
        }
        return matcher.group(1);
    }

    static String extractResetToken(MimeMessage message) throws Exception {
        String text = extractTextBody(message);
        Matcher matcher = RESET_URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No password reset token found in email body:\n" + text);
        }
        return matcher.group(2);
    }

    private static String extractPartByType(Part part, String mimeType) throws Exception {
        if (part instanceof MimeMessage mimeMessage) {
            mimeMessage.saveChanges();
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String found = extractPartByType(bodyPart, mimeType);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (part.isMimeType(mimeType)) {
            return content.toString();
        }
        return null;
    }
}
