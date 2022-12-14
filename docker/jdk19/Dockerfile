FROM public.ecr.aws/amazoncorretto/amazoncorretto:19-al2-jdk as build

# Copy the software folder to the image and build the function
COPY . build
WORKDIR /build
RUN ./mvnw -ntp -e -q package -pl software -am -DskipTests

# Find JDK module dependencies dynamically from the uber jar
RUN jdeps -q \
    --ignore-missing-deps \
    --multi-release 19 \
    --print-module-deps \
    software/target/function.jar > /tmp/jre-deps.info

# Create a slim JRE which only contains the required modules to run the function
RUN jlink --verbose \
    --compress 2 \
    --strip-java-debug-attributes \
    --no-header-files \
    --no-man-pages \
    --output /jre-slim \
    --add-modules $(cat /tmp/jre-deps.info)

# Generate CDS archive for our slim JRE
# It creates the file /jre-slim/lib/server/classes.jsa
RUN /jre-slim/bin/java -Xshare:dump -XX:+UseSerialGC -version

WORKDIR /build/software/target
RUN export AWS_LAMBDA_RUNTIME_API="localhost:8080" &&\
    /jre-slim/bin/java --add-opens java.base/java.util=ALL-UNNAMED -XX:ArchiveClassesAtExit=appCds.jsa -jar function.jar "com.claranet.vies.proxy.Handler::handleRequest"  2>/dev/null 1>&2 || :

FROM public.ecr.aws/debian/debian:stable-slim
ENV JAVA_HOME=/opt/jre
ENV LANG=en_US.UTF-8
ENV TZ=:/etc/localtime
ENV PATH=/var/lang/bin:/usr/local/bin:/usr/bin/:/bin:/opt/bin
ENV LD_LIBRARY_PATH=/var/lang/lib:/lib64:/usr/lib64:/var/runtime:/var/runtime/lib:/var/task:/var/task/lib:/opt/lib
ENV LAMBDA_TASK_ROOT=/var/task
ENV LAMBDA_RUNTIME_DIR=/var/runtime
WORKDIR $LAMBDA_TASK_ROOT
COPY --from=build /jre-slim $JAVA_HOME/
COPY --from=build /build/software/target/appCds.jsa appCds.jsa
COPY --from=build /build/software/target/function.jar function.jar
COPY bootstrap bootstrap
RUN chmod a+x bootstrap

ENTRYPOINT ["/var/task/bootstrap"]
