FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
# Includes the inventory/scale controllers, Flyway migrations and Java AI models.
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /build/target/iot-0.0.1-SNAPSHOT.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh
USER app
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
