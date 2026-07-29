# ============================================
# StocKKnock Backend — Render용 multi-stage build
# Java 17 / Spring Boot 3.3 / Gradle Wrapper
# ============================================
# Render 설정:
#   Environment: Docker
#   Root Directory: knockBE
#   Dockerfile Path: ./Dockerfile
# ============================================

# ── Build stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle wrapper + 의존성 정의만 먼저 복사 (캐시 활용)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드 (테스트는 배포 빌드에서 스킵)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test \
    && JAR=$(ls build/libs/*.jar | grep -v plain | head -n1) \
    && cp "$JAR" /app/app.jar

# ── Runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 비-root 사용자
RUN groupadd -r app && useradd -r -g app app

# bootJar 결과물만 복사 (-plain.jar 제외, build stage에서 골라둠)
COPY --from=build /app/app.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app

# Render 가 주입하는 PORT 사용 (기본 8080)
ENV PORT=8080
EXPOSE 8080

# spring.profiles.active 는 Render Environment Variables 로 설정
#   예: SPRING_PROFILES_ACTIVE=prod  또는  supabase
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar /app/app.jar"]
