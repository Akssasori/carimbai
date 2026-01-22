# Stage 1: Build
FROM maven:3.9-openjdk-25 AS build
LABEL authors="ludiniz"
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:25-jdk-slim
WORKDIR /app
COPY --from=build /app/target/carimbai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
