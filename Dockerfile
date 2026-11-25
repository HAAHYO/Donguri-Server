# 저장소만으로 빌드한다 (Railway 는 저장소를 받아 이 파일로 빌드하고, CD 도 같은 파일로 이미지를 만든다).
# 테스트는 CI 의 ./gradlew check 가 맡고, 여기서는 jar 만 만든다.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src/main src/main
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/build/libs/app.jar app.jar
USER app
EXPOSE 8080
# Render free 인스턴스(512 MB, 0.1 CPU)에 맞춘다: 힙을 컨테이너 메모리의 60% 로, GC 스레드를 하나로
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
