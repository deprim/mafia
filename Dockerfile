FROM maven:3.10.0-eclipse-temurin-21-noble AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /build/target/mafia-game-*.jar app.jar

ENV SERVER_PORT=3200
EXPOSE 3200
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
