# Simuladores de sistemas externos (bancos, suiche, BCV, cripto, KYC, LLM, notificaciones)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/simuladores-*.jar /app/simuladores.jar
EXPOSE 8090
ENTRYPOINT ["java", "-Duser.timezone=America/Caracas", "-jar", "/app/simuladores.jar"]
