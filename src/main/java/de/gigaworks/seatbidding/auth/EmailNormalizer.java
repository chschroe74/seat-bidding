package de.gigaworks.seatbidding.auth;

import java.text.Normalizer;
import java.util.Locale;

public final class EmailNormalizer {
    
    private EmailNormalizer() {
    }
    
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
    
    public static boolean isValid(String email) {
        if (email == null || email.length() > 320 || email.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1;
    }
    
}
