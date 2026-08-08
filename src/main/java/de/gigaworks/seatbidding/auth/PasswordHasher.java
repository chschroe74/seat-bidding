package de.gigaworks.seatbidding.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PasswordHasher {
    
    @Inject
    AuthenticationConfiguration configuration;
    
    public String hash(String password) {
        return Password.hash(password).addRandomSalt(16).with(configured()).getResult();
    }
    
    public boolean verify(String password, String encodedHash) {
        if (password == null || encodedHash == null || !encodedHash.startsWith("$argon2id$")) {
            return false;
        }
        try {
            return Password.check(password, encodedHash).with(Argon2Function.getInstanceFromHash(encodedHash));
        }
        catch (RuntimeException _) {
            return false;
        }
    }
    
    public boolean needsRehash(String encodedHash) {
        try {
            return !configured().equals(Argon2Function.getInstanceFromHash(encodedHash));
        }
        catch (RuntimeException _) {
            return true;
        }
    }
    
    private Argon2Function configured() {
        var password = configuration.password();
        return Argon2Function.getInstance(password.argon2MemoryKib(), password.argon2Iterations(),
                password.argon2Parallelism(), 32, Argon2.ID);
    }
    
}
