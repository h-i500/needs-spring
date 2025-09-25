# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data/output /data/error
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar
EXPOSE 10080
ENTRYPOINT ["java","-jar","/app/app.jar"]
