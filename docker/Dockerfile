# syntax=docker/dockerfile:1.7
# Build context: build/ (produced by scripts/assemble.sh)
#   docker build -f docker/Dockerfile -t ecmr-backend build

# eSeal signer/verifier from pinned source (see upstream.lock)
FROM eclipse-temurin:21-jdk AS eseal
WORKDIR /eseal
COPY eseal/ .
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon -q :signer:publishToMavenLocal :verifier:publishToMavenLocal -x test

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY --from=eseal /root/.m2/repository/org/openlogisticsfoundation/ecmr/ /eseal-repo/
COPY src/pom.xml .
COPY src/cargotrack/ cargotrack/
# Replace any registry copy of signer/verifier in the cached local repo with the source build
RUN --mount=type=cache,target=/root/.m2 \
    rm -rf /root/.m2/repository/org/openlogisticsfoundation/ecmr/signer /root/.m2/repository/org/openlogisticsfoundation/ecmr/verifier \
 && mkdir -p /root/.m2/repository/org/openlogisticsfoundation/ecmr \
 && cp -R /eseal-repo/. /root/.m2/repository/org/openlogisticsfoundation/ecmr/ \
 && mvn -B -q dependency:go-offline \
 && sh cargotrack/verify-olf-artifacts.sh
COPY src/src/ src/
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
