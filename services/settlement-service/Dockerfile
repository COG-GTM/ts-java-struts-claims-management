# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
# Optional Maven repository mirror for hosts that cannot reach Maven Central,
# e.g. --build-arg MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2
ARG MAVEN_MIRROR=
WORKDIR /build
RUN if [ -n "$MAVEN_MIRROR" ]; then \
      mkdir -p /root/.m2 && \
      printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR" > /root/.m2/settings.xml; \
    fi
COPY pom.xml ./
COPY config config
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 settlement
COPY --from=build /build/target/settlement-service-*.jar /app/settlement-service.jar
USER settlement
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/settlement-service.jar"]
