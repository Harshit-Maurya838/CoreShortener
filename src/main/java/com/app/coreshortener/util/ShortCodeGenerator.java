package com.app.coreshortener.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

@Component
public class ShortCodeGenerator {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_CHARS.length();
    private static final int DEFAULT_LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<String> RESERVED_WORDS = Set.of(
            "api",
            "auth",
            "urls",
            "error",
            "actuator",
            "swagger-ui",
            "v3",
            "swagger",
            "login",
            "register",
            "health",
            "favicon.ico",
            "robots.txt"
    );

    public String generateRandomCode() {
        return generateRandomCode(DEFAULT_LENGTH);
    }

    public String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_CHARS.charAt(RANDOM.nextInt(BASE)));
        }
        return sb.toString();
    }

    public String encodeBase62(long id) {
        if (id <= 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long num = id;
        while (num > 0) {
            sb.append(BASE62_CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }
        return sb.reverse().toString();
    }

    public boolean isReserved(String code) {
        return code != null && RESERVED_WORDS.contains(code.toLowerCase());
    }
}

