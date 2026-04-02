FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
	CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]