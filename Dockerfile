FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml owasp-suppressions.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:24-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/trade-store-*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD curl -sf http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
