package com.claranet.vies.proxy.stack;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static com.claranet.vies.proxy.common.EnvironmentVars.JAVA_OPTIONS;

public class ViesProxyStack extends Stack {

    private static final String APP_CDS = "-XX:SharedArchiveFile=appCds.jsa";
    private static final List<RuntimeConfiguration> RUNTIME_CONFIGURATIONS = List.of(
        new RuntimeConfiguration("11-vanilla", ""),
        new RuntimeConfiguration("11", ""),
        new RuntimeConfiguration("19", APP_CDS)
    );
    private static final String ARM_64 = "arm64";

    public ViesProxyStack(@Nullable Construct scope, @Nullable String id, @Nullable StackProps props) {
        super(scope, id, props);

        // API Gateway

        var restApi = new RestApi(this, "ViesProxy", RestApiProps.builder()
            .description("Vies Proxy API Gateway")
            .deployOptions(StageOptions.builder().stageName("dev").build())
            .build());

        var allowedArchitectures = List.of(ARM_64, "x86_64");
        var architectureParam = CfnParameter.Builder.create(this, "architecture")
            .type("String")
            .allowedValues(allowedArchitectures)
            .description("Runtime architecture. Allowed values: "+ allowedArchitectures)
            .build();

        var targetArchitecture = ARM_64.equals(architectureParam.getValueAsString()) ? Architecture.ARM_64 : Architecture.X86_64;

        RUNTIME_CONFIGURATIONS.forEach(configuration -> {
            var function = DockerImageFunction.Builder.create(this, "ViesProxy" + configuration.runtime)
                .code(DockerImageCode.fromImageAsset(".", AssetImageCodeProps.builder().file("docker/jdk" + configuration.runtime + "/Dockerfile").build()))
                .functionName("vies-proxy-" + configuration.runtime)
                .architecture(targetArchitecture)
                .memorySize(512)
                .environment(
                    Map.of(
                        JAVA_OPTIONS.key(), configuration.javaOptions
                    ))
                .timeout(Duration.seconds(15))
                .logRetention(RetentionDays.FIVE_DAYS)
                .tracing(Tracing.ACTIVE)
                .build();

            function.grantInvoke(new ServicePrincipal("apigateway.amazonaws.com"));

            restApi.getRoot()
                .addResource(configuration.runtime)
                .addResource("validate")
                .addResource("{country}")
                .addResource("{vatNumber}")
                .addMethod("GET", LambdaIntegration.Builder.create(function).timeout(Duration.seconds(15)).build());
        });

    }

    private static class RuntimeConfiguration {
        private final String runtime;
        private final String javaOptions;

        private RuntimeConfiguration(String runtime, String javaOptions) {
            this.runtime = runtime;
            this.javaOptions = javaOptions;
        }
    }
}
