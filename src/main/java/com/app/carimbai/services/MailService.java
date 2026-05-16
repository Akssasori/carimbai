package com.app.carimbai.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * Envia emails transacionais. Hoje so o de reset de senha.
 *
 * Comportamento "silencioso" se SMTP nao configurado: em vez de quebrar, loga
 * WARN com o conteudo do email. Isso permite rodar local sem credenciais Resend
 * e mesmo assim usar o fluxo (admin copia o link do log).
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${carimbai.mail.from:}")
    private String from;

    @Value("${carimbai.mail.from-name:Carimbai}")
    private String fromName;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetUrl) {
        if (isMailDisabled()) {
            log.warn("Mail nao configurado (CARIMBAI_RESEND_API_KEY/CARIMBAI_MAIL_FROM vazios). " +
                            "Reset link para {} (copie manualmente em dev): {}",
                    toEmail, resetUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject("Carimbai — redefinicao de senha");
            helper.setText(buildHtml(recipientName, resetUrl), true);

            mailSender.send(message);
            log.info("Email de reset enviado para {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Engole a falha: usuario pode pedir reset de novo. Audit log capta a tentativa.
            log.error("Falha ao enviar email de reset para {}: {}", toEmail, e.getMessage(), e);
        } catch (org.springframework.mail.MailException e) {
            log.error("Erro do JavaMailSender ao enviar para {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private boolean isMailDisabled() {
        return smtpPassword == null || smtpPassword.isBlank()
                || from == null || from.isBlank();
    }

    private String buildHtml(String recipientName, String resetUrl) {
        String safeName = recipientName != null && !recipientName.isBlank() ? recipientName : "Equipe";
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"></head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1f2937; max-width: 560px; margin: 0 auto; padding: 24px;">
                    <h1 style="font-size: 22px; color: #1f2937;">Carimbai</h1>
                    <p>Ola, %s!</p>
                    <p>Recebemos um pedido para redefinir a senha da sua conta no Carimbai.</p>
                    <p style="margin: 24px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background: linear-gradient(90deg, #7c3aed 0%%, #3b82f6 100%%); color: white; text-decoration: none; border-radius: 8px; font-weight: 600;">Redefinir senha</a>
                    </p>
                    <p style="color: #6b7280; font-size: 14px;">Ou copie e cole este link no navegador:<br><a href="%s" style="color: #7c3aed; word-break: break-all;">%s</a></p>
                    <p style="color: #6b7280; font-size: 13px; margin-top: 32px;">O link expira em 1 hora. Se voce nao pediu, ignore este email — sua senha continua segura.</p>
                </body>
                </html>
                """.formatted(safeName, resetUrl, resetUrl, resetUrl);
    }
}
