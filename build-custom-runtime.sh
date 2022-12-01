#!/usr/bin/env bash

yum -y update && yum install -y zip

./mvnw -ntp -e -q package -pl software -am -DskipTests

jdeps -q \
    --ignore-missing-deps \
    --multi-release 19 \
    --print-module-deps \
    software/target/function.jar > /tmp/jre-deps.info

jlink --verbose \
    --compress 2 \
    --strip-java-debug-attributes \
    --no-header-files \
    --no-man-pages \
    --output /jre \
    --add-modules $(cat /tmp/jre-deps.info)

/jre/bin/java -Xshare:dump -XX:+UseSerialGC -version

mkdir -p /tmp/software
cp bootstrap software/target/function.jar /tmp/software
cd /tmp/software/
export AWS_LAMBDA_RUNTIME_API="localhost:8080"
/jre/bin/java --add-opens java.base/java.util=ALL-UNNAMED -XX:ArchiveClassesAtExit=appCds.jsa -jar function.jar "com.claranet.vies.proxy.Handler::handleRequest" 2>/dev/null 1>&2 || :

chmod 755 bootstrap
zip -r runtime.zip bootstrap appCds.jsa function.jar /jre
mv runtime.zip /asset-output
