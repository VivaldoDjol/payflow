# STAGE 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# STAGE 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for health check
RUN apk add --no-cache curl

# Copy the built JAR from builder stage
COPY --from=builder /app/target/payflow-*.jar app.jar

# Create non-root user (security best practice)
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application with optimized JVM settings
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]