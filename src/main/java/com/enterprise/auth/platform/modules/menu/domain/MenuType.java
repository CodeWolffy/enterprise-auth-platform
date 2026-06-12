package com.enterprise.auth.platform.modules.menu.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MenuType {
    MENU("0"),
    BUTTON("1");

    private final String value;

    MenuType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MenuType fromValue(String value) {
        for (MenuType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid MenuType: " + value);
    }
}