# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Собираем, пропуская тесты (для скорости)
RUN mvn clean package -DskipTests

# Run stage - ИСПОЛЬЗУЕМ ОБЫЧНЫЙ LINUX (НЕ ALPINE)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/cirno-1.0-SNAPSHOT.jar bot.jar

# Настройки памяти для 2GB сервера:
# Бот получает 512MB. Этого достаточно для логики.
# ZGC оставляем для плавности.
CMD ["java", "-Xmx512m", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "bot.jar"]