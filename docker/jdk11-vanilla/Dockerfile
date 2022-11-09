FROM public.ecr.aws/amazoncorretto/amazoncorretto:11-al2-jdk as build

# Copy the software folder to the image and build the function
COPY . build
WORKDIR /build
# skipping tests because it would require a docker-compose setup, which is out of scope for this example
RUN ./mvnw package -DskipTests

FROM public.ecr.aws/amazoncorretto/amazoncorretto:11.0.16-al2
ENV LANG=en_US.UTF-8
ENV TZ=:/etc/localtime
ENV PATH=/var/lang/bin:/usr/local/bin:/usr/bin/:/bin:/opt/bin
ENV LD_LIBRARY_PATH=/var/lang/lib:/lib64:/usr/lib64:/var/runtime:/var/runtime/lib:/var/task:/var/task/lib:/opt/lib
ENV LAMBDA_TASK_ROOT=/var/task
ENV LAMBDA_RUNTIME_DIR=/var/runtime
WORKDIR $LAMBDA_TASK_ROOT
# keep the image compatible with the "slim" version
RUN ln -s $JAVA_HOME /opt/jre

COPY --from=build /build/software/target/function.jar function.jar
COPY bootstrap bootstrap
RUN chmod a+x bootstrap

ENTRYPOINT ["/var/task/bootstrap"]