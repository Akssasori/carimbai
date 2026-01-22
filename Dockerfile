# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
LABEL authors="ludiniz"
WORKDIR /app

# Instalar Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/carimbai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
