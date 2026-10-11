package com.example.bakir_khata.security;

import com.example.bakir_khata.exception.BusinessRuleException;

/** A minimal canonical phone-number format for the university project.
 * Production international numbering should use a tested phone-number library.
 */
public final class PhoneNumbers {
    private PhoneNumbers() { }
    public static String canonical(String input) {
        if (input==null || input.isBlank()) throw new BusinessRuleException("Phone number is required.");
        String value=input.replaceAll("[\\s-]", "");
        if (value.startsWith("01") && value.length()==11) value="+880"+value.substring(1);
        else if (value.startsWith("880") && value.length()==13) value="+"+value;
        if (!value.matches("\\+[1-9][0-9]{7,14}"))
            throw new BusinessRuleException("Phone must be in international format, e.g. +88017XXXXXXXX.");
        return value;
    }
}
