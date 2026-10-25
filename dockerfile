# Build the application using Java 21 and Maven
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .

RUN mvn -f backend/pom.xml -DskipTests clean package

# Run the application using Java 21
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/backend/target/*.jar /app/app.jar

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
Paste these into the docker file