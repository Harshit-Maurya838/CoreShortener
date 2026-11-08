package com.app.coreshortener.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("random code has the default length and only base62 characters")
    void randomCodeShape() {
        String code = generator.generateRandomCode();

        assertThat(code).hasSize(7).matches("^[0-9a-zA-Z]+$");
    }

    @Test
    @DisplayName("random codes do not repeat in a small sample")
    void randomCodesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generateRandomCode());
        }
        assertThat(codes).hasSize(1_000);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0, 0",
            "1, 1",
            "61, Z",
            "62, 10",
            "3843, ZZ",
            "3844, 100"
    })
    @DisplayName("base62 encoding")
    void base62(long id, String expected) {
        assertThat(generator.encodeBase62(id)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "URLS", "Auth", "actuator", "favicon.ico"})
    @DisplayName("reserved words are rejected regardless of case")
    void reservedWords(String word) {
        assertThat(generator.isReserved(word)).isTrue();
    }

    @Test
    @DisplayName("normal codes and null are not reserved")
    void notReserved() {
        assertThat(generator.isReserved("my-link")).isFalse();
        assertThat(generator.isReserved(null)).isFalse();
    }
}
