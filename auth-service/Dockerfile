FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -q -B -pl auth-service -am package -DskipTests && \
    mkdir -p /out && cp auth-service/target/auth-service-1.0.0.jar /out/app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /out/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
