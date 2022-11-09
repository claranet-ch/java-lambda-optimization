package com.claranet.vies.proxy.common;

import java.util.Optional;

public enum EnvironmentVars {
    JAVA_OPTIONS("JAVA_OPTIONS");

    private final String key;

    EnvironmentVars(String key) {
        this.key = key;
    }

    public Optional<String> expand() {
        return Optional.ofNullable(System.getenv(key));
    }

    public String key() {
        return key;
    }
}
