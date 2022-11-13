FROM public.ecr.aws/amazoncorretto/amazoncorretto:19-al2-jdk as build

RUN yum -y update && yum install -y zip

# Copy the software folder to the image and build the function
COPY . build
WORKDIR /build
# skipping tests because it would require a docker-compose setup, which is out of scope for this example
RUN ./mvnw -ntp -e -q package -DskipTests

# Find JDK module dependencies dynamically from the uber jar
RUN jdeps -q \
    --ignore-missing-deps \
    --multi-release 19 \
    --print-module-deps \
    software/target/function.jar > jre-deps.info

# Create a slim JRE which only contains the required modules to run the function
RUN jlink --verbose \
    --compress 2 \
    --strip-java-debug-attributes \
    --no-header-files \
    --no-man-pages \
    --output /jre \
    --add-modules $(cat jre-deps.info)

# Generate CDS archive for our slim JRE
# It creates the file /jre/lib/server/classes.jsa
RUN /jre/bin/java -Xshare:dump -version

WORKDIR /build/software/target
RUN export AWS_LAMBDA_RUNTIME_API="localhost:8080" &&\
    /jre/bin/java --add-opens java.base/java.util=ALL-UNNAMED -XX:ArchiveClassesAtExit=appCds.jsa -jar function.jar "com.claranet.vies.proxy.Handler::handleRequest" 2>/dev/null 1>&2 || :
COPY bootstrap bootstrap
RUN chmod 755 bootstrap && \
    zip -r runtime.zip bootstrap appCds.jsa function.jar /jre && \
    mkdir -p /asset-output && mv runtime.zip /asset-output


