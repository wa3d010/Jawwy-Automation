package com.jawwy.automation.payload;

import java.util.Map;

public final class TemplateEngine {

    private TemplateEngine() {
    }

    public static String apply(String template, Map<String, String> replacements) {
        String resolved = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }
}
