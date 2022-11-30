package com.claranet.vies.proxy.model;

import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonCreator;

public class ValidationRequest {
    private final String country;
    private final String vatNumber;

    @JsonCreator
    public ValidationRequest(String country, String vatNumber) {
        this.country = country;
        this.vatNumber = vatNumber;
    }

    public String getCountry() {
        return country;
    }

    public String getVatNumber() {
        return vatNumber;
    }
}
