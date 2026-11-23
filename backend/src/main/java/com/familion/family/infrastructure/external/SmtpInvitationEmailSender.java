package com.familion.family.infrastructure.external;

import com.familion.family.application.InvitationEmailSender;
import com.familion.family.infrastructure.config.FamilyAppProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class SmtpInvitationEmailSender implements InvitationEmailSender {

    public static final String INVITATION_SUBJECT = "Te invitaron a FamiliON";

    private static final Logger log = LoggerFactory.getLogger(SmtpInvitationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;

    public SmtpInvitationEmailSender(
            JavaMailSender mailSender, FamilyAppProperties appProperties) {
        this.mailSender = mailSender;
        this.mailFrom = appProperties.mailFrom();
    }

    @Override
    public void sendInvitation(
            String recipientEmail, String familyName, String inviterFullName, String inviteUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail);
            helper.setSubject(INVITATION_SUBJECT);
            helper.setText(
                    buildPlainText(familyName, inviterFullName, inviteUrl),
                    buildHtml(familyName, inviterFullName, inviteUrl));
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn(
                    "Failed to send invitation email to {}: {}",
                    recipientEmail,
                    ex.getMessage());
        }
    }

    private static String buildPlainText(
            String familyName, String inviterFullName, String inviteUrl) {
        return """
                Hola,

                %s te invitó a unirte a la familia "%s" en FamiliON.

                Para crear tu cuenta y aceptar la invitación, ingresá al siguiente enlace:
                %s

                Este enlace vence en 7 días.
                """
                .formatted(inviterFullName, familyName, inviteUrl);
    }

    private static String buildHtml(
            String familyName, String inviterFullName, String inviteUrl) {
        String safeInviter = HtmlUtils.htmlEscape(inviterFullName);
        String safeFamily = HtmlUtils.htmlEscape(familyName);
        String safeUrl = HtmlUtils.htmlEscape(inviteUrl);
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="font-family: sans-serif; color: #1f2937; line-height: 1.5;">
                  <h2>Te invitaron a FamiliON</h2>
                  <p><strong>%s</strong> te invitó a unirte a la familia <strong>%s</strong> en FamiliON.</p>
                  <p>Para crear tu cuenta y aceptar la invitación, hacé clic en el siguiente enlace:</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#0F766E;color:#ffffff;text-decoration:none;border-radius:6px;">Aceptar invitación</a></p>
                  <p style="font-size: 13px; color: #6b7280;">Si el botón no funciona, copiá y pegá este enlace en tu navegador:<br/>%s</p>
                  <p style="font-size: 12px; color: #6b7280;">Este enlace vence en 7 días.</p>
                </body>
                </html>
                """
                .formatted(safeInviter, safeFamily, safeUrl, safeUrl);
    }
}
