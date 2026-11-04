# Build stages run on the build host's platform; only the runtime stage is per-target.
# Multi-arch: docker buildx build --platform linux/amd64,linux/arm64 -t koifolio .

FROM --platform=$BUILDPLATFORM node:24-alpine AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-25 AS server
WORKDIR /src
COPY pom.xml ./
COPY core/pom.xml core/
COPY server/pom.xml server/
RUN mvn -q -B dependency:go-offline
COPY core/src core/src
COPY server/src server/src
COPY --from=web /src/web/dist server/src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 koifolio
WORKDIR /app
COPY --from=server /src/server/target/koifolio.jar app.jar
USER koifolio
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
