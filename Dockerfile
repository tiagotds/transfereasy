# ---- build stage: compiles, runs unit + integration tests and the coverage gate
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon -q build installDist

# ---- runtime stage: JRE only, non-root
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/build/install/transfereasy/lib lib
USER app
ENV HTTP_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=10 \
  CMD ["java", "-cp", "lib/*", "br.com.tiagotds.transfereasy.HealthCheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "lib/*", "br.com.tiagotds.transfereasy.Main"]
