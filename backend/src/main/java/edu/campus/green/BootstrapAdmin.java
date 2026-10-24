package edu.campus.green;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public BootstrapAdmin(UserRepository users, PasswordEncoder encoder, Environment environment) {
        this.users = users; this.encoder = encoder; this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = environment.getProperty("CAMPUS_ADMIN_USERNAME");
        String password = environment.getProperty("CAMPUS_ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.length() < 12) {
            throw new IllegalStateException("Set CAMPUS_ADMIN_USERNAME and a CAMPUS_ADMIN_PASSWORD of at least 12 characters before starting.");
        }
        if (users.findByUsername(username).isEmpty()) {
            users.save(new AppUser(username, encoder.encode(password), "Campus Administrator", Role.ADMIN));
        }
    }
}
