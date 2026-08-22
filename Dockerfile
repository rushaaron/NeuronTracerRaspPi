# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY src ./src

# The cache mount keeps Maven from re-downloading its plugins on every rebuild.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre

# fontconfig and freetype keep java.desktop happy on a headless ARM box;
# curl is only here for the container health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin tracer

WORKDIR /app
COPY --from=build /build/target/neuron-tracer.jar ./neuron-tracer.jar

USER tracer

ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.awt.headless=true"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/healthz" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/neuron-tracer.jar"]
