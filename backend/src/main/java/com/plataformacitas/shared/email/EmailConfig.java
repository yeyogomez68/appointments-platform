package com.plataformacitas.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.event.TransactionalEventListener;

/** Proveedor de correo ({@code smtp} o {@code fake}) y despacho de mensajes después del commit. */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    private final ObjectProvider<EmailSender> emailSender;

    public EmailConfig(ObjectProvider<EmailSender> emailSender) {
        this.emailSender = emailSender;
    }

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
    EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${app.email.from}") String from) {
        return message -> {
            try {
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(message.to());
                helper.setSubject(message.subject());
                helper.setText(message.text(), message.html());
                mailSender.send(mime);
            } catch (MessagingException e) {
                throw new MailSendException("No se pudo construir el correo", e);
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "fake")
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    /** Se ejecuta tras el commit (o de inmediato sin transacción); un fallo de envío no rompe la petición. */
    @TransactionalEventListener(fallbackExecution = true)
    void dispatch(EmailSender.Message message) {
        try {
            emailSender.getObject().send(message);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo «{}»", message.subject(), e);
        }
    }

    /** Emisor de pruebas: guarda los mensajes en memoria. */
    public static final class RecordingEmailSender implements EmailSender {
        private final List<Message> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(Message message) {
            sent.add(message);
        }

        public List<Message> sent() {
            synchronized (sent) {
                return List.copyOf(sent);
            }
        }

        public void clear() {
            sent.clear();
        }
    }
}
