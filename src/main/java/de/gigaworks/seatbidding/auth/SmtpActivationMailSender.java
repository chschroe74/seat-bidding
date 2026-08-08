package de.gigaworks.seatbidding.auth;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;

@ApplicationScoped
public class SmtpActivationMailSender implements ActivationMailSender {
    
    @Inject
    Mailer mailer;
    
    @Override
    public void send(String email, String code, Instant expiresAt) {
        String text = "Your Office Seat Bidding activation code is: " + code + "\n\n"
                + "It expires at " + expiresAt + ".\n\n"
                + "If you did not initiate activation, you can ignore this message.";
        mailer.send(Mail.withText(email, "Office Seat Bidding activation code", text));
    }
    
}
