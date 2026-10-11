package com.example.bakir_khata.model.enums;

public enum MobileProvider {
    BKASH("bKash"),
    NAGAD("Nagad"),
    ROCKET("Rocket"),
    OTHER("Other");

    private final String displayName;

    MobileProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
