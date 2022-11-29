package com.claranet.vies.proxy.stack;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.ecr.assets.Platform;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static com.claranet.vies.proxy.common.EnvironmentVars.JAVA_HOME;
import static com.claranet.vies.proxy.common.EnvironmentVars.JAVA_OPTIONS;
import static com.claranet.vies.proxy.stack.ViesProxyStack.TargetPlatform.ARM_64;

public class ViesProxyStack extends Stack {

    enum TargetPlatform {
        ARM_64("aarch64", Architecture.ARM_64), X86_64("x86_64", Architecture.X86_64);

        final String id;
        final Architecture architecture;
        TargetPlatform(String id, Architecture architecture) {
            this.id = id;
            this.architecture = architecture;
        }

        static TargetPlatform matchingCurrent() {
            return ARM_64.id.equals(System.getProperty("os.arch")) ? ARM_64 : X86_64;
        }
    }

    private static final String APP_CDS = "-XX:SharedArchiveFile=appCds.jsa";
    private static final List<RuntimeConfiguration> RUNTIME_CONFIGURATIONS = List.of(
        new RuntimeConfiguration("19", APP_CDS)
    );

    public ViesProxyStack(@Nullable Construct parentScope, @Nullable String id, @Nullable StackProps props) {
        super(parentScope, id, props);

        // API Gateway

        var restApi = new RestApi(this, "ViesProxy", RestApiProps.builder()
            .description("Vies Proxy API Gateway")
            .deployOptions(StageOptions.builder().stageName("dev").build())
            .build());

        var targetPlatform = TargetPlatform.matchingCurrent();

        buildBaseline(this, restApi);
        buildDockerFunctions(this, restApi, targetPlatform);
        buildCustomRuntimeJava19(this, restApi);
    }

    private static void buildBaseline(Construct scope, RestApi restApi) {
        var bundlingOptions = BundlingOptions.builder()
                .image(Runtime.JAVA_11.getBundlingImage())
                .command(List.of("./mvnw -ntp -e -q package -pl software -am -DskipTests -P snapStart" +
                        " && cp software/target/function.jar /asset-output/"))
                .user("root")
                .entrypoint(List.of("/bin/sh", "-c"))
                .outputType(BundlingOutput.ARCHIVED)
                .build();
        var baselineFunction = Function.Builder.create(scope, "ViesProxy19-baseline")
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.X86_64)
                .functionName("vies-proxy-11-baseline")
                .code(Code.fromAsset(".", AssetOptions.builder().bundling(bundlingOptions).build()))
                .handler("com.claranet.vies.proxy.Handler::handleRequest")
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .logRetention(RetentionDays.FIVE_DAYS)
                .tracing(Tracing.DISABLED) // tracing is not yet supported for SnapStart
                .build();
        buildMethod(scope, restApi, "11-baseline", baselineFunction);
    }

    private static void buildCustomRuntimeJava19(Construct scope, RestApi restApi) {
        var bundlingOptions = BundlingOptions.builder()
                .image(DockerImage.fromRegistry("public.ecr.aws/amazoncorretto/amazoncorretto:19-al2-jdk"))
                .command(List.of("./build-custom-runtime.sh && ./mvnw clean"))
                .entrypoint(List.of("/bin/sh", "-c"))
                .user("root")
                .outputType(BundlingOutput.ARCHIVED)
                .build();

        var customRuntimeFunction = Function.Builder.create(scope, "ViesProxy19-custom")
            .runtime(Runtime.PROVIDED)
            .architecture(Architecture.X86_64)
            .functionName("vies-proxy-19-custom")
            .memorySize(512)
            .environment(Map.of(
                JAVA_OPTIONS.key(), APP_CDS,
                JAVA_HOME.key(), "./jre"
            ))
            .handler("not.really.needed")
            .code(Code.fromAsset(".", AssetOptions.builder().bundling(bundlingOptions).build()))
            .timeout(Duration.seconds(15))
            .logRetention(RetentionDays.FIVE_DAYS)
            .tracing(Tracing.ACTIVE)
            .build();

        buildMethod(scope, restApi, "19-custom", customRuntimeFunction);
    }

    private static void buildDockerFunctions(Construct scope, RestApi restApi, TargetPlatform targetPlatform) {
        var ecrPlatform = targetPlatform == ARM_64 ? Platform.LINUX_ARM64 : Platform.LINUX_AMD64;
        RUNTIME_CONFIGURATIONS.forEach(configuration -> {
            var buildConfiguration = AssetImageCodeProps.builder()
                .file("docker/jdk" + configuration.runtime + "/Dockerfile")
                .platform(ecrPlatform)
                .build();
            var function = DockerImageFunction.Builder.create(scope, "ViesProxy" + configuration.runtime)
                .code(DockerImageCode.fromImageAsset(".", buildConfiguration))
                .functionName("vies-proxy-" + configuration.runtime)
                .architecture(targetPlatform.architecture)
                .memorySize(512)
                .environment(
                    Map.of(
                        JAVA_OPTIONS.key(), configuration.javaOptions
                    ))
                .timeout(Duration.seconds(15))
                .logRetention(RetentionDays.FIVE_DAYS)
                .tracing(Tracing.ACTIVE)
                .build();

            buildMethod(scope, restApi, configuration.runtime, function);
        });
    }

    private static void buildMethod(Construct scope, RestApi restApi, String path, Function function) {

        function.grantInvoke(new ServicePrincipal("apigateway.amazonaws.com"));

        var method = restApi.getRoot()
            .addResource(path)
            .addResource("validate")
            .addResource("{country}")
            .addResource("{vatNumber}")
            .addMethod("GET", LambdaIntegration.Builder.create(function).timeout(Duration.seconds(15)).build());

        CfnOutput.Builder.create(scope, "vies-proxy-" + path)
            .description("Endpoint for lambda " + path)
            .exportName(path + "Endpoint")
            .value(
                restApi.getUrl() +
                method.getResource().getPath().substring(1) // remove trailing slash
            )
            .build();
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
