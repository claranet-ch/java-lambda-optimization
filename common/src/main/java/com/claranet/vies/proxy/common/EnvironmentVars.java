package com.claranet.vies.proxy.common;

import java.util.Optional;

public enum EnvironmentVars {
    JAVA_HOME("JAVA_HOME"),
    JAVA_TOOL_OPTIONS("JAVA_TOOL_OPTIONS");

    private final String key;

    EnvironmentVars(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
