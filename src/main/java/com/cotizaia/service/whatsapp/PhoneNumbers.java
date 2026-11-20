package com.cotizaia.service.whatsapp;

import java.util.regex.Pattern;

/**
 * WhatsApp addresses numbers in E.164. The country code cannot be guessed, and a wrong guess reaches a stranger,
 * so it must be typed: {@code +573001234567}. Spaces, hyphens, dots and parentheses are ignored.
 */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

    private PhoneNumbers() {
    }

    public static String normalize(String typed) {
        String cleaned = typed == null ? "" : SEPARATORS.matcher(typed).replaceAll("");
        if (!E164.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("phone must include the country code, like +573001234567");
        }
        return cleaned;
    }

    /** The number with every digit but the last four hidden, for responses and logs. */
    public static String masked(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
