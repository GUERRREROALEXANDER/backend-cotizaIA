# =========================
# Build stage
# =========================
FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Normalize line endings and the exec bit so a build from a Windows checkout also works.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src ./src
RUN ./mvnw -q -B clean package -DskipTests

# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN useradd --system --uid 1001 appuser

COPY --from=build /workspace/target/backend-cotizaia.jar app.jar

USER appuser

# Production profile by default; Render's free instance has 512 MB, so cap the heap
# relative to the container and use the low-footprint serial collector.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080

# No Docker HEALTHCHECK: the JRE image has no curl, and Render already probes
# /actuator/health (healthCheckPath in render.yaml).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
