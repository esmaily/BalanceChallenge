FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S balance && adduser -S balance -G balance
WORKDIR /app
COPY --from=build /workspace/target/balance-challenge-*.jar app.jar

USER balance
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
