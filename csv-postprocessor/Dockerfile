# ===== build =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /work
COPY pom.xml .
RUN mvn -q -e -U -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ===== run =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /work/target/csv-postprocessor-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 10082
ENTRYPOINT ["java","-jar","/app/app.jar"]
