package com.example.iot.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signing and verification must use the same key, including in local development. */
@Component
public class JwtKey {
    private final byte[] bytes;

    public JwtKey(@Value("${security.jwt.secret:}") String secret,
                  @Value("${app.cloud:false}") boolean cloud) {
        if (secret.isBlank()) {
            if (cloud) throw new IllegalStateException("JWT_SECRET is required on cloud");
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
        }
        bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
    }

    public byte[] bytes() { return bytes.clone(); }
}
