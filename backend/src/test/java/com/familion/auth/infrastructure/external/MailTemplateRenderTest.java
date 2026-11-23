package com.familion.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.auth.infrastructure.config.AuthMailConfig;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class MailTemplateRenderTest {

    private static final String FOOTER =
            "Si no solicitaste esta acción, puedes ignorar este mensaje.";

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("templates/");
        htmlResolver.setSuffix(".html");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        htmlResolver.setOrder(2);

        AuthMailConfig config = new AuthMailConfig();
        this.templateEngine = new SpringTemplateEngine();
        this.templateEngine.addTemplateResolver(config.mailTextTemplateResolver());
        this.templateEngine.addTemplateResolver(htmlResolver);
    }

    @Test
    void verifyEmailTemplatesRenderCleanly() {
        Context ctx = context(Map.of("fullName", "Ana Pérez", "code", "042198", "expiresInHours", 24));

        String html = templateEngine.process("mail/verify-email", ctx);
        String text = templateEngine.process("mail/verify-email.txt", ctx);

        assertThat(html).contains("Famili<span style=\"color:#0F766E;\">ON</span>");
        assertThat(html).contains("Ana Pérez").contains("042198").contains("24").contains(FOOTER);
        assertThat(text).contains("Ana Pérez").contains("042198").contains("24").contains(FOOTER);
    }

    @Test
    void twoFactorCodeTemplatesRenderCleanly() {
        Context ctx = context(Map.of("fullName", "Carlos Díaz", "code", "009876", "expiresInMinutes", 10));

        String html = templateEngine.process("mail/two-factor-code", ctx);
        String text = templateEngine.process("mail/two-factor-code.txt", ctx);

        assertThat(html).contains("#0F766E").contains("Carlos Díaz").contains("009876").contains(FOOTER);
        assertThat(text).contains("Carlos Díaz").contains("009876").contains("10").contains(FOOTER);
    }

    @Test
    void passwordResetTemplatesRenderCleanly() {
        String url = "http://localhost:8088/auth/reset-password?token=abc-123_XYZ";
        Context ctx = context(Map.of("fullName", "Sofía López", "resetUrl", url, "expiresInMinutes", 20));

        String html = templateEngine.process("mail/password-reset", ctx);
        String text = templateEngine.process("mail/password-reset.txt", ctx);

        assertThat(html).contains("#0F766E").contains("Sofía López").contains(url).contains(FOOTER);
        assertThat(text).contains("Sofía López").contains(url).contains("20").contains(FOOTER);
    }

    @Test
    void passwordChangedTemplatesRenderCleanly() {
        Context ctx = context(Map.of("fullName", "Diego Ruiz"));

        String html = templateEngine.process("mail/password-changed", ctx);
        String text = templateEngine.process("mail/password-changed.txt", ctx);

        assertThat(html).contains("#0F766E").contains("Diego Ruiz").contains(FOOTER);
        assertThat(text).contains("Diego Ruiz").contains(FOOTER);
    }

    private static Context context(Map<String, Object> vars) {
        Context ctx = new Context(Locale.forLanguageTag("es"));
        ctx.setVariables(vars);
        return ctx;
    }
}
