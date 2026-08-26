# ---- Build stage ----------------------------------------------------------
# Uses a full JDK + Maven to compile and package the app into a jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the POM and sources, then build the jar in a single step. Tests are skipped
# here — CI runs them; baking Testcontainers into the image would need Docker-in-Docker.
#
# We deliberately do NOT run `dependency:go-offline`: it fires hundreds of requests at
# Maven Central and, on shared CI IPs, trips its rate limit (HTTP 429). `package` fetches
# only what's actually needed, and the retry flags below ride out transient 429/timeout
# blips (native resolver + wagon names, so it works whichever transport Maven picks).
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests \
      -Daether.connector.http.retryHandler.count=5 \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
      clean package

# ---- Runtime stage --------------------------------------------------------
# A slim JRE (no compiler/Maven) — smaller image, smaller attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user (security best practice; K8s securityContext expects this).
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

# Container-friendly JVM flags: honour cgroup memory limits set by Docker/K8s.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

EXPOSE 8080

# Spring Boot actuator health endpoint powers the Docker/K8s health checks.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
