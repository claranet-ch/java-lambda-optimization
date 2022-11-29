package com.claranet.vies.proxy.model;

public class ValidationResponse {
    private final boolean valid;
    private final String name;
    private final String address;

    public ValidationResponse(boolean valid, String name, String address) {
        this.valid = valid;
        this.name = name;
        this.address = address;
    }

    public boolean isValid() {
        return valid;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String toJsonString() {
        return "{ \"valid\": "+valid+", \"address\": "+address+"\"}";
    }
}
