package com.plataformacitas.shared.email;

/**
 * Envío de correo. Los servicios no lo llaman directamente: publican un {@link Message} con
 * {@code ApplicationEventPublisher} y {@link EmailConfig} lo despacha después del commit.
 */
public interface EmailSender {

    record Message(String to, String subject, String text, String html) {}

    void send(Message message);
}
