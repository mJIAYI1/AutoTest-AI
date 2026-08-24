# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN apk add --no-cache curl \
    && addgroup -S autotest \
    && adduser -S -G autotest -u 10001 autotest

WORKDIR /app
COPY --from=build /workspace/target/autotest-ai-backend-*.jar /app/app.jar

USER autotest
EXPOSE 18080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD curl --fail --silent --show-error http://127.0.0.1:18080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
