package com.claranet.vies.proxy.stack;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ecr.assets.Platform;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static com.claranet.vies.proxy.common.EnvironmentVars.JAVA_HOME;
import static com.claranet.vies.proxy.common.EnvironmentVars.JAVA_TOOL_OPTIONS;
import static com.claranet.vies.proxy.stack.ViesProxyStack.TargetPlatform.ARM_64;

public class ViesProxyStack extends Stack {

    private static final String TIERED_COMPILATION = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseSerialGC";

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

    private static final String APP_CDS = TIERED_COMPILATION + " -XX:SharedArchiveFile=appCds.jsa";
    private static final List<RuntimeConfiguration> RUNTIME_CONFIGURATIONS = List.of(
        new RuntimeConfiguration("19", APP_CDS)
    );

    public ViesProxyStack(@Nullable Construct parentScope, @Nullable String id, @Nullable StackProps props) {
        super(parentScope, id, props);

        var targetPlatform = TargetPlatform.matchingCurrent();

        buildBaseline(this);
        buildDockerFunctions(this, targetPlatform);
        buildCustomRuntimeJava19(this);
    }

    private static void buildBaseline(Construct scope) {
        var bundlingOptions = BundlingOptions.builder()
                .image(Runtime.JAVA_11.getBundlingImage())
                .command(List.of("./mvnw -ntp -e -q package -pl software -am -DskipTests -P snapStart" +
                        " && cp software/target/function.jar /asset-output/"))
                .user("root")
                .entrypoint(List.of("/bin/sh", "-c"))
                .outputType(BundlingOutput.ARCHIVED)
                .build();
        var baselineFunction = Function.Builder.create(scope, "ViesProxy11-baseline")
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.X86_64)
                .functionName("vies-proxy-11")
                .code(Code.fromAsset(".", AssetOptions.builder().bundling(bundlingOptions).build()))
                .handler("com.claranet.vies.proxy.Handler::handleRequest")
                .environment(Map.of(
                    JAVA_TOOL_OPTIONS.key(), TIERED_COMPILATION
                ))
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .logRetention(RetentionDays.FIVE_DAYS)
                .tracing(Tracing.DISABLED) // tracing is not yet supported for SnapStart, so we disable it
                .build();
        outputFunctionARN(scope, "11", baselineFunction);
    }

    private static void buildCustomRuntimeJava19(Construct scope) {
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
                JAVA_TOOL_OPTIONS.key(), APP_CDS,
                JAVA_HOME.key(), "./jre"
            ))
            .handler("not.really.needed")
            .code(Code.fromAsset(".", AssetOptions.builder().bundling(bundlingOptions).build()))
            .timeout(Duration.seconds(15))
            .logRetention(RetentionDays.FIVE_DAYS)
            .tracing(Tracing.ACTIVE)
            .build();

        outputFunctionARN(scope, "19-custom", customRuntimeFunction);
    }

    private static void buildDockerFunctions(Construct scope, TargetPlatform targetPlatform) {
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
                .environment(Map.of(
                    JAVA_TOOL_OPTIONS.key(), configuration.javaOptions
                ))
                .timeout(Duration.seconds(15))
                .logRetention(RetentionDays.FIVE_DAYS)
                .tracing(Tracing.ACTIVE)
                .build();

            outputFunctionARN(scope, configuration.runtime, function);
        });
    }

    private static void outputFunctionARN(Construct scope, String path, Function function) {

        CfnOutput.Builder.create(scope, "vies-proxy-" + path)
                .description("ARN " + path)
                .exportName(path + "ARN")
                .value(function.getFunctionArn())
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
