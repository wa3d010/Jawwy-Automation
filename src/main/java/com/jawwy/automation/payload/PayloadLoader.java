package com.jawwy.automation.payload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PayloadLoader {

    private PayloadLoader() {
    }

    public static String load(String classpathLocation) {
        try (InputStream inputStream = PayloadLoader.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Payload not found: " + classpathLocation);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read payload: " + classpathLocation, exception);
        }
    }
}
