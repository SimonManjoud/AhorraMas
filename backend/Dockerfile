# Núcleo Ahorra+ (monolito modular) — imagen multi-etapa
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system ahorra && mkdir -p /app/data && chown ahorra /app/data
COPY --from=build /app/target/nucleo-*.jar /app/nucleo.jar
USER ahorra
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=America/Caracas", "-jar", "/app/nucleo.jar"]
