package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.exception.ConfigurationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PasswordPolicy {
    
    @Inject
    AuthenticationConfiguration configuration;
    private Set<String> blockedPasswords;
    
    @PostConstruct
    void loadBlocklist() {
        var resource = configuration.password().blocklistResource();
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new ConfigurationException("Password blocklist resource does not exist: {}", resource);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            blockedPasswords = reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        }
        catch (java.io.IOException exception) {
            throw new ConfigurationException(exception, "Password blocklist resource cannot be read: {}", resource);
        }
    }
    
    public void validate(String password, String confirmation) {
        if (password == null || !password.equals(confirmation)) {
            throw ApplicationProblem.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "Passwords do not match",
                    "Password and password confirmation must match exactly.");
        }
        long length = password.codePoints().count();
        if (length < configuration.password().minimumLength()
                || length > configuration.password().maximumLength()
                || blockedPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            throw ApplicationProblem.badRequest("PASSWORD_POLICY_VIOLATION", "Password does not meet requirements",
                    "Use 15 to 128 characters and choose a password that is not commonly used or compromised.");
        }
    }
    
}
