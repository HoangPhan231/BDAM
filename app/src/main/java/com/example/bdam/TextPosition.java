package com.example.bdam;

public enum TextPosition {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_LEFT("Bottom Left"),
    TOP_RIGHT("Top Right"),
    TOP_CENTER("Top Center"),
    TOP_LEFT("Top Left"),
    CENTER("Center");

    private final String displayName;

    TextPosition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}