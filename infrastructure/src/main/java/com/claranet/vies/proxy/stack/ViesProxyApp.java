package com.claranet.vies.proxy.stack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class ViesProxyApp {
    public static void main(String[] args) {
        var app = new App();

        new ViesProxyStack(app, "ViesProxyStack", StackProps.builder()
            .env(Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build())
            .build());

        app.synth();
    }
}
