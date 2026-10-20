# syntax=docker/dockerfile:1.7
# Build context: build/src (produced by scripts/assemble.sh)
#   docker build -f docker/Dockerfile -t ecmr-backend build/src

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY cargotrack/ cargotrack/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline  && sh cargotrack/verify-olf-artifacts.sh
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Dmaven.source.skip=true -Dmaven.javadoc.skip=true \
 && cp target/ecmr-backend-*.jar /src/app.jar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get upgrade -y && apt-get clean && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app ecmr \
 && mkdir -p /app /data/attachments && chown -R ecmr /app /data
COPY --from=build /src/app.jar /app/ecmr-backend.jar
USER ecmr
WORKDIR /app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=cargotrack \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/ecmr-backend.jar"]
