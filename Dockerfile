# =============================================================================
# Multi-Stage Production Dockerfile for Enterprise Spring Boot on Java 21 LTS
# =============================================================================

# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Cache maven dependencies
COPY pom.xml .
COPY .mvn .mvn
RUN apt-get update && apt-get install -y maven && mvn dependency:go-offline -B || true

# Compile application
COPY src src
RUN mvn clean package -DskipTests

# Extract layers for optimized container caching
WORKDIR /workspace/target
RUN java -Djarmode=layertools -jar *.jar extract

# Stage 2: Minimal Distroless / Hardened Runtime Container
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as non-root unprivileged user
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy layered spring components
COPY --from=builder /workspace/target/dependencies/ ./
COPY --from=builder /workspace/target/spring-boot-loader/ ./
COPY --from=builder /workspace/target/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/application/ ./

# High-concurrency JVM performance tuning for 16-vCPU, 32GB RAM Hostinger VPS
ENV JAVA_TOOL_OPTIONS="\
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+ParallelRefProcEnabled \
    -XX:InitiatingHeapOccupancyPercent=45 \
    -XX:G1ReservePercent=15 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

EXPOSE 8002 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8002/api/v1/ping || curl -f http://localhost:8080/api/v1/ping || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

