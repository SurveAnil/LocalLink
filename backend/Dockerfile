FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace/backend
COPY backend/pom.xml pom.xml
COPY backend/.mvn .mvn
COPY backend/mvnw mvnw
RUN chmod +x mvnw
COPY backend/src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
