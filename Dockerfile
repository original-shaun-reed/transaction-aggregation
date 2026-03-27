# Multi-stage Dockerfile for Transaction Aggregator API Service
# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and all module poms
COPY pom.xml .
COPY api-service/pom.xml api-service/
COPY common/pom.xml common/
COPY ingestor-service/pom.xml ingestor-service/
COPY processing-service/pom.xml processing-service/
COPY mock-source-service/pom.xml mock-source-service/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY . .

# Build the project (skip tests for faster build)
RUN mvn clean package -Dmaven.test.skip=true -pl -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR from builder stage
COPY --from=builder /app/api-service/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]