# ---- build stage: compiles, runs the whole test suite and the coverage gate
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q verify

# ---- runtime stage: JRE only, non-root
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/transfereasy.jar app.jar
COPY --from=build /src/target/lib lib
USER app
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=10 \
  CMD ["java", "-cp", "app.jar:lib/*", "br.com.tiagotds.transfereasy.HealthCheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
