# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/

COPY common/src common/src
COPY auth-service/src auth-service/src

RUN mvn clean package -pl auth-service -am -DskipTests -B

# Stage 2: Runtime with JRE 17
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache dumb-init

RUN addgroup -S delivery && adduser -S delivery -G delivery

WORKDIR /app

COPY --from=builder /app/auth-service/target/*.jar app.jar

RUN chown -R delivery:delivery /app

USER delivery

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["dumb-init", "--"]

CMD ["java", "-jar", "app.jar"]
