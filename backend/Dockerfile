# =============================================================================
# Gemek Premium Backend — Multi-stage Dockerfile
# Stage 1: Build with Maven
# Stage 2: Run with minimal JRE image
# =============================================================================

# --- Stage 1: Build ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching for dependency downloads.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the JAR.
COPY src ./src
RUN mvn package -DskipTests -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root user for security best practice.
RUN addgroup -S gemek && adduser -S gemek -G gemek

COPY --from=builder /app/target/gemek-backend-*.jar app.jar

RUN chown gemek:gemek app.jar
USER gemek

EXPOSE 8080

# Enable virtual threads and set reasonable JVM memory defaults.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
