package com.familion.family.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.family.infrastructure.config.FamilyAppProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpInvitationEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsMultipartEmailWithPlainTextAndHtmlContainingInviteDetails() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        SmtpInvitationEmailSender sender = new SmtpInvitationEmailSender(
                mailSender,
                new FamilyAppProperties("http://localhost:8088", "no-reply@familion.local"));

        String inviteUrl = "http://localhost:8088/auth/invite?token=abc123";
        sender.sendInvitation("mateo@familion.test", "Familia Perez", "Ana Perez", inviteUrl);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Te invitaron a FamiliON");
        assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@familion.local");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("mateo@familion.test");

        String body = extractAllText(sent.getContent());
        assertThat(body).contains("Ana Perez");
        assertThat(body).contains("Familia Perez");
        assertThat(body).contains(inviteUrl);
    }

    @Test
    void swallowsMailSendFailureWithoutThrowing() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP down"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        SmtpInvitationEmailSender sender = new SmtpInvitationEmailSender(
                mailSender,
                new FamilyAppProperties("http://localhost:8088", "no-reply@familion.local"));

        assertThatCode(() -> sender.sendInvitation(
                        "mateo@familion.test",
                        "Familia Perez",
                        "Ana Perez",
                        "http://localhost:8088/auth/invite?token=abc123"))
                .doesNotThrowAnyException();
    }

    private static String extractAllText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                builder.append(extractAllText(multipart.getBodyPart(i).getContent()));
            }
            return builder.toString();
        }
        return "";
    }
}
