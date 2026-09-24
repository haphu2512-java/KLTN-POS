# Stage 1: Build JAR using Maven & Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and package application
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal Runtime with JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S posgroup && adduser -S posuser -G posgroup
USER posuser

# Copy built artifact from builder
COPY --from=builder /build/target/pos-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
