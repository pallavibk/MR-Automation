package com.qa.dto;

public enum PhoneType {
    MOBILE("Mobile"), WORK("Work"), HOME("Home");

    private final String type;

    PhoneType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return this.type;
    }
}
