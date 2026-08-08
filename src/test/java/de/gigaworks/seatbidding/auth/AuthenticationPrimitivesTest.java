package de.gigaworks.seatbidding.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class AuthenticationPrimitivesTest {
    
    @Test
    void normalizesUnicodeWhitespaceAndCaseBeforeValidation() {
        assertEquals("alex@example.com", EmailNormalizer.normalize("  ALEX@EXAMPLE.COM  "));
        assertEquals("full@example.com", EmailNormalizer.normalize("ＦＵＬＬ@example.com"));
        assertTrue(EmailNormalizer.isValid("alex@example.com"));
        assertFalse(EmailNormalizer.isValid("alex example.com"));
        assertFalse(EmailNormalizer.isValid("alex@@example.com"));
        assertNull(EmailNormalizer.normalize("  "));
    }
    
    @Test
    void generatesSixDigitCodesAndAtLeast256BitOpaqueCredentials() {
        var generator = new SecureTokenGenerator();
        assertTrue(generator.activationCode().matches("[0-9]{6}"));
        var leadingZeroGenerator = new SecureTokenGenerator(new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 31947;
            }
        });
        assertEquals("031947", leadingZeroGenerator.activationCode());
        String first = generator.opaqueToken();
        String second = generator.opaqueToken();
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        assertNotEquals(first, second);
    }
    
    @Test
    void keyedCodeDigestsAndOpaqueTokenHashesAreDeterministicButDistinct() {
        String code = "031947";
        String keyed = SecretDigests.hmacSha256("a-secret-pepper-with-more-than-32-characters", code);
        assertEquals(64, keyed.length());
        assertNotEquals(SecretDigests.sha256(code), keyed);
        assertTrue(SecretDigests.matches(keyed, SecretDigests.hmacSha256("a-secret-pepper-with-more-than-32-characters", code)));
        assertFalse(SecretDigests.matches(keyed, SecretDigests.hmacSha256("another-secret-pepper-with-32-characters", code)));
    }
    
}
