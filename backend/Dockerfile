FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:24-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S familion && adduser -S -G familion familion

COPY --from=build --chown=familion:familion /workspace/target/*.jar /app/app.jar

USER familion
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
