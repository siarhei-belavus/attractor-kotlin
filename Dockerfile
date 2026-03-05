# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Download dependencies in a separate layer for better caching
RUN --mount=type=cache,target=/root/.gradle,id=gradle-$TARGETARCH \
    chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle,id=gradle-$TARGETARCH \
    ./gradlew releaseJar --no-daemon -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
        # required tools
        graphviz \
        git \
        # optional tools
        python3 \
        ruby \
        nodejs \
        npm \
        golang-go \
        rustc \
        cargo \
        gcc \
        g++ \
        clang \
        make \
        gradle \
        maven \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/build/libs/attractor-server-*.jar /app/attractor-server.jar

# Persist the SQLite database outside the container
VOLUME /app/data
ENV ATTRACTOR_DB_NAME=/app/data/attractor.db

# LLM provider API keys — supply at runtime via --env-file or -e flags
ENV ANTHROPIC_API_KEY=""
ENV OPENAI_API_KEY=""
ENV GEMINI_API_KEY=""
ENV GOOGLE_API_KEY=""

# Custom OpenAI-compatible API (Ollama, LM Studio, vLLM, etc.)
# These bootstrap the settings on first start; values saved via the UI take precedence.
ENV ATTRACTOR_CUSTOM_API_ENABLED=""
ENV ATTRACTOR_CUSTOM_API_HOST=""
ENV ATTRACTOR_CUSTOM_API_PORT=""
ENV ATTRACTOR_CUSTOM_API_KEY=""
ENV ATTRACTOR_CUSTOM_API_MODEL=""

EXPOSE 7070

ENTRYPOINT ["java", "-jar", "/app/attractor-server.jar"]
CMD ["--web-port", "7070"]
