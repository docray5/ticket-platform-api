# This is configured specifically for render's free tier

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Dserver.port=${PORT:-8080} \
  -Djava.security.egd=file:/dev/./urandom \
  -jar app.jar"]