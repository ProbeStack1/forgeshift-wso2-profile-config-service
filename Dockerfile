# The JAR is built by the pipeline, not here.
#
# This used to be a two-stage build that ran `mvn package` in a maven image.
# That stopped being possible when the service took a dependency on
# forge-auth-lib, which lives in a private Artifact Registry: the builder
# container has no Google credentials, so the dependency cannot be resolved
# inside it. The workflow authenticates and builds the JAR on the runner, and
# this image just carries it - the same arrangement ps-community-svc and the
# other w2k services use.
#
# Building locally therefore needs the JAR first:
#   mvn clean package -DskipTests && docker build -t profile-config .
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --chown=app:app target/forgeshift-wso2-profile-config-service-*.jar /app/app.jar
USER app
EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
