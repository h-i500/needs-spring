# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# ↓ これを追加（healthcheck用）
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar
EXPOSE 10081
ENTRYPOINT ["java","-jar","/app/app.jar"]
